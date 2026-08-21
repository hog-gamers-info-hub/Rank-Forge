import { EdgeFunctionError } from "../_shared/errors.ts";
import {
  type MatchExportRequest,
  parseMatchExportRequest,
} from "../_shared/matchExport.ts";
import {
  type OfficialMatchExportData,
  validateOfficialMatchExport,
} from "../_shared/officialMatchExport.ts";

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

function validRequest(
  finalizedAt = "",
  participantCount = 12,
): MatchExportRequest {
  return parseMatchExportRequest({
    operation: "export_match",
    tournament_id: TOURNAMENT_ID,
    match_id: MATCH_ID,
    rows: Array.from({ length: participantCount }, (_, index) => {
      const placement = index + 1;
      const kills = index;

      return {
        export_schema_version: "phase_10_v1",
        export_type: "match_result",
        tournament_id: TOURNAMENT_ID,
        tournament_name: "Championship",
        match_id: MATCH_ID,
        match_label: "Match 1",
        match_finalized_at: finalizedAt,
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
  });
}

function teamSlotId(slotNumber: number): string {
  return `team-slot-${slotNumber}`;
}

function validOfficialData(participantCount = 12): OfficialMatchExportData {
  return {
    tournament: {
      id: TOURNAMENT_ID,
      name: "Championship",
    },
    match: {
      id: MATCH_ID,
      tournament_id: TOURNAMENT_ID,
      match_number: 1,
      status: "finalized",
      finalized_at: "2026-07-31T12:30:00Z",
    },
    teamSlots: Array.from({ length: 12 }, (_, index) => {
      const slotNumber = index + 1;

      return {
        id: teamSlotId(slotNumber),
        tournament_id: TOURNAMENT_ID,
        slot_number: slotNumber,
        team_name: slotNumber <= participantCount ? `Team ${slotNumber}` : "",
      };
    }),
    matchResults: Array.from({ length: participantCount }, (_, index) => {
      const slotNumber = index + 1;

      return {
        id: `result-${slotNumber}`,
        match_id: MATCH_ID,
        team_slot_id: teamSlotId(slotNumber),
        placement: slotNumber,
        kills: index,
        review_status: "confirmed",
      };
    }),
    players: Array.from({ length: participantCount }, (_, index) => {
      const slotNumber = index + 1;

      return ["A", "B", "C", "D"].map((suffix) => ({
        id: `player-${slotNumber}-${suffix}`,
        team_slot_id: teamSlotId(slotNumber),
        display_name: `Player ${slotNumber}${suffix}`,
      }));
    }).flat(),
  };
}

function assertRejected(
  mutate: (
    request: MatchExportRequest,
    data: OfficialMatchExportData,
  ) => void,
  expectedCode = "MATCH_EXPORT_DATA_MISMATCH",
  expectedStatus = 409,
): void {
  const request = validRequest();
  const data = validOfficialData();
  mutate(request, data);

  try {
    validateOfficialMatchExport(request, data);
  } catch (error) {
    assert(error instanceof EdgeFunctionError);
    assertEquals(error.code, expectedCode);
    assertEquals(error.status, expectedStatus);
    return;
  }

  throw new Error(`Expected ${expectedCode}`);
}

Deno.test("valid finalized official data matches export payload", () => {
  validateOfficialMatchExport(validRequest(), validOfficialData());
});

Deno.test("valid ten-participant official data allows blank inactive slots", () => {
  validateOfficialMatchExport(validRequest("", 10), validOfficialData(10));
});

Deno.test("official tournament identity and name must match", () => {
  assertRejected((_request, data) => {
    data.tournament.id = "99999999-9999-4999-8999-999999999999";
  });

  assertRejected((_request, data) => {
    data.tournament.name = "Another Championship";
  });
});

Deno.test("official match must belong to the requested tournament", () => {
  assertRejected((_request, data) => {
    data.match.tournament_id = "99999999-9999-4999-8999-999999999999";
  });
});

Deno.test("non-finalized match uses dedicated failure", () => {
  assertRejected(
    (_request, data) => {
      data.match.status = "draft";
    },
    "MATCH_NOT_FINALIZED",
    409,
  );
});

Deno.test("official match number must match exported label", () => {
  assertRejected((_request, data) => {
    data.match.match_number = 2;
  });
});

Deno.test("official data requires exactly twelve team slots", () => {
  assertRejected((_request, data) => {
    data.teamSlots.pop();
  });
});

Deno.test("official team slots must be unique and tournament-scoped", () => {
  assertRejected((_request, data) => {
    data.teamSlots[1].slot_number = 1;
  });

  assertRejected((_request, data) => {
    data.teamSlots[0].tournament_id = "99999999-9999-4999-8999-999999999999";
  });
});

Deno.test("official results and request rows must have the same participant count", () => {
  assertRejected((_request, data) => {
    data.matchResults = validOfficialData(10).matchResults;
  });

  const request = validRequest("", 10);
  const data = validOfficialData(10);
  data.matchResults.pop();

  try {
    validateOfficialMatchExport(request, data);
  } catch (error) {
    assert(error instanceof EdgeFunctionError);
    assertEquals(error.code, "MATCH_EXPORT_DATA_MISMATCH");
    return;
  }

  throw new Error("Expected MATCH_EXPORT_DATA_MISMATCH");
});

Deno.test("official results must remain confirmed", () => {
  assertRejected((_request, data) => {
    data.matchResults[0].review_status = "draft";
  });
});

Deno.test("a referenced inactive slot with a blank team name is rejected", () => {
  const request = validRequest("", 10);
  const data = validOfficialData(10);
  data.matchResults[0].team_slot_id = teamSlotId(11);
  request.rows[0].team_slot = 11;
  request.rows[0].team_name = "";

  try {
    validateOfficialMatchExport(request, data);
  } catch (error) {
    assert(error instanceof EdgeFunctionError);
    assertEquals(error.code, "MATCH_EXPORT_DATA_MISMATCH");
    return;
  }

  throw new Error("Expected MATCH_EXPORT_DATA_MISMATCH");
});

Deno.test("ten-participant official placements must cover one through ten", () => {
  const request = validRequest("", 10);
  const data = validOfficialData(10);
  data.matchResults[9].placement = 11;

  try {
    validateOfficialMatchExport(request, data);
  } catch (error) {
    assert(error instanceof EdgeFunctionError);
    assertEquals(error.code, "MATCH_EXPORT_DATA_MISMATCH");
    return;
  }

  throw new Error("Expected MATCH_EXPORT_DATA_MISMATCH");
});

Deno.test("official placements and result team slots must be unique", () => {
  assertRejected((_request, data) => {
    data.matchResults[1].placement = 1;
  });

  assertRejected((_request, data) => {
    data.matchResults[1].team_slot_id = teamSlotId(1);
  });
});

Deno.test("official placement and kills must match payload", () => {
  assertRejected((_request, data) => {
    data.matchResults[0].placement = 2;
  });

  assertRejected((_request, data) => {
    data.matchResults[0].kills = 99;
  });
});

Deno.test("official team name must match payload exactly", () => {
  assertRejected((_request, data) => {
    data.teamSlots[0].team_name = "team 1";
  });
});

Deno.test("exported player must belong to the official team slot", () => {
  assertRejected((request) => {
    request.rows[0].player_1_name = "Unknown Player";
  });

  assertRejected((request) => {
    request.rows[0].player_1_name = "Player 2A";
  });
});

Deno.test("empty exported player fields are accepted", () => {
  const request = validRequest();
  const data = validOfficialData();

  request.rows[0].player_3_name = "";
  request.rows[0].player_4_name = "";

  validateOfficialMatchExport(request, data);
});

Deno.test("players returned outside requested team slots are rejected", () => {
  assertRejected((_request, data) => {
    data.players.push({
      id: "unexpected-player",
      team_slot_id: "unexpected-slot",
      display_name: "Unexpected",
    });
  });
});

Deno.test("empty finalized timestamp is accepted", () => {
  validateOfficialMatchExport(validRequest(""), validOfficialData());
});

Deno.test("equivalent RFC 3339 timestamp offsets are accepted", () => {
  validateOfficialMatchExport(
    validRequest("2026-07-31T18:00:00+05:30"),
    validOfficialData(),
  );
});

Deno.test("non-matching finalized timestamp is rejected", () => {
  const request = validRequest("2026-07-31T12:31:00Z");
  const data = validOfficialData();

  try {
    validateOfficialMatchExport(request, data);
  } catch (error) {
    assert(error instanceof EdgeFunctionError);
    assertEquals(error.code, "MATCH_EXPORT_DATA_MISMATCH");
    return;
  }

  throw new Error("Expected timestamp mismatch");
});

Deno.test("non-empty timestamp is rejected when official timestamp is null", () => {
  const request = validRequest("2026-07-31T12:30:00Z");
  const data = validOfficialData();
  data.match.finalized_at = null;

  try {
    validateOfficialMatchExport(request, data);
  } catch (error) {
    assert(error instanceof EdgeFunctionError);
    assertEquals(error.code, "MATCH_EXPORT_DATA_MISMATCH");
    return;
  }

  throw new Error("Expected timestamp mismatch");
});
