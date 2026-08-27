import { type FetchImplementation } from "../_shared/http.ts";
import { handleRequest } from "./index.ts";

const SHARE_TOKEN = "11111111-1111-4111-8111-111111111111";
const UNKNOWN_TOKEN = "22222222-2222-4222-8222-222222222222";
const TEST_SECRET = "server-secret-must-not-leak";
const CORS_ORIGIN = "https://hog-gamers-info-hub.github.io";

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

function env() {
  return (name: string) =>
    ({
      SUPABASE_URL: "https://supabase.invalid",
      SUPABASE_SERVICE_ROLE_KEY: TEST_SECRET,
    } as Record<string, string>)[name];
}

function responseJson(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function mockFetch(response: Response) {
  const calls: Array<{ url: string; init: RequestInit }> = [];
  const fetchImpl: FetchImplementation = (input, init = {}) => {
    calls.push({ url: String(input), init });
    return Promise.resolve(response.clone());
  };
  return { calls, fetchImpl };
}

function request(
  token?: string,
  method = "GET",
): Request {
  const suffix = token === undefined ? "" : `?token=${token}`;
  return new Request(`https://function.invalid${suffix}`, { method });
}

const VALID_ROW = {
  displayOrder: 1,
  teamSlotNumber: 1,
  teamName: "Test Team",
  totalPoints: 20,
  totalPositionPoints: 12,
  totalKillPoints: 8,
  firstPlaceFinishes: 1,
  latestMatchPlacement: 1,
  matchesIncluded: 2,
  isCompleteTie: false,
};

function snapshotRow(overrides: Record<string, unknown> = {}) {
  return { ...VALID_ROW, ...overrides };
}

async function responseJsonValue(response: Response): Promise<unknown> {
  return await response.json();
}

Deno.test("missing and malformed tokens return the same 404 JSON response", async () => {
  const missing = await handleRequest(request(), { env: env() });
  const malformed = await handleRequest(
    request("not-a-uuid"),
    { env: env() },
  );

  assertEquals(missing.status, 404);
  assertEquals(malformed.status, 404);
  assertEquals(
    await responseJsonValue(missing),
    await responseJsonValue(malformed),
  );
  assertEquals(
    missing.headers.get("content-type"),
    "application/json; charset=utf-8",
  );
  assertEquals(missing.headers.get("access-control-allow-origin"), CORS_ORIGIN);
});

Deno.test("unknown tokens return the same generic 404 without data", async () => {
  const { fetchImpl, calls } = mockFetch(responseJson([]));
  const response = await handleRequest(request(UNKNOWN_TOKEN), {
    env: env(),
    fetchImpl,
  });

  assertEquals(response.status, 404);
  assertEquals(await responseJsonValue(response), { error: "not_found" });
  assertEquals(calls.length, 1);
});

Deno.test("valid snapshots return exact standings JSON and query only the share table", async () => {
  const standings = [
    snapshotRow({ teamName: null, latestMatchPlacement: null }),
  ];
  const { fetchImpl, calls } = mockFetch(responseJson([{ standings }]));
  const response = await handleRequest(request(SHARE_TOKEN), {
    env: env(),
    fetchImpl,
  });
  const query = new URL(calls[0].url).searchParams;

  assertEquals(response.status, 200);
  assertEquals(
    response.headers.get("content-type"),
    "application/json; charset=utf-8",
  );
  assertEquals(
    await responseJsonValue(response),
    { standings },
  );
  assertEquals(query.get("select"), "standings");
  assertEquals(query.get("share_token"), `eq.${SHARE_TOKEN}`);
  assert(!query.has("tournament_id"));
  assertEquals(
    response.headers.get("access-control-allow-origin"),
    CORS_ORIGIN,
  );
  assertEquals(
    response.headers.get("access-control-allow-methods"),
    "GET, OPTIONS",
  );
  assertEquals(response.headers.get("vary"), "Origin");
  assertEquals(response.headers.get("cache-control"), "no-store");
  assert(!JSON.stringify({ standings }).includes(TEST_SECRET));
  assertEquals(calls[0].init.method, "GET");
  assertEquals(
    new Headers(calls[0].init.headers).get("authorization"),
    `Bearer ${TEST_SECRET}`,
  );
});

Deno.test("team names remain JSON data rather than HTML", async () => {
  const teamName = '<img src="x" onerror="alert(1)">';
  const { fetchImpl } = mockFetch(responseJson([{
    standings: [snapshotRow({ teamName })],
  }]));
  const response = await handleRequest(request(SHARE_TOKEN), {
    env: env(),
    fetchImpl,
  });

  assertEquals(await responseJsonValue(response), {
    standings: [snapshotRow({ teamName })],
  });
});

Deno.test("empty snapshots return an empty standings array", async () => {
  const { fetchImpl } = mockFetch(responseJson([{ standings: [] }]));
  const response = await handleRequest(request(SHARE_TOKEN), {
    env: env(),
    fetchImpl,
  });

  assertEquals(response.status, 200);
  assertEquals(await responseJsonValue(response), { standings: [] });
});

Deno.test("non-GET requests return deterministic method rejection", async () => {
  const { fetchImpl, calls } = mockFetch(responseJson([]));
  const response = await handleRequest(request(SHARE_TOKEN, "POST"), {
    env: env(),
    fetchImpl,
  });

  assertEquals(response.status, 405);
  assertEquals(await responseJsonValue(response), {
    error: "method_not_allowed",
  });
  assertEquals(
    response.headers.get("access-control-allow-origin"),
    CORS_ORIGIN,
  );
  assertEquals(calls.length, 0);
});

Deno.test("OPTIONS returns a CORS preflight response without querying Supabase", async () => {
  const { fetchImpl, calls } = mockFetch(responseJson([]));
  const response = await handleRequest(request(undefined, "OPTIONS"), {
    env: env(),
    fetchImpl,
  });

  assertEquals(response.status, 204);
  assertEquals(await response.text(), "");
  assertEquals(
    response.headers.get("access-control-allow-origin"),
    CORS_ORIGIN,
  );
  assertEquals(
    response.headers.get("access-control-allow-methods"),
    "GET, OPTIONS",
  );
  assertEquals(calls.length, 0);
});

Deno.test("malformed stored snapshots fail with a safe generic JSON response", async () => {
  const { fetchImpl } = mockFetch(responseJson([{
    standings: '<script>alert("unsafe")</script>',
  }]));
  const response = await handleRequest(request(SHARE_TOKEN), {
    env: env(),
    fetchImpl,
  });

  assertEquals(response.status, 500);
  assertEquals(await responseJsonValue(response), {
    error: "standings_unavailable",
  });
});

Deno.test("snapshots with more than 12 rows fail safely", async () => {
  const { fetchImpl } = mockFetch(responseJson([{
    standings: Array.from({ length: 13 }, () => snapshotRow()),
  }]));
  const response = await handleRequest(request(SHARE_TOKEN), {
    env: env(),
    fetchImpl,
  });

  assertEquals(response.status, 500);
  assertEquals(await responseJsonValue(response), {
    error: "standings_unavailable",
  });
});

Deno.test("invalid snapshot row field types fail safely", async () => {
  const { fetchImpl } = mockFetch(responseJson([{
    standings: [snapshotRow({ totalPoints: "20" })],
  }]));
  const response = await handleRequest(request(SHARE_TOKEN), {
    env: env(),
    fetchImpl,
  });

  assertEquals(response.status, 500);
  assertEquals(await responseJsonValue(response), {
    error: "standings_unavailable",
  });
});

Deno.test("generic upstream failures return no internal details", async () => {
  const internalDetails = "database-internal-secret";
  const fetchImpl: FetchImplementation = async () => {
    throw new Error(internalDetails);
  };
  const response = await handleRequest(request(SHARE_TOKEN), {
    env: env(),
    fetchImpl,
  });
  const body = await response.text();

  assertEquals(response.status, 500);
  assertEquals(body, JSON.stringify({ error: "standings_unavailable" }));
  assert(!body.includes(internalDetails));
  assert(!body.includes(TEST_SECRET));
});
