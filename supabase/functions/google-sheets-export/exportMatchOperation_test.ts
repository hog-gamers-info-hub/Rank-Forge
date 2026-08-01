import { MATCH_EXPORT_COLUMNS } from "../_shared/matchExport.ts";
import type { FetchImplementation } from "../_shared/http.ts";
import { handleRequest } from "./index.ts";

const TOURNAMENT_ID = "11111111-1111-4111-8111-111111111111";
const MATCH_ID = "22222222-2222-4222-8222-222222222222";
const OPERATION_ID = "33333333-3333-4333-8333-333333333333";
const LEASE_TOKEN = "44444444-4444-4444-8444-444444444444";

const TEST_ENV: Record<string, string> = {
  SUPABASE_URL: "https://project.supabase.co",
  SUPABASE_ANON_KEY: "anon-key",
  GOOGLE_SHEETS_CLIENT_EMAIL: "service@example.invalid",
  GOOGLE_SHEETS_PRIVATE_KEY: "synthetic-private-key",
  GOOGLE_SHEETS_SPREADSHEET_ID: "spreadsheet-id",
};

const PLACEMENT_POINTS = [12, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0, 0];

interface Call {
  url: URL;
  method: string;
  headers: Headers;
  body: string;
}

interface ExportFetchOverrides {
  tournamentName?: string;
  matchStatus?: string;
  header?: unknown;
  updatedRows?: number;
  claimOutcome?: "claimed" | "replayed" | "in_progress" | "outcome_uncertain";
  appendStatus?: number;
  writeStartedStatus?: number;
  completeStatus?: number;
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

function responseJson(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function makeFetch(
  responder: (call: Call, index: number) => Response | Promise<Response>,
): { fetchImpl: FetchImplementation; calls: Call[] } {
  const calls: Call[] = [];

  const fetchImpl: FetchImplementation = async (input, init = {}) => {
    const call: Call = {
      url: new URL(String(input)),
      method: init.method ?? "GET",
      headers: new Headers(init.headers),
      body: typeof init.body === "string" ? init.body : "",
    };
    calls.push(call);
    return await responder(call, calls.length - 1);
  };

  return { fetchImpl, calls };
}

function env(name: string): string | undefined {
  return TEST_ENV[name];
}

function request(
  body: unknown,
  authorization = "Bearer caller-token",
): Request {
  return new Request("https://function.invalid", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(authorization ? { Authorization: authorization } : {}),
    },
    body: JSON.stringify(body),
  });
}

async function jsonBody(response: Response): Promise<Record<string, unknown>> {
  return await response.json() as Record<string, unknown>;
}

function teamSlotId(slot: number): string {
  return `team-slot-${slot}`;
}

function validPayload(): Record<string, unknown> {
  return {
    operation: "export_match",
    tournament_id: TOURNAMENT_ID,
    match_id: MATCH_ID,
    rows: Array.from({ length: 12 }, (_, index) => {
      const placement = index + 1;
      const kills = index;

      return {
        export_schema_version: "phase_10_v1",
        export_type: "match_result",
        tournament_id: TOURNAMENT_ID,
        tournament_name: "Championship",
        match_id: MATCH_ID,
        match_label: "Match 1",
        match_finalized_at: "",
        row_number: placement,
        placement,
        team_slot: placement,
        team_name: `Team ${placement}`,
        player_1_name: `Player ${placement}A`,
        player_2_name: `Player ${placement}B`,
        player_3_name: `Player ${placement}C`,
        player_4_name: `Player ${placement}D`,
        placement_points: PLACEMENT_POINTS[index],
        kills,
        kill_points: kills,
        total_points: PLACEMENT_POINTS[index] + kills,
        correction_status: "original_finalized",
      };
    }),
  };
}

function tournamentResponse(name = "Championship"): Response {
  return responseJson([{ id: TOURNAMENT_ID, name }]);
}

function matchResponse(status = "finalized"): Response {
  return responseJson([{
    id: MATCH_ID,
    tournament_id: TOURNAMENT_ID,
    match_number: 1,
    status,
    finalized_at: "2026-07-31T12:30:00Z",
  }]);
}

