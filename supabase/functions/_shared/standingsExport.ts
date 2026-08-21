import { EdgeFunctionError } from "./errors.ts";

export const STANDINGS_EXPORT_COLUMNS = [
  "export_schema_version",
  "export_type",
  "tournament_id",
  "tournament_name",
  "exported_match_count",
  "standings_rank",
  "team_slot",
  "team_name",
  "player_1_name",
  "player_2_name",
  "player_3_name",
  "player_4_name",
  "matches_played",
  "total_position_points",
  "total_kills",
  "total_kill_points",
  "total_points",
  "best_placement",
  "first_place_count",
  "tie_break_status",
] as const;

export const MAX_STANDINGS_EXPORT_ROWS = 12;

const STANDINGS_EXPORT_REQUEST_KEYS = [
  "operation",
  "tournament_id",
  "rows",
] as const;

const APPROVED_TIE_BREAK_STATUSES = new Set([
  "unique_order",
  "tie_break_applied",
  "unresolved_tie",
  "resolved_by_existing_order",
]);

export const APPROVED_EXPORT_SCHEMA_VERSIONS = new Set([
  "phase_10_v1",
  "phase_10_v2",
]);

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export interface StandingsExportRow {
  export_schema_version: string;
  export_type: string;
  tournament_id: string;
  tournament_name: string;
  exported_match_count: number;
  standings_rank: number;
  team_slot: number;
  team_name: string;
  player_1_name: string;
  player_2_name: string;
  player_3_name: string;
  player_4_name: string;
  matches_played: number;
  total_position_points: number;
  total_kills: number;
  total_kill_points: number;
  total_points: number;
  best_placement: number | null;
  first_place_count: number;
  tie_break_status: string;
}

export interface StandingsExportRequest {
  operation: "export_standings";
  tournament_id: string;
  rows: StandingsExportRow[];
}

export type StandingsExportCell = string | number | null;

function invalidPayload(): never {
  throw new EdgeFunctionError("INVALID_STANDINGS_EXPORT_PAYLOAD");
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" &&
    value !== null &&
    !Array.isArray(value);
}

function hasExactKeys(
  record: Record<string, unknown>,
  expectedKeys: readonly string[],
): boolean {
  const actualKeys = Object.keys(record);

  return actualKeys.length === expectedKeys.length &&
    actualKeys.every((key) => expectedKeys.includes(key));
}

function readString(
  record: Record<string, unknown>,
  key: string,
): string {
  const value = record[key];

  if (typeof value !== "string") {
    invalidPayload();
  }

  return value;
}

function readInteger(
  record: Record<string, unknown>,
  key: string,
): number {
  const value = record[key];

  if (typeof value !== "number" || !Number.isInteger(value)) {
    invalidPayload();
  }

  return value;
}

function isValidUuid(value: string): boolean {
  return UUID_PATTERN.test(value);
}

function parseStandingsExportRow(value: unknown): StandingsExportRow {
  if (!isRecord(value) || !hasExactKeys(value, STANDINGS_EXPORT_COLUMNS)) {
    invalidPayload();
  }

  return {
    export_schema_version: readString(value, "export_schema_version"),
    export_type: readString(value, "export_type"),
    tournament_id: readString(value, "tournament_id"),
    tournament_name: readString(value, "tournament_name"),
    exported_match_count: readInteger(value, "exported_match_count"),
    standings_rank: readInteger(value, "standings_rank"),
    team_slot: readInteger(value, "team_slot"),
    team_name: readString(value, "team_name"),
    player_1_name: readString(value, "player_1_name"),
    player_2_name: readString(value, "player_2_name"),
    player_3_name: readString(value, "player_3_name"),
    player_4_name: readString(value, "player_4_name"),
    matches_played: readInteger(value, "matches_played"),
    total_position_points: readInteger(value, "total_position_points"),
    total_kills: readInteger(value, "total_kills"),
    total_kill_points: readInteger(value, "total_kill_points"),
    total_points: readInteger(value, "total_points"),
    best_placement: value.best_placement === null
      ? null
      : readInteger(value, "best_placement"),
    first_place_count: readInteger(value, "first_place_count"),
    tie_break_status: readString(value, "tie_break_status"),
  };
}

function validateStandingsExportRows(
  request: StandingsExportRequest,
): void {
  if (
    request.rows.length < 1 ||
    request.rows.length > MAX_STANDINGS_EXPORT_ROWS
  ) {
    invalidPayload();
  }

  const tournamentName = request.rows[0].tournament_name;
  const exportedMatchCount = request.rows[0].exported_match_count;
  const teamSlots = new Set<number>();

  request.rows.forEach((row, index) => {
    const expectedRank = index + 1;

    if (
      !APPROVED_EXPORT_SCHEMA_VERSIONS.has(row.export_schema_version) ||
      row.export_type !== "tournament_standings" ||
      row.tournament_id !== request.tournament_id ||
      row.tournament_name !== tournamentName ||
      row.exported_match_count !== exportedMatchCount ||
      row.exported_match_count < 1 ||
      row.exported_match_count > 10 ||
      row.standings_rank !== expectedRank ||
      row.team_slot < 1 ||
      row.team_slot > 12 ||
      teamSlots.has(row.team_slot) ||
      row.matches_played < 0 ||
      row.matches_played > row.exported_match_count ||
      row.total_position_points < 0 ||
      row.total_kills < 0 ||
      row.total_kill_points < 0 ||
      row.total_kill_points !== row.total_kills ||
      row.total_points < 0 ||
      row.total_points !==
        row.total_position_points + row.total_kill_points ||
      (row.best_placement !== null &&
        (row.best_placement < 1 ||
          row.best_placement > MAX_STANDINGS_EXPORT_ROWS)) ||
      row.first_place_count < 0 ||
      row.first_place_count > row.matches_played ||
      !APPROVED_TIE_BREAK_STATUSES.has(row.tie_break_status)
    ) {
      invalidPayload();
    }

    const exportedPlayers = [
      row.player_1_name,
      row.player_2_name,
      row.player_3_name,
      row.player_4_name,
    ].filter((name) => name.length > 0);

    if (new Set(exportedPlayers).size !== exportedPlayers.length) {
      invalidPayload();
    }

    teamSlots.add(row.team_slot);
  });
}

export function parseStandingsExportRequest(
  value: unknown,
): StandingsExportRequest {
  if (
    !isRecord(value) ||
    !hasExactKeys(value, STANDINGS_EXPORT_REQUEST_KEYS)
  ) {
    invalidPayload();
  }

  if (
    value.operation !== "export_standings" ||
    typeof value.tournament_id !== "string" ||
    !isValidUuid(value.tournament_id) ||
    !Array.isArray(value.rows) ||
    value.rows.length < 1 ||
    value.rows.length > MAX_STANDINGS_EXPORT_ROWS
  ) {
    invalidPayload();
  }

  const request: StandingsExportRequest = {
    operation: "export_standings",
    tournament_id: value.tournament_id,
    rows: value.rows.map(parseStandingsExportRow),
  };

  validateStandingsExportRows(request);

  return request;
}

export function toStandingsGoogleSheetValues(
  request: StandingsExportRequest,
): StandingsExportCell[][] {
  return request.rows.map((row) =>
    STANDINGS_EXPORT_COLUMNS.map((column) => row[column])
  );
}
