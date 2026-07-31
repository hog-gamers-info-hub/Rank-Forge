import {
  buildGoogleJwtClaims,
  buildGoogleJwtHeader,
  createGoogleServiceAccountAssertion,
  exchangeGoogleToken,
  GOOGLE_SHEETS_SCOPE,
  GOOGLE_TOKEN_ENDPOINT,
  importGooglePrivateKey,
  normalizePrivateKey,
  verifySpreadsheetAccess,
} from "../_shared/google.ts";
import { EdgeFunctionError } from "../_shared/errors.ts";
import { type FetchImplementation } from "../_shared/http.ts";
import { handleRequest } from "./index.ts";

const TEST_ENV: Record<string, string> = {
  SUPABASE_URL: "https://supabase.invalid",
  SUPABASE_ANON_KEY: "anon-test-key",
  GOOGLE_SHEETS_CLIENT_EMAIL: "service-account@example.invalid",
  GOOGLE_SHEETS_PRIVATE_KEY: "synthetic-private-key",
  GOOGLE_SHEETS_SPREADSHEET_ID: "sheet/id",
};

type Call = { url: string; method: string; headers: Headers; body: string };
type JsonBody = {
  ok?: boolean;
  operation?: string;
  spreadsheet_access?: string;
  error?: { code?: string; message?: string };
};

function assert(
  condition: unknown,
  message = "assertion failed",
): asserts condition {
  if (!condition) throw new Error(message);
}

function assertEquals<T>(actual: T, expected: T, message = "mismatch") {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(
      `${message}: ${JSON.stringify(actual)} !== ${JSON.stringify(expected)}`,
    );
  }
}

function assertIncludes(actual: string, expected: string) {
  assert(
    actual.includes(expected),
    `expected ${actual} to include ${expected}`,
  );
}

function decodeBase64Url(value: string): string {
  const base64 = value.replace(/-/g, "+").replace(/_/g, "/");
  return atob(base64 + "=".repeat((4 - base64.length % 4) % 4));
}

async function assertRejects(
  operation: () => Promise<unknown>,
  code: string,
  status?: number,
) {
  try {
    await operation();
  } catch (error) {
    assert(error instanceof EdgeFunctionError);
    assertEquals(error.code, code);
    if (status !== undefined) assertEquals(error.status, status);
    return;
  }
  throw new Error(`expected rejection with ${code}`);
}

function envWith(overrides: Record<string, string | undefined> = {}) {
  return (name: string) => {
    if (Object.prototype.hasOwnProperty.call(overrides, name)) {
      return overrides[name];
    }
    return TEST_ENV[name];
  };
}

function responseJson(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), { status });
}

function makeFetch(
  responder: (call: Call, index: number) => Response | Promise<Response>,
) {
  const calls: Call[] = [];
  const fetchImpl: FetchImplementation = async (input, init = {}) => {
    const call: Call = {
      url: String(input),
      method: init.method ?? "GET",
      headers: new Headers(init.headers),
      body: typeof init.body === "string" ? init.body : "",
    };
    calls.push(call);
    return await responder(call, calls.length - 1);
  };
  return { fetchImpl, calls };
}

function request(body: unknown, authorization = "Bearer supabase-token") {
  return new Request("https://function.invalid", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(authorization ? { Authorization: authorization } : {}),
    },
    body: JSON.stringify(body),
  });
}

async function jsonBody(response: Response): Promise<JsonBody> {
  return await response.json() as JsonBody;
}

const injectedSigner = () => Promise.resolve(new Uint8Array([1, 2, 3]));

function standardSuccessFetch() {
  return makeFetch((_, index) => {
    if (index === 0) return responseJson({ id: "user-id" });
    if (index === 1) {
      return responseJson({
        access_token: "google-token",
        token_type: "Bearer",
      });
    }
    return responseJson({ spreadsheetId: "metadata-must-not-leak" });
  });
}