function matchResultsResponse(): Response {
  return responseJson(
    Array.from({ length: 12 }, (_, index) => {
      const slot = index + 1;

      return {
        id: `result-${slot}`,
        match_id: MATCH_ID,
        team_slot_id: teamSlotId(slot),
        placement: slot,
        kills: index,
        review_status: "confirmed",
      };
    }),
  );
}

function teamSlotsResponse(): Response {
  return responseJson(
    Array.from({ length: 12 }, (_, index) => {
      const slot = index + 1;

      return {
        id: teamSlotId(slot),
        tournament_id: TOURNAMENT_ID,
        slot_number: slot,
        team_name: `Team ${slot}`,
      };
    }),
  );
}

function playersResponse(): Response {
  return responseJson(
    Array.from({ length: 12 }, (_, index) => {
      const slot = index + 1;

      return ["A", "B", "C", "D"].map((suffix) => ({
        id: `player-${slot}-${suffix}`,
        team_slot_id: teamSlotId(slot),
        display_name: `Player ${slot}${suffix}`,
      }));
    }).flat(),
  );
}

function claimResponse(
  outcome: ExportFetchOverrides["claimOutcome"] = "claimed",
): Response {
  if (outcome === "replayed") {
    return responseJson([{
      outcome: "replayed",
      operation_id: OPERATION_ID,
      lease_token: null,
      state: "succeeded",
      attempt_count: 1,
      rows_written: 12,
      exported_match_count: null,
    }]);
  }

  if (outcome === "in_progress") {
    return responseJson([{
      outcome: "in_progress",
      operation_id: OPERATION_ID,
      lease_token: null,
      state: "in_progress",
      attempt_count: 1,
      rows_written: null,
      exported_match_count: null,
    }]);
  }

  if (outcome === "outcome_uncertain") {
    return responseJson([{
      outcome: "outcome_uncertain",
      operation_id: OPERATION_ID,
      lease_token: null,
      state: "outcome_uncertain",
      attempt_count: 1,
      rows_written: null,
      exported_match_count: null,
    }]);
  }

  return responseJson([{
    outcome: "claimed",
    operation_id: OPERATION_ID,
    lease_token: LEASE_TOKEN,
    state: "in_progress",
    attempt_count: 1,
    rows_written: null,
    exported_match_count: null,
  }]);
}

function successfulExportFetch(
  overrides: ExportFetchOverrides = {},
): { fetchImpl: FetchImplementation; calls: Call[] } {
  return makeFetch((call) => {
    const path = decodeURIComponent(call.url.pathname);

    if (path === "/auth/v1/user") {
      return responseJson({ id: "user-id" });
    }

    if (path === "/rest/v1/tournaments") {
      return tournamentResponse(overrides.tournamentName);
    }

    if (path === "/rest/v1/matches") {
      return matchResponse(overrides.matchStatus);
    }

    if (path === "/rest/v1/match_results") {
      return matchResultsResponse();
    }

    if (path === "/rest/v1/tournament_team_slots") {
      return teamSlotsResponse();
    }

    if (path === "/rest/v1/players") {
      return playersResponse();
    }

    if (path === "/rest/v1/rpc/claim_export_operation") {
      return claimResponse(overrides.claimOutcome);
    }

    if (path === "/token") {
      return responseJson({
        access_token: "google-token",
        token_type: "Bearer",
      });
    }

    if (
      path ===
        "/v4/spreadsheets/spreadsheet-id/values/Match Results!A1:T1"
    ) {
      return responseJson({
        values: overrides.header ?? [[...MATCH_EXPORT_COLUMNS]],
      });
    }

    if (path === "/rest/v1/rpc/mark_export_operation_write_started") {
      return responseJson(
        overrides.writeStartedStatus === undefined ? "write_started" : {
          message: "blocked",
        },
        overrides.writeStartedStatus ?? 200,
      );
    }

    if (
      path ===
        "/v4/spreadsheets/spreadsheet-id/values/Match Results!A:T:append"
    ) {
      if (overrides.appendStatus !== undefined) {
        return responseJson(
          { error: "synthetic append rejection" },
          overrides.appendStatus,
        );
      }

      return responseJson({
        updates: { updatedRows: overrides.updatedRows ?? 12 },
      });
    }

    if (path === "/rest/v1/rpc/complete_export_operation_success") {
      return responseJson(
        overrides.completeStatus === undefined ? "succeeded" : {
          message: "synthetic completion failure",
        },
        overrides.completeStatus ?? 200,
      );
    }

    if (path === "/rest/v1/rpc/mark_export_operation_retryable_failure") {
      return responseJson("retryable_failure");
    }

    if (path === "/rest/v1/rpc/mark_export_operation_outcome_uncertain") {
      return responseJson("outcome_uncertain");
    }

    throw new Error(`Unexpected network request: ${call.method} ${path}`);
  });
}

