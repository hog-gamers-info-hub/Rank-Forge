import {
  EdgeFunctionError,
  type ErrorCode,
  errorResponse,
} from "../_shared/errors.ts";
import {
  parseStandingsExportRequest,
  STANDINGS_EXPORT_COLUMNS,
  toStandingsGoogleSheetValues,
} from "../_shared/standingsExport.ts";

const TOURNAMENT_ID = "11111111-1111-4111-8111-111111111111";

type MutablePayload = {
  operation: unknown;
  tournament_id: unknown;
  rows: Array<Record<string, unknown>>;
  [key: string]: unknown;
};

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

function validPayload(): MutablePayload {
  return {
    operation: "export_standings",
    tournament_id: TOURNAMENT_ID,
    rows: Array.from({ length: 12 }, (_, index) => {
      const rank = index + 1;
      const totalPositionPoints = 40 - index;
      const totalKills = 24 - index;

      return {
        export_schema_version: "phase_10_v1",
        export_type: "tournament_standings",
        tournament_id: TOURNAMENT_ID,
        tournament_name: "Summer Championship",
        exported_match_count: 3,
        standings_rank: rank,
        team_slot: rank,
        team_name: `Team ${rank}`,
        player_1_name: `Player ${rank}A`,
        player_2_name: `Player ${rank}B`,
        player_3_name: `Player ${rank}C`,
        player_4_name: `Player ${rank}D`,
        matches_played: 3,
        total_position_points: totalPositionPoints,
        total_kills: totalKills,
        total_kill_points: totalKills,
        total_points: totalPositionPoints + totalKills,
        best_placement: rank,
        first_place_count: rank === 1 ? 2 : 0,
        tie_break_status: "unique_order",
      };
    }),
  };
}

function assertInvalid(
  mutate: (payload: MutablePayload) => void,
): void {
  const payload = validPayload();
  mutate(payload);

  try {
    parseStandingsExportRequest(payload);
  } catch (error) {
    assert(error instanceof EdgeFunctionError);
    assertEquals(error.code, "INVALID_STANDINGS_EXPORT_PAYLOAD");
    assertEquals(error.status, 400);
    return;
  }

  throw new Error("Expected INVALID_STANDINGS_EXPORT_PAYLOAD");
}

Deno.test("valid standings payload preserves exact Unicode values", () => {
  const payload = validPayload();

  for (const row of payload.rows) {
    row.tournament_name = "टूर्नामेंट 日本";
  }

  payload.rows[0].team_name = " Élite, Squad ";
  payload.rows[0].player_1_name = "निशांत";

  const request = parseStandingsExportRequest(payload);

  assertEquals(request.rows.length, 12);
  assertEquals(request.rows[0].tournament_name, "टूर्नामेंट 日本");
  assertEquals(request.rows[0].team_name, " Élite, Squad ");
  assertEquals(request.rows[0].player_1_name, "निशांत");
});

Deno.test("Google values use exact 20-column standings order", () => {
  const request = parseStandingsExportRequest(validPayload());
  const values = toStandingsGoogleSheetValues(request);

  assertEquals(STANDINGS_EXPORT_COLUMNS.length, 20);
  assertEquals(values.length, 12);
  assertEquals(values[0].length, 20);
  assertEquals(values[0], [
    "phase_10_v1",
    "tournament_standings",
    TOURNAMENT_ID,
    "Summer Championship",
    3,
    1,
    1,
    "Team 1",
    "Player 1A",
    "Player 1B",
    "Player 1C",
    "Player 1D",
    3,
    40,
    24,
    24,
    64,
    1,
    2,
    "unique_order",
  ]);
});

Deno.test("top-level shape and operation are strict", () => {
  assertInvalid((payload) => {
    payload.extra = true;
  });

  assertInvalid((payload) => {
    Reflect.deleteProperty(payload, "rows");
  });

  assertInvalid((payload) => {
    payload.operation = "export_match";
  });
});

Deno.test("invalid tournament UUID is rejected", () => {
  assertInvalid((payload) => {
    payload.tournament_id = "not-a-uuid";
  });
});

Deno.test("row counts other than twelve are rejected", () => {
  assertInvalid((payload) => {
    payload.rows.pop();
  });
});

Deno.test("row keys and field types are strict", () => {
  assertInvalid((payload) => {
    payload.rows[0].extra = true;
  });

  assertInvalid((payload) => {
    delete payload.rows[0].team_name;
  });

  assertInvalid((payload) => {
    payload.rows[0].total_kills = "24";
  });
});

Deno.test("standings ranks must match array order one through twelve", () => {
  assertInvalid((payload) => {
    payload.rows[0].standings_rank = 2;
  });

  assertInvalid((payload) => {
    const first = payload.rows[0];
    payload.rows[0] = payload.rows[1];
    payload.rows[1] = first;
  });
});

