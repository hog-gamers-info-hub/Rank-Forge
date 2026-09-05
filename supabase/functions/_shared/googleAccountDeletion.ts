import { EdgeFunctionError } from "./errors.ts";
import {
  type FetchImplementation,
  fetchWithTimeout,
  UpstreamTimeoutError,
} from "./http.ts";
import {
  MATCH_RESULTS_HEADER_RANGE,
  MATCH_RESULTS_WORKSHEET,
} from "./googleMatchExport.ts";
import {
  TOURNAMENT_STANDINGS_HEADER_RANGE,
  TOURNAMENT_STANDINGS_WORKSHEET,
} from "./googleStandingsExport.ts";
import { MATCH_EXPORT_COLUMNS } from "./matchExport.ts";
import { STANDINGS_EXPORT_COLUMNS } from "./standingsExport.ts";

export interface GoogleAccountDeletionOptions {
  fetchImpl?: FetchImplementation;
  timeoutMs: number;
}

export interface GoogleAccountDeletionResult {
  deletedMatchResultRows: number;
  deletedTournamentStandingsRows: number;
}

interface WorksheetMetadata {
  title: string;
  sheetId: number;
}

interface DeleteDimensionRequest {
  deleteDimension: {
    range: {
      sheetId: number;
      dimension: "ROWS";
      startIndex: number;
      endIndex: number;
    };
  };
}

type HeaderSchemaErrorCode =
  | "GOOGLE_SHEET_SCHEMA_MISMATCH"
  | "GOOGLE_STANDINGS_SHEET_SCHEMA_MISMATCH";

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const MATCH_RESULTS_DATA_RANGE = `${MATCH_RESULTS_WORKSHEET}!C2:C`;
const TOURNAMENT_STANDINGS_DATA_RANGE =
  `${TOURNAMENT_STANDINGS_WORKSHEET}!C2:C`;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function mapGoogleStatus(status: number): never {
  if (status === 403) {
    throw new EdgeFunctionError("GOOGLE_SHEETS_ACCESS_DENIED");
  }

  if (status === 404) {
    throw new EdgeFunctionError("GOOGLE_SHEETS_NOT_FOUND");
  }

  if (status === 429) {
    throw new EdgeFunctionError("GOOGLE_API_RATE_LIMITED");
  }

  throw new EdgeFunctionError("GOOGLE_ACCOUNT_DELETION_FAILURE");
}

function invalidDeletionResponse(): never {
  throw new EdgeFunctionError("GOOGLE_ACCOUNT_DELETION_FAILURE");
}

function spreadsheetEndpoint(spreadsheetId: string): string {
  return `https://sheets.googleapis.com/v4/spreadsheets/${
    encodeURIComponent(spreadsheetId)
  }`;
}

function valuesEndpoint(spreadsheetId: string, range: string): string {
  return `${spreadsheetEndpoint(spreadsheetId)}/values/${
    encodeURIComponent(range)
  }`;
}

function batchUpdateEndpoint(spreadsheetId: string): string {
  return `${spreadsheetEndpoint(spreadsheetId)}:batchUpdate`;
}

async function fetchJson(
  accessToken: string,
  input: string,
  options: GoogleAccountDeletionOptions,
): Promise<unknown> {
  const fetchImpl = options.fetchImpl ?? fetch;
  let response: Response;

  try {
    response = await fetchWithTimeout(
      fetchImpl,
      input,
      {
        method: "GET",
        headers: {
          Accept: "application/json",
          Authorization: `Bearer ${accessToken}`,
        },
      },
      options.timeoutMs,
    );
  } catch (error) {
    if (error instanceof UpstreamTimeoutError) {
      throw new EdgeFunctionError("UPSTREAM_TIMEOUT");
    }

    throw new EdgeFunctionError("GOOGLE_ACCOUNT_DELETION_FAILURE");
  }

  if (!response.ok) {
    mapGoogleStatus(response.status);
  }

  try {
    return await response.json();
  } catch {
    invalidDeletionResponse();
  }
}

