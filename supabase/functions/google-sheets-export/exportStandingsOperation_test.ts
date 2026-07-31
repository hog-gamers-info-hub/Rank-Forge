import { STANDINGS_EXPORT_COLUMNS } from "../_shared/standingsExport.ts";
import type { FetchImplementation } from "../_shared/http.ts";
import { handleRequest } from "./index.ts";

const TOURNAMENT_ID = "11111111-1111-4111-8111-111111111111";
const MATCH_ID = "22222222-2222-4222-8222-222222222222";
const DRAFT_MATCH_ID = "33333333-3333-4333-8333-333333333333";

const TEST_ENV: Record<string, string> = {
  SUPABASE_URL: "https://project.supabase.co",
  SUPABASE_ANON_KEY: "anon-key",
  GOOGLE_SHEETS_CLIENT_EMAIL: "service@example.invalid",
  GOOGLE_SHEETS_PRIVATE_KEY: "synthetic-private-key",
  GOOGLE_SHEETS_SPREADSHEET_ID: "spreadsheet-id",
};

const POSITION_POINTS = [12, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0, 0];

interface Call {
  url: URL;
  method: string;
  headers: Headers;
  body: string;
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

function responseJson(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function makeFetch(
  responder: (call: Call, index: number) => Response | Promise<Response>,
): { fetchImpl: FetchImplementation; calls: Call[] } {
  const calls: Call[] = [];

  const fetchImpl: FetchImplementation = async (input, init = {}) => {
    const call: Call = {
      url: new URL(String(input)),
      method: init.method ?? "GET",
      headers: new Headers(init.headers),
      body: typeof init.body === "string" ? init.body : "",
    };

    calls.push(call);

    return await responder(call, calls.length - 1);
  };

  return { fetchImpl, calls };
}

function env(name: string): string | undefined {
  return TEST_ENV[name];
}

function request(
  body: unknown,
  authorization = "Bearer caller-token",
): Request {
  return new Request("https://function.invalid", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(authorization ? { Authorization: authorization } : {}),
    },
    body: JSON.stringify(body),
  });
}

async function jsonBody(
  response: Response,
): Promise<Record<string, unknown>> {
  return await response.json() as Record<string, unknown>;
}

function teamSlotId(slot: number): string {
  return `team-slot-${slot}`;
}

function validPayload(): Record<string, unknown> {
  return {
    operation: "export_standings",
    tournament_id: TOURNAMENT_ID,
    rows: Array.from({ length: 12 }, (_, index) => {
      const slot = index + 1;
      const totalPositionPoints = POSITION_POINTS[index];

      return {
        export_schema_version: "phase_10_v1",
        export_type: "tournament_standings",
        tournament_id: TOURNAMENT_ID,
        tournament_name: "Championship",
        exported_match_count: 1,
        standings_rank: slot,
        team_slot: slot,
        team_name: `Team ${slot}`,
        player_1_name: `Player ${slot}A`,
        player_2_name: `Player ${slot}B`,
        player_3_name: `Player ${slot}C`,
        player_4_name: `Player ${slot}D`,
        matches_played: 1,
        total_position_points: totalPositionPoints,
        total_kills: 0,
        total_kill_points: 0,
        total_points: totalPositionPoints,
        best_placement: slot,
        first_place_count: slot === 1 ? 1 : 0,
        tie_break_status: slot >= 11 ? "tie_break_applied" : "unique_order",
      };
    }),
  };
}

function tournamentResponse(name = "Championship"): Response {
  return responseJson([{ id: TOURNAMENT_ID, name }]);
}

function matchesResponse(
  options: {
    finalized?: boolean;
    includeDraft?: boolean;
  } = {},
): Response {
  const finalized = options.finalized ?? true;
  const matches = [{
    id: MATCH_ID,
    tournament_id: TOURNAMENT_ID,
    match_number: 1,
    status: finalized ? "finalized" : "draft",
    finalized_at: finalized ? "2026-07-31T12:30:00Z" : null,
  }];

  if (options.includeDraft ?? true) {
    matches.push({
      id: DRAFT_MATCH_ID,
      tournament_id: TOURNAMENT_ID,
      match_number: 2,
      status: "draft",
      finalized_at: null,
    });
  }

  return responseJson(matches);
}

function matchResultsResponse(): Response {
  return responseJson(
    Array.from({ length: 12 }, (_, index) => {
      const slot = index + 1;

      return {
        id: `result-${slot}`,
        match_id: MATCH_ID,
        team_slot_id: teamSlotId(slot),
        placement: slot,
        kills: 0,
        review_status: "confirmed",
      };
    }),
  );
}

