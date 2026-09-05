import {
  accountStoragePrefixes,
  deleteOwnedStorageObjects,
} from "../_shared/accountDeletionStorage.ts";
import type { FetchImplementation } from "../_shared/http.ts";
import { MATCH_EXPORT_COLUMNS } from "../_shared/matchExport.ts";
import { STANDINGS_EXPORT_COLUMNS } from "../_shared/standingsExport.ts";
import { handleRequest } from "./index.ts";

const SUPABASE_URL = "https://example.supabase.co";
const ANON_KEY = "anon-secret";
const SERVICE_ROLE_KEY = "service-role-secret";
const GOOGLE_PRIVATE_KEY = "google-private-key";
const GOOGLE_ACCESS_TOKEN = "google-access-token";
const USER_A = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
const USER_B = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
const TOURNAMENT_A = "11111111-1111-4111-8111-111111111111";
const TOURNAMENT_B = "22222222-2222-4222-8222-222222222222";

interface Call {
  method: string;
  url: URL;
  body: string | null;
}

interface FakeState {
  userId?: string;
  tournamentPages?: string[][];
  barrierActiveExportOperations?: number;
  barrierResponse?: unknown;
  purgeResponse?: unknown;
  residualTable?: string;
  googleFailure?: boolean;
  storageFailure?: boolean;
  storageDeleteFailure?: boolean;
  finalStorageObject?: boolean;
  authDeleteStatus?: number;
  calls: Call[];
  storageListBodies: Array<{ bucket: string; body: Record<string, unknown> }>;
}

function assert(
  condition: unknown,
  message = "assertion failed",
): asserts condition {
  if (!condition) throw new Error(message);
}

function assertEquals(
  actual: unknown,
  expected: unknown,
  message = "values differ",
): void {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(
      `${message}: ${JSON.stringify(actual)} !== ${JSON.stringify(expected)}`,
    );
  }
}

