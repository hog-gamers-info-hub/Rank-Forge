import { EdgeFunctionError } from "../_shared/errors.ts";
import {
  type ExportVerificationCell,
  MAX_VERIFICATION_DATA_ROWS,
  reconcileUncertainExport,
  verifyAppendedExportRows,
} from "../_shared/exportVerification.ts";
import type { FetchImplementation } from "../_shared/http.ts";

interface Call {
  url: URL;
  method: string;
  headers: Headers;
  body: string | null;
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

async function assertRejects(
  operation: () => Promise<unknown>,
  code: string,
  status: number,
): Promise<void> {
  try {
    await operation();
  } catch (error) {
    assert(error instanceof EdgeFunctionError);
    assertEquals(error.code, code);
    assertEquals(error.status, status);
    return;
  }

  throw new Error(`Expected ${code}`);
}

function responseJson(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function makeFetch(
  responder: (call: Call, index: number) => Response | Promise<Response>,
): { fetchImpl: FetchImplementation; calls: Call[] } {
  const calls: Call[] = [];

  const fetchImpl: FetchImplementation = (input, init) => {
    const call: Call = {
      url: new URL(String(input)),
      method: init?.method ?? "GET",
      headers: new Headers(init?.headers),
      body: typeof init?.body === "string" ? init.body : null,
    };
    calls.push(call);
    return Promise.resolve(responder(call, calls.length - 1));
  };

  return { fetchImpl, calls };
}

function expectedRows(): ExportVerificationCell[][] {
  return Array.from({ length: 12 }, (_, index) => {
    const row = Array.from(
      { length: 20 },
      (_unused, columnIndex): ExportVerificationCell =>
        columnIndex < 7
          ? `value-${index + 1}-${columnIndex + 1}`
          : index + columnIndex,
    );

    row[0] = "phase_10_v1";
    row[1] = "match_result";
    row[2] = "11111111-1111-4111-8111-111111111111";
    row[3] = "Championship Finals";
    row[4] = "22222222-2222-4222-8222-222222222222";
    row[10] = index === 0 ? "Team São Paulo, Elite" : `Team ${index + 1}`;
    return row;
  });
}

function cloneRows(
  rows: readonly (readonly ExportVerificationCell[])[],
): ExportVerificationCell[][] {
  return rows.map((row) => [...row]);
}

Deno.test("post-append verification reads the exact returned range with exact values", async () => {
  const rows = expectedRows();
  const { fetchImpl, calls } = makeFetch(() => responseJson({ values: rows }));

  await verifyAppendedExportRows(
    "google-token",
    "spreadsheet-id",
    "'Match Results'!A2:T13",
    rows,
    { fetchImpl, timeoutMs: 100 },
  );

  assertEquals(calls.length, 1);
  assertEquals(calls[0].method, "GET");
  assertEquals(
    decodeURIComponent(calls[0].url.pathname),
    "/v4/spreadsheets/spreadsheet-id/values/'Match Results'!A2:T13",
  );
  assertEquals(calls[0].url.searchParams.get("majorDimension"), "ROWS");
  assertEquals(
    calls[0].url.searchParams.get("valueRenderOption"),
    "UNFORMATTED_VALUE",
  );
  assertEquals(calls[0].headers.get("authorization"), "Bearer google-token");
  assertEquals(calls[0].body, null);
});

Deno.test("post-append verification rejects wrong values, order, row count, and numeric strings", async () => {
  const rows = expectedRows();
  const wrongPlacement = cloneRows(rows);
  wrongPlacement[0][8] = 99;
  const wrongOrder = cloneRows(rows).reverse();
  const wrongRowCount = cloneRows(rows).slice(0, 11);
  const numericString = cloneRows(rows);
  numericString[0][15] = String(numericString[0][15]);

  for (
    const observedRows of [
      wrongPlacement,
      wrongOrder,
      wrongRowCount,
      numericString,
    ]
  ) {
    const { fetchImpl } = makeFetch(() =>
      responseJson({ values: observedRows })
    );

    await assertRejects(
      () =>
        verifyAppendedExportRows(
          "google-token",
          "spreadsheet-id",
          "'Match Results'!A2:T13",
          rows,
          { fetchImpl, timeoutMs: 100 },
        ),
      "EXPORT_VERIFICATION_CONFLICT",
      409,
    );
  }
});

Deno.test("post-append verification failures are safe and do not expose raw data", async () => {
  const rows = expectedRows();
  const { fetchImpl } = makeFetch(() =>
    new Response("raw worksheet secret", { status: 500 })
  );

  await assertRejects(
    () =>
      verifyAppendedExportRows(
        "google-token",
        "spreadsheet-id",
        "'Match Results'!A2:T13",
        rows,
        { fetchImpl, timeoutMs: 100 },
      ),
    "EXPORT_VERIFICATION_FAILURE",
    502,
  );
});

Deno.test("post-append verification rejects an empty ValueRange without values", async () => {
  const rows = expectedRows();
  const { fetchImpl, calls } = makeFetch(() =>
    responseJson({
      range: "'Match Results'!A2:T13",
      majorDimension: "ROWS",
    })
  );

  await assertRejects(
    () =>
      verifyAppendedExportRows(
        "google-token",
        "spreadsheet-id",
        "'Match Results'!A2:T13",
        rows,
        { fetchImpl, timeoutMs: 100 },
      ),
    "EXPORT_VERIFICATION_CONFLICT",
    409,
  );

  assertEquals(calls.length, 1);
  assertEquals(calls[0].method, "GET");
});

Deno.test("uncertain reconciliation treats an empty ValueRange as no candidates", async () => {
  const rows = expectedRows();

  const { fetchImpl, calls } = makeFetch((call) => {
    const path = decodeURIComponent(call.url.pathname);

    if (path === "/v4/spreadsheets/spreadsheet-id") {
      return responseJson({
        sheets: [{
          properties: {
            title: "Match Results",
            gridProperties: { rowCount: 1000 },
          },
        }],
      });
    }

    return responseJson({
      range: "'Match Results'!A2:T1000",
      majorDimension: "ROWS",
    });
  });

  const result = await reconcileUncertainExport(
    "google-token",
    "spreadsheet-id",
    { worksheetName: "Match Results", candidateIndexes: [0, 1, 2, 4] },
    rows,
    { fetchImpl, timeoutMs: 100 },
  );

  assertEquals(result, "no_candidates");
  assertEquals(calls.length, 2);
  assertEquals(calls.map((call) => call.method), ["GET", "GET"]);
  assert(
    calls.every((call) => call.url.hostname === "sheets.googleapis.com"),
  );
  assert(!calls.some((call) => call.url.hostname.includes("drive")));
  assert(!calls.some((call) => call.method !== "GET"));
});
Deno.test("uncertain reconciliation resolves one exact contiguous block without append or Drive API", async () => {
  const rows = expectedRows();
  const { fetchImpl, calls } = makeFetch((call) => {
    const path = decodeURIComponent(call.url.pathname);

    if (path === "/v4/spreadsheets/spreadsheet-id") {
      return responseJson({
        sheets: [{
          properties: {
            title: "Match Results",
            gridProperties: { rowCount: 13 },
          },
        }],
      });
    }

    return responseJson({ values: rows });
  });

  const result = await reconcileUncertainExport(
    "google-token",
    "spreadsheet-id",
    { worksheetName: "Match Results", candidateIndexes: [0, 1, 2, 4] },
    rows,
    { fetchImpl, timeoutMs: 100 },
  );

  assertEquals(result, "verified_success");
  assertEquals(calls.map((call) => call.method), ["GET", "GET"]);
  assert(
    calls.every((call) => call.url.hostname === "sheets.googleapis.com"),
  );
  assert(!calls.some((call) => call.url.hostname.includes("drive")));
  assert(!calls.some((call) => call.method !== "GET"));
  assertEquals(
    decodeURIComponent(calls[1].url.pathname),
    "/v4/spreadsheets/spreadsheet-id/values/'Match Results'!A2:T13",
  );
});

Deno.test("uncertain reconciliation reports zero candidates without resolving absence", async () => {
  const rows = expectedRows();
  const unrelated = cloneRows(rows).map((row) => {
    row[2] = "99999999-9999-4999-8999-999999999999";
    return row;
  });
  const { fetchImpl } = makeFetch((call) =>
    decodeURIComponent(call.url.pathname) === "/v4/spreadsheets/spreadsheet-id"
      ? responseJson({
        sheets: [{
          properties: {
            title: "Match Results",
            gridProperties: { rowCount: 13 },
          },
        }],
      })
      : responseJson({ values: unrelated })
  );

  assertEquals(
    await reconcileUncertainExport(
      "google-token",
      "spreadsheet-id",
      { worksheetName: "Match Results", candidateIndexes: [0, 1, 2, 4] },
      rows,
      { fetchImpl, timeoutMs: 100 },
    ),
    "no_candidates",
  );
});

Deno.test("partial, mismatched, and duplicate candidates remain conflicting", async () => {
  const rows = expectedRows();
  const partial = cloneRows(rows).slice(0, 6);
  const mismatched = cloneRows(rows);
  mismatched[2][16] = 999;
  const duplicate = [...cloneRows(rows), ...cloneRows(rows)];

  for (const observedRows of [partial, mismatched, duplicate]) {
    const { fetchImpl } = makeFetch((call) =>
      decodeURIComponent(call.url.pathname) ===
          "/v4/spreadsheets/spreadsheet-id"
        ? responseJson({
          sheets: [{
            properties: {
              title: "Match Results",
              gridProperties: { rowCount: observedRows.length + 1 },
            },
          }],
        })
        : responseJson({ values: observedRows })
    );

    await assertRejects(
      () =>
        reconcileUncertainExport(
          "google-token",
          "spreadsheet-id",
          { worksheetName: "Match Results", candidateIndexes: [0, 1, 2, 4] },
          rows,
          { fetchImpl, timeoutMs: 100 },
        ),
      "EXPORT_VERIFICATION_CONFLICT",
      409,
    );
  }
});

Deno.test("uncertain reconciliation enforces the 50000 row boundary before data scan", async () => {
  const rows = expectedRows();
  const { fetchImpl, calls } = makeFetch(() =>
    responseJson({
      sheets: [{
        properties: {
          title: "Match Results",
          gridProperties: { rowCount: MAX_VERIFICATION_DATA_ROWS + 2 },
        },
      }],
    })
  );

  await assertRejects(
    () =>
      reconcileUncertainExport(
        "google-token",
        "spreadsheet-id",
        { worksheetName: "Match Results", candidateIndexes: [0, 1, 2, 4] },
        rows,
        { fetchImpl, timeoutMs: 100 },
      ),
    "EXPORT_VERIFICATION_RANGE_EXCEEDED",
    409,
  );

  assertEquals(calls.length, 1);
});
