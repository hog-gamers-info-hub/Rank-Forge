import { EdgeFunctionError } from "../_shared/errors.ts";
import {
  MATCH_EXPORT_COLUMNS,
  parseMatchExportRequest,
  toGoogleSheetValues,
} from "../_shared/matchExport.ts";

const TOURNAMENT_ID = "11111111-1111-4111-8111-111111111111";
const MATCH_ID = "22222222-2222-4222-8222-222222222222";

const PLACEMENT_POINTS = [
  12,
  9,
  8,
  7,
  6,
  5,
  4,
  3,
  2,
  1,
  0,
  0,
];

type MutablePayload = {
  operation: unknown;
  tournament_id: unknown;
  match_id: unknown;
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
    operation: "export_match",
    tournament_id: TOURNAMENT_ID,
    match_id: MATCH_ID,
    rows: Array.from({ length: 12 }, (_, index) => {
      const placement = index + 1;
      const kills = index;

      return {
        export_schema_version: "phase_10_v1",
        export_type: "match_result",
        tournament_id: TOURNAMENT_ID,
        tournament_name: "Summer Championship",
        match_id: MATCH_ID,
        match_label: "Match 1",
        match_finalized_at: "",
        row_number: placement,
        placement,
        team_slot: placement,
        team_name: `Team ${placement}`,
        player_1_name: `Player ${placement}A`,
        player_2_name: `Player ${placement}B`,
        player_3_name: `Player ${placement}C`,
        player_4_name: `Player ${placement}D`,
        placement_points: PLACEMENT_POINTS[index],
        kills,
        kill_points: kills,
        total_points: PLACEMENT_POINTS[index] + kills,
        correction_status: "original_finalized",
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
    parseMatchExportRequest(payload);
  } catch (error) {
    assert(error instanceof EdgeFunctionError);
    assertEquals(error.code, "INVALID_MATCH_EXPORT_PAYLOAD");
    assertEquals(error.status, 400);
    return;
  }

  throw new Error("Expected INVALID_MATCH_EXPORT_PAYLOAD");
}

Deno.test("valid match export payload preserves exact values", () => {
  const payload = validPayload();
  payload.rows[0].tournament_name = "टूर्नामेंट 日本";
  payload.rows[0].team_name = "Élite, Squad";
  payload.rows[0].player_1_name = "निशांत";

  for (let index = 1; index < payload.rows.length; index += 1) {
    payload.rows[index].tournament_name = "टूर्नामेंट 日本";
  }

  const request = parseMatchExportRequest(payload);

  assertEquals(request.rows.length, 12);
  assertEquals(request.rows[0].tournament_name, "टूर्नामेंट 日本");
  assertEquals(request.rows[0].team_name, "Élite, Squad");
  assertEquals(request.rows[0].player_1_name, "निशांत");
});

Deno.test("Google values use exact 20-column order", () => {
  const request = parseMatchExportRequest(validPayload());
  const values = toGoogleSheetValues(request);

  assertEquals(values.length, 12);
  assertEquals(values[0].length, 20);
  assertEquals(MATCH_EXPORT_COLUMNS.length, 20);
  assertEquals(values[0], [
    "phase_10_v1",
    "match_result",
    TOURNAMENT_ID,
    "Summer Championship",
    MATCH_ID,
    "Match 1",
    "",
    1,
    1,
    1,
    "Team 1",
    "Player 1A",
    "Player 1B",
    "Player 1C",
    "Player 1D",
    12,
    0,
    0,
    12,
    "original_finalized",
  ]);
});

Deno.test("unknown top-level fields are rejected", () => {
  assertInvalid((payload) => {
    payload.extra = true;
  });
});

Deno.test("missing top-level fields are rejected", () => {
  assertInvalid((payload) => {
    delete payload.match_id;
  });
});

Deno.test("invalid UUID values are rejected", () => {
  assertInvalid((payload) => {
    payload.match_id = "not-a-uuid";
  });
});

Deno.test("row counts other than twelve are rejected", () => {
  assertInvalid((payload) => {
    payload.rows.pop();
  });
});

Deno.test("unknown row fields are rejected", () => {
  assertInvalid((payload) => {
    payload.rows[0].extra = true;
  });
});

Deno.test("wrong row field types are rejected", () => {
  assertInvalid((payload) => {
    payload.rows[0].kills = "0";
  });
});

Deno.test("duplicate team slots are rejected", () => {
  assertInvalid((payload) => {
    payload.rows[1].team_slot = 1;
  });
});

Deno.test("row order and row numbers must match placement order", () => {
  assertInvalid((payload) => {
    payload.rows[0].placement = 2;
  });

  assertInvalid((payload) => {
    payload.rows[0].row_number = 2;
  });
});

Deno.test("schema and request identifiers must remain consistent", () => {
  assertInvalid((payload) => {
    payload.rows[0].export_schema_version = "phase_10_v2";
  });

  assertInvalid((payload) => {
    payload.rows[0].export_type = "tournament_standings";
  });

  assertInvalid((payload) => {
    payload.rows[0].tournament_id = "33333333-3333-4333-8333-333333333333";
  });

  assertInvalid((payload) => {
    payload.rows[0].match_id = "44444444-4444-4444-8444-444444444444";
  });
});

Deno.test("tournament names and match labels must be consistent", () => {
  assertInvalid((payload) => {
    payload.rows[1].tournament_name = "Another Tournament";
  });

  assertInvalid((payload) => {
    payload.rows[1].match_label = "Match 2";
  });
});

Deno.test("approved scoring relationships are enforced", () => {
  assertInvalid((payload) => {
    payload.rows[0].placement_points = 11;
  });

  assertInvalid((payload) => {
    payload.rows[0].kill_points = 1;
  });

  assertInvalid((payload) => {
    payload.rows[0].total_points = 99;
  });

  assertInvalid((payload) => {
    payload.rows[0].kills = -1;
    payload.rows[0].kill_points = -1;
    payload.rows[0].total_points = 11;
  });
});

Deno.test("only approved correction statuses are accepted", () => {
  assertInvalid((payload) => {
    payload.rows[0].correction_status = "draft";
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

  const request = parseMatchExportRequest(payload);

  assertEquals(request.rows[0].player_3_name, "");
  assertEquals(request.rows[0].player_4_name, "");
});

Deno.test("finalized timestamp accepts empty and valid RFC 3339 values", () => {
  const emptyTimestamp = parseMatchExportRequest(validPayload());
  assertEquals(emptyTimestamp.rows[0].match_finalized_at, "");

  const payload = validPayload();

  for (const row of payload.rows) {
    row.match_finalized_at = "2026-07-31T18:00:00.123+05:30";
  }

  const request = parseMatchExportRequest(payload);

  assertEquals(
    request.rows[0].match_finalized_at,
    "2026-07-31T18:00:00.123+05:30",
  );
});

Deno.test("invalid finalized timestamps are rejected", () => {
  assertInvalid((payload) => {
    payload.rows[0].match_finalized_at = "2026-02-30T12:00:00Z";
  });

  assertInvalid((payload) => {
    payload.rows[0].match_finalized_at = "July 31, 2026";
  });
});