Deno.test("POST verify_connection succeeds and follows safe call order", async () => {
  const { fetchImpl, calls } = standardSuccessFetch();
  const response = await handleRequest(
    request({ operation: "verify_connection" }),
    {
      env: envWith(),
      fetchImpl,
      signer: injectedSigner,
      clock: () => 1_700_000_000,
    },
  );
  assertEquals(response.status, 200);
  assertEquals(response.headers.get("content-type"), "application/json");
  assertEquals(await jsonBody(response), {
    ok: true,
    operation: "verify_connection",
    spreadsheet_access: "verified",
  });
  assertEquals(calls.map((call) => call.method), ["GET", "POST", "GET"]);
  assertEquals(calls[0].headers.get("authorization"), "Bearer supabase-token");
  assertEquals(
    calls[1].headers.get("content-type"),
    "application/x-www-form-urlencoded",
  );
  const form = new URLSearchParams(calls[1].body);
  assertEquals(
    form.get("grant_type"),
    "urn:ietf:params:oauth:grant-type:jwt-bearer",
  );
  assert(form.get("assertion") !== null);
  assertIncludes(calls[2].url, "fields=spreadsheetId");
  assertIncludes(calls[2].url, "sheet%2Fid");
  assertEquals(calls[2].headers.get("authorization"), "Bearer google-token");
  assert(
    !JSON.stringify({
      ok: true,
      operation: "verify_connection",
      spreadsheet_access: "verified",
    }).includes("metadata"),
  );
});

Deno.test("unsupported methods return METHOD_NOT_ALLOWED", async () => {
  const response = await handleRequest(
    new Request("https://function.invalid", { method: "GET" }),
    {
      env: envWith(),
      fetchImpl: makeFetch(() => responseJson({})).fetchImpl,
    },
  );
  assertEquals(response.status, 405);
  assertEquals((await jsonBody(response)).error?.code, "METHOD_NOT_ALLOWED");
});

Deno.test("invalid JSON, missing operation, unsupported operation, and extra fields are rejected", async () => {
  const invalidJson = new Request("https://function.invalid", {
    method: "POST",
    body: "not-json",
  });
  const invalidJsonResponse = await handleRequest(invalidJson, {
    env: envWith(),
  });
  assertEquals(invalidJsonResponse.status, 400);
  assertEquals(
    (await jsonBody(invalidJsonResponse)).error?.code,
    "INVALID_JSON",
  );
  for (
    const body of [{}, { operation: "other" }, {
      operation: "verify_connection",
      extra: true,
    }]
  ) {
    const response = await handleRequest(request(body), { env: envWith() });
    assertEquals(response.status, 400);
    assertEquals((await jsonBody(response)).error?.code, "INVALID_OPERATION");
  }
});

Deno.test("missing or malformed authorization returns UNAUTHORIZED before Supabase", async () => {
  const calls: Call[] = [];
  const fetchImpl: FetchImplementation = (input, init) => {
    calls.push({
      url: String(input),
      method: init?.method ?? "GET",
      headers: new Headers(init?.headers),
      body: "",
    });
    return Promise.resolve(responseJson({ id: "unexpected" }));
  };
  for (const authorization of ["", "Basic token", "Bearer", "Bearer one two"]) {
    const response = await handleRequest(
      request({ operation: "verify_connection" }, authorization),
      { env: envWith(), fetchImpl },
    );
    assertEquals(response.status, 401);
    assertEquals((await jsonBody(response)).error?.code, "UNAUTHORIZED");
  }
  assertEquals(calls.length, 0);
});

Deno.test("invalid Supabase user response and auth failures are safe", async () => {
  const invalidUser = makeFetch(() => responseJson({ email: "missing-id" }));
  const invalidUserResponse = await handleRequest(
    request({ operation: "verify_connection" }),
    { env: envWith(), fetchImpl: invalidUser.fetchImpl },
  );
  assertEquals(invalidUserResponse.status, 401);
  assertEquals(
    (await jsonBody(invalidUserResponse)).error?.code,
    "UNAUTHORIZED",
  );
  assertEquals(invalidUser.calls.length, 1);

  const rawSecret = "supabase-raw-error-secret";
  const authFailure = makeFetch(() => Promise.reject(new Error(rawSecret)));
  const authFailureResponse = await handleRequest(
    request({ operation: "verify_connection" }),
    { env: envWith(), fetchImpl: authFailure.fetchImpl },
  );
  const body = JSON.stringify(await jsonBody(authFailureResponse));
  assertEquals(authFailureResponse.status, 502);
  assertIncludes(body, "SUPABASE_AUTH_FAILURE");
  assert(!body.includes(rawSecret));
  assertEquals(authFailure.calls.length, 1);
});

