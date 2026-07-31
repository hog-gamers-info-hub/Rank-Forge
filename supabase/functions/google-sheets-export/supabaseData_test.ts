import { EdgeFunctionError } from "../_shared/errors.ts";
import type { FetchImplementation } from "../_shared/http.ts";
import {
  readOfficialMatchResults,
  readOfficialPlayers,
  readOfficialTeamSlots,
  readVisibleMatch,
  readVisibleTournament,
  type SupabaseDataContext,
} from "../_shared/supabaseData.ts";

const TOURNAMENT_ID = "11111111-1111-4111-8111-111111111111";
const MATCH_ID = "22222222-2222-4222-8222-222222222222";
const TEAM_SLOT_ID = "33333333-3333-4333-8333-333333333333";

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

Deno.test("visible tournament uses caller RLS headers and narrow columns", async () => {
  const { fetchImpl, calls } = makeFetch(() =>
    responseJson([{ id: TOURNAMENT_ID, name: "Championship" }])
  );

  const tournament = await readVisibleTournament(
    TOURNAMENT_ID,
    context(fetchImpl),
  );

  assertEquals(tournament, {
    id: TOURNAMENT_ID,
    name: "Championship",
  });
  assertEquals(calls.length, 1);
  assertEquals(calls[0].method, "GET");
  assertEquals(calls[0].url.pathname, "/rest/v1/tournaments");
  assertEquals(calls[0].url.searchParams.get("select"), "id,name");
  assertEquals(calls[0].url.searchParams.get("id"), `eq.${TOURNAMENT_ID}`);
  assertEquals(calls[0].url.searchParams.get("limit"), "2");
  assertEquals(
    calls[0].headers.get("authorization"),
    "Bearer caller-token",
  );
  assertEquals(calls[0].headers.get("apikey"), "anon-key");
  assertEquals(calls[0].headers.get("accept"), "application/json");
});

Deno.test("RLS-hidden tournament maps safely", async () => {
  const { fetchImpl } = makeFetch(() => responseJson([]));

  await assertRejects(
    () => readVisibleTournament(TOURNAMENT_ID, context(fetchImpl)),
    "TOURNAMENT_NOT_FOUND_OR_FORBIDDEN",
    404,
  );
});

Deno.test("visible match uses narrow columns and hidden match maps safely", async () => {
  const success = makeFetch(() =>
    responseJson([{
      id: MATCH_ID,
      tournament_id: TOURNAMENT_ID,
      match_number: 1,
      status: "finalized",
      finalized_at: "2026-07-31T12:30:00Z",
    }])
  );

  const match = await readVisibleMatch(MATCH_ID, context(success.fetchImpl));

  assertEquals(match.match_number, 1);
  assertEquals(match.status, "finalized");
  assertEquals(success.calls[0].url.pathname, "/rest/v1/matches");
  assertEquals(
    success.calls[0].url.searchParams.get("select"),
    "id,tournament_id,match_number,status,finalized_at",
  );

  const hidden = makeFetch(() => responseJson([]));

  await assertRejects(
    () => readVisibleMatch(MATCH_ID, context(hidden.fetchImpl)),
    "MATCH_NOT_FOUND_OR_FORBIDDEN",
    404,
  );
});

Deno.test("official match results use match filter and deterministic order", async () => {
  const { fetchImpl, calls } = makeFetch(() =>
    responseJson([{
      id: "result-id",
      match_id: MATCH_ID,
      team_slot_id: TEAM_SLOT_ID,
      placement: 1,
      kills: 8,
      review_status: "confirmed",
    }])
  );

  const rows = await readOfficialMatchResults(MATCH_ID, context(fetchImpl));

  assertEquals(rows.length, 1);
  assertEquals(calls[0].url.pathname, "/rest/v1/match_results");
  assertEquals(calls[0].url.searchParams.get("match_id"), `eq.${MATCH_ID}`);
  assertEquals(
    calls[0].url.searchParams.get("order"),
    "placement.asc.nullslast",
  );
});

Deno.test("official team slots use tournament filter and slot order", async () => {
  const { fetchImpl, calls } = makeFetch(() =>
    responseJson([{
      id: TEAM_SLOT_ID,
      tournament_id: TOURNAMENT_ID,
      slot_number: 1,
      team_name: "Team One",
    }])
  );

  const rows = await readOfficialTeamSlots(
    TOURNAMENT_ID,
    context(fetchImpl),
  );

  assertEquals(rows.length, 1);
  assertEquals(calls[0].url.pathname, "/rest/v1/tournament_team_slots");
  assertEquals(
    calls[0].url.searchParams.get("tournament_id"),
    `eq.${TOURNAMENT_ID}`,
  );
  assertEquals(calls[0].url.searchParams.get("order"), "slot_number.asc");
});

Deno.test("official players use only requested team-slot IDs", async () => {
  const { fetchImpl, calls } = makeFetch(() =>
    responseJson([{
      id: "player-id",
      team_slot_id: TEAM_SLOT_ID,
      display_name: "Player One",
    }])
  );

  const rows = await readOfficialPlayers(
    [TEAM_SLOT_ID],
    context(fetchImpl),
  );

  assertEquals(rows.length, 1);
  assertEquals(calls[0].url.pathname, "/rest/v1/players");
  assertEquals(
    calls[0].url.searchParams.get("team_slot_id"),
    `in.(${TEAM_SLOT_ID})`,
  );
  assertEquals(
    calls[0].url.searchParams.get("order"),
    "team_slot_id.asc,display_name.asc",
  );
});

Deno.test("empty player ID list performs no request", async () => {
  const { fetchImpl, calls } = makeFetch(() => {
    throw new Error("fetch must not run");
  });

  const rows = await readOfficialPlayers([], context(fetchImpl));

  assertEquals(rows, []);
  assertEquals(calls.length, 0);
});

Deno.test("Supabase non-success and malformed payloads map safely", async () => {
  const failed = makeFetch(() =>
    new Response("sensitive upstream body", { status: 500 })
  );

  await assertRejects(
    () => readOfficialMatchResults(MATCH_ID, context(failed.fetchImpl)),
    "SUPABASE_DATA_FAILURE",
    502,
  );

  const malformed = makeFetch(() => responseJson({ unexpected: true }));

  await assertRejects(
    () => readOfficialTeamSlots(TOURNAMENT_ID, context(malformed.fetchImpl)),
    "SUPABASE_DATA_FAILURE",
    502,
  );
});

Deno.test("invalid row shapes map to Supabase data failure", async () => {
  const { fetchImpl } = makeFetch(() =>
    responseJson([{
      id: "result-id",
      match_id: MATCH_ID,
      team_slot_id: TEAM_SLOT_ID,
      placement: "1",
      kills: 8,
      review_status: "confirmed",
    }])
  );

  await assertRejects(
    () => readOfficialMatchResults(MATCH_ID, context(fetchImpl)),
    "SUPABASE_DATA_FAILURE",
    502,
  );
});

Deno.test("Supabase timeout maps to upstream timeout", async () => {
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
      readVisibleTournament(TOURNAMENT_ID, {
        ...context(timeoutFetch),
        timeoutMs: 1,
      }),
    "UPSTREAM_TIMEOUT",
    504,
  );
});
