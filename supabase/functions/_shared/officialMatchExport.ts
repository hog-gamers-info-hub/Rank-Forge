import { EdgeFunctionError } from "./errors.ts";
import type { MatchExportRequest } from "./matchExport.ts";
import type {
  OfficialMatch,
  OfficialMatchResult,
  OfficialPlayer,
  OfficialTeamSlot,
  OfficialTournament,
} from "./supabaseData.ts";

export interface OfficialMatchExportData {
  tournament: OfficialTournament;
  match: OfficialMatch;
  matchResults: OfficialMatchResult[];
  teamSlots: OfficialTeamSlot[];
  players: OfficialPlayer[];
}

function mismatch(): never {
  throw new EdgeFunctionError("MATCH_EXPORT_DATA_MISMATCH");
}

function parseTimestamp(value: string): number {
  const timestamp = Date.parse(value);

  if (Number.isNaN(timestamp)) {
    mismatch();
  }

  return timestamp;
}

function validateFinalizedTimestamps(
  request: MatchExportRequest,
  finalizedAt: string | null,
): void {
  const officialTimestamp = finalizedAt === null
    ? null
    : parseTimestamp(finalizedAt);

  for (const row of request.rows) {
    if (row.match_finalized_at.length === 0) {
      continue;
    }

    if (
      officialTimestamp === null ||
      parseTimestamp(row.match_finalized_at) !== officialTimestamp
    ) {
      mismatch();
    }
  }
}

export function validateOfficialMatchExport(
  request: MatchExportRequest,
  data: OfficialMatchExportData,
): void {
  if (
    data.tournament.id !== request.tournament_id ||
    request.rows.some((row) => row.tournament_name !== data.tournament.name)
  ) {
    mismatch();
  }

  if (
    data.match.id !== request.match_id ||
    data.match.tournament_id !== request.tournament_id
  ) {
    mismatch();
  }

  if (data.match.status !== "finalized") {
    throw new EdgeFunctionError("MATCH_NOT_FINALIZED");
  }

  const expectedMatchLabel = `Match ${data.match.match_number}`;

  if (request.rows.some((row) => row.match_label !== expectedMatchLabel)) {
    mismatch();
  }

  validateFinalizedTimestamps(request, data.match.finalized_at);

  if (data.teamSlots.length !== 12) {
    mismatch();
  }

  const teamSlotsById = new Map<string, OfficialTeamSlot>();
  const teamSlotsByNumber = new Map<number, OfficialTeamSlot>();

  for (const teamSlot of data.teamSlots) {
    if (
      teamSlot.tournament_id !== request.tournament_id ||
      teamSlot.slot_number < 1 ||
      teamSlot.slot_number > 12 ||
      teamSlotsById.has(teamSlot.id) ||
      teamSlotsByNumber.has(teamSlot.slot_number) ||
      typeof teamSlot.team_name !== "string" ||
      teamSlot.team_name.length === 0
    ) {
      mismatch();
    }

    teamSlotsById.set(teamSlot.id, teamSlot);
    teamSlotsByNumber.set(teamSlot.slot_number, teamSlot);
  }

  if (data.matchResults.length !== 12) {
    mismatch();
  }

  const resultsBySlotNumber = new Map<number, OfficialMatchResult>();
  const officialPlacements = new Set<number>();
  const resultTeamSlotIds = new Set<string>();

  for (const result of data.matchResults) {
    const teamSlot = teamSlotsById.get(result.team_slot_id);

    if (
      result.match_id !== request.match_id ||
      result.review_status !== "confirmed" ||
      result.placement === null ||
      result.placement < 1 ||
      result.placement > 12 ||
      result.kills < 0 ||
      teamSlot === undefined ||
      officialPlacements.has(result.placement) ||
      resultTeamSlotIds.has(result.team_slot_id)
    ) {
      mismatch();
    }

    officialPlacements.add(result.placement);
    resultTeamSlotIds.add(result.team_slot_id);
    resultsBySlotNumber.set(teamSlot.slot_number, result);
  }

  if (
    officialPlacements.size !== 12 ||
    resultsBySlotNumber.size !== 12
  ) {
    mismatch();
  }

  const playersByTeamSlotId = new Map<string, Set<string>>();

  for (const player of data.players) {
    if (!teamSlotsById.has(player.team_slot_id)) {
      mismatch();
    }

    const names = playersByTeamSlotId.get(player.team_slot_id) ?? new Set();
    names.add(player.display_name);
    playersByTeamSlotId.set(player.team_slot_id, names);
  }

  for (const row of request.rows) {
    const teamSlot = teamSlotsByNumber.get(row.team_slot);
    const result = resultsBySlotNumber.get(row.team_slot);

    if (
      teamSlot === undefined ||
      result === undefined ||
      teamSlot.team_name !== row.team_name ||
      result.placement !== row.placement ||
      result.kills !== row.kills
    ) {
      mismatch();
    }

    const officialPlayerNames = playersByTeamSlotId.get(teamSlot.id) ??
      new Set<string>();
    const exportedPlayerNames = [
      row.player_1_name,
      row.player_2_name,
      row.player_3_name,
      row.player_4_name,
    ].filter((name) => name.length > 0);

    if (
      exportedPlayerNames.some((name) => !officialPlayerNames.has(name))
    ) {
      mismatch();
    }
  }
}