function teamSlotsResponse(
  firstTeamName = "Team 1",
): Response {
  return responseJson(
    Array.from({ length: 12 }, (_, index) => {
      const slot = index + 1;

      return {
        id: teamSlotId(slot),
        tournament_id: TOURNAMENT_ID,
        slot_number: slot,
        team_name: slot === 1 ? firstTeamName : `Team ${slot}`,
      };
    }),
  );
}

function playersResponse(
  firstPlayerName = "Player 1A",
): Response {
  return responseJson(
    Array.from({ length: 12 }, (_, index) => {
      const slot = index + 1;

      return ["A", "B", "C", "D"].map((suffix) => ({
        id: `player-${slot}-${suffix}`,
        team_slot_id: teamSlotId(slot),
        display_name: slot === 1 && suffix === "A"
          ? firstPlayerName
          : `Player ${slot}${suffix}`,
      }));
    }).flat(),
  );
}

function successfulExportFetch(
  overrides: {
    tournamentName?: string;
    finalized?: boolean;
    includeDraft?: boolean;
    firstTeamName?: string;
    firstPlayerName?: string;
    header?: unknown;
    updatedRows?: number;
  } = {},
): { fetchImpl: FetchImplementation; calls: Call[] } {
  return makeFetch((_call, index) => {
    switch (index) {
      case 0:
        return responseJson({ id: "user-id" });
      case 1:
        return tournamentResponse(overrides.tournamentName);
      case 2:
        return matchesResponse({
          finalized: overrides.finalized,
          includeDraft: overrides.includeDraft,
        });
      case 3:
        return matchResultsResponse();
      case 4:
        return teamSlotsResponse(overrides.firstTeamName);
      case 5:
        return playersResponse(overrides.firstPlayerName);
      case 6:
        return responseJson({
          access_token: "google-token",
          token_type: "Bearer",
        });
      case 7:
        return responseJson({
          values: overrides.header ?? [[...STANDINGS_EXPORT_COLUMNS]],
        });
      case 8:
        return responseJson({
          updates: { updatedRows: overrides.updatedRows ?? 12 },
        });
      default:
        throw new Error("Unexpected network request");
    }
  });
}

const injectedSigner = () => Promise.resolve(new Uint8Array([1, 2, 3]));

Deno.test(
  "export_standings follows auth, RLS reads, OAuth, header, append order",
  async () => {
    const { fetchImpl, calls } = successfulExportFetch();

    const response = await handleRequest(request(validPayload()), {
      env,
      fetchImpl,
      signer: injectedSigner,
      clock: () => 1_700_000_000,
    });

    assertEquals(response.status, 200);
    assertEquals(await jsonBody(response), {
      ok: true,
      operation: "export_standings",
      tournament_id: TOURNAMENT_ID,
      exported_match_count: 1,
      rows_written: 12,
    });

    assertEquals(calls.length, 9);
    assertEquals(
      calls.map((call) => decodeURIComponent(call.url.pathname)),
      [
        "/auth/v1/user",
        "/rest/v1/tournaments",
        "/rest/v1/matches",
        "/rest/v1/match_results",
        "/rest/v1/tournament_team_slots",
        "/rest/v1/players",
        "/token",
        "/v4/spreadsheets/spreadsheet-id/values/Tournament Standings!A1:T1",
        "/v4/spreadsheets/spreadsheet-id/values/Tournament Standings!A:T:append",
      ],
    );

    assertEquals(
      calls[2].url.searchParams.get("tournament_id"),
      `eq.${TOURNAMENT_ID}`,
    );
    assertEquals(
      calls[2].url.searchParams.get("order"),
      "match_number.asc,id.asc",
    );
    assertEquals(
      calls[3].url.searchParams.get("match_id"),
      `in.(${MATCH_ID})`,
    );

    for (const call of calls.slice(1, 6)) {
      assertEquals(
        call.headers.get("authorization"),
        "Bearer caller-token",
      );
      assertEquals(call.headers.get("apikey"), "anon-key");
    }

    assertEquals(calls[7].method, "GET");
    assertEquals(calls[8].method, "POST");
    assertEquals(
      calls[8].url.searchParams.get("valueInputOption"),
      "RAW",
    );
    assertEquals(
      calls[8].url.searchParams.get("insertDataOption"),
      "INSERT_ROWS",
    );
    assertEquals(
      calls[8].headers.get("authorization"),
      "Bearer google-token",
    );

    const appendBody = JSON.parse(calls[8].body);

    assertEquals(appendBody.majorDimension, "ROWS");
    assertEquals(appendBody.values.length, 12);
    assert(
      appendBody.values.every(
        (row: unknown[]) => row.length === STANDINGS_EXPORT_COLUMNS.length,
      ),
    );
  },
);

