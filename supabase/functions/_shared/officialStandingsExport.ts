import { EdgeFunctionError } from "./errors.ts";
import type {
  StandingsExportRequest,
  StandingsExportRow,
} from "./standingsExport.ts";
import { APPROVED_EXPORT_SCHEMA_VERSIONS } from "./standingsExport.ts";
import type {
  OfficialMatch,
  OfficialMatchResult,
  OfficialPlayer,
  OfficialTeamSlot,
  OfficialTournament,
} from "./supabaseData.ts";

export interface OfficialStandingsExportData {
  tournament: OfficialTournament;
  matches: OfficialMatch[];
  matchResults: OfficialMatchResult[];
  teamSlots: OfficialTeamSlot[];
  players: OfficialPlayer[];
}

export interface OfficialStandingTotals {
  teamSlot: number;
  matchesPlayed: number;
  totalPositionPoints: number;
  totalKills: number;
  totalKillPoints: number;
  totalPoints: number;
  bestPlacement: number | null;
  firstPlaceCount: number;
  latestMatchPlacement: number | null;
}

export interface RankedOfficialStanding extends OfficialStandingTotals {
  standingsRank: number;
  tieBreakStatus:
    | "unique_order"
    | "tie_break_applied"
    | "unresolved_tie";
}

export interface OfficialStandingsValidationResult {
  exportedMatchCount: number;
  rankedStandings: RankedOfficialStanding[];
}

const REQUIRED_SLOT_NUMBERS = new Set(
  Array.from({ length: 12 }, (_, index) => index + 1),
);

const POSITION_POINTS = new Map<number, number>([
  [1, 12],
  [2, 9],
  [3, 8],
  [4, 7],
  [5, 6],
  [6, 5],
  [7, 4],
  [8, 3],
  [9, 2],
  [10, 1],
  [11, 0],
  [12, 0],
]);

function mismatch(): never {
  throw new EdgeFunctionError("STANDINGS_EXPORT_DATA_MISMATCH");
}

function duplicateValues<T>(values: readonly T[]): boolean {
  return new Set(values).size !== values.length;
}

function completeTieKey(standing: OfficialStandingTotals): string {
  return [
    standing.totalPoints,
    standing.firstPlaceCount,
    standing.totalKills,
    standing.latestMatchPlacement,
  ].join(":");
}

export function rankOfficialStandings(
  standings: readonly OfficialStandingTotals[],
): RankedOfficialStanding[] {
  const completeTieCounts = new Map<string, number>();
  const totalPointCounts = new Map<number, number>();

  for (const standing of standings) {
    const tieKey = completeTieKey(standing);

    completeTieCounts.set(
      tieKey,
      (completeTieCounts.get(tieKey) ?? 0) + 1,
    );
    totalPointCounts.set(
      standing.totalPoints,
      (totalPointCounts.get(standing.totalPoints) ?? 0) + 1,
    );
  }

  return [...standings]
    .sort((left, right) =>
      right.totalPoints - left.totalPoints ||
      right.firstPlaceCount - left.firstPlaceCount ||
      right.totalKills - left.totalKills ||
      (left.latestMatchPlacement === null ? 1 : 0) -
        (right.latestMatchPlacement === null ? 1 : 0) ||
      (left.latestMatchPlacement ?? 0) -
        (right.latestMatchPlacement ?? 0) ||
      left.teamSlot - right.teamSlot
    )
    .map((standing, index) => {
      const isCompleteTie =
        (completeTieCounts.get(completeTieKey(standing)) ?? 0) > 1;
      const sharesTotalPoints =
        (totalPointCounts.get(standing.totalPoints) ?? 0) > 1;

      return {
        ...standing,
        standingsRank: index + 1,
        tieBreakStatus: isCompleteTie
          ? "unresolved_tie"
          : sharesTotalPoints
          ? "tie_break_applied"
          : "unique_order",
      };
    });
}

function validateTeamSlots(
  request: StandingsExportRequest,
  teamSlots: readonly OfficialTeamSlot[],
): {
  byId: Map<string, OfficialTeamSlot>;
  byNumber: Map<number, OfficialTeamSlot>;
} {
  if (teamSlots.length !== 12) {
    mismatch();
  }

  const byId = new Map<string, OfficialTeamSlot>();
  const byNumber = new Map<number, OfficialTeamSlot>();

  for (const teamSlot of teamSlots) {
    if (
      teamSlot.tournament_id !== request.tournament_id ||
      !REQUIRED_SLOT_NUMBERS.has(teamSlot.slot_number) ||
      byId.has(teamSlot.id) ||
      byNumber.has(teamSlot.slot_number) ||
      typeof teamSlot.team_name !== "string"
    ) {
      mismatch();
    }

    byId.set(teamSlot.id, teamSlot);
    byNumber.set(teamSlot.slot_number, teamSlot);
  }

  if (
    byId.size !== 12 ||
    byNumber.size !== 12 ||
    [...REQUIRED_SLOT_NUMBERS].some((slot) => !byNumber.has(slot))
  ) {
    mismatch();
  }

  return { byId, byNumber };
}

