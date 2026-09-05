import { EdgeFunctionError } from "../_shared/errors.ts";
import type { FetchImplementation } from "../_shared/http.ts";
import {
  deleteTournamentExportRows,
} from "../_shared/googleAccountDeletion.ts";
import { MATCH_EXPORT_COLUMNS } from "../_shared/matchExport.ts";
import { STANDINGS_EXPORT_COLUMNS } from "../_shared/standingsExport.ts";

const SPREADSHEET_ID = "spreadsheet-id";
const ACCESS_TOKEN = "google-token";
const TOURNAMENT_A = "11111111-1111-4111-8111-111111111111";
const TOURNAMENT_B = "22222222-2222-4222-8222-222222222222";
const TOURNAMENT_C = "33333333-3333-4333-8333-333333333333";

interface Call {
  url: URL;
  method: string;
  headers: Headers;
  body: string | null;
}

interface FakeSheetState {
  metadata?: unknown;
  matchHeader?: unknown;
  standingsHeader?: unknown;
  matchValues?: unknown;
  standingsValues?: unknown;
  afterMatchValues?: unknown;
  afterStandingsValues?: unknown;
  batchResponse?: unknown;
  metadataStatus?: number;
  matchHeaderStatus?: number;
  standingsHeaderStatus?: number;
  matchValuesStatus?: number;
  standingsValuesStatus?: number;
  batchStatus?: number;
}

