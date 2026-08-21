import { EdgeFunctionError } from "../_shared/errors.ts";
import {
  appendTournamentStandings,
  TOURNAMENT_STANDINGS_APPEND_RANGE,
  TOURNAMENT_STANDINGS_HEADER_RANGE,
  verifyTournamentStandingsHeader,
} from "../_shared/googleStandingsExport.ts";
import type { FetchImplementation } from "../_shared/http.ts";
import {
  STANDINGS_EXPORT_COLUMNS,
  type StandingsExportCell,
} from "../_shared/standingsExport.ts";

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

function values(rowCount = 12): StandingsExportCell[][] {
  return Array.from(
    { length: rowCount },
    (_, rowIndex) =>
      STANDINGS_EXPORT_COLUMNS.map((column, columnIndex) =>
        columnIndex < 4 ? `${column}-${rowIndex + 1}` : rowIndex + columnIndex
      ),
  );
}

Deno.test(
  "exact Tournament Standings header is accepted with a read-only request",
  async () => {
    const { fetchImpl, calls } = makeFetch(() =>
      responseJson({
        range: TOURNAMENT_STANDINGS_HEADER_RANGE,
        majorDimension: "ROWS",
        values: [[...STANDINGS_EXPORT_COLUMNS]],
      })
    );

    await verifyTournamentStandingsHeader(
      "google-token",
      "spreadsheet-id",
      { fetchImpl, timeoutMs: 100 },
    );

    assertEquals(calls.length, 1);
    assertEquals(calls[0].method, "GET");
    assertEquals(
      decodeURIComponent(calls[0].url.pathname),
      `/v4/spreadsheets/spreadsheet-id/values/${TOURNAMENT_STANDINGS_HEADER_RANGE}`,
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
  },
);

Deno.test(
  "missing, reordered, additional, and multi-row headers are rejected",
  async () => {
    const invalidHeaders = [
      [],
      [[...STANDINGS_EXPORT_COLUMNS].reverse()],
      [[...STANDINGS_EXPORT_COLUMNS, "additional"]],
      [
        [...STANDINGS_EXPORT_COLUMNS],
        [...STANDINGS_EXPORT_COLUMNS],
      ],
    ];

    for (const headerValues of invalidHeaders) {
      const { fetchImpl } = makeFetch(() =>
        responseJson({ values: headerValues })
      );

      await assertRejects(
        () =>
          verifyTournamentStandingsHeader(
            "google-token",
            "spreadsheet-id",
            { fetchImpl, timeoutMs: 100 },
          ),
        "GOOGLE_STANDINGS_SHEET_SCHEMA_MISMATCH",
        409,
      );
    }
  },
);

Deno.test(
  "malformed successful standings header response is rejected safely",
  async () => {
    const { fetchImpl } = makeFetch(() =>
      new Response("not-json", { status: 200 })
    );

    await assertRejects(
      () =>
        verifyTournamentStandingsHeader(
          "google-token",
          "spreadsheet-id",
          { fetchImpl, timeoutMs: 100 },
        ),
      "GOOGLE_STANDINGS_SHEET_SCHEMA_MISMATCH",
      409,
    );
  },
);

Deno.test("standings header status failures use safe mappings", async () => {
  const cases = [
    [403, "GOOGLE_SHEETS_ACCESS_DENIED", 403],
    [404, "GOOGLE_SHEETS_NOT_FOUND", 404],
    [429, "GOOGLE_API_RATE_LIMITED", 429],
    [500, "GOOGLE_STANDINGS_EXPORT_FAILURE", 502],
  ] as const;

  for (const [upstreamStatus, code, status] of cases) {
    const { fetchImpl } = makeFetch(() =>
      new Response("sensitive body", { status: upstreamStatus })
    );

    await assertRejects(
      () =>
        verifyTournamentStandingsHeader(
          "google-token",
          "spreadsheet-id",
          { fetchImpl, timeoutMs: 100 },
        ),
      code,
      status,
    );
  }
});

Deno.test("standings header timeout maps safely", async () => {
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
      verifyTournamentStandingsHeader(
        "google-token",
        "spreadsheet-id",
        { fetchImpl: timeoutFetch, timeoutMs: 1 },
      ),
    "UPSTREAM_TIMEOUT",
    504,
  );
});