function selectFinalizedMatches(
  request: StandingsExportRequest,
  matches: readonly OfficialMatch[],
): OfficialMatch[] {
  if (
    matches.some((match) => match.tournament_id !== request.tournament_id) ||
    duplicateValues(matches.map((match) => match.id)) ||
    duplicateValues(matches.map((match) => match.match_number)) ||
    matches.some((match) =>
      match.status !== "draft" && match.status !== "finalized"
    )
  ) {
    mismatch();
  }

  const finalizedMatches = matches
    .filter((match) => match.status === "finalized")
    .sort((left, right) =>
      left.match_number - right.match_number ||
      left.id.localeCompare(right.id)
    );

  if (finalizedMatches.length === 0) {
    throw new EdgeFunctionError("NO_FINALIZED_MATCHES");
  }

  if (finalizedMatches.length > 10) {
    mismatch();
  }

  return finalizedMatches;
}

function reconstructStandings(
  finalizedMatches: readonly OfficialMatch[],
  matchResults: readonly OfficialMatchResult[],
  teamSlotsById: ReadonlyMap<string, OfficialTeamSlot>,
): RankedOfficialStanding[] {
  const finalizedMatchIds = new Set(finalizedMatches.map((match) => match.id));

  if (
    duplicateValues(matchResults.map((result) => result.id)) ||
    matchResults.some((result) => !finalizedMatchIds.has(result.match_id))
  ) {
    mismatch();
  }

  const resultsByMatchId = new Map<string, OfficialMatchResult[]>();

  for (const result of matchResults) {
    const rows = resultsByMatchId.get(result.match_id) ?? [];
    rows.push(result);
    resultsByMatchId.set(result.match_id, rows);
  }

  const totalsBySlot = new Map<number, OfficialStandingTotals>();
  const legacySnapshot = matchResults.every((result) =>
    result.participation_status === undefined
  );

  finalizedMatches.forEach((match) => {
    const results = resultsByMatchId.get(match.id) ?? [];

    if (results.length < 1 || results.length > 12) {
      mismatch();
    }

    const placements = new Set<number>();
    const teamSlotIds = new Set<string>();
    const seenSlotNumbers = new Set<number>();
    const participatedResults = results.filter((result) =>
      (result.participation_status ?? "PARTICIPATED") === "PARTICIPATED"
    );

    if (participatedResults.length < 1) {
      mismatch();
    }

    for (const result of results) {
      const teamSlot = teamSlotsById.get(result.team_slot_id);
      const placement = result.placement;
      const participatedIndex = participatedResults.indexOf(result);
      const expectedPlacement = participatedIndex + 1;
      const status = result.participation_status ?? "PARTICIPATED";

      if (
        result.match_id !== match.id ||
        result.review_status !== "confirmed" ||
        !Number.isInteger(result.kills) ||
        result.kills < 0 ||
        (status === "PARTICIPATED"
          ? placement !== expectedPlacement
          : status === "NO_SHOW"
          ? placement !== null || result.kills !== 0
          : true) ||
        teamSlot === undefined ||
        typeof teamSlot.team_name !== "string" ||
        teamSlot.team_name.length === 0 ||
        (placement !== null && placements.has(placement)) ||
        teamSlotIds.has(result.team_slot_id) ||
        seenSlotNumbers.has(teamSlot.slot_number)
      ) {
        mismatch();
      }

      const totals = totalsBySlot.get(teamSlot.slot_number) ?? {
        teamSlot: teamSlot.slot_number,
        matchesPlayed: 0,
        totalPositionPoints: 0,
        totalKills: 0,
        totalKillPoints: 0,
        totalPoints: 0,
        bestPlacement: legacySnapshot ? 13 : null,
        firstPlaceCount: 0,
        latestMatchPlacement: legacySnapshot ? 13 : null,
      };

      if (status === "PARTICIPATED") {
        const placementPoints = POSITION_POINTS.get(placement!);
        if (placementPoints === undefined) {
          mismatch();
        }
        totals.matchesPlayed += 1;
        totals.totalPositionPoints += placementPoints;
        totals.totalKills += result.kills;
        totals.totalKillPoints += result.kills;
        totals.totalPoints += placementPoints + result.kills;
        totals.bestPlacement = totals.bestPlacement === null
          ? placement
          : Math.min(totals.bestPlacement, placement!);

        if (placement === 1) {
          totals.firstPlaceCount += 1;
        }

        totals.latestMatchPlacement = placement;
      }

      totalsBySlot.set(teamSlot.slot_number, totals);

      if (placement !== null) {
        placements.add(placement);
      }
      teamSlotIds.add(result.team_slot_id);
      seenSlotNumbers.add(teamSlot.slot_number);
    }

    if (
      placements.size !== participatedResults.length ||
      teamSlotIds.size !== results.length ||
      seenSlotNumbers.size !== results.length ||
      Array.from(
        { length: participatedResults.length },
        (_, index) => index + 1,
      ).some(
        (value) => !placements.has(value) || !seenSlotNumbers.has(value),
      )
    ) {
      mismatch();
    }
  });

  if (
    totalsBySlot.size < 1 ||
    [...totalsBySlot.values()].some((standing) => standing.matchesPlayed < 0)
  ) {
    mismatch();
  }

  return rankOfficialStandings([...totalsBySlot.values()]);
}

