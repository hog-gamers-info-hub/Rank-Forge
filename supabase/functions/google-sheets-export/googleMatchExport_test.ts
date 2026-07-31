import { EdgeFunctionError } from "../_shared/errors.ts";
import {
  appendMatchResults,
  MATCH_RESULTS_APPEND_RANGE,
  MATCH_RESULTS_HEADER_RANGE,
  verifyMatchResultsHeader,
} from "../_shared/googleMatchExport.ts";
import type { FetchImplementation } from "../_shared/http.ts";
import {
  MATCH_EXPORT_COLUMNS,
  type MatchExportCell,
} from "../_shared/matchExport.ts";

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

function values(): MatchExportCell[][] {
  return Array.from(
    { length: 12 },
    (_, rowIndex) =>
      MATCH_EXPORT_COLUMNS.map((column, columnIndex) =>
        columnIndex < 7 ? `${column}-${rowIndex + 1}` : rowIndex + columnIndex
      ),
  );
}

Deno.test("exact Match Results header is accepted with a read-only request", async () => {
  const { fetchImpl, calls } = makeFetch(() =>
    responseJson({
      range: MATCH_RESULTS_HEADER_RANGE,
      majorDimension: "ROWS",
      values: [[...MATCH_EXPORT_COLUMNS]],
    })
  );

  await verifyMatchResultsHeader(
    "google-token",
    "spreadsheet-id",
    { fetchImpl, timeoutMs: 100 },
  );

  assertEquals(calls.length, 1);
  assertEquals(calls[0].method, "GET");
  assertEquals(
    decodeURIComponent(calls[0].url.pathname),
    `/v4/spreadsheets/spreadsheet-id/values/${MATCH_RESULTS_HEADER_RANGE}`,
  );
  assertEquals(
    calls[0].url.searchParams.get("majorDimension"),
    "ROWS",
  );
  assertEquals(
    calls[0].headers.get("authorization"),
    "Bearer google-token",
  );
  assertEquals(calls[0].headers.get("accept"), "application/json");
  assertEquals(calls[0].body, null);
});

Deno.test("missing, reordered, and additional headers are rejected", async () => {
  const invalidHeaders = [
    [],
    [[...MATCH_EXPORT_COLUMNS].reverse()],
    [[...MATCH_EXPORT_COLUMNS, "additional"]],
  ];

  for (const headerValues of invalidHeaders) {
    const { fetchImpl } = makeFetch(() =>
      responseJson({ values: headerValues })
    );

    await assertRejects(
      () =>
        verifyMatchResultsHeader(
          "google-token",
          "spreadsheet-id",
          { fetchImpl, timeoutMs: 100 },
        ),
      "GOOGLE_SHEET_SCHEMA_MISMATCH",
      409,
    );
  }
});

Deno.test("malformed successful header response is rejected safely", async () => {
  const { fetchImpl } = makeFetch(() =>
    new Response("not-json", { status: 200 })
  );

  await assertRejects(
    () =>
      verifyMatchResultsHeader(
        "google-token",
        "spreadsheet-id",
        { fetchImpl, timeoutMs: 100 },
      ),
    "GOOGLE_SHEET_SCHEMA_MISMATCH",
    409,
  );
});

Deno.test("header status failures use safe Google mappings", async () => {
  const cases = [
    [403, "GOOGLE_SHEETS_ACCESS_DENIED", 403],
    [404, "GOOGLE_SHEETS_NOT_FOUND", 404],
    [429, "GOOGLE_API_RATE_LIMITED", 429],
    [500, "GOOGLE_MATCH_EXPORT_FAILURE", 502],
  ] as const;

  for (const [upstreamStatus, code, status] of cases) {
    const { fetchImpl } = makeFetch(() =>
      new Response("sensitive body", { status: upstreamStatus })
    );

    await assertRejects(
      () =>
        verifyMatchResultsHeader(
          "google-token",
          "spreadsheet-id",
          { fetchImpl, timeoutMs: 100 },
        ),
      code,
      status,
    );
  }
});

Deno.test("header timeout maps to upstream timeout", async () => {
  const timeoutFetch: FetchImplementation = (_input, init) =>
    new Promise((_resolve, reject) => {
      init?.signal?.addEventListener(
        "abort",
        () => reject(new Error("aborted")),
        { once: true },
      );
    });

  await assertRejects(
    () =>
      verifyMatchResultsHeader(
        "google-token",
        "spreadsheet-id",
        { fetchImpl: timeoutFetch, timeoutMs: 1 },
      ),
    "UPSTREAM_TIMEOUT",
    504,
  );
});