Deno.test(
  "invalid standings payload is rejected before authentication",
  async () => {
    const payload = validPayload();
    payload.extra = true;

    const { fetchImpl, calls } = makeFetch(() => {
      throw new Error("fetch must not run");
    });

    const response = await handleRequest(request(payload), {
      env,
      fetchImpl,
    });

    assertEquals(response.status, 400);
    assertEquals((await jsonBody(response)).error, {
      code: "INVALID_STANDINGS_EXPORT_PAYLOAD",
      message: "The standings export payload is invalid.",
    });
    assertEquals(calls.length, 0);
  },
);

Deno.test(
  "unauthenticated standings export contacts neither Supabase nor Google",
  async () => {
    const { fetchImpl, calls } = makeFetch(() => {
      throw new Error("fetch must not run");
    });

    const response = await handleRequest(
      request(validPayload(), ""),
      { env, fetchImpl },
    );

    assertEquals(response.status, 401);
    assertEquals((await jsonBody(response)).error, {
      code: "UNAUTHORIZED",
      message: "Authentication is required.",
    });
    assertEquals(calls.length, 0);
  },
);

Deno.test(
  "tournament without finalized matches is rejected before result or Google access",
  async () => {
    const { fetchImpl, calls } = makeFetch((_call, index) => {
      switch (index) {
        case 0:
          return responseJson({ id: "user-id" });
        case 1:
          return tournamentResponse();
        case 2:
          return matchesResponse({ finalized: false });
        default:
          throw new Error("Unexpected network request");
      }
    });

    const response = await handleRequest(request(validPayload()), {
      env,
      fetchImpl,
      signer: injectedSigner,
    });

    assertEquals(response.status, 409);
    assertEquals((await jsonBody(response)).error, {
      code: "NO_FINALIZED_MATCHES",
      message: "The tournament has no finalized matches to export.",
    });
    assertEquals(calls.length, 3);
    assert(
      calls.every(
        (call) => !call.url.hostname.includes("googleapis.com"),
      ),
    );
  },
);

Deno.test(
  "official standings mismatch prevents every Google request",
  async () => {
    const { fetchImpl, calls } = successfulExportFetch({
      firstTeamName: "Different Team",
    });

    const response = await handleRequest(request(validPayload()), {
      env,
      fetchImpl,
      signer: injectedSigner,
    });

    assertEquals(response.status, 409);
    assertEquals((await jsonBody(response)).error, {
      code: "STANDINGS_EXPORT_DATA_MISMATCH",
      message: "The standings export data does not match finalized records.",
    });
    assertEquals(calls.length, 6);
    assert(
      calls.every(
        (call) => !call.url.hostname.includes("googleapis.com"),
      ),
    );
  },
);

Deno.test(
  "invalid official roster membership prevents every Google request",
  async () => {
    const { fetchImpl, calls } = successfulExportFetch({
      firstPlayerName: "Different Player",
    });

    const response = await handleRequest(request(validPayload()), {
      env,
      fetchImpl,
      signer: injectedSigner,
    });

    assertEquals(response.status, 409);
    assertEquals((await jsonBody(response)).error, {
      code: "STANDINGS_EXPORT_DATA_MISMATCH",
      message: "The standings export data does not match finalized records.",
    });
    assertEquals(calls.length, 6);
    assert(
      calls.every(
        (call) => !call.url.hostname.includes("googleapis.com"),
      ),
    );
  },
);

Deno.test("standings header mismatch blocks append", async () => {
  const { fetchImpl, calls } = successfulExportFetch({
    header: [[...STANDINGS_EXPORT_COLUMNS].reverse()],
  });

  const response = await handleRequest(request(validPayload()), {
    env,
    fetchImpl,
    signer: injectedSigner,
  });

  assertEquals(response.status, 409);
  assertEquals((await jsonBody(response)).error, {
    code: "GOOGLE_STANDINGS_SHEET_SCHEMA_MISMATCH",
    message: "The Tournament Standings worksheet header is invalid.",
  });
  assertEquals(calls.length, 8);
  assertEquals(
    calls.filter((call) =>
      call.url.hostname === "sheets.googleapis.com" &&
      call.method === "POST"
    ).length,
    0,
  );
});

Deno.test(
  "invalid standings append confirmation fails safely after one write",
  async () => {
    const { fetchImpl, calls } = successfulExportFetch({
      updatedRows: 11,
    });

    const response = await handleRequest(request(validPayload()), {
      env,
      fetchImpl,
      signer: injectedSigner,
    });

    assertEquals(response.status, 502);
    assertEquals((await jsonBody(response)).error, {
      code: "GOOGLE_STANDINGS_EXPORT_RESPONSE_INVALID",
      message: "Google Sheets returned an invalid standings export response.",
    });
    assertEquals(calls.length, 9);
    assertEquals(
      calls.filter((call) =>
        call.url.hostname === "sheets.googleapis.com" &&
        call.method === "POST"
      ).length,
      1,
    );
  },
);