Deno.test("team slots must be unique and within one through twelve", () => {
  assertInvalid((payload) => {
    payload.rows[1].team_slot = 1;
  });

  assertInvalid((payload) => {
    payload.rows[0].team_slot = 13;
  });

  assertInvalid((payload) => {
    payload.rows[0].team_slot = 0;
  });
});

Deno.test("schema constants and tournament IDs must remain exact", () => {
  assertInvalid((payload) => {
    payload.rows[0].export_schema_version = "phase_10_v2";
  });

  assertInvalid((payload) => {
    payload.rows[0].export_type = "match_result";
  });

  assertInvalid((payload) => {
    payload.rows[0].tournament_id = "22222222-2222-4222-8222-222222222222";
  });
});

Deno.test("tournament names and exported match counts are consistent", () => {
  assertInvalid((payload) => {
    payload.rows[1].tournament_name = "Another Tournament";
  });

  assertInvalid((payload) => {
    payload.rows[1].exported_match_count = 2;
    payload.rows[1].matches_played = 2;
  });
});

Deno.test("exported match count must be between one and ten", () => {
  assertInvalid((payload) => {
    for (const row of payload.rows) {
      row.exported_match_count = 0;
      row.matches_played = 0;
      row.first_place_count = 0;
    }
  });

  assertInvalid((payload) => {
    for (const row of payload.rows) {
      row.exported_match_count = 11;
      row.matches_played = 11;
    }
  });
});

Deno.test("matches played must equal exported match count", () => {
  assertInvalid((payload) => {
    payload.rows[0].matches_played = 2;
  });
});

Deno.test("cumulative totals must be non-negative and arithmetically valid", () => {
  assertInvalid((payload) => {
    payload.rows[0].total_position_points = -1;
  });

  assertInvalid((payload) => {
    payload.rows[0].total_kills = -1;
    payload.rows[0].total_kill_points = -1;
  });

  assertInvalid((payload) => {
    payload.rows[0].total_kill_points = 23;
  });

  assertInvalid((payload) => {
    payload.rows[0].total_points = 999;
  });
});

Deno.test("best placement and first-place count use approved ranges", () => {
  assertInvalid((payload) => {
    payload.rows[0].best_placement = 0;
  });

  assertInvalid((payload) => {
    payload.rows[0].best_placement = 13;
  });

  assertInvalid((payload) => {
    payload.rows[0].first_place_count = -1;
  });

  assertInvalid((payload) => {
    payload.rows[0].first_place_count = 4;
  });
});

Deno.test("only approved tie-break statuses are accepted", () => {
  for (
    const status of [
      "unique_order",
      "tie_break_applied",
      "unresolved_tie",
      "resolved_by_existing_order",
    ]
  ) {
    const payload = validPayload();
    payload.rows[0].tie_break_status = status;

    assertEquals(
      parseStandingsExportRequest(payload).rows[0].tie_break_status,
      status,
    );
  }

  assertInvalid((payload) => {
    payload.rows[0].tie_break_status = "custom_order";
  });
});

Deno.test("duplicate non-empty player names are rejected", () => {
  assertInvalid((payload) => {
    payload.rows[0].player_2_name = payload.rows[0].player_1_name;
  });
});

Deno.test("empty player fields are accepted", () => {
  const payload = validPayload();

  payload.rows[0].player_3_name = "";
  payload.rows[0].player_4_name = "";

  const request = parseStandingsExportRequest(payload);

  assertEquals(request.rows[0].player_3_name, "");
  assertEquals(request.rows[0].player_4_name, "");
});

Deno.test("new standings errors expose approved statuses and messages", async () => {
  const cases: Array<{
    code: ErrorCode;
    status: number;
    message: string;
  }> = [
    {
      code: "INVALID_STANDINGS_EXPORT_PAYLOAD",
      status: 400,
      message: "The standings export payload is invalid.",
    },
    {
      code: "NO_FINALIZED_MATCHES",
      status: 409,
      message: "The tournament has no finalized matches to export.",
    },
    {
      code: "STANDINGS_EXPORT_DATA_MISMATCH",
      status: 409,
      message: "The standings export data does not match finalized records.",
    },
    {
      code: "GOOGLE_STANDINGS_SHEET_SCHEMA_MISMATCH",
      status: 409,
      message: "The Tournament Standings worksheet header is invalid.",
    },
    {
      code: "GOOGLE_STANDINGS_EXPORT_FAILURE",
      status: 502,
      message: "Google Sheets could not export the standings.",
    },
    {
      code: "GOOGLE_STANDINGS_EXPORT_RESPONSE_INVALID",
      status: 502,
      message: "Google Sheets returned an invalid standings export response.",
    },
  ];

  for (const testCase of cases) {
    const response = errorResponse(new EdgeFunctionError(testCase.code));
    const body = await response.json();

    assertEquals(response.status, testCase.status);
    assertEquals(body, {
      ok: false,
      error: {
        code: testCase.code,
        message: testCase.message,
      },
    });
  }
});