function responseJson(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function env(overrides: Record<string, string | undefined> = {}) {
  const values: Record<string, string | undefined> = {
    SUPABASE_URL,
    SUPABASE_ANON_KEY: ANON_KEY,
    SUPABASE_SERVICE_ROLE_KEY: SERVICE_ROLE_KEY,
    GOOGLE_SHEETS_CLIENT_EMAIL: "service@example.test",
    GOOGLE_SHEETS_PRIVATE_KEY: GOOGLE_PRIVATE_KEY,
    GOOGLE_SHEETS_SPREADSHEET_ID: "spreadsheet-id",
    ...overrides,
  };
  return (name: string) => values[name];
}

function options(state: FakeState, overrides: Record<string, unknown> = {}) {
  return {
    env: env(),
    fetchImpl: makeFetch(state),
    signer: async () => new Uint8Array([1, 2, 3]),
    timeouts: {
      supabaseAuth: 100,
      supabaseAccount: 100,
      googleToken: 100,
      googleSheets: 100,
      storage: 100,
      authDelete: 100,
    },
    ...overrides,
  };
}

function validState(overrides: Partial<FakeState> = {}): FakeState {
  return {
    userId: USER_A,
    tournamentPages: [[TOURNAMENT_A]],
    purgeResponse: [{
      deleted_tournaments: 1,
      deleted_custom_designs: 0,
      deleted_deletion_receipts: 0,
      deleted_export_operations: 0,
    }],
    calls: [],
    storageListBodies: [],
    ...overrides,
  };
}

function path(url: URL): string {
  return decodeURIComponent(url.pathname);
}

function makeFetch(state: FakeState): FetchImplementation {
  let tournamentRead = 0;
  let googleMutated = false;

  return async (input, init) => {
    const url = new URL(String(input));
    const call: Call = {
      method: init?.method ?? "GET",
      url,
      body: typeof init?.body === "string" ? init.body : null,
    };
    state.calls.push(call);

    if (path(url) === "/auth/v1/user") {
      return responseJson({ id: state.userId ?? USER_A });
    }

    if (path(url) === "/auth/v1/admin/users/" + (state.userId ?? USER_A)) {
      return new Response(null, { status: state.authDeleteStatus ?? 204 });
    }

    if (path(url) === "/rest/v1/rpc/begin_account_deletion") {
      return responseJson(
        state.barrierResponse ?? [{
          state: "deleting",
          active_export_operations: state.barrierActiveExportOperations ?? 0,
        }],
      );
    }

    if (path(url) === "/rest/v1/tournaments" && call.method === "GET") {
      if (url.searchParams.get("select") !== "id") {
        return responseJson(
          state.residualTable === "tournaments" ? [{ owner_id: USER_A }] : [],
        );
      }
      const pages = state.tournamentPages ?? [[]];
      const rows = pages[Math.min(tournamentRead++, pages.length - 1)] ?? [];
      return responseJson(rows.map((id) => ({ id })));
    }

    if (path(url) === "/rest/v1/rpc/purge_account_data") {
      if (state.purgeResponse instanceof Error) throw state.purgeResponse;
      return responseJson(state.purgeResponse ?? []);
    }

    if (path(url).startsWith("/rest/v1/") && call.method === "GET") {
      if (
        state.residualTable && path(url) === `/rest/v1/${state.residualTable}`
      ) {
        return responseJson([{ owner_id: USER_A }]);
      }
      return responseJson([]);
    }

    if (url.hostname === "oauth2.googleapis.com") {
      if (state.googleFailure) {
        return responseJson({ error: "google secret" }, 500);
      }
      return responseJson({
        access_token: GOOGLE_ACCESS_TOKEN,
        token_type: "Bearer",
      });
    }

    if (url.hostname === "sheets.googleapis.com") {
      if (state.googleFailure) {
        return responseJson({ error: "google secret" }, 500);
      }
      if (path(url).endsWith("/v4/spreadsheets/spreadsheet-id")) {
        return responseJson({
          sheets: [
            { properties: { title: "Match Results", sheetId: 101 } },
            { properties: { title: "Tournament Standings", sheetId: 202 } },
          ],
        });
      }
      if (path(url).includes("/values/Match Results!A1:")) {
        return responseJson({ values: [MATCH_EXPORT_COLUMNS] });
      }
      if (path(url).includes("/values/Tournament Standings!A1:")) {
        return responseJson({ values: [STANDINGS_EXPORT_COLUMNS] });
      }
      if (path(url).endsWith("/v4/spreadsheets/spreadsheet-id:batchUpdate")) {
        googleMutated = true;
        const body = JSON.parse(call.body ?? "{}") as { requests?: unknown[] };
        return responseJson({
          replies: Array.from(
            { length: body.requests?.length ?? 0 },
            () => ({}),
          ),
        });
      }
      if (path(url).endsWith("/values/Match Results!C2:C")) {
        return responseJson({ values: googleMutated ? [] : [[TOURNAMENT_A]] });
      }
      if (path(url).endsWith("/values/Tournament Standings!C2:C")) {
        return responseJson({ values: googleMutated ? [] : [[TOURNAMENT_A]] });
      }
    }

    if (path(url).startsWith("/storage/v1/object/list/")) {
      const bucket = path(url).split("/").pop() as string;
      const body = call.body === null
        ? {}
        : JSON.parse(call.body) as Record<string, unknown>;
      state.storageListBodies.push({ bucket, body });
      if (state.storageFailure) {
        return responseJson({ error: "storage secret" }, 500);
      }
      if (state.finalStorageObject && state.storageListBodies.length > 6) {
        return responseJson([{ name: "late.png", id: "late-object" }]);
      }
      return responseJson([]);
    }

    if (
      path(url).startsWith("/storage/v1/object/") && call.method === "DELETE"
    ) {
      if (state.storageDeleteFailure) {
        return responseJson({ error: "storage secret" }, 500);
      }
      return responseJson({ message: "ok" });
    }

    throw new Error(`unexpected request ${call.method} ${url}`);
  };
}

function request(
  method = "POST",
  body?: string,
  headers: Record<string, string> = { authorization: `Bearer ${USER_A}` },
): Request {
  return new Request("https://functions.example/delete-account", {
    method,
    headers,
    body,
  });
}

async function body(response: Response): Promise<Record<string, unknown>> {
  return await response.json() as Record<string, unknown>;
}

async function assertError(
  response: Response,
  code: string,
  status: number,
): Promise<void> {
  assertEquals(response.status, status);
  const payload = await body(response);
  assertEquals((payload.error as Record<string, unknown>).code, code);
}

function pathNames(state: FakeState): string[] {
  return state.calls.map((call) => path(call.url));
}

Deno.test("OPTIONS is a CORS preflight and performs no work", async () => {
  const state = validState();
  const response = await handleRequest(
    request("OPTIONS", undefined, {}),
    options(state),
  );
  assertEquals(response.status, 204);
  assertEquals(state.calls.length, 0);
  assertEquals(
    response.headers.get("access-control-allow-methods"),
    "POST, OPTIONS",
  );
});

Deno.test("non-POST methods are rejected", async () => {
  const state = validState();
  await assertError(
    await handleRequest(request("GET"), options(state)),
    "METHOD_NOT_ALLOWED",
    405,
  );
  assertEquals(state.calls.length, 0);
});

Deno.test("missing Authorization is rejected", async () => {
  const state = validState();
  await assertError(
    await handleRequest(request("POST", undefined, {}), options(state)),
    "UNAUTHORIZED",
    401,
  );
  assertEquals(state.calls.length, 0);
});

Deno.test("malformed Bearer authorization is rejected", async () => {
  const state = validState();
  await assertError(
    await handleRequest(
      request("POST", undefined, { authorization: "Bearer a b" }),
      options(state),
    ),
    "UNAUTHORIZED",
    401,
  );
  assertEquals(state.calls.length, 0);
});

Deno.test("invalid Auth user response is rejected", async () => {
  const state = validState({ userId: "" });
  await assertError(
    await handleRequest(request(), options(state)),
    "UNAUTHORIZED",
    401,
  );
  assertEquals(state.calls.length, 1);
});

Deno.test("malformed JSON is rejected before destructive work", async () => {
  const state = validState();
  await assertError(
    await handleRequest(request("POST", "{"), options(state)),
    "INVALID_JSON",
    400,
  );
  assertEquals(state.calls.length, 0);
});

Deno.test("empty body and empty object are accepted", async () => {
  for (const requestBody of [undefined, "{}"] as const) {
    const state = validState({ tournamentPages: [[]] });
    const response = await handleRequest(
      request("POST", requestBody),
      options(state),
    );
    assertEquals(response.status, 200);
    assertEquals(await response.text(), '{"ok":true}');
  }
});

Deno.test("request fields including user IDs are rejected", async () => {
  for (
    const requestBody of [
      JSON.stringify({ user_id: USER_B }),
      JSON.stringify({ uid: USER_B }),
      JSON.stringify({ owner_id: USER_B }),
      JSON.stringify({ anything: true }),
    ]
  ) {
    const state = validState();
    await assertError(
      await handleRequest(request("POST", requestBody), options(state)),
      "INVALID_ACCOUNT_DELETE_REQUEST",
      400,
    );
    assertEquals(state.calls.length, 0);
  }
});

Deno.test("successful deletion returns exactly ok true", async () => {
  const state = validState({ tournamentPages: [[]] });
  const response = await handleRequest(request(), options(state));
  assertEquals(response.status, 200);
  assertEquals(await response.text(), '{"ok":true}');
});

Deno.test("UID comes from validated Auth and scopes the tournament query", async () => {
  const state = validState({ userId: USER_A, tournamentPages: [[]] });
  const response = await handleRequest(request(), options(state));
  assertEquals(response.status, 200);
  const tournamentCall = state.calls.find((call) =>
    path(call.url) === "/rest/v1/tournaments"
  );
  assert(tournamentCall);
  assertEquals(tournamentCall.url.searchParams.get("owner_id"), `eq.${USER_A}`);
  assert(!tournamentCall.url.search.includes(USER_B));
});

Deno.test("successful destructive stages occur in the required order", async () => {
  const state = validState();
  const response = await handleRequest(request(), options(state));
  assertEquals(response.status, 200);
  const names = pathNames(state);
  const first = (value: string) => names.indexOf(value);
  const auth = first("/auth/v1/user");
  const tournamentIndexes = state.calls.flatMap((call, index) =>
    path(call.url) === "/rest/v1/tournaments" ? [index] : []
  );
  const capture = tournamentIndexes[0];
  const barrier = first("/rest/v1/rpc/begin_account_deletion");
  const google = first("/v4/spreadsheets/spreadsheet-id");
  const storage = names.findIndex((name) =>
    name.startsWith("/storage/v1/object/list/")
  );
  const scope = tournamentIndexes[1];
  const purge = first("/rest/v1/rpc/purge_account_data");
  const dbVerify = names.findIndex((name) =>
    name === "/rest/v1/custom_design_templates"
  );
  const authDelete = names.findIndex((name) =>
    name.startsWith("/auth/v1/admin/users/")
  );
  assert(
    auth < barrier && barrier < capture && capture < google &&
      google < storage && storage < scope,
  );
  assert(scope < purge && purge < dbVerify && dbVerify < authDelete);
  assertEquals(authDelete, names.length - 1);
});

Deno.test("active external export fails closed after the deletion barrier", async () => {
  const state = validState({ barrierActiveExportOperations: 1 });
  await assertError(
    await handleRequest(request(), options(state)),
    "DATABASE_PURGE_FAILED",
    502,
  );
  assertEquals(
    pathNames(state),
    ["/auth/v1/user", "/rest/v1/rpc/begin_account_deletion"],
  );
});

Deno.test("Google failure stops Storage, DB, and Auth", async () => {
  const state = validState({ googleFailure: true });
  await assertError(
    await handleRequest(request(), options(state)),
    "GOOGLE_CLEANUP_FAILED",
    502,
  );
  assert(!pathNames(state).some((name) => name.startsWith("/storage/")));
  assert(!pathNames(state).includes("/rest/v1/rpc/purge_account_data"));
  assert(
    !pathNames(state).some((name) => name.startsWith("/auth/v1/admin/users/")),
  );
});

Deno.test("zero tournaments skips Google calls and still completes", async () => {
  const state = validState({ tournamentPages: [[]] });
  const response = await handleRequest(request(), options(state));
  assertEquals(response.status, 200);
  assert(!pathNames(state).some((name) => name.includes("spreadsheets")));
  assert(!pathNames(state).some((name) => name.includes("oauth2")));
});

Deno.test("Storage cleanup uses all three authenticated-user prefixes", async () => {
  const state = validState({ tournamentPages: [[]] });
  assertEquals(accountStoragePrefixes(USER_A), [
    `users/${USER_A}/`,
    `users/${USER_A}/`,
    `users/${USER_A}/`,
  ]);
  assertEquals((await handleRequest(request(), options(state))).status, 200);
  assertEquals(
    [...new Set(state.storageListBodies.map((entry) => entry.bucket))].sort(),
    ["custom-designs", "match-screenshots", "ocr-screenshots"],
  );
  for (const entry of state.storageListBodies) {
    assertEquals(entry.body.prefix, `users/${USER_A}/`);
  }
});

Deno.test("Storage failure stops DB and Auth", async () => {
  const state = validState({ tournamentPages: [[]], storageFailure: true });
  await assertError(
    await handleRequest(request(), options(state)),
    "STORAGE_CLEANUP_FAILED",
    502,
  );
  assert(!pathNames(state).includes("/rest/v1/rpc/purge_account_data"));
  assert(
    !pathNames(state).some((name) => name.startsWith("/auth/v1/admin/users/")),
  );
});

Deno.test("scope drift stops before database purge and Auth", async () => {
  const state = validState({
    tournamentPages: [[TOURNAMENT_A], [TOURNAMENT_A, TOURNAMENT_B]],
  });
  await assertError(
    await handleRequest(request(), options(state)),
    "DATABASE_PURGE_FAILED",
    502,
  );
  assert(!pathNames(state).includes("/rest/v1/rpc/purge_account_data"));
  assert(
    !pathNames(state).some((name) => name.startsWith("/auth/v1/admin/users/")),
  );
});

Deno.test("database RPC failure stops Auth", async () => {
  const state = validState({
    tournamentPages: [[]],
    purgeResponse: { malformed: true },
  });
  await assertError(
    await handleRequest(request(), options(state)),
    "DATABASE_PURGE_FAILED",
    502,
  );
  assert(
    !pathNames(state).some((name) => name.startsWith("/auth/v1/admin/users/")),
  );
});

Deno.test("any one of the nine database residual references stops Auth", async () => {
  for (
    const table of [
      "tournaments",
      "custom_design_templates",
      "deletion_receipts",
      "export_operations",
      "matches",
      "match_correction_audit_entries",
      "match_lobby_screenshot_assets",
      "match_result_screenshot_assets",
      "match_screenshot_metadata",
    ]
  ) {
    const state = validState({ tournamentPages: [[]], residualTable: table });
    await assertError(
      await handleRequest(request(), options(state)),
      "DATABASE_PURGE_FAILED",
      502,
    );
    assert(
      !pathNames(state).some((name) =>
        name.startsWith("/auth/v1/admin/users/")
      ),
    );
  }
});

Deno.test("final Storage verification catches a newly appearing object", async () => {
  const state = validState({ tournamentPages: [[]], finalStorageObject: true });
  await assertError(
    await handleRequest(request(), options(state)),
    "STORAGE_CLEANUP_FAILED",
    502,
  );
  assert(
    !pathNames(state).some((name) => name.startsWith("/auth/v1/admin/users/")),
  );
});

Deno.test("Auth hard delete is last, targets validated UID, and has no soft-delete body", async () => {
  const state = validState({ tournamentPages: [[]] });
  assertEquals((await handleRequest(request(), options(state))).status, 200);
  const deleteCall = state.calls.find((call) => call.method === "DELETE");
  assert(deleteCall);
  assertEquals(path(deleteCall.url), `/auth/v1/admin/users/${USER_A}`);
  assertEquals(deleteCall.body, null);
  assert(!path(deleteCall.url).includes(USER_B));
});

Deno.test("already-absent Auth identity is success only after all gates", async () => {
  const state = validState({ tournamentPages: [[]], authDeleteStatus: 404 });
  assertEquals((await handleRequest(request(), options(state))).status, 200);
});

Deno.test("Auth deletion failure is mapped safely", async () => {
  const state = validState({ tournamentPages: [[]], authDeleteStatus: 500 });
  await assertError(
    await handleRequest(request(), options(state)),
    "ACCOUNT_DELETE_FAILED",
    502,
  );
});

Deno.test("unexpected handler exceptions map to INTERNAL_ERROR", async () => {
  const state = validState();
  const response = await handleRequest(request(), {
    env: () => {
      throw new Error("private service error");
    },
    fetchImpl: makeFetch(state),
  });
  await assertError(response, "INTERNAL_ERROR", 500);
});

Deno.test("safe public errors do not expose upstream secrets", async () => {
  const state = validState({ googleFailure: true });
  const response = await handleRequest(request(), options(state));
  const text = await response.text();
  assert(!text.includes("google secret"));
  assert(!text.includes(SERVICE_ROLE_KEY));
  assert(!text.includes(GOOGLE_ACCESS_TOKEN));
  assert(!text.includes(GOOGLE_PRIVATE_KEY));
});

Deno.test("recursive Storage listing handles nested folders without deleting while listing", async () => {
  const userPrefix = `users/${USER_A}/`;
  const optionsState = {
    supabaseUrl: SUPABASE_URL,
    serviceRoleKey: SERVICE_ROLE_KEY,
    timeoutMs: 100,
    storageListBodies: [] as Array<
      { bucket: string; body: Record<string, unknown> }
    >,
    calls: [] as Call[],
  };
  let listCount = 0;
  const fetchImpl: FetchImplementation = async (input, init) => {
    const url = new URL(String(input));
    const call: Call = {
      method: init?.method ?? "GET",
      url,
      body: typeof init?.body === "string" ? init.body : null,
    };
    optionsState.calls.push(call);
    if (path(url).includes("/object/list/")) {
      const requestBody = JSON.parse(call.body ?? "{}") as Record<
        string,
        unknown
      >;
      optionsState.storageListBodies.push({
        bucket: "custom-designs",
        body: requestBody,
      });
      listCount += 1;
      if (listCount === 1) {
        return responseJson([
          { name: "nested", id: null },
          { name: "root.png", id: "root-id" },
        ]);
      }
      if (listCount === 2) {
        return responseJson([{ name: "deep.png", id: "deep-id" }]);
      }
      return responseJson([]);
    }
    if (init?.method === "DELETE") return responseJson({ message: "ok" });
    throw new Error("unexpected storage request");
  };
  await deleteOwnedStorageObjects(USER_A, {
    ...optionsState,
    fetchImpl,
  });
  const deleteIndex = optionsState.calls.findIndex((call) =>
    call.method === "DELETE"
  );
  const listingEnd = optionsState.calls.findIndex((call, index) =>
    index > deleteIndex && path(call.url).includes("/object/list/")
  );
  assert(deleteIndex > 1 && listingEnd > deleteIndex);
  assertEquals(optionsState.storageListBodies[0].body.prefix, userPrefix);
  assertEquals(
    optionsState.storageListBodies[1].body.prefix,
    `${userPrefix}nested/`,
  );
});

Deno.test("Storage deletion is bounded and pagination completes before deletion", async () => {
  const files = Array.from({ length: 1005 }, (_, index) => ({
    name: `file-${String(index).padStart(3, "0")}.png`,
    id: `id-${index}`,
  }));
  const calls: Call[] = [];
  const listBodies: Record<string, unknown>[] = [];
  const deleteBodies: Record<string, unknown>[] = [];
  let initialListing = true;
  const fetchImpl: FetchImplementation = async (input, init) => {
    const url = new URL(String(input));
    const call: Call = {
      method: init?.method ?? "GET",
      url,
      body: typeof init?.body === "string" ? init.body : null,
    };
    calls.push(call);
    if (path(url).includes("/object/list/")) {
      const requestBody = JSON.parse(call.body ?? "{}") as Record<
        string,
        unknown
      >;
      listBodies.push(requestBody);
      const offset = Number(requestBody.offset ?? 0);
      if (initialListing && offset === 0) {
        return responseJson(files.slice(0, 1000));
      }
      if (initialListing && offset === 1000) {
        initialListing = false;
        return responseJson(files.slice(1000));
      }
      return responseJson([]);
    }
    if (call.method === "DELETE") {
      deleteBodies.push(JSON.parse(call.body ?? "{}"));
      return responseJson({ message: "ok" });
    }
    throw new Error("unexpected storage request");
  };
  await deleteOwnedStorageObjects(USER_A, {
    supabaseUrl: SUPABASE_URL,
    serviceRoleKey: SERVICE_ROLE_KEY,
    fetchImpl,
    timeoutMs: 100,
  });
  assertEquals(deleteBodies.length, 11);
  assert(
    deleteBodies.every((deleteBody) => Array.isArray(deleteBody.prefixes)),
  );
  assert(
    deleteBodies.every((deleteBody) =>
      (deleteBody.prefixes as unknown[]).length <= 100
    ),
  );
  assert(calls.findIndex((call) => call.method === "DELETE") > 0);
  assert(listBodies.length > 3);
});