async function postBatchUpdate(
  accessToken: string,
  spreadsheetId: string,
  requests: readonly DeleteDimensionRequest[],
  options: GoogleAccountDeletionOptions,
): Promise<void> {
  const fetchImpl = options.fetchImpl ?? fetch;
  let response: Response;

  try {
    response = await fetchWithTimeout(
      fetchImpl,
      batchUpdateEndpoint(spreadsheetId),
      {
        method: "POST",
        headers: {
          Accept: "application/json",
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ requests }),
      },
      options.timeoutMs,
    );
  } catch (error) {
    if (error instanceof UpstreamTimeoutError) {
      throw new EdgeFunctionError("UPSTREAM_TIMEOUT");
    }

    throw new EdgeFunctionError("GOOGLE_ACCOUNT_DELETION_FAILURE");
  }

  if (!response.ok) {
    mapGoogleStatus(response.status);
  }

  try {
    const payload: unknown = await response.json();

    if (
      !isRecord(payload) ||
      !Array.isArray(payload.replies) ||
      payload.replies.length !== requests.length
    ) {
      invalidDeletionResponse();
    }
  } catch (error) {
    if (error instanceof EdgeFunctionError) {
      throw error;
    }

    invalidDeletionResponse();
  }
}

function parseWorksheetMetadata(payload: unknown): WorksheetMetadata[] {
  if (!isRecord(payload) || !Array.isArray(payload.sheets)) {
    invalidDeletionResponse();
  }

  return payload.sheets.map((sheet) => {
    if (!isRecord(sheet) || !isRecord(sheet.properties)) {
      invalidDeletionResponse();
    }

    const title = sheet.properties.title;
    const sheetId = sheet.properties.sheetId;

    if (
      typeof title !== "string" ||
      typeof sheetId !== "number" ||
      !Number.isInteger(sheetId) ||
      sheetId < 0
    ) {
      invalidDeletionResponse();
    }

    return { title, sheetId };
  });
}

function findWorksheet(
  worksheets: readonly WorksheetMetadata[],
  title: string,
): WorksheetMetadata {
  const matches = worksheets.filter((worksheet) => worksheet.title === title);

  if (matches.length !== 1) {
    invalidDeletionResponse();
  }

  return matches[0];
}

function parseExactHeader(
  payload: unknown,
  expectedColumns: readonly string[],
  schemaErrorCode: HeaderSchemaErrorCode,
): void {
  if (!isRecord(payload) || !Array.isArray(payload.values)) {
    throw new EdgeFunctionError(schemaErrorCode);
  }

  const values = payload.values;

  if (
    values.length !== 1 ||
    !Array.isArray(values[0]) ||
    values[0].length !== expectedColumns.length ||
    values[0].some((cell, index) => cell !== expectedColumns[index])
  ) {
    throw new EdgeFunctionError(schemaErrorCode);
  }
}

function parseTournamentIdColumn(payload: unknown): string[] {
  if (!isRecord(payload) || !Array.isArray(payload.values)) {
    invalidDeletionResponse();
  }

  return payload.values.map((row) => {
    if (!Array.isArray(row) || row.length > 1) {
      invalidDeletionResponse();
    }

    if (row.length === 0 || row[0] === null) {
      return "";
    }

    if (typeof row[0] !== "string") {
      invalidDeletionResponse();
    }

    return row[0];
  });
}

async function readTournamentIdColumn(
  accessToken: string,
  spreadsheetId: string,
  range: string,
  options: GoogleAccountDeletionOptions,
): Promise<string[]> {
  const payload = await fetchJson(
    accessToken,
    valuesEndpoint(spreadsheetId, range),
    options,
  );

  if (
    isRecord(payload) &&
    !Object.prototype.hasOwnProperty.call(payload, "values")
  ) {
    if (typeof payload.range !== "string") {
      invalidDeletionResponse();
    }

    return [];
  }

  return parseTournamentIdColumn(payload);
}

function matchingRowIndexes(
  values: readonly string[],
  targetIds: ReadonlySet<string>,
): number[] {
  return values.flatMap((value, index) =>
    targetIds.has(value.toLowerCase()) ? [index + 1] : []
  );
}

function deleteRequestsForRows(
  sheetId: number,
  rowIndexes: readonly number[],
): DeleteDimensionRequest[] {
  const sortedIndexes = [...new Set(rowIndexes)].sort((a, b) => a - b);
  const groups: Array<{ start: number; end: number }> = [];

  for (const rowIndex of sortedIndexes) {
    const previous = groups[groups.length - 1];

    if (previous && rowIndex === previous.end) {
      previous.end += 1;
    } else {
      groups.push({ start: rowIndex, end: rowIndex + 1 });
    }
  }

  return groups.reverse().map(({ start, end }) => ({
    deleteDimension: {
      range: {
        sheetId,
        dimension: "ROWS",
        startIndex: start,
        endIndex: end,
      },
    },
  }));
}