Deno.test("missing Google configuration fails closed after authentication", async () => {
  const { fetchImpl, calls } = makeFetch((_, index) =>
    index === 0 ? responseJson({ id: "user-id" }) : responseJson({})
  );
  const response = await handleRequest(
    request({ operation: "verify_connection" }),
    {
      env: envWith({ GOOGLE_SHEETS_PRIVATE_KEY: undefined }),
      fetchImpl,
    },
  );
  assertEquals(response.status, 500);
  assertEquals((await jsonBody(response)).error?.code, "GOOGLE_CONFIG_MISSING");
  assertEquals(calls.length, 1);
});

Deno.test("private-key newline normalization and malformed credentials are handled", async () => {
  assertEquals(normalizePrivateKey("line-1\\nline-2"), "line-1\nline-2");
  await assertRejects(
    () => importGooglePrivateKey("not-a-pem"),
    "GOOGLE_CREDENTIAL_INVALID",
    500,
  );
});

Deno.test("JWT header, claims, scope, audience, and lifetime are approved", async () => {
  assertEquals(buildGoogleJwtHeader(), { alg: "RS256", typ: "JWT" });
  const claims = buildGoogleJwtClaims("service@example.invalid", 1_700_000_000);
  assertEquals(claims.iss, "service@example.invalid");
  assertEquals(claims.scope, GOOGLE_SHEETS_SCOPE);
  assertEquals(claims.aud, GOOGLE_TOKEN_ENDPOINT);
  assert(claims.exp - claims.iat <= 3600);

  let signingInput = "";
  const assertion = await createGoogleServiceAccountAssertion({
    clientEmail: "service@example.invalid",
    privateKey: "synthetic",
    spreadsheetId: "sheet-id",
  }, {
    issuedAtSeconds: 1_700_000_000,
    signer: (input) => {
      signingInput = new TextDecoder().decode(input);
      return Promise.resolve(new Uint8Array([255]));
    },
  });
  const parts = assertion.split(".");
  assertEquals(parts.length, 3);
  const decodedClaims = JSON.parse(decodeBase64Url(parts[1]));
  assertEquals(decodedClaims.scope, GOOGLE_SHEETS_SCOPE);
  assertIncludes(signingInput, "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9");
});

Deno.test("OAuth exchange uses form-urlencoded and rejects malformed or denied responses", async () => {
  let requestBody = "";
  const success = makeFetch((call) => {
    requestBody = call.body;
    return responseJson({ access_token: "access-token", token_type: "bearer" });
  });
  assertEquals(
    await exchangeGoogleToken("signed-assertion", {
      fetchImpl: success.fetchImpl,
      timeoutMs: 100,
    }),
    "access-token",
  );
  const form = new URLSearchParams(requestBody);
  assertEquals(
    form.get("grant_type"),
    "urn:ietf:params:oauth:grant-type:jwt-bearer",
  );
  assertEquals(form.get("assertion"), "signed-assertion");

  const malformed = makeFetch(() => responseJson({ token_type: "Bearer" }));
  await assertRejects(
    () =>
      exchangeGoogleToken("assertion", {
        fetchImpl: malformed.fetchImpl,
        timeoutMs: 100,
      }),
    "GOOGLE_TOKEN_RESPONSE_INVALID",
    502,
  );
  const rawBody = "oauth-secret-description";
  const denied = makeFetch(() => new Response(rawBody, { status: 400 }));
  await assertRejects(
    () =>
      exchangeGoogleToken("assertion", {
        fetchImpl: denied.fetchImpl,
        timeoutMs: 100,
      }),
    "GOOGLE_TOKEN_FAILURE",
    502,
  );
});

