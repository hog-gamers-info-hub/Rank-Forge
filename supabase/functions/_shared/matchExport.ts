import { EdgeFunctionError } from "./errors.ts";

export const MATCH_EXPORT_COLUMNS = [
  "export_schema_version",
  "export_type",
  "tournament_id",
  "tournament_name",
  "match_id",
  "match_label",
  "match_finalized_at",
  "row_number",
  "placement",
  "team_slot",
  "team_name",
  "player_1_name",
  "player_2_name",
  "player_3_name",
  "player_4_name",
  "placement_points",
  "kills",
  "kill_points",
  "total_points",
  "correction_status",
] as const;

const MATCH_EXPORT_REQUEST_KEYS = [
  "operation",
  "tournament_id",
  "match_id",
  "rows",
] as const;

const PLACEMENT_POINTS = new Map<number, number>([
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

const APPROVED_CORRECTION_STATUSES = new Set([
  "original_finalized",
  "corrected_finalized",
]);

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export interface MatchExportRow {
  export_schema_version: string;
  export_type: string;
  tournament_id: string;
  tournament_name: string;
  match_id: string;
  match_label: string;
  match_finalized_at: string;
  row_number: number;
  placement: number;
  team_slot: number;
  team_name: string;
  player_1_name: string;
  player_2_name: string;
  player_3_name: string;
  player_4_name: string;
  placement_points: number;
  kills: number;
  kill_points: number;
  total_points: number;
  correction_status: string;
}

export interface MatchExportRequest {
  operation: "export_match";
  tournament_id: string;
  match_id: string;
  rows: MatchExportRow[];
}

export type MatchExportCell = string | number;

function invalidPayload(): never {
  throw new EdgeFunctionError("INVALID_MATCH_EXPORT_PAYLOAD");
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

export function isValidMatchFinalizedAt(value: string): boolean {
  if (value.length === 0) {
    return true;
  }

  const match = value.match(
    /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d{1,9})?(?:Z|([+-])(\d{2}):(\d{2}))$/,
  );

  if (!match) {
    return false;
  }

  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const hour = Number(match[4]);
  const minute = Number(match[5]);
  const second = Number(match[6]);
  const offsetHour = match[8] === undefined ? 0 : Number(match[8]);
  const offsetMinute = match[9] === undefined ? 0 : Number(match[9]);

  if (
    month < 1 ||
    month > 12 ||
    day < 1 ||
    hour > 23 ||
    minute > 59 ||
    second > 59 ||
    offsetHour > 23 ||
    offsetMinute > 59
  ) {
    return false;
  }

  const daysInMonth = new Date(Date.UTC(year, month, 0)).getUTCDate();

  return day <= daysInMonth && !Number.isNaN(Date.parse(value));
}

function parseMatchExportRow(value: unknown): MatchExportRow {
  if (!isRecord(value) || !hasExactKeys(value, MATCH_EXPORT_COLUMNS)) {
    invalidPayload();
  }

  return {
    export_schema_version: readString(value, "export_schema_version"),
    export_type: readString(value, "export_type"),
    tournament_id: readString(value, "tournament_id"),
    tournament_name: readString(value, "tournament_name"),
    match_id: readString(value, "match_id"),
    match_label: readString(value, "match_label"),
    match_finalized_at: readString(value, "match_finalized_at"),
    row_number: readInteger(value, "row_number"),
    placement: readInteger(value, "placement"),
    team_slot: readInteger(value, "team_slot"),
    team_name: readString(value, "team_name"),
    player_1_name: readString(value, "player_1_name"),
    player_2_name: readString(value, "player_2_name"),
    player_3_name: readString(value, "player_3_name"),
    player_4_name: readString(value, "player_4_name"),
    placement_points: readInteger(value, "placement_points"),
    kills: readInteger(value, "kills"),
    kill_points: readInteger(value, "kill_points"),
    total_points: readInteger(value, "total_points"),
    correction_status: readString(value, "correction_status"),
  };
}

function validateMatchExportRows(
  request: MatchExportRequest,
): void {
  if (request.rows.length !== 12) {
    invalidPayload();
  }

  const firstRow = request.rows[0];
  const tournamentName = firstRow.tournament_name;
  const matchLabel = firstRow.match_label;
  const teamSlots = new Set<number>();

  request.rows.forEach((row, index) => {
    const expectedPosition = index + 1;
    const expectedPlacementPoints = PLACEMENT_POINTS.get(row.placement);

    if (
      row.export_schema_version !== "phase_10_v1" ||
      row.export_type !== "match_result" ||
      row.tournament_id !== request.tournament_id ||
      row.match_id !== request.match_id ||
      row.tournament_name !== tournamentName ||
      row.match_label !== matchLabel ||
      row.row_number !== expectedPosition ||
      row.placement !== expectedPosition ||
      row.team_slot < 1 ||
      row.team_slot > 12 ||
      teamSlots.has(row.team_slot) ||
      expectedPlacementPoints === undefined ||
      row.placement_points !== expectedPlacementPoints ||
      row.kills < 0 ||
      row.kill_points !== row.kills ||
      row.total_points !== row.placement_points + row.kill_points ||
      !APPROVED_CORRECTION_STATUSES.has(row.correction_status) ||
      !isValidMatchFinalizedAt(row.match_finalized_at)
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

export function parseMatchExportRequest(
  value: unknown,
): MatchExportRequest {
  if (!isRecord(value) || !hasExactKeys(value, MATCH_EXPORT_REQUEST_KEYS)) {
    invalidPayload();
  }

  if (
    value.operation !== "export_match" ||
    typeof value.tournament_id !== "string" ||
    typeof value.match_id !== "string" ||
    !isValidUuid(value.tournament_id) ||
    !isValidUuid(value.match_id) ||
    !Array.isArray(value.rows) ||
    value.rows.length !== 12
  ) {
    invalidPayload();
  }

  const request: MatchExportRequest = {
    operation: "export_match",
    tournament_id: value.tournament_id,
    match_id: value.match_id,
    rows: value.rows.map(parseMatchExportRow),
  };

  validateMatchExportRows(request);

  return request;
}

export function toGoogleSheetValues(
  request: MatchExportRequest,
): MatchExportCell[][] {
  return request.rows.map((row) =>
    MATCH_EXPORT_COLUMNS.map((column) => row[column])
  );
}