Deno.test(
  "standings append sends exactly one RAW twelve-row request",
  async () => {
    const standingsValues = values();
    const { fetchImpl, calls } = makeFetch(() =>
      responseJson({
        updates: {
          updatedRows: 12,
          updatedRange: "'Tournament Standings'!A2:T13",
        },
      })
    );

    const appendResult = await appendTournamentStandings(
      "google-token",
      "spreadsheet-id",
      standingsValues,
      { fetchImpl, timeoutMs: 100 },
    );

    assertEquals(appendResult, {
      rowsWritten: 12,
      updatedRange: "'Tournament Standings'!A2:T13",
    });
    assertEquals(calls.length, 1);
    assertEquals(calls[0].method, "POST");
    assertEquals(
      decodeURIComponent(calls[0].url.pathname),
      `/v4/spreadsheets/spreadsheet-id/values/${TOURNAMENT_STANDINGS_APPEND_RANGE}:append`,
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
    assertEquals(body.values, standingsValues);
    assertEquals(body.values.length, 12);
    assert(
      body.values.every((row: unknown[]) => row.length === 20),
    );
  },
);

Deno.test(
  "standings append supports a ten-row participant-aware request and range",
  async () => {
    const standingsValues = values(10);
    const { fetchImpl, calls } = makeFetch(() =>
      responseJson({
        updates: {
          updatedRows: 10,
          updatedRange: "'Tournament Standings'!A2:T11",
        },
      })
    );

    const appendResult = await appendTournamentStandings(
      "google-token",
      "spreadsheet-id",
      standingsValues,
      { fetchImpl, timeoutMs: 100 },
    );

    assertEquals(appendResult, {
      rowsWritten: 10,
      updatedRange: "'Tournament Standings'!A2:T11",
    });
    assertEquals(JSON.parse(calls[0].body ?? "null").values.length, 10);
  },
);

Deno.test(
  "invalid standings append dimensions fail before network access",
  async () => {
    const invalidValues: StandingsExportCell[][][] = [
      [],
      values(13),
      values().map((row, index) => index === 0 ? row.slice(0, 19) : row),
      values().map((row, index) =>
        index === 0 ? [...row.slice(0, 19), 1.5] : row
      ),
    ];

    for (const candidate of invalidValues) {
      const { fetchImpl, calls } = makeFetch(() => {
        throw new Error("fetch must not run");
      });

      await assertRejects(
        () =>
          appendTournamentStandings(
            "google-token",
            "spreadsheet-id",
            candidate,
            { fetchImpl, timeoutMs: 100 },
          ),
        "GOOGLE_STANDINGS_EXPORT_FAILURE",
        502,
      );

      assertEquals(calls.length, 0);
    }
  },
);

Deno.test(
  "standings append requires expected updated rows and a valid returned range",
  async () => {
    const invalidResponses = [
      {
        updates: {
          updatedRows: 11,
          updatedRange: "'Tournament Standings'!A2:T13",
        },
      },
      {
        updates: {
          updatedRows: "12",
          updatedRange: "'Tournament Standings'!A2:T13",
        },
      },
      { updates: { updatedRows: 12 } },
      {
        updates: {
          updatedRows: 12,
          updatedRange: "Tournament Standings!A2:T13",
        },
      },
      { updates: { updatedRows: 12, updatedRange: "'Other'!A2:T13" } },
      {
        updates: {
          updatedRows: 12,
          updatedRange: "'Tournament Standings'!A1:T12",
        },
      },
      {
        updates: {
          updatedRows: 12,
          updatedRange: "'Tournament Standings'!A2:U13",
        },
      },
      { updates: {} },
      {},
    ];

    for (const payload of invalidResponses) {
      const { fetchImpl } = makeFetch(() => responseJson(payload));

      await assertRejects(
        () =>
          appendTournamentStandings(
            "google-token",
            "spreadsheet-id",
            values(),
            { fetchImpl, timeoutMs: 100 },
          ),
        "GOOGLE_STANDINGS_EXPORT_RESPONSE_INVALID",
        502,
      );
    }
  },
);

Deno.test(
  "malformed successful standings append response is rejected safely",
  async () => {
    const { fetchImpl } = makeFetch(() =>
      new Response("not-json", { status: 200 })
    );

    await assertRejects(
      () =>
        appendTournamentStandings(
          "google-token",
          "spreadsheet-id",
          values(),
          { fetchImpl, timeoutMs: 100 },
        ),
      "GOOGLE_STANDINGS_EXPORT_RESPONSE_INVALID",
      502,
    );
  },
);

Deno.test("standings append status failures use safe mappings", async () => {
  const cases = [
    [403, "GOOGLE_SHEETS_ACCESS_DENIED", 403],
    [404, "GOOGLE_SHEETS_NOT_FOUND", 404],
    [429, "GOOGLE_API_RATE_LIMITED", 429],
    [500, "GOOGLE_STANDINGS_EXPORT_FAILURE", 502],
  ] as const;

  for (const [upstreamStatus, code, status] of cases) {
    const { fetchImpl } = makeFetch(() =>
      new Response("sensitive body", { status: upstreamStatus })
    );

    await assertRejects(
      () =>
        appendTournamentStandings(
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

Deno.test(
  "standings append network failure performs no automatic retry",
  async () => {
    const { fetchImpl, calls } = makeFetch(() => {
      throw new Error("network failure");
    });

    await assertRejects(
      () =>
        appendTournamentStandings(
          "google-token",
          "spreadsheet-id",
          values(),
          { fetchImpl, timeoutMs: 100 },
        ),
      "GOOGLE_STANDINGS_EXPORT_FAILURE",
      502,
    );

    assertEquals(calls.length, 1);
  },
);

Deno.test(
  "standings append timeout performs no automatic retry",
  async () => {
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
        appendTournamentStandings(
          "google-token",
          "spreadsheet-id",
          values(),
          { fetchImpl: timeoutFetch, timeoutMs: 1 },
        ),
      "UPSTREAM_TIMEOUT",
      504,
    );

    assertEquals(calls, 1);
  },
);