Deno.test("Sheets status mappings are read-only and Drive-free", async () => {
  for (
    const [status, code, expectedErrorStatus] of [
      [403, "GOOGLE_SHEETS_ACCESS_DENIED", 403],
      [404, "GOOGLE_SHEETS_NOT_FOUND", 404],
      [429, "GOOGLE_API_RATE_LIMITED", 429],
      [500, "GOOGLE_API_FAILURE", 502],
    ] as const
  ) {
    const calls: Call[] = [];
    const fetchImpl: FetchImplementation = (input, init) => {
      calls.push({
        url: String(input),
        method: init?.method ?? "GET",
        headers: new Headers(init?.headers),
        body: "",
      });
      return Promise.resolve(
        new Response("raw-google-body-secret", { status }),
      );
    };
    await assertRejects(
      () =>
        verifySpreadsheetAccess("google-token", "sheet-id", {
          fetchImpl,
          timeoutMs: 100,
        }),
      code,
      expectedErrorStatus,
    );
    assertEquals(calls[0].method, "GET");
    assertIncludes(calls[0].url, "sheets.googleapis.com");
  }
});

Deno.test("OAuth failure prevents Sheets access and timeout maps safely", async () => {
  const oauthFailure = makeFetch((_, index) =>
    index === 0
      ? responseJson({ id: "user-id" })
      : new Response("raw-oauth-error", { status: 500 })
  );
  const oauthResponse = await handleRequest(
    request({ operation: "verify_connection" }),
    {
      env: envWith(),
      fetchImpl: oauthFailure.fetchImpl,
      signer: injectedSigner,
    },
  );
  const oauthBody = JSON.stringify(await jsonBody(oauthResponse));
  assertEquals(oauthResponse.status, 502);
  assertIncludes(oauthBody, "GOOGLE_TOKEN_FAILURE");
  assert(!oauthBody.includes("raw-oauth-error"));
  assertEquals(oauthFailure.calls.length, 2);

  const sheetsFailure = makeFetch((_, index) => {
    if (index === 0) return responseJson({ id: "user-id" });
    if (index === 1) {
      return responseJson({
        access_token: "google-token",
        token_type: "Bearer",
      });
    }
    return new Response("raw-sheets-error", { status: 403 });
  });
  const sheetsResponse = await handleRequest(
    request({ operation: "verify_connection" }),
    {
      env: envWith(),
      fetchImpl: sheetsFailure.fetchImpl,
      signer: injectedSigner,
    },
  );
  const sheetsBody = JSON.stringify(await jsonBody(sheetsResponse));
  assertEquals(sheetsResponse.status, 403);
  assertIncludes(sheetsBody, "GOOGLE_SHEETS_ACCESS_DENIED");
  assert(!sheetsBody.includes("raw-sheets-error"));

  const timeoutFetch: FetchImplementation = (_input, init) =>
    new Promise((_resolve, reject) => {
      init?.signal?.addEventListener(
        "abort",
        () => reject(new Error("aborted")),
        { once: true },
      );
    });
  const timeoutResponse = await handleRequest(
    request({ operation: "verify_connection" }),
    {
      env: envWith(),
      fetchImpl: timeoutFetch,
      signer: injectedSigner,
      timeouts: { supabaseAuth: 1 },
    },
  );
  assertEquals(timeoutResponse.status, 504);
  assertEquals(
    (await jsonBody(timeoutResponse)).error?.code,
    "UPSTREAM_TIMEOUT",
  );
});

Deno.test("OAuth and Sheets requests each enforce their own timeout", async () => {
  const timeoutFetch: FetchImplementation = (_input, init) =>
    new Promise((_resolve, reject) => {
      init?.signal?.addEventListener(
        "abort",
        () => reject(new Error("aborted")),
        { once: true },
      );
    });
  await assertRejects(
    () =>
      exchangeGoogleToken("assertion", {
        fetchImpl: timeoutFetch,
        timeoutMs: 1,
      }),
    "UPSTREAM_TIMEOUT",
    504,
  );
  await assertRejects(
    () =>
      verifySpreadsheetAccess("token", "sheet-id", {
        fetchImpl: timeoutFetch,
        timeoutMs: 1,
      }),
    "UPSTREAM_TIMEOUT",
    504,
  );
});

Deno.test("no secret appears in error responses", async () => {
  const secret = "private-secret-value";
  const response = await handleRequest(
    request({ operation: "verify_connection" }),
    {
      env: envWith({ GOOGLE_SHEETS_PRIVATE_KEY: secret }),
      fetchImpl: makeFetch(() => responseJson({ id: "user-id" })).fetchImpl,
    },
  );
  const body = JSON.stringify(await jsonBody(response));
  assert(!body.includes(secret));
  assert(!body.includes("google-token"));
  assert(!body.includes("sheet/id"));
});