function paths(calls: readonly Call[]): string[] {
  return calls.map((call) => decodeURIComponent(call.url.pathname));
}

function sheetAppendCalls(calls: readonly Call[]): Call[] {
  return calls.filter((call) =>
    call.url.hostname === "sheets.googleapis.com" &&
    call.method === "POST" &&
    decodeURIComponent(call.url.pathname).endsWith("!A:T:append")
  );
}

function rpcCall(calls: readonly Call[], name: string): Call {
  const match = calls.find((call) =>
    decodeURIComponent(call.url.pathname) === `/rest/v1/rpc/${name}`
  );

  if (!match) {
    throw new Error(`RPC call not found: ${name}`);
  }

  return match;
}

const injectedSigner = () => Promise.resolve(new Uint8Array([1, 2, 3]));

Deno.test("export_match validates official data, claims, marks write, appends once, and completes", async () => {
  const { fetchImpl, calls } = successfulExportFetch();

  const response = await handleRequest(request(validPayload()), {
    env,
    fetchImpl,
    signer: injectedSigner,
    clock: () => 1_700_000_000,
  });

  assertEquals(response.status, 200);
  assertEquals(await jsonBody(response), {
    ok: true,
    operation: "export_match",
    tournament_id: TOURNAMENT_ID,
    match_id: MATCH_ID,
    rows_written: 12,
  });

  assertEquals(paths(calls), [
    "/auth/v1/user",
    "/rest/v1/tournaments",
    "/rest/v1/matches",
    "/rest/v1/match_results",
    "/rest/v1/tournament_team_slots",
    "/rest/v1/players",
    "/rest/v1/rpc/claim_export_operation",
    "/token",
    "/v4/spreadsheets/spreadsheet-id/values/Match Results!A1:T1",
    "/rest/v1/rpc/mark_export_operation_write_started",
    "/v4/spreadsheets/spreadsheet-id/values/Match Results!A:T:append",
    "/rest/v1/rpc/complete_export_operation_success",
  ]);

  assertEquals(sheetAppendCalls(calls).length, 1);
  assert(
    paths(calls).indexOf("/rest/v1/rpc/mark_export_operation_write_started") <
      paths(calls).indexOf(
        "/v4/spreadsheets/spreadsheet-id/values/Match Results!A:T:append",
      ),
  );

  for (
    const call of calls.filter((call) =>
      call.url.hostname === "project.supabase.co" &&
      call.url.pathname !== "/auth/v1/user"
    )
  ) {
    assertEquals(call.headers.get("authorization"), "Bearer caller-token");
    assertEquals(call.headers.get("apikey"), "anon-key");
  }

  const appendCall = sheetAppendCalls(calls)[0];
  assertEquals(appendCall.url.searchParams.get("valueInputOption"), "RAW");
  assertEquals(
    appendCall.url.searchParams.get("insertDataOption"),
    "INSERT_ROWS",
  );

  const appendBody = JSON.parse(appendCall.body);
  assertEquals(appendBody.majorDimension, "ROWS");
  assertEquals(appendBody.values.length, 12);
  assert(
    appendBody.values.every(
      (row: unknown[]) => row.length === MATCH_EXPORT_COLUMNS.length,
    ),
  );
});