function assert(
  condition: unknown,
  message = "assertion failed",
): asserts condition {
  if (!condition) throw new Error(message);
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

function metadata(): unknown {
  return {
    sheets: [
      { properties: { sheetId: 101, title: "Match Results" } },
      { properties: { sheetId: 202, title: "Tournament Standings" } },
      { properties: { sheetId: 303, title: "Other" } },
    ],
  };
}

function matchHeader(): unknown {
  return { values: [[...MATCH_EXPORT_COLUMNS]] };
}

function standingsHeader(): unknown {
  return { values: [[...STANDINGS_EXPORT_COLUMNS]] };
}

function columnValues(...ids: string[]): string[][] {
  return ids.map((id) => [id]);
}

function path(call: Call): string {
  return decodeURIComponent(call.url.pathname);
}

function makeFetch(
  state: FakeSheetState = {},
): { fetchImpl: FetchImplementation; calls: Call[]; batchBodies: unknown[] } {
  const calls: Call[] = [];
  const batchBodies: unknown[] = [];
  let didMutate = false;

  const fetchImpl: FetchImplementation = (input, init) => {
    const call: Call = {
      url: new URL(String(input)),
      method: init?.method ?? "GET",
      headers: new Headers(init?.headers),
      body: typeof init?.body === "string" ? init.body : null,
    };
    calls.push(call);

    const requestPath = path(call);

    if (
      call.method === "GET" &&
      requestPath === `/v4/spreadsheets/${SPREADSHEET_ID}`
    ) {
      return Promise.resolve(
        responseJson(state.metadata ?? metadata(), state.metadataStatus ?? 200),
      );
    }

    if (
      call.method === "GET" &&
      requestPath.endsWith("/values/Match Results!A1:U1")
    ) {
      return Promise.resolve(
        responseJson(
          state.matchHeader ?? matchHeader(),
          state.matchHeaderStatus ?? 200,
        ),
      );
    }

    if (
      call.method === "GET" &&
      requestPath.endsWith("/values/Tournament Standings!A1:T1")
    ) {
      return Promise.resolve(
        responseJson(
          state.standingsHeader ?? standingsHeader(),
          state.standingsHeaderStatus ?? 200,
        ),
      );
    }

    if (
      call.method === "GET" &&
      requestPath.endsWith("/values/Match Results!C2:C")
    ) {
      return Promise.resolve(
        responseJson(
          {
            values: didMutate
              ? state.afterMatchValues ?? []
              : state.matchValues ?? [],
          },
          state.matchValuesStatus ?? 200,
        ),
      );
    }

    if (
      call.method === "GET" &&
      requestPath.endsWith("/values/Tournament Standings!C2:C")
    ) {
      return Promise.resolve(
        responseJson(
          {
            values: didMutate
              ? state.afterStandingsValues ?? []
              : state.standingsValues ?? [],
          },
          state.standingsValuesStatus ?? 200,
        ),
      );
    }

    if (
      call.method === "POST" &&
      requestPath === `/v4/spreadsheets/${SPREADSHEET_ID}:batchUpdate`
    ) {
      const body = call.body === null ? null : JSON.parse(call.body);
      batchBodies.push(body);
      didMutate = true;
      const requestCount = Array.isArray(body?.requests)
        ? body.requests.length
        : 0;
      return Promise.resolve(
        responseJson(
          state.batchResponse ?? {
            replies: Array.from({ length: requestCount }, () => ({})),
          },
          state.batchStatus ?? 200,
        ),
      );
    }

    throw new Error(`Unexpected request: ${call.method} ${requestPath}`);
  };

  return { fetchImpl, calls, batchBodies };
}

function options(fetchImpl: FetchImplementation) {
  return { fetchImpl, timeoutMs: 100 };
}

function validState(): FakeSheetState {
  return {
    matchValues: columnValues(
      TOURNAMENT_A,
      TOURNAMENT_B,
      TOURNAMENT_A,
      TOURNAMENT_C,
      TOURNAMENT_A,
    ),
    standingsValues: columnValues(
      TOURNAMENT_B,
      TOURNAMENT_A,
      TOURNAMENT_A,
      TOURNAMENT_C,
    ),
    afterMatchValues: columnValues(TOURNAMENT_B, TOURNAMENT_C),
    afterStandingsValues: columnValues(TOURNAMENT_B, TOURNAMENT_C),
  };
}

Deno.test("empty tournament IDs return zero without network calls", async () => {
  let calls = 0;
  const result = await deleteTournamentExportRows(
    ACCESS_TOKEN,
    SPREADSHEET_ID,
    [],
    options(async () => {
      calls += 1;
      return responseJson({});
    }),
  );

  assertEquals(result, {
    deletedMatchResultRows: 0,
    deletedTournamentStandingsRows: 0,
  });
  assertEquals(calls, 0);
});

Deno.test("invalid tournament IDs fail before network calls", async () => {
  let calls = 0;
  await assertRejects(
    () =>
      deleteTournamentExportRows(
        ACCESS_TOKEN,
        SPREADSHEET_ID,
        ["not-a-uuid"],
        options(async () => {
          calls += 1;
          return responseJson({});
        }),
      ),
    "GOOGLE_ACCOUNT_DELETION_FAILURE",
    502,
  );
  assertEquals(calls, 0);
});

Deno.test("deletes both worksheets with exact grouped descending ranges", async () => {
  const fake = makeFetch(validState());
  const result = await deleteTournamentExportRows(
    ACCESS_TOKEN,
    SPREADSHEET_ID,
    [TOURNAMENT_A],
    options(fake.fetchImpl),
  );

  assertEquals(result, {
    deletedMatchResultRows: 3,
    deletedTournamentStandingsRows: 2,
  });
  assertEquals(fake.batchBodies.length, 1);
  assertEquals(fake.batchBodies[0], {
    requests: [
      {
        deleteDimension: {
          range: {
            sheetId: 101,
            dimension: "ROWS",
            startIndex: 5,
            endIndex: 6,
          },
        },
      },
      {
        deleteDimension: {
          range: {
            sheetId: 101,
            dimension: "ROWS",
            startIndex: 3,
            endIndex: 4,
          },
        },
      },
      {
        deleteDimension: {
          range: {
            sheetId: 101,
            dimension: "ROWS",
            startIndex: 1,
            endIndex: 2,
          },
        },
      },
      {
        deleteDimension: {
          range: {
            sheetId: 202,
            dimension: "ROWS",
            startIndex: 2,
            endIndex: 4,
          },
        },
      },
    ],
  });
  assert(
    (fake.batchBodies[0] as {
      requests: Array<{ deleteDimension: { range: { startIndex: number } } }>;
    })
      .requests.every((request) =>
        request.deleteDimension.range.startIndex >= 1
      ),
    "header row must not be deleted",
  );
});

Deno.test("deduplicates IDs and matches UUIDs case-insensitively", async () => {
  const state: FakeSheetState = {
    matchValues: columnValues(TOURNAMENT_A, TOURNAMENT_B, TOURNAMENT_C),
    standingsValues: columnValues(TOURNAMENT_B, TOURNAMENT_A),
    afterMatchValues: columnValues(TOURNAMENT_C),
    afterStandingsValues: [],
  };
  const fake = makeFetch(state);
  const result = await deleteTournamentExportRows(
    ACCESS_TOKEN,
    SPREADSHEET_ID,
    [TOURNAMENT_A.toUpperCase(), TOURNAMENT_B, TOURNAMENT_A],
    options(fake.fetchImpl),
  );

  assertEquals(result, {
    deletedMatchResultRows: 2,
    deletedTournamentStandingsRows: 2,
  });
  const body = fake.batchBodies[0] as { requests: unknown[] };
  assertEquals(body.requests.length, 2);
});

Deno.test("no matching rows returns zero without batch mutation", async () => {
  const fake = makeFetch({
    matchValues: columnValues(TOURNAMENT_B),
    standingsValues: columnValues(TOURNAMENT_C),
  });
  const result = await deleteTournamentExportRows(
    ACCESS_TOKEN,
    SPREADSHEET_ID,
    [TOURNAMENT_A],
    options(fake.fetchImpl),
  );

  assertEquals(result, {
    deletedMatchResultRows: 0,
    deletedTournamentStandingsRows: 0,
  });
  assertEquals(fake.batchBodies.length, 0);
});

Deno.test("repeated deletion is idempotent and sends one mutation", async () => {
  const fake = makeFetch(validState());
  const first = await deleteTournamentExportRows(
    ACCESS_TOKEN,
    SPREADSHEET_ID,
    [TOURNAMENT_A],
    options(fake.fetchImpl),
  );
  const second = await deleteTournamentExportRows(
    ACCESS_TOKEN,
    SPREADSHEET_ID,
    [TOURNAMENT_A],
    options(fake.fetchImpl),
  );

  assertEquals(first.deletedMatchResultRows, 3);
  assertEquals(first.deletedTournamentStandingsRows, 2);
  assertEquals(second, {
    deletedMatchResultRows: 0,
    deletedTournamentStandingsRows: 0,
  });
  assertEquals(fake.batchBodies.length, 1);
});

Deno.test("header mismatch fails before any mutation", async () => {
  const fake = makeFetch({
    matchHeader: { values: [[...MATCH_EXPORT_COLUMNS].reverse()] },
  });
  await assertRejects(
    () =>
      deleteTournamentExportRows(
        ACCESS_TOKEN,
        SPREADSHEET_ID,
        [TOURNAMENT_A],
        options(fake.fetchImpl),
      ),
    "GOOGLE_SHEET_SCHEMA_MISMATCH",
    409,
  );
  assertEquals(fake.batchBodies.length, 0);
});

Deno.test("Tournament Standings header mismatch fails before any mutation", async () => {
  const fake = makeFetch({
    standingsHeader: { values: [[...STANDINGS_EXPORT_COLUMNS, "extra"]] },
  });
  await assertRejects(
    () =>
      deleteTournamentExportRows(
        ACCESS_TOKEN,
        SPREADSHEET_ID,
        [TOURNAMENT_A],
        options(fake.fetchImpl),
      ),
    "GOOGLE_STANDINGS_SHEET_SCHEMA_MISMATCH",
    409,
  );
  assertEquals(fake.batchBodies.length, 0);
});

Deno.test("missing or invalid worksheet metadata fails before mutation", async () => {
  for (
    const invalidMetadata of [
      { sheets: [] },
      { sheets: [{ properties: { title: "Match Results", sheetId: "101" } }] },
      {
        sheets: [
          { properties: { title: "Match Results", sheetId: 101 } },
          { properties: { title: "Match Results", sheetId: 102 } },
        ],
      },
    ]
  ) {
    const fake = makeFetch({ metadata: invalidMetadata });
    await assertRejects(
      () =>
        deleteTournamentExportRows(
          ACCESS_TOKEN,
          SPREADSHEET_ID,
          [TOURNAMENT_A],
          options(fake.fetchImpl),
        ),
      "GOOGLE_ACCOUNT_DELETION_FAILURE",
      502,
    );
    assertEquals(fake.batchBodies.length, 0);
  }
});

Deno.test("malformed successful column read fails closed", async () => {
  const fake = makeFetch({ matchValues: { values: "not-an-array" } });
  await assertRejects(
    () =>
      deleteTournamentExportRows(
        ACCESS_TOKEN,
        SPREADSHEET_ID,
        [TOURNAMENT_A],
        options(fake.fetchImpl),
      ),
    "GOOGLE_ACCOUNT_DELETION_FAILURE",
    502,
  );
  assertEquals(fake.batchBodies.length, 0);
});

Deno.test("malformed successful batch response fails closed", async () => {
  const fake = makeFetch({
    ...validState(),
    batchResponse: {},
  });
  await assertRejects(
    () =>
      deleteTournamentExportRows(
        ACCESS_TOKEN,
        SPREADSHEET_ID,
        [TOURNAMENT_A],
        options(fake.fetchImpl),
      ),
    "GOOGLE_ACCOUNT_DELETION_FAILURE",
    502,
  );
});

Deno.test("residual target rows after mutation fail closed", async () => {
  const fake = makeFetch({
    ...validState(),
    afterMatchValues: columnValues(TOURNAMENT_A),
    afterStandingsValues: [],
  });
  await assertRejects(
    () =>
      deleteTournamentExportRows(
        ACCESS_TOKEN,
        SPREADSHEET_ID,
        [TOURNAMENT_A],
        options(fake.fetchImpl),
      ),
    "GOOGLE_ACCOUNT_DELETION_FAILURE",
    502,
  );
});

Deno.test("Google status failures use safe mappings", async () => {
  for (
    const [status, code, mappedStatus] of [
      [403, "GOOGLE_SHEETS_ACCESS_DENIED", 403],
      [404, "GOOGLE_SHEETS_NOT_FOUND", 404],
      [429, "GOOGLE_API_RATE_LIMITED", 429],
      [500, "GOOGLE_ACCOUNT_DELETION_FAILURE", 502],
    ] as const
  ) {
    const fake = makeFetch({ metadataStatus: status });
    await assertRejects(
      () =>
        deleteTournamentExportRows(
          ACCESS_TOKEN,
          SPREADSHEET_ID,
          [TOURNAMENT_A],
          options(fake.fetchImpl),
        ),
      code,
      mappedStatus,
    );
  }
});

Deno.test("timeout maps safely", async () => {
  const fetchImpl: FetchImplementation = (_input, init) =>
    new Promise((_resolve, reject) => {
      init?.signal?.addEventListener(
        "abort",
        () => reject(new Error("aborted")),
      );
    });

  await assertRejects(
    () =>
      deleteTournamentExportRows(
        ACCESS_TOKEN,
        SPREADSHEET_ID,
        [TOURNAMENT_A],
        { fetchImpl, timeoutMs: 1 },
      ),
    "UPSTREAM_TIMEOUT",
    504,
  );
});

Deno.test("network failure maps safely", async () => {
  const fetchImpl: FetchImplementation = () =>
    Promise.reject(new Error("network failure"));

  await assertRejects(
    () =>
      deleteTournamentExportRows(
        ACCESS_TOKEN,
        SPREADSHEET_ID,
        [TOURNAMENT_A],
        options(fetchImpl),
      ),
    "GOOGLE_ACCOUNT_DELETION_FAILURE",
    502,
  );
});

Deno.test("uses Bearer authorization without leaking credentials", async () => {
  const fake = makeFetch(validState());
  await deleteTournamentExportRows(
    ACCESS_TOKEN,
    SPREADSHEET_ID,
    [TOURNAMENT_A],
    options(fake.fetchImpl),
  );

  for (const call of fake.calls) {
    assertEquals(call.headers.get("authorization"), `Bearer ${ACCESS_TOKEN}`);
    assert(!call.url.toString().includes(ACCESS_TOKEN));
    assert(!call.url.search.includes(ACCESS_TOKEN));
    assert(!(call.body ?? "").includes(ACCESS_TOKEN));
  }
});