function validatePlayers(
  request: StandingsExportRequest,
  players: readonly OfficialPlayer[],
  teamSlotsById: ReadonlyMap<string, OfficialTeamSlot>,
  teamSlotsByNumber: ReadonlyMap<number, OfficialTeamSlot>,
): void {
  const playerNamesByTeamSlotId = new Map<string, Set<string>>();

  for (const player of players) {
    if (!teamSlotsById.has(player.team_slot_id)) {
      mismatch();
    }

    const names = playerNamesByTeamSlotId.get(player.team_slot_id) ??
      new Set<string>();

    names.add(player.display_name);
    playerNamesByTeamSlotId.set(player.team_slot_id, names);
  }

  for (const row of request.rows) {
    const teamSlot = teamSlotsByNumber.get(row.team_slot);

    if (teamSlot === undefined) {
      mismatch();
    }

    const officialNames = playerNamesByTeamSlotId.get(teamSlot.id) ??
      new Set<string>();
    const exportedNames = [
      row.player_1_name,
      row.player_2_name,
      row.player_3_name,
      row.player_4_name,
    ].filter((name) => name.length > 0);

    if (exportedNames.some((name) => !officialNames.has(name))) {
      mismatch();
    }
  }
}

function compareOfficialRows(
  request: StandingsExportRequest,
  tournament: OfficialTournament,
  teamSlotsByNumber: ReadonlyMap<number, OfficialTeamSlot>,
  rankedStandings: readonly RankedOfficialStanding[],
  exportedMatchCount: number,
): void {
  if (
    tournament.id !== request.tournament_id ||
    request.rows.some((row) => row.tournament_name !== tournament.name) ||
    request.rows.length !== rankedStandings.length
  ) {
    mismatch();
  }

  for (let index = 0; index < rankedStandings.length; index += 1) {
    const official = rankedStandings[index];
    const row: StandingsExportRow = request.rows[index];
    const teamSlot = teamSlotsByNumber.get(official.teamSlot);

    if (
      teamSlot === undefined ||
      !APPROVED_EXPORT_SCHEMA_VERSIONS.has(row.export_schema_version) ||
      row.export_type !== "tournament_standings" ||
      row.tournament_id !== tournament.id ||
      row.tournament_name !== tournament.name ||
      row.exported_match_count !== exportedMatchCount ||
      row.standings_rank !== official.standingsRank ||
      row.team_slot !== official.teamSlot ||
      row.team_name !== teamSlot.team_name ||
      row.matches_played !== official.matchesPlayed ||
      row.total_position_points !== official.totalPositionPoints ||
      row.total_kills !== official.totalKills ||
      row.total_kill_points !== official.totalKillPoints ||
      row.total_points !== official.totalPoints ||
      row.best_placement !== official.bestPlacement ||
      row.first_place_count !== official.firstPlaceCount ||
      row.tie_break_status !== official.tieBreakStatus
    ) {
      mismatch();
    }
  }
}

export function validateOfficialStandingsExport(
  request: StandingsExportRequest,
  data: OfficialStandingsExportData,
): OfficialStandingsValidationResult {
  if (
    data.tournament.id !== request.tournament_id ||
    request.rows.some((row) => row.tournament_name !== data.tournament.name)
  ) {
    mismatch();
  }

  const teamSlots = validateTeamSlots(request, data.teamSlots);
  const finalizedMatches = selectFinalizedMatches(request, data.matches);
  const rankedStandings = reconstructStandings(
    finalizedMatches,
    data.matchResults,
    teamSlots.byId,
  );

  compareOfficialRows(
    request,
    data.tournament,
    teamSlots.byNumber,
    rankedStandings,
    finalizedMatches.length,
  );

  validatePlayers(
    request,
    data.players,
    teamSlots.byId,
    teamSlots.byNumber,
  );

  return {
    exportedMatchCount: finalizedMatches.length,
    rankedStandings,
  };
}