function validateTournamentIds(
  tournamentIds: readonly string[],
): ReadonlySet<string> {
  if (
    !Array.isArray(tournamentIds) ||
    tournamentIds.some((tournamentId) =>
      typeof tournamentId !== "string" || !UUID_PATTERN.test(tournamentId)
    )
  ) {
    throw new EdgeFunctionError("GOOGLE_ACCOUNT_DELETION_FAILURE");
  }

  return new Set(
    tournamentIds.map((tournamentId) => tournamentId.toLowerCase()),
  );
}

export async function deleteTournamentExportRows(
  accessToken: string,
  spreadsheetId: string,
  tournamentIds: readonly string[],
  options: GoogleAccountDeletionOptions,
): Promise<GoogleAccountDeletionResult> {
  const targetIds = validateTournamentIds(tournamentIds);

  if (targetIds.size === 0) {
    return {
      deletedMatchResultRows: 0,
      deletedTournamentStandingsRows: 0,
    };
  }

  const metadataPayload = await fetchJson(
    accessToken,
    `${
      spreadsheetEndpoint(spreadsheetId)
    }?fields=sheets(properties(sheetId,title))`,
    options,
  );
  const worksheets = parseWorksheetMetadata(metadataPayload);
  const matchResultsSheet = findWorksheet(worksheets, MATCH_RESULTS_WORKSHEET);
  const tournamentStandingsSheet = findWorksheet(
    worksheets,
    TOURNAMENT_STANDINGS_WORKSHEET,
  );

  const matchHeaderPayload = await fetchJson(
    accessToken,
    `${
      valuesEndpoint(spreadsheetId, MATCH_RESULTS_HEADER_RANGE)
    }?majorDimension=ROWS`,
    options,
  );
  parseExactHeader(
    matchHeaderPayload,
    MATCH_EXPORT_COLUMNS,
    "GOOGLE_SHEET_SCHEMA_MISMATCH",
  );

  const standingsHeaderPayload = await fetchJson(
    accessToken,
    `${
      valuesEndpoint(spreadsheetId, TOURNAMENT_STANDINGS_HEADER_RANGE)
    }?majorDimension=ROWS`,
    options,
  );
  parseExactHeader(
    standingsHeaderPayload,
    STANDINGS_EXPORT_COLUMNS,
    "GOOGLE_STANDINGS_SHEET_SCHEMA_MISMATCH",
  );

  const matchValues = await readTournamentIdColumn(
    accessToken,
    spreadsheetId,
    MATCH_RESULTS_DATA_RANGE,
    options,
  );
  const standingsValues = await readTournamentIdColumn(
    accessToken,
    spreadsheetId,
    TOURNAMENT_STANDINGS_DATA_RANGE,
    options,
  );

  const matchRowIndexes = matchingRowIndexes(matchValues, targetIds);
  const standingsRowIndexes = matchingRowIndexes(standingsValues, targetIds);
  const requests = [
    ...deleteRequestsForRows(matchResultsSheet.sheetId, matchRowIndexes),
    ...deleteRequestsForRows(
      tournamentStandingsSheet.sheetId,
      standingsRowIndexes,
    ),
  ];

  if (requests.length === 0) {
    return {
      deletedMatchResultRows: 0,
      deletedTournamentStandingsRows: 0,
    };
  }

  await postBatchUpdate(accessToken, spreadsheetId, requests, options);

  const remainingMatchValues = await readTournamentIdColumn(
    accessToken,
    spreadsheetId,
    MATCH_RESULTS_DATA_RANGE,
    options,
  );
  const remainingStandingsValues = await readTournamentIdColumn(
    accessToken,
    spreadsheetId,
    TOURNAMENT_STANDINGS_DATA_RANGE,
    options,
  );

  if (
    remainingMatchValues.some((value) => targetIds.has(value.toLowerCase())) ||
    remainingStandingsValues.some((value) => targetIds.has(value.toLowerCase()))
  ) {
    throw new EdgeFunctionError("GOOGLE_ACCOUNT_DELETION_FAILURE");
  }

  return {
    deletedMatchResultRows: matchRowIndexes.length,
    deletedTournamentStandingsRows: standingsRowIndexes.length,
  };
}
