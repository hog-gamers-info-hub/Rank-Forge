import { EdgeFunctionError } from "../_shared/errors.ts";
import type { FetchImplementation } from "../_shared/http.ts";
import {
  readOfficialMatchResultsForMatches,
  readVisibleTournamentMatches,
  type SupabaseDataContext,
} from "../_shared/supabaseData.ts";

const TOURNAMENT_ID = "11111111-1111-4111-8111-111111111111";
const MATCH_ID_1 = "22222222-2222-4222-8222-222222222222";
const MATCH_ID_2 = "33333333-3333-4333-8333-333333333333";
const TEAM_SLOT_ID = "44444444-4444-4444-8444-444444444444";

interface Call {
  url: URL;
  method: string;
  headers: Headers;
}

function assert(
  condition: unknown,
  message = "assertion failed",
): asserts condition {
  if (!condition) {
    throw new Error(message);
  }
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

async function assertRejects(
  operation: () => Promise<unknown>,
  code: string,
  status: number,
): Promise<void> {
  try {
    await operation();
  } catch (error) {
    assert(error instanceof EdgeFunctionError);
    assertEquals(error.code, code);
    assertEquals(error.status, status);
    return;
  }

  throw new Error(`Expected ${code}`);
}

function responseJson(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function makeFetch(
  responder: (call: Call, index: number) => Response | Promise<Response>,
): { fetchImpl: FetchImplementation; calls: Call[] } {
  const calls: Call[] = [];

  const fetchImpl: FetchImplementation = (input, init) => {
    const call: Call = {
      url: new URL(String(input)),
      method: init?.method ?? "GET",
      headers: new Headers(init?.headers),
    };

    calls.push(call);

    return Promise.resolve(responder(call, calls.length - 1));
  };

  return { fetchImpl, calls };
}

function context(fetchImpl: FetchImplementation): SupabaseDataContext {
  return {
    config: {
      url: "https://project.supabase.co",
      anonKey: "anon-key",
    },
    accessToken: "caller-token",
    fetchImpl,
    timeoutMs: 100,
  };
}

function officialMatch(
  id: string,
  matchNumber: number,
  status: string,
): Record<string, unknown> {
  return {
    id,
    tournament_id: TOURNAMENT_ID,
    match_number: matchNumber,
    status,
    finalized_at: status === "finalized"
      ? `2026-07-${30 + matchNumber}T12:30:00Z`
      : null,
  };
}

function officialResult(
  id: string,
  matchId: string,
  placement: number,
): Record<string, unknown> {
  return {
    id,
    match_id: matchId,
    team_slot_id: TEAM_SLOT_ID,
    placement,
    kills: placement + 2,
    review_status: "confirmed",
  };
}

Deno.test(
  "tournament matches use caller RLS, narrow columns, filter, and deterministic order",
  async () => {
    const { fetchImpl, calls } = makeFetch(() =>
      responseJson([
        officialMatch(MATCH_ID_1, 1, "finalized"),
        officialMatch(MATCH_ID_2, 2, "draft"),
      ])
    );

    const matches = await readVisibleTournamentMatches(
      TOURNAMENT_ID,
      context(fetchImpl),
    );

    assertEquals(matches.length, 2);
    assertEquals(matches[0].id, MATCH_ID_1);
    assertEquals(matches[0].status, "finalized");
    assertEquals(matches[1].id, MATCH_ID_2);
    assertEquals(matches[1].status, "draft");
    assertEquals(calls.length, 1);
    assertEquals(calls[0].method, "GET");
    assertEquals(calls[0].url.pathname, "/rest/v1/matches");
    assertEquals(
      calls[0].url.searchParams.get("select"),
      "id,tournament_id,match_number,status,finalized_at",
    );
    assertEquals(
      calls[0].url.searchParams.get("tournament_id"),
      `eq.${TOURNAMENT_ID}`,
    );
    assertEquals(
      calls[0].url.searchParams.get("order"),
      "match_number.asc,id.asc",
    );
    assertEquals(
      calls[0].headers.get("authorization"),
      "Bearer caller-token",
    );
    assertEquals(calls[0].headers.get("apikey"), "anon-key");
    assertEquals(calls[0].headers.get("accept"), "application/json");
  },
);

Deno.test("tournament with no visible matches returns an empty list", async () => {
  const { fetchImpl, calls } = makeFetch(() => responseJson([]));

  const matches = await readVisibleTournamentMatches(
    TOURNAMENT_ID,
    context(fetchImpl),
  );

  assertEquals(matches, []);
  assertEquals(calls.length, 1);
});

Deno.test("malformed tournament-match rows map safely", async () => {
  const { fetchImpl } = makeFetch(() =>
    responseJson([{
      ...officialMatch(MATCH_ID_1, 1, "finalized"),
      match_number: "1",
    }])
  );

  await assertRejects(
    () => readVisibleTournamentMatches(TOURNAMENT_ID, context(fetchImpl)),
    "SUPABASE_DATA_FAILURE",
    502,
  );
});

Deno.test(
  "multi-match results use exact IDs, narrow columns, and deterministic order",
  async () => {
    const { fetchImpl, calls } = makeFetch(() =>
      responseJson([
        officialResult("result-1", MATCH_ID_1, 1),
        officialResult("result-2", MATCH_ID_2, 2),
      ])
    );

    const rows = await readOfficialMatchResultsForMatches(
      [MATCH_ID_1, MATCH_ID_2],
      context(fetchImpl),
    );

    assertEquals(rows.length, 2);
    assertEquals(rows[0].match_id, MATCH_ID_1);
    assertEquals(rows[1].match_id, MATCH_ID_2);
    assertEquals(calls.length, 1);
    assertEquals(calls[0].method, "GET");
    assertEquals(calls[0].url.pathname, "/rest/v1/match_results");
    assertEquals(
      calls[0].url.searchParams.get("select"),
      "id,match_id,team_slot_id,participation_status,placement,kills,review_status",
    );
    assertEquals(
      calls[0].url.searchParams.get("match_id"),
      `in.(${MATCH_ID_1},${MATCH_ID_2})`,
    );
    assertEquals(
      calls[0].url.searchParams.get("order"),
      "match_id.asc,placement.asc.nullslast",
    );
    assertEquals(
      calls[0].headers.get("authorization"),
      "Bearer caller-token",
    );
    assertEquals(calls[0].headers.get("apikey"), "anon-key");
  },
);

Deno.test("empty match-ID list performs no result request", async () => {
  const { fetchImpl, calls } = makeFetch(() => {
    throw new Error("fetch must not run");
  });

  const rows = await readOfficialMatchResultsForMatches(
    [],
    context(fetchImpl),
  );

  assertEquals(rows, []);
  assertEquals(calls.length, 0);
});

Deno.test("new readers map non-success and malformed payloads safely", async () => {
  const failed = makeFetch(() =>
    new Response("sensitive upstream body", { status: 500 })
  );

  await assertRejects(
    () =>
      readVisibleTournamentMatches(TOURNAMENT_ID, context(failed.fetchImpl)),
    "SUPABASE_DATA_FAILURE",
    502,
  );

  const malformed = makeFetch(() => responseJson({ unexpected: true }));

  await assertRejects(
    () =>
      readOfficialMatchResultsForMatches(
        [MATCH_ID_1],
        context(malformed.fetchImpl),
      ),
    "SUPABASE_DATA_FAILURE",
    502,
  );
});

Deno.test("malformed multi-match result rows map safely", async () => {
  const { fetchImpl } = makeFetch(() =>
    responseJson([{
      ...officialResult("result-1", MATCH_ID_1, 1),
      kills: "3",
    }])
  );

  await assertRejects(
    () =>
      readOfficialMatchResultsForMatches(
        [MATCH_ID_1],
        context(fetchImpl),
      ),
    "SUPABASE_DATA_FAILURE",
    502,
  );
});

Deno.test("new tournament-match read timeout maps safely", async () => {
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
      readVisibleTournamentMatches(TOURNAMENT_ID, {
        ...context(timeoutFetch),
        timeoutMs: 1,
      }),
    "UPSTREAM_TIMEOUT",
    504,
  );
});
