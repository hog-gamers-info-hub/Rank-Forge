import { EdgeFunctionError } from "./errors.ts";
import {
  type FetchImplementation,
  fetchWithTimeout,
  UpstreamTimeoutError,
} from "./http.ts";
import { MATCH_EXPORT_COLUMNS, type MatchExportCell } from "./matchExport.ts";

export const MATCH_RESULTS_WORKSHEET = "Match Results";
export const MATCH_RESULTS_HEADER_RANGE = "Match Results!A1:U1";
export const MATCH_RESULTS_APPEND_RANGE = "Match Results!A:U";
export const GOOGLE_HEADER_TIMEOUT_MS = 10_000;
export const GOOGLE_APPEND_TIMEOUT_MS = 10_000;

export interface GoogleMatchExportOptions {
  fetchImpl?: FetchImplementation;
  timeoutMs: number;
}

export interface GoogleMatchAppendResult {
  rowsWritten: number;
  updatedRange: string;
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

  throw new EdgeFunctionError("GOOGLE_MATCH_EXPORT_FAILURE");
}

function valuesEndpoint(
  spreadsheetId: string,
  range: string,
): string {
  return `https://sheets.googleapis.com/v4/spreadsheets/${
    encodeURIComponent(spreadsheetId)
  }/values/${encodeURIComponent(range)}`;
}

function isExactHeader(value: unknown): boolean {
  if (!Array.isArray(value) || value.length !== 1) {
    return false;
  }

  const header = value[0];

  return Array.isArray(header) &&
    header.length === MATCH_EXPORT_COLUMNS.length &&
    header.every((cell, index) => cell === MATCH_EXPORT_COLUMNS[index]);
}

export async function verifyMatchResultsHeader(
  accessToken: string,
  spreadsheetId: string,
  options: GoogleMatchExportOptions,
): Promise<void> {
  const fetchImpl = options.fetchImpl ?? fetch;
  const url = new URL(
    valuesEndpoint(spreadsheetId, MATCH_RESULTS_HEADER_RANGE),
  );
  url.searchParams.set("majorDimension", "ROWS");

  let response: Response;

  try {
    response = await fetchWithTimeout(
      fetchImpl,
      url,
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

    throw new EdgeFunctionError("GOOGLE_MATCH_EXPORT_FAILURE");
  }

  if (!response.ok) {
    mapGoogleStatus(response.status);
  }

  try {
    const payload: unknown = await response.json();

    if (
      typeof payload !== "object" ||
      payload === null ||
      !("values" in payload) ||
      !isExactHeader((payload as { values?: unknown }).values)
    ) {
      throw new EdgeFunctionError("GOOGLE_SHEET_SCHEMA_MISMATCH");
    }
  } catch (error) {
    if (error instanceof EdgeFunctionError) {
      throw error;
    }

    throw new EdgeFunctionError("GOOGLE_SHEET_SCHEMA_MISMATCH");
  }
}

function hasValidDimensions(
  values: readonly (readonly MatchExportCell[])[],
): boolean {
  return values.length >= 1 && values.length <= 12 &&
    values.every((row) =>
      row.length === MATCH_EXPORT_COLUMNS.length &&
      row.every((cell) =>
        cell === null ||
        typeof cell === "string" ||
        (typeof cell === "number" && Number.isInteger(cell))
      )
    );
}

function isValidUpdatedRange(
  updatedRange: unknown,
  expectedRows: number,
): updatedRange is string {
  if (typeof updatedRange !== "string") {
    return false;
  }

  const match = updatedRange.match(
    /^'Match Results'!A([1-9][0-9]*):U([1-9][0-9]*)$/,
  );

  if (!match) {
    return false;
  }

  const startRow = Number(match[1]);
  const endRow = Number(match[2]);

  return startRow >= 2 && endRow === startRow + expectedRows - 1;
}

export async function appendMatchResults(
  accessToken: string,
  spreadsheetId: string,
  values: readonly (readonly MatchExportCell[])[],
  options: GoogleMatchExportOptions,
): Promise<GoogleMatchAppendResult> {
  if (!hasValidDimensions(values)) {
    throw new EdgeFunctionError("GOOGLE_MATCH_EXPORT_FAILURE");
  }

  const fetchImpl = options.fetchImpl ?? fetch;
  const url = new URL(
    `${valuesEndpoint(spreadsheetId, MATCH_RESULTS_APPEND_RANGE)}:append`,
  );
  url.searchParams.set("valueInputOption", "RAW");
  url.searchParams.set("insertDataOption", "INSERT_ROWS");

  let response: Response;

  try {
    response = await fetchWithTimeout(
      fetchImpl,
      url,
      {
        method: "POST",
        headers: {
          Accept: "application/json",
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          majorDimension: "ROWS",
          values,
        }),
      },
      options.timeoutMs,
    );
  } catch (error) {
    if (error instanceof UpstreamTimeoutError) {
      throw new EdgeFunctionError("UPSTREAM_TIMEOUT");
    }

    throw new EdgeFunctionError("GOOGLE_MATCH_EXPORT_FAILURE");
  }

  if (!response.ok) {
    mapGoogleStatus(response.status);
  }

  try {
    const payload: unknown = await response.json();

    if (
      typeof payload !== "object" ||
      payload === null ||
      !("updates" in payload) ||
      typeof (payload as { updates?: unknown }).updates !== "object" ||
      (payload as { updates?: unknown }).updates === null ||
      !("updatedRows" in (
        payload as { updates: Record<string, unknown> }
      ).updates) ||
      (payload as { updates: { updatedRows?: unknown } }).updates
          .updatedRows !== values.length ||
      !isValidUpdatedRange(
        (payload as { updates: { updatedRange?: unknown } }).updates
          .updatedRange,
        values.length,
      )
    ) {
      throw new EdgeFunctionError(
        "GOOGLE_MATCH_EXPORT_RESPONSE_INVALID",
      );
    }

    return {
      rowsWritten: values.length,
      updatedRange: (payload as { updates: { updatedRange: string } }).updates
        .updatedRange,
    };
  } catch (error) {
    if (error instanceof EdgeFunctionError) {
      throw error;
    }

    throw new EdgeFunctionError(
      "GOOGLE_MATCH_EXPORT_RESPONSE_INVALID",
    );
  }
}
