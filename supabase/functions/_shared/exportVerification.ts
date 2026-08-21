import { EdgeFunctionError } from "./errors.ts";
import {
  type FetchImplementation,
  fetchWithTimeout,
  UpstreamTimeoutError,
} from "./http.ts";

export const GOOGLE_EXPORT_VERIFICATION_TIMEOUT_MS = 10_000;
export const MAX_VERIFICATION_DATA_ROWS = 50_000;
export const EXPORT_VERIFICATION_COLUMN_COUNT = 20;
const MAX_EXPORT_VERIFICATION_COLUMN_COUNT = 21;
export const EXPORT_VERIFICATION_ROW_COUNT = 12;

export type ExportVerificationCell = string | number | null;
export type ExportVerificationRows =
  readonly (readonly ExportVerificationCell[])[];

export interface ExportVerificationOptions {
  fetchImpl?: FetchImplementation;
  timeoutMs: number;
}

export interface UncertainVerificationTarget {
  worksheetName: string;
  candidateIndexes: readonly [0, 1, 2, 4];
}

export type UncertainVerificationResult =
  | "verified_success"
  | "no_candidates";

function valuesEndpoint(spreadsheetId: string, range: string): string {
  return `https://sheets.googleapis.com/v4/spreadsheets/${
    encodeURIComponent(spreadsheetId)
  }/values/${encodeURIComponent(range)}`;
}

function spreadsheetEndpoint(spreadsheetId: string): string {
  return `https://sheets.googleapis.com/v4/spreadsheets/${
    encodeURIComponent(spreadsheetId)
  }`;
}

function quoteSheetName(worksheetName: string): string {
  return `'${worksheetName.replaceAll("'", "''")}'`;
}

function isSupportedCell(value: unknown): value is ExportVerificationCell {
  return value === null ||
    typeof value === "string" ||
    (typeof value === "number" && Number.isInteger(value));
}

function assertExpectedDimensions(
  expectedRows: ExportVerificationRows,
): void {
  const expectedColumnCount = expectedRows[0]?.length ?? 0;
  if (
    expectedRows.length < 1 ||
    expectedRows.length > EXPORT_VERIFICATION_ROW_COUNT ||
    !expectedRows.every((row) =>
      row.length === expectedColumnCount &&
      expectedColumnCount >= EXPORT_VERIFICATION_COLUMN_COUNT &&
      expectedColumnCount <= MAX_EXPORT_VERIFICATION_COLUMN_COUNT &&
      row.every(isSupportedCell)
    )
  ) {
    throw new EdgeFunctionError("EXPORT_VERIFICATION_FAILURE");
  }
}

function parseRows(value: unknown): ExportVerificationCell[][] {
  if (!Array.isArray(value)) {
    throw new EdgeFunctionError("EXPORT_VERIFICATION_FAILURE");
  }

  return value.map((row) => {
    if (
      !Array.isArray(row) || row.length > MAX_EXPORT_VERIFICATION_COLUMN_COUNT
    ) {
      throw new EdgeFunctionError("EXPORT_VERIFICATION_FAILURE");
    }

    return row.map((cell) => {
      if (!isSupportedCell(cell)) {
        throw new EdgeFunctionError("EXPORT_VERIFICATION_FAILURE");
      }

      return cell;
    });
  });
}

function exactCellEquals(
  actual: ExportVerificationCell | undefined,
  expected: ExportVerificationCell,
): boolean {
  return typeof actual === typeof expected && actual === expected;
}

function exactRowEquals(
  actual: readonly ExportVerificationCell[],
  expected: readonly ExportVerificationCell[],
): boolean {
  return actual.length === expected.length &&
    expected.every((cell, index) => exactCellEquals(actual[index], cell));
}

function exactBlockEquals(
  actualRows: readonly (readonly ExportVerificationCell[])[],
  expectedRows: ExportVerificationRows,
): boolean {
  return actualRows.length === expectedRows.length &&
    expectedRows.every((row, index) => exactRowEquals(actualRows[index], row));
}

function isCandidateRow(
  row: readonly ExportVerificationCell[],
  expectedFirstRow: readonly ExportVerificationCell[],
  candidateIndexes: readonly [0, 1, 2, 4],
): boolean {
  return candidateIndexes.every((index) =>
    exactCellEquals(row[index], expectedFirstRow[index])
  );
}

async function fetchJson(
  accessToken: string,
  url: URL,
  options: ExportVerificationOptions,
): Promise<unknown> {
  const fetchImpl = options.fetchImpl ?? fetch;
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
      throw new EdgeFunctionError("EXPORT_VERIFICATION_FAILURE");
    }

    throw new EdgeFunctionError("EXPORT_VERIFICATION_FAILURE");
  }

  if (!response.ok) {
    throw new EdgeFunctionError("EXPORT_VERIFICATION_FAILURE");
  }

  try {
    return await response.json();
  } catch {
    throw new EdgeFunctionError("EXPORT_VERIFICATION_FAILURE");
  }
}

