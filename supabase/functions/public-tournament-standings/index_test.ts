import { type FetchImplementation } from "../_shared/http.ts";
import { handleRequest } from "./index.ts";

const SHARE_TOKEN = "11111111-1111-4111-8111-111111111111";
const UNKNOWN_TOKEN = "22222222-2222-4222-8222-222222222222";
const TEST_SECRET = "server-secret-must-not-leak";

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

function request(token?: string): Request {
  const suffix = token === undefined ? "" : `?token=${token}`;
  return new Request(`https://function.invalid${suffix}`, { method: "GET" });
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

async function responseText(response: Response): Promise<string> {
  return await response.text();
}

Deno.test("missing and malformed tokens return the same 404 response", async () => {
  const missing = await handleRequest(request(), { env: env() });
  const malformed = await handleRequest(
    request("not-a-uuid"),
    { env: env() },
  );

  assertEquals(missing.status, 404);
  assertEquals(malformed.status, 404);
  assertEquals(
    await responseText(missing),
    await responseText(malformed),
  );
});

Deno.test("unknown tokens return the same 404 response without data", async () => {
  const { fetchImpl, calls } = mockFetch(responseJson([]));
  const response = await handleRequest(request(UNKNOWN_TOKEN), {
    env: env(),
    fetchImpl,
  });

  assertEquals(response.status, 404);
  assertEquals(
    await responseText(response),
    `<!doctype html>
<html lang="en">
<head><meta charset="utf-8"><title>Not found</title></head>
<body><h1>Not found</h1></body>
</html>`,
  );
  assertEquals(calls.length, 1);
});

Deno.test("valid snapshots render HTML and query only the share table", async () => {
  const { fetchImpl, calls } = mockFetch(responseJson([{
    standings: [{
      displayOrder: 1,
      teamSlotNumber: 4,
      teamName: "Alpha",
      totalPoints: 20,
      totalPositionPoints: 12,
      totalKillPoints: 8,
      firstPlaceFinishes: 1,
      latestMatchPlacement: 1,
      matchesIncluded: 2,
      isCompleteTie: false,
    }],
  }]));
  const response = await handleRequest(request(SHARE_TOKEN), {
    env: env(),
    fetchImpl,
  });
  const html = await responseText(response);
  const query = new URL(calls[0].url).searchParams;

  assertEquals(response.status, 200);
  assertEquals(
    response.headers.get("content-type"),
    "text/html; charset=utf-8",
  );
  assert(html.includes("Tournament standings"));
  assert(html.includes("Alpha"));
  assert(html.includes("Kill points"));
  assert(html.includes("Position points"));
  assert(html.includes("Total points"));
  assertEquals(query.get("select"), "standings");
  assertEquals(query.get("share_token"), `eq.${SHARE_TOKEN}`);
  assert(!query.has("tournament_id"));
  assert(!html.includes(TEST_SECRET));
  assertEquals(calls[0].init.method, "GET");
  assertEquals(
    new Headers(calls[0].init.headers).get("authorization"),
    `Bearer ${TEST_SECRET}`,
  );
});

Deno.test("team names containing HTML are escaped", async () => {
  const { fetchImpl } = mockFetch(responseJson([{
    standings: [{
      displayOrder: 1,
      teamSlotNumber: 1,
      teamName: '<img src="x" onerror="alert(1)">',
      totalPoints: 1,
      totalPositionPoints: 1,
      totalKillPoints: 0,
      firstPlaceFinishes: 0,
      latestMatchPlacement: null,
      matchesIncluded: 1,
      isCompleteTie: true,
    }],
  }]));
  const html = await responseText(
    await handleRequest(request(SHARE_TOKEN), {
      env: env(),
      fetchImpl,
    }),
  );

  assert(
    html.includes("&lt;img src=&quot;x&quot; onerror=&quot;alert(1)&quot;&gt;"),
  );
  assert(!html.includes('<img src="x"'));
  assert(html.includes("Complete tie; displayed in Team Slot order."));
});

Deno.test("empty snapshots render the existing empty state", async () => {
  const { fetchImpl } = mockFetch(responseJson([{ standings: [] }]));
  const html = await responseText(
    await handleRequest(request(SHARE_TOKEN), {
      env: env(),
      fetchImpl,
    }),
  );

  assert(html.includes("No finalized matches yet"));
  assert(html.includes("Finalize a match to see tournament standings."));
  assert(!html.includes('<article class="standing-card">'));
});

Deno.test("non-GET requests return deterministic method rejection", async () => {
  const { fetchImpl, calls } = mockFetch(responseJson([]));
  const response = await handleRequest(
    new Request(`https://function.invalid?token=${SHARE_TOKEN}`, {
      method: "POST",
    }),
    { env: env(), fetchImpl },
  );

  assertEquals(response.status, 405);
  assertEquals(await responseText(response), "Method Not Allowed");
  assertEquals(calls.length, 0);
});

Deno.test("malformed stored snapshots fail with a safe generic response", async () => {
  const { fetchImpl } = mockFetch(responseJson([{
    standings: '<script>alert("unsafe")</script>',
  }]));
  const response = await handleRequest(request(SHARE_TOKEN), {
    env: env(),
    fetchImpl,
  });
  const html = await responseText(response);

  assertEquals(response.status, 500);
  assert(html.includes("Standings unavailable"));
  assert(!html.includes("unsafe"));
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
  assertEquals(
    await responseText(response),
    `<!doctype html>
<html lang="en">
<head><meta charset="utf-8"><title>Standings unavailable</title></head>
<body><h1>Standings unavailable</h1></body>
</html>`,
  );
});

Deno.test("invalid snapshot row field types are never rendered", async () => {
  const { fetchImpl } = mockFetch(responseJson([{
    standings: [snapshotRow({
      teamName: "<b>unsafe</b>",
      totalPoints: "20",
    })],
  }]));
  const html = await responseText(
    await handleRequest(request(SHARE_TOKEN), {
      env: env(),
      fetchImpl,
    }),
  );

  assert(html.includes("Standings unavailable"));
  assert(!html.includes("unsafe"));
  assert(!html.includes("<b>"));
});

Deno.test("generic upstream failures return no internal details", async () => {
  const internalDetails = "database-internal-secret";
  const fetchImpl: FetchImplementation = async () => {
    throw new Error(internalDetails);
  };
  const html = await responseText(
    await handleRequest(request(SHARE_TOKEN), {
      env: env(),
      fetchImpl,
    }),
  );

  assert(html.includes("Standings unavailable"));
  assert(!html.includes(internalDetails));
  assert(!html.includes(TEST_SECRET));
});