Deno.test("invalid export payload is rejected before authentication", async () => {
  const payload = validPayload();
  payload.extra = true;
  const { fetchImpl, calls } = makeFetch(() => {
    throw new Error("fetch must not run");
  });

  const response = await handleRequest(request(payload), {
    env,
    fetchImpl,
  });

  assertEquals(response.status, 400);
  assertEquals((await jsonBody(response)).error, {
    code: "INVALID_MATCH_EXPORT_PAYLOAD",
    message: "The match export payload is invalid.",
  });
  assertEquals(calls.length, 0);
});

Deno.test("unauthenticated export contacts neither Supabase nor Google", async () => {
  const { fetchImpl, calls } = makeFetch(() => {
    throw new Error("fetch must not run");
  });

  const response = await handleRequest(
    request(validPayload(), ""),
    { env, fetchImpl },
  );

  assertEquals(response.status, 401);
  assertEquals((await jsonBody(response)).error, {
    code: "UNAUTHORIZED",
    message: "Authentication is required.",
  });
  assertEquals(calls.length, 0);
});

Deno.test("official-data mismatch is rejected before idempotency claim", async () => {
  const { fetchImpl, calls } = successfulExportFetch({
    tournamentName: "Different Championship",
  });

  const response = await handleRequest(request(validPayload()), {
    env,
    fetchImpl,
    signer: injectedSigner,
  });

  assertEquals(response.status, 409);
  assertEquals((await jsonBody(response)).error, {
    code: "MATCH_EXPORT_DATA_MISMATCH",
    message: "The match export data does not match finalized records.",
  });
  assertEquals(calls.length, 6);
  assert(!paths(calls).includes("/rest/v1/rpc/claim_export_operation"));
  assertEquals(sheetAppendCalls(calls).length, 0);
});

Deno.test("draft match is rejected before idempotency claim", async () => {
  const { fetchImpl, calls } = successfulExportFetch({
    matchStatus: "draft",
  });

  const response = await handleRequest(request(validPayload()), {
    env,
    fetchImpl,
    signer: injectedSigner,
  });

  assertEquals(response.status, 409);
  assertEquals((await jsonBody(response)).error, {
    code: "MATCH_NOT_FINALIZED",
    message: "Only finalized matches can be exported.",
  });
  assertEquals(calls.length, 6);
  assert(!paths(calls).includes("/rest/v1/rpc/claim_export_operation"));
});

Deno.test("successful replay returns the original public response without Google access", async () => {
  const { fetchImpl, calls } = successfulExportFetch({
    claimOutcome: "replayed",
  });

  const response = await handleRequest(request(validPayload()), {
    env,
    fetchImpl,
    signer: injectedSigner,
  });

  assertEquals(response.status, 200);
  assertEquals(await jsonBody(response), {
    ok: true,
    operation: "export_match",
    tournament_id: TOURNAMENT_ID,
    match_id: MATCH_ID,
    rows_written: 12,
  });
  assertEquals(calls.length, 7);
  assertEquals(sheetAppendCalls(calls).length, 0);
  assert(!calls.some((call) => call.url.hostname.includes("googleapis.com")));
});

Deno.test("active identical export returns EXPORT_IN_PROGRESS without Google access", async () => {
  const { fetchImpl, calls } = successfulExportFetch({
    claimOutcome: "in_progress",
  });

  const response = await handleRequest(request(validPayload()), {
    env,
    fetchImpl,
    signer: injectedSigner,
  });

  assertEquals(response.status, 409);
  assertEquals((await jsonBody(response)).error, {
    code: "EXPORT_IN_PROGRESS",
    message: "An identical export is already in progress.",
  });
  assertEquals(calls.length, 7);
  assertEquals(sheetAppendCalls(calls).length, 0);
});

Deno.test("uncertain identical export blocks Google with EXPORT_OUTCOME_UNCERTAIN", async () => {
  const { fetchImpl, calls } = successfulExportFetch({
    claimOutcome: "outcome_uncertain",
  });

  const response = await handleRequest(request(validPayload()), {
    env,
    fetchImpl,
    signer: injectedSigner,
  });

  assertEquals(response.status, 409);
  assertEquals((await jsonBody(response)).error, {
    code: "EXPORT_OUTCOME_UNCERTAIN",
    message:
      "The previous export outcome is uncertain and cannot be retried safely.",
  });
  assertEquals(calls.length, 7);
  assertEquals(sheetAppendCalls(calls).length, 0);
});