async function readValues(
  accessToken: string,
  spreadsheetId: string,
  range: string,
  options: ExportVerificationOptions,
): Promise<ExportVerificationCell[][]> {
  const url = new URL(valuesEndpoint(spreadsheetId, range));
  url.searchParams.set("majorDimension", "ROWS");
  url.searchParams.set("valueRenderOption", "UNFORMATTED_VALUE");

  const payload = await fetchJson(accessToken, url, options);

  if (
    typeof payload !== "object" ||
    payload === null ||
    Array.isArray(payload)
  ) {
    throw new EdgeFunctionError("EXPORT_VERIFICATION_FAILURE");
  }

  if (!("values" in payload)) {
    return [];
  }

  return parseRows((payload as { values?: unknown }).values);
}

async function readWorksheetGridRowCount(
  accessToken: string,
  spreadsheetId: string,
  worksheetName: string,
  options: ExportVerificationOptions,
): Promise<number> {
  const url = new URL(spreadsheetEndpoint(spreadsheetId));
  url.searchParams.set(
    "fields",
    "sheets(properties(title,gridProperties(rowCount)))",
  );

  const payload = await fetchJson(accessToken, url, options);

  if (
    typeof payload !== "object" ||
    payload === null ||
    !Array.isArray((payload as { sheets?: unknown }).sheets)
  ) {
    throw new EdgeFunctionError("EXPORT_VERIFICATION_FAILURE");
  }

  for (const sheet of (payload as { sheets: unknown[] }).sheets) {
    if (typeof sheet !== "object" || sheet === null) {
      throw new EdgeFunctionError("EXPORT_VERIFICATION_FAILURE");
    }

    const properties = (sheet as { properties?: unknown }).properties;

    if (typeof properties !== "object" || properties === null) {
      throw new EdgeFunctionError("EXPORT_VERIFICATION_FAILURE");
    }

    const title = (properties as { title?: unknown }).title;
    const gridProperties =
      (properties as { gridProperties?: unknown }).gridProperties;

    if (title !== worksheetName) {
      continue;
    }

    if (
      typeof gridProperties !== "object" ||
      gridProperties === null ||
      !Number.isInteger(
        (gridProperties as { rowCount?: unknown }).rowCount,
      )
    ) {
      throw new EdgeFunctionError("EXPORT_VERIFICATION_FAILURE");
    }

    return (gridProperties as { rowCount: number }).rowCount;
  }

  throw new EdgeFunctionError("EXPORT_VERIFICATION_FAILURE");
}

export async function verifyAppendedExportRows(
  accessToken: string,
  spreadsheetId: string,
  updatedRange: string,
  expectedRows: ExportVerificationRows,
  options: ExportVerificationOptions,
): Promise<void> {
  assertExpectedDimensions(expectedRows);

  const observedRows = await readValues(
    accessToken,
    spreadsheetId,
    updatedRange,
    options,
  );

  if (!exactBlockEquals(observedRows, expectedRows)) {
    throw new EdgeFunctionError("EXPORT_VERIFICATION_CONFLICT");
  }
}

export async function reconcileUncertainExport(
  accessToken: string,
  spreadsheetId: string,
  target: UncertainVerificationTarget,
  expectedRows: ExportVerificationRows,
  options: ExportVerificationOptions,
): Promise<UncertainVerificationResult> {
  assertExpectedDimensions(expectedRows);

  const gridRowCount = await readWorksheetGridRowCount(
    accessToken,
    spreadsheetId,
    target.worksheetName,
    options,
  );

  const dataRows = Math.max(0, gridRowCount - 1);

  if (dataRows > MAX_VERIFICATION_DATA_ROWS) {
    throw new EdgeFunctionError("EXPORT_VERIFICATION_RANGE_EXCEEDED");
  }

  if (dataRows === 0) {
    return "no_candidates";
  }

  const observedRows = await readValues(
    accessToken,
    spreadsheetId,
    `${quoteSheetName(target.worksheetName)}!A2:${
      expectedRows[0].length === 21 ? "U" : "T"
    }${gridRowCount}`,
    options,
  );

  const expectedFirstRow = expectedRows[0];
  const candidateIndexes = observedRows
    .map((row, index) =>
      isCandidateRow(row, expectedFirstRow, target.candidateIndexes)
        ? index
        : -1
    )
    .filter((index) => index >= 0);

  if (candidateIndexes.length === 0) {
    return "no_candidates";
  }

  const exactBlockStarts: number[] = [];

  for (
    let startIndex = 0;
    startIndex <= observedRows.length - expectedRows.length;
    startIndex += 1
  ) {
    if (
      exactBlockEquals(
        observedRows.slice(
          startIndex,
          startIndex + expectedRows.length,
        ),
        expectedRows,
      )
    ) {
      exactBlockStarts.push(startIndex);
    }
  }

  if (exactBlockStarts.length !== 1) {
    throw new EdgeFunctionError("EXPORT_VERIFICATION_CONFLICT");
  }

  const exactStart = exactBlockStarts[0];
  const exactCandidateIndexes = Array.from(
    { length: expectedRows.length },
    (_, index) => exactStart + index,
  );

  if (
    candidateIndexes.length !== expectedRows.length ||
    !candidateIndexes.every((index, offset) =>
      index === exactCandidateIndexes[offset]
    )
  ) {
    throw new EdgeFunctionError("EXPORT_VERIFICATION_CONFLICT");
  }

  return "verified_success";
}