Deno.test("append sends exactly one RAW twelve-row request", async () => {
  const matchValues = values();
  const { fetchImpl, calls } = makeFetch(() =>
    responseJson({ updates: { updatedRows: 12 } })
  );

  const rowsWritten = await appendMatchResults(
    "google-token",
    "spreadsheet-id",
    matchValues,
    { fetchImpl, timeoutMs: 100 },
  );

  assertEquals(rowsWritten, 12);
  assertEquals(calls.length, 1);
  assertEquals(calls[0].method, "POST");
  assertEquals(
    decodeURIComponent(calls[0].url.pathname),
    `/v4/spreadsheets/spreadsheet-id/values/${MATCH_RESULTS_APPEND_RANGE}:append`,
  );
  assertEquals(
    calls[0].url.searchParams.get("valueInputOption"),
    "RAW",
  );
  assertEquals(
    calls[0].url.searchParams.get("insertDataOption"),
    "INSERT_ROWS",
  );
  assertEquals(
    calls[0].headers.get("authorization"),
    "Bearer google-token",
  );
  assertEquals(
    calls[0].headers.get("content-type"),
    "application/json",
  );

  const body = JSON.parse(calls[0].body ?? "null");
  assertEquals(body.majorDimension, "ROWS");
  assertEquals(body.values, matchValues);
  assertEquals(body.values.length, 12);
  assert(body.values.every((row: unknown[]) => row.length === 20));
});

Deno.test("invalid append dimensions fail before network access", async () => {
  const { fetchImpl, calls } = makeFetch(() => {
    throw new Error("fetch must not run");
  });

  await assertRejects(
    () =>
      appendMatchResults(
        "google-token",
        "spreadsheet-id",
        values().slice(0, 11),
        { fetchImpl, timeoutMs: 100 },
      ),
    "GOOGLE_MATCH_EXPORT_FAILURE",
    502,
  );

  assertEquals(calls.length, 0);
});

Deno.test("append requires exactly twelve updated rows", async () => {
  const { fetchImpl } = makeFetch(() =>
    responseJson({ updates: { updatedRows: 11 } })
  );

  await assertRejects(
    () =>
      appendMatchResults(
        "google-token",
        "spreadsheet-id",
        values(),
        { fetchImpl, timeoutMs: 100 },
      ),
    "GOOGLE_MATCH_EXPORT_RESPONSE_INVALID",
    502,
  );
});

Deno.test("malformed successful append response is rejected safely", async () => {
  const { fetchImpl } = makeFetch(() => responseJson({ updates: {} }));

  await assertRejects(
    () =>
      appendMatchResults(
        "google-token",
        "spreadsheet-id",
        values(),
        { fetchImpl, timeoutMs: 100 },
      ),
    "GOOGLE_MATCH_EXPORT_RESPONSE_INVALID",
    502,
  );
});

Deno.test("append status failures use safe Google mappings", async () => {
  const cases = [
    [403, "GOOGLE_SHEETS_ACCESS_DENIED", 403],
    [404, "GOOGLE_SHEETS_NOT_FOUND", 404],
    [429, "GOOGLE_API_RATE_LIMITED", 429],
    [500, "GOOGLE_MATCH_EXPORT_FAILURE", 502],
  ] as const;

  for (const [upstreamStatus, code, status] of cases) {
    const { fetchImpl } = makeFetch(() =>
      new Response("sensitive body", { status: upstreamStatus })
    );

    await assertRejects(
      () =>
        appendMatchResults(
          "google-token",
          "spreadsheet-id",
          values(),
          { fetchImpl, timeoutMs: 100 },
        ),
      code,
      status,
    );
  }
});

Deno.test("append network failure performs no automatic retry", async () => {
  const { fetchImpl, calls } = makeFetch(() => {
    throw new Error("network failure");
  });

  await assertRejects(
    () =>
      appendMatchResults(
        "google-token",
        "spreadsheet-id",
        values(),
        { fetchImpl, timeoutMs: 100 },
      ),
    "GOOGLE_MATCH_EXPORT_FAILURE",
    502,
  );

  assertEquals(calls.length, 1);
});

Deno.test("append timeout performs no automatic retry", async () => {
  let calls = 0;
  const timeoutFetch: FetchImplementation = (_input, init) => {
    calls += 1;

    return new Promise((_resolve, reject) => {
      init?.signal?.addEventListener(
        "abort",
        () => reject(new Error("aborted")),
        { once: true },
      );
    });
  };

  await assertRejects(
    () =>
      appendMatchResults(
        "google-token",
        "spreadsheet-id",
        values(),
        { fetchImpl: timeoutFetch, timeoutMs: 1 },
      ),
    "UPSTREAM_TIMEOUT",
    504,
  );

  assertEquals(calls, 1);
});