Deno.test("pre-write header mismatch records retryable failure and never appends", async () => {
  const { fetchImpl, calls } = successfulExportFetch({
    header: [[...MATCH_EXPORT_COLUMNS].reverse()],
  });

  const response = await handleRequest(request(validPayload()), {
    env,
    fetchImpl,
    signer: injectedSigner,
  });

  assertEquals(response.status, 409);
  assertEquals((await jsonBody(response)).error, {
    code: "GOOGLE_SHEET_SCHEMA_MISMATCH",
    message: "The Match Results worksheet header is invalid.",
  });
  assertEquals(sheetAppendCalls(calls).length, 0);

  const failureCall = rpcCall(
    calls,
    "mark_export_operation_retryable_failure",
  );
  assertEquals(
    JSON.parse(failureCall.body).p_failure_code,
    "GOOGLE_SHEET_SCHEMA_MISMATCH",
  );
});

Deno.test("ambiguous append confirmation records uncertain state after exactly one append", async () => {
  const { fetchImpl, calls } = successfulExportFetch({
    updatedRows: 11,
  });

  const response = await handleRequest(request(validPayload()), {
    env,
    fetchImpl,
    signer: injectedSigner,
  });

  assertEquals(response.status, 409);
  assertEquals((await jsonBody(response)).error, {
    code: "EXPORT_OUTCOME_UNCERTAIN",
    message:
      "The previous export outcome is uncertain and cannot be retried safely.",
  });
  assertEquals(sheetAppendCalls(calls).length, 1);

  const uncertainCall = rpcCall(
    calls,
    "mark_export_operation_outcome_uncertain",
  );
  assertEquals(
    JSON.parse(uncertainCall.body).p_failure_code,
    "GOOGLE_MATCH_EXPORT_RESPONSE_INVALID",
  );
});

Deno.test("definitive append rate limit records retryable failure without append retry", async () => {
  const { fetchImpl, calls } = successfulExportFetch({
    appendStatus: 429,
  });

  const response = await handleRequest(request(validPayload()), {
    env,
    fetchImpl,
    signer: injectedSigner,
  });

  assertEquals(response.status, 429);
  assertEquals((await jsonBody(response)).error, {
    code: "GOOGLE_API_RATE_LIMITED",
    message: "Google API rate limit exceeded.",
  });
  assertEquals(sheetAppendCalls(calls).length, 1);

  const failureCall = rpcCall(
    calls,
    "mark_export_operation_retryable_failure",
  );
  assertEquals(
    JSON.parse(failureCall.body).p_failure_code,
    "GOOGLE_API_RATE_LIMITED",
  );
});

Deno.test("success-persistence failure becomes uncertain and never appends twice", async () => {
  const { fetchImpl, calls } = successfulExportFetch({
    completeStatus: 500,
  });

  const response = await handleRequest(request(validPayload()), {
    env,
    fetchImpl,
    signer: injectedSigner,
  });

  assertEquals(response.status, 409);
  assertEquals((await jsonBody(response)).error, {
    code: "EXPORT_OUTCOME_UNCERTAIN",
    message:
      "The previous export outcome is uncertain and cannot be retried safely.",
  });
  assertEquals(sheetAppendCalls(calls).length, 1);

  const uncertainCall = rpcCall(
    calls,
    "mark_export_operation_outcome_uncertain",
  );
  assertEquals(
    JSON.parse(uncertainCall.body).p_failure_code,
    "EXPORT_IDEMPOTENCY_FAILURE",
  );
});

Deno.test("failed durable write-start transition prevents Google append", async () => {
  const { fetchImpl, calls } = successfulExportFetch({
    writeStartedStatus: 500,
  });

  const response = await handleRequest(request(validPayload()), {
    env,
    fetchImpl,
    signer: injectedSigner,
  });

  assertEquals(response.status, 502);
  assertEquals((await jsonBody(response)).error, {
    code: "EXPORT_IDEMPOTENCY_FAILURE",
    message: "The export operation state could not be updated safely.",
  });
  assertEquals(sheetAppendCalls(calls).length, 0);
});
