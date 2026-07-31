import { EdgeFunctionError } from "../_shared/errors.ts";
import {
  type OfficialStandingTotals,
  rankOfficialStandings,
  validateOfficialStandingsExport,
} from "../_shared/officialStandingsExport.ts";
import {
  parseStandingsExportRequest,
  type StandingsExportRequest,
} from "../_shared/standingsExport.ts";
import type {
  OfficialMatch,
  OfficialMatchResult,
  OfficialPlayer,
  OfficialTeamSlot,
  OfficialTournament,
} from "../_shared/supabaseData.ts";

const TOURNAMENT_ID = "11111111-1111-4111-8111-111111111111";
const OTHER_TOURNAMENT_ID = "99999999-9999-4999-8999-999999999999";

const POSITION_POINTS = [12, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0, 0];

interface Scenario {
  request: StandingsExportRequest;
  tournament: OfficialTournament;
  matches: OfficialMatch[];
  matchResults: OfficialMatchResult[];
  teamSlots: OfficialTeamSlot[];
  players: OfficialPlayer[];
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

function assertMismatch(operation: () => unknown): void {
  try {
    operation();
  } catch (error) {
    assert(error instanceof EdgeFunctionError);
    assertEquals(error.code, "STANDINGS_EXPORT_DATA_MISMATCH");
    assertEquals(error.status, 409);
    return;
  }

  throw new Error("Expected STANDINGS_EXPORT_DATA_MISMATCH");
}

function teamSlotId(slot: number): string {
  return `30000000-0000-4000-8000-${slot.toString().padStart(12, "0")}`;
}

function matchId(matchNumber: number): string {
  return `20000000-0000-4000-8000-${
    matchNumber
      .toString()
      .padStart(12, "0")
  }`;
}

function resultId(matchNumber: number, slot: number): string {
  const value = matchNumber * 100 + slot;

  return `40000000-0000-4000-8000-${value.toString().padStart(12, "0")}`;
}

function validScenario(finalizedMatchCount = 1): Scenario {
  const tournament: OfficialTournament = {
    id: TOURNAMENT_ID,
    name: "Summer Championship",
  };

  const teamSlots: OfficialTeamSlot[] = Array.from(
    { length: 12 },
    (_, index) => {
      const slot = index + 1;

      return {
        id: teamSlotId(slot),
        tournament_id: TOURNAMENT_ID,
        slot_number: slot,
        team_name: `Team ${slot}`,
      };
    },
  );

  const players: OfficialPlayer[] = teamSlots.flatMap((teamSlot) =>
    Array.from({ length: 4 }, (_, index) => ({
      id: `${teamSlot.id}-player-${index + 1}`,
      team_slot_id: teamSlot.id,
      display_name: `Player ${teamSlot.slot_number}${
        String.fromCharCode(
          65 + index,
        )
      }`,
    }))
  );

  const matches: OfficialMatch[] = Array.from(
    { length: finalizedMatchCount },
    (_, index) => {
      const number = index + 1;

      return {
        id: matchId(number),
        tournament_id: TOURNAMENT_ID,
        match_number: number,
        status: "finalized",
        finalized_at: `2026-07-${
          (20 + number)
            .toString()
            .padStart(2, "0")
        }T12:00:00Z`,
      };
    },
  );

  const matchResults: OfficialMatchResult[] = matches.flatMap((match) =>
    teamSlots.map((teamSlot) => ({
      id: resultId(match.match_number, teamSlot.slot_number),
      match_id: match.id,
      team_slot_id: teamSlot.id,
      placement: teamSlot.slot_number,
      kills: 0,
      review_status: "confirmed",
    }))
  );

  const rows = teamSlots.map((teamSlot, index) => {
    const slot = teamSlot.slot_number;
    const totalPositionPoints = POSITION_POINTS[index] * finalizedMatchCount;

    return {
      export_schema_version: "phase_10_v1",
      export_type: "tournament_standings",
      tournament_id: TOURNAMENT_ID,
      tournament_name: tournament.name,
      exported_match_count: finalizedMatchCount,
      standings_rank: slot,
      team_slot: slot,
      team_name: teamSlot.team_name,
      player_1_name: `Player ${slot}A`,
      player_2_name: `Player ${slot}B`,
      player_3_name: `Player ${slot}C`,
      player_4_name: `Player ${slot}D`,
      matches_played: finalizedMatchCount,
      total_position_points: totalPositionPoints,
      total_kills: 0,
      total_kill_points: 0,
      total_points: totalPositionPoints,
      best_placement: slot,
      first_place_count: slot === 1 ? finalizedMatchCount : 0,
      tie_break_status: slot >= 11 ? "tie_break_applied" : "unique_order",
    };
  });

  return {
    request: parseStandingsExportRequest({
      operation: "export_standings",
      tournament_id: TOURNAMENT_ID,
      rows,
    }),
    tournament,
    matches,
    matchResults,
    teamSlots,
    players,
  };
}

function validate(scenario: Scenario) {
  return validateOfficialStandingsExport(scenario.request, {
    tournament: scenario.tournament,
    matches: scenario.matches,
    matchResults: scenario.matchResults,
    teamSlots: scenario.teamSlots,
    players: scenario.players,
  });
}

Deno.test("valid official standings reconstruct exact totals and order", () => {
  const scenario = validScenario(2);
  const result = validate(scenario);

  assertEquals(result.exportedMatchCount, 2);
  assertEquals(result.rankedStandings.length, 12);
  assertEquals(result.rankedStandings[0], {
    teamSlot: 1,
    matchesPlayed: 2,
    totalPositionPoints: 24,
    totalKills: 0,
    totalKillPoints: 0,
    totalPoints: 24,
    bestPlacement: 1,
    firstPlaceCount: 2,
    latestMatchPlacement: 1,
    standingsRank: 1,
    tieBreakStatus: "unique_order",
  });
  assertEquals(result.rankedStandings[10].tieBreakStatus, "tie_break_applied");
  assertEquals(result.rankedStandings[11].tieBreakStatus, "tie_break_applied");
});

Deno.test("draft matches are excluded from official standings", () => {
  const scenario = validScenario(1);

  scenario.matches.push({
    id: matchId(2),
    tournament_id: TOURNAMENT_ID,
    match_number: 2,
    status: "draft",
    finalized_at: null,
  });

  const result = validate(scenario);

  assertEquals(result.exportedMatchCount, 1);
});

Deno.test("no finalized matches use the dedicated safe error", () => {
  const scenario = validScenario(1);
  scenario.matches[0].status = "draft";
  scenario.matches[0].finalized_at = null;
  scenario.matchResults = [];

  try {
    validate(scenario);
  } catch (error) {
    assert(error instanceof EdgeFunctionError);
    assertEquals(error.code, "NO_FINALIZED_MATCHES");
    assertEquals(error.status, 409);
    return;
  }

  throw new Error("Expected NO_FINALIZED_MATCHES");
});

Deno.test("more than ten finalized matches are rejected", () => {
  const scenario = validScenario(10);
  const extraMatch: OfficialMatch = {
    id: matchId(11),
    tournament_id: TOURNAMENT_ID,
    match_number: 11,
    status: "finalized",
    finalized_at: "2026-08-01T12:00:00Z",
  };

  scenario.matches.push(extraMatch);

  assertMismatch(() => validate(scenario));
});

Deno.test("duplicate official match IDs and numbers are rejected", () => {
  const duplicateId = validScenario(1);
  duplicateId.matches.push({
    ...duplicateId.matches[0],
    match_number: 2,
  });

  assertMismatch(() => validate(duplicateId));

  const duplicateNumber = validScenario(1);
  duplicateNumber.matches.push({
    ...duplicateNumber.matches[0],
    id: matchId(2),
  });

  assertMismatch(() => validate(duplicateNumber));
});

Deno.test("tournament identity and official name must match", () => {
  const wrongIdentity = validScenario();
  wrongIdentity.tournament.id = OTHER_TOURNAMENT_ID;

  assertMismatch(() => validate(wrongIdentity));

  const wrongName = validScenario();
  wrongName.tournament.name = "Different Tournament";

  assertMismatch(() => validate(wrongName));
});

Deno.test("official team slots must be exactly twelve valid named slots", () => {
  const missing = validScenario();
  missing.teamSlots.pop();

  assertMismatch(() => validate(missing));

  const duplicate = validScenario();
  duplicate.teamSlots[1].slot_number = 1;

  assertMismatch(() => validate(duplicate));

  const blankName = validScenario();
  blankName.teamSlots[0].team_name = "";

  assertMismatch(() => validate(blankName));
});

Deno.test("every finalized match requires exactly twelve results", () => {
  const scenario = validScenario();
  scenario.matchResults.pop();

  assertMismatch(() => validate(scenario));
});

Deno.test("official result rows must be confirmed and belong to a finalized match", () => {
  const unconfirmed = validScenario();
  unconfirmed.matchResults[0].review_status = "pending";

  assertMismatch(() => validate(unconfirmed));

  const unknownMatch = validScenario();
  unknownMatch.matchResults[0].match_id = matchId(9);

  assertMismatch(() => validate(unknownMatch));
});

Deno.test("official placements and team slots must be unique and complete", () => {
  const duplicatePlacement = validScenario();
  duplicatePlacement.matchResults[1].placement = 1;

  assertMismatch(() => validate(duplicatePlacement));

  const duplicateTeam = validScenario();
  duplicateTeam.matchResults[1].team_slot_id =
    duplicateTeam.matchResults[0].team_slot_id;

  assertMismatch(() => validate(duplicateTeam));
});

Deno.test("negative official kills are rejected", () => {
  const scenario = validScenario();
  scenario.matchResults[0].kills = -1;

  assertMismatch(() => validate(scenario));
});

Deno.test("payload cumulative totals and match count must match reconstruction", () => {
  const totals = validScenario();
  totals.request.rows[0].total_position_points += 1;
  totals.request.rows[0].total_points += 1;

  assertMismatch(() => validate(totals));

  const matchCount = validScenario();
  matchCount.request.rows.forEach((row) => {
    row.exported_match_count = 2;
    row.matches_played = 2;
  });

  assertMismatch(() => validate(matchCount));
});

Deno.test("payload ranking order and team identity must match reconstruction", () => {
  const rank = validScenario();
  const first = rank.request.rows[0];
  rank.request.rows[0] = rank.request.rows[1];
  rank.request.rows[1] = first;

  assertMismatch(() => validate(rank));

  const teamName = validScenario();
  teamName.request.rows[0].team_name = "Wrong Team";

  assertMismatch(() => validate(teamName));
});

Deno.test("payload best placement and first-place count are verified", () => {
  const best = validScenario();
  best.request.rows[0].best_placement = 2;

  assertMismatch(() => validate(best));

  const wins = validScenario();
  wins.request.rows[0].first_place_count = 0;

  assertMismatch(() => validate(wins));
});

Deno.test("tie-break status is reconstructed and compared", () => {
  const scenario = validScenario();
  scenario.request.rows[10].tie_break_status = "unique_order";

  assertMismatch(() => validate(scenario));
});

Deno.test("ranking helper supports applied and unresolved ties", () => {
  const base: OfficialStandingTotals = {
    teamSlot: 1,
    matchesPlayed: 2,
    totalPositionPoints: 10,
    totalKills: 5,
    totalKillPoints: 5,
    totalPoints: 15,
    bestPlacement: 2,
    firstPlaceCount: 0,
    latestMatchPlacement: 3,
  };

  const applied = rankOfficialStandings([
    base,
    {
      ...base,
      teamSlot: 2,
      totalKills: 4,
      totalKillPoints: 4,
      totalPoints: 15,
      latestMatchPlacement: 4,
    },
  ]);

  assertEquals(applied[0].teamSlot, 1);
  assertEquals(applied[0].tieBreakStatus, "tie_break_applied");
  assertEquals(applied[1].tieBreakStatus, "tie_break_applied");

  const unresolved = rankOfficialStandings([
    base,
    { ...base, teamSlot: 2 },
  ]);

  assertEquals(unresolved[0].tieBreakStatus, "unresolved_tie");
  assertEquals(unresolved[1].tieBreakStatus, "unresolved_tie");
  assertEquals(unresolved[0].teamSlot, 1);
  assertEquals(unresolved[1].teamSlot, 2);
});

Deno.test("player names must belong to the exported team roster", () => {
  const unknown = validScenario();
  unknown.request.rows[0].player_1_name = "Unknown Player";

  assertMismatch(() => validate(unknown));

  const crossTeam = validScenario();
  crossTeam.request.rows[0].player_1_name = "Player 2A";

  assertMismatch(() => validate(crossTeam));
});

Deno.test("empty player fields are accepted and unknown player slots fail", () => {
  const empty = validScenario();
  empty.request.rows[0].player_3_name = "";
  empty.request.rows[0].player_4_name = "";

  assertEquals(validate(empty).exportedMatchCount, 1);

  const unknownSlot = validScenario();
  unknownSlot.players[0].team_slot_id = "88888888-8888-4888-8888-888888888888";

  assertMismatch(() => validate(unknownSlot));
});
