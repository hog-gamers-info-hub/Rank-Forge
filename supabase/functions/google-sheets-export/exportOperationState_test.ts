import { EdgeFunctionError } from "../_shared/errors.ts";
import {
  claimExportOperation,
  completeExportOperationSuccess,
  type ExportOperationContext,
  markExportOperationOutcomeUncertain,
  markExportOperationRetryableFailure,
  markExportOperationWriteStarted,
} from "../_shared/exportOperationState.ts";
import type { FetchImplementation } from "../_shared/http.ts";

const TOURNAMENT_ID = "11111111-1111-4111-8111-111111111111";
const MATCH_ID = "22222222-2222-4222-8222-222222222222";
const OPERATION_ID = "33333333-3333-4333-8333-333333333333";
const LEASE_TOKEN = "44444444-4444-4444-8444-444444444444";
const FINGERPRINT = "a".repeat(64);

interface Call {
  url: URL;
  method: string;
  headers: Headers;
  body: unknown;
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

  const fetchImpl: FetchImplementation = async (input, init) => {
    let body: unknown = null;

    if (typeof init?.body === "string") {
      body = JSON.parse(init.body);
    }

    const call: Call = {
      url: new URL(String(input)),
      method: init?.method ?? "GET",
      headers: new Headers(init?.headers),
      body,
    };

    calls.push(call);
    return await responder(call, calls.length - 1);
  };

  return { fetchImpl, calls };
}

function context(fetchImpl: FetchImplementation): ExportOperationContext {
  return {
    config: {
      url: "https://example.supabase.co",
      anonKey: "anon-key",
    },
    accessToken: "caller-token",
    fetchImpl,
    timeoutMs: 50,
  };
}

function claimRow(
  overrides: Record<string, unknown> = {},
): Record<string, unknown> {
  return {
    outcome: "claimed",
    operation_id: OPERATION_ID,
    lease_token: LEASE_TOKEN,
    state: "in_progress",
    attempt_count: 1,
    rows_written: null,
    exported_match_count: null,
    ...overrides,
  };
}

Deno.test("claim uses caller token, anon key, exact RPC, and exact parameters", async () => {
  const { fetchImpl, calls } = makeFetch(() => responseJson([claimRow()]));

  const result = await claimExportOperation(
    {
      operationType: "export_match",
      tournamentId: TOURNAMENT_ID,
      matchId: MATCH_ID,
      payloadFingerprint: FINGERPRINT,
    },
    context(fetchImpl),
  );

  assertEquals(result.outcome, "claimed");
  assertEquals(result.operationId, OPERATION_ID);
  assertEquals(result.leaseToken, LEASE_TOKEN);
  assertEquals(calls.length, 1);
  assertEquals(
    calls[0].url.toString(),
    "https://example.supabase.co/rest/v1/rpc/claim_export_operation",
  );
  assertEquals(calls[0].method, "POST");
  assertEquals(calls[0].headers.get("authorization"), "Bearer caller-token");
  assertEquals(calls[0].headers.get("apikey"), "anon-key");
  assertEquals(calls[0].headers.get("content-type"), "application/json");
  assertEquals(calls[0].body, {
    p_operation_type: "export_match",
    p_tournament_id: TOURNAMENT_ID,
    p_match_id: MATCH_ID,
    p_payload_fingerprint: FINGERPRINT,
  });
});

Deno.test("standings claim sends null match id", async () => {
  const { fetchImpl, calls } = makeFetch(() => responseJson([claimRow()]));

  await claimExportOperation(
    {
      operationType: "export_standings",
      tournamentId: TOURNAMENT_ID,
      matchId: null,
      payloadFingerprint: FINGERPRINT,
    },
    context(fetchImpl),
  );

  assertEquals(calls[0].body, {
    p_operation_type: "export_standings",
    p_tournament_id: TOURNAMENT_ID,
    p_match_id: null,
    p_payload_fingerprint: FINGERPRINT,
  });
});

Deno.test("successful match replay parses persisted replay metadata", async () => {
  const { fetchImpl } = makeFetch(() =>
    responseJson([
      claimRow({
        outcome: "replayed",
        lease_token: null,
        state: "succeeded",
        rows_written: 12,
      }),
    ])
  );

  const result = await claimExportOperation(
    {
      operationType: "export_match",
      tournamentId: TOURNAMENT_ID,
      matchId: MATCH_ID,
      payloadFingerprint: FINGERPRINT,
    },
    context(fetchImpl),
  );

  assertEquals(result.outcome, "replayed");
  assertEquals(result.rowsWritten, 12);
  assertEquals(result.exportedMatchCount, null);
});

Deno.test("successful standings replay requires exported match count", async () => {
  const { fetchImpl } = makeFetch(() =>
    responseJson([
      claimRow({
        outcome: "replayed",
        lease_token: null,
        state: "succeeded",
        rows_written: 12,
        exported_match_count: 3,
      }),
    ])
  );

  const result = await claimExportOperation(
    {
      operationType: "export_standings",
      tournamentId: TOURNAMENT_ID,
      matchId: null,
      payloadFingerprint: FINGERPRINT,
    },
    context(fetchImpl),
  );

  assertEquals(result.outcome, "replayed");
  assertEquals(result.exportedMatchCount, 3);
});

Deno.test("active duplicate claim parses without exposing another lease", async () => {
  const { fetchImpl } = makeFetch(() =>
    responseJson([
      claimRow({
        outcome: "in_progress",
        lease_token: null,
        state: "write_started",
      }),
    ])
  );

  const result = await claimExportOperation(
    {
      operationType: "export_match",
      tournamentId: TOURNAMENT_ID,
      matchId: MATCH_ID,
      payloadFingerprint: FINGERPRINT,
    },
    context(fetchImpl),
  );

  assertEquals(result.outcome, "in_progress");
  assertEquals(result.leaseToken, null);
});

Deno.test("uncertain claim parses as terminal no-lease outcome", async () => {
  const { fetchImpl } = makeFetch(() =>
    responseJson([
      claimRow({
        outcome: "outcome_uncertain",
        lease_token: null,
        state: "outcome_uncertain",
      }),
    ])
  );

  const result = await claimExportOperation(
    {
      operationType: "export_match",
      tournamentId: TOURNAMENT_ID,
      matchId: MATCH_ID,
      payloadFingerprint: FINGERPRINT,
    },
    context(fetchImpl),
  );

  assertEquals(result.outcome, "outcome_uncertain");
  assertEquals(result.leaseToken, null);
});

Deno.test("malformed claim response fails closed", async () => {
  const { fetchImpl } = makeFetch(() =>
    responseJson([
      claimRow({
        outcome: "claimed",
        lease_token: null,
      }),
    ])
  );

  await assertRejects(
    () =>
      claimExportOperation(
        {
          operationType: "export_match",
          tournamentId: TOURNAMENT_ID,
          matchId: MATCH_ID,
          payloadFingerprint: FINGERPRINT,
        },
        context(fetchImpl),
      ),
    "EXPORT_IDEMPOTENCY_FAILURE",
    502,
  );
});

Deno.test("non-success RPC response maps safely", async () => {
  const { fetchImpl } = makeFetch(() =>
    responseJson({ message: "database detail must not escape" }, 500)
  );

  await assertRejects(
    () =>
      claimExportOperation(
        {
          operationType: "export_match",
          tournamentId: TOURNAMENT_ID,
          matchId: MATCH_ID,
          payloadFingerprint: FINGERPRINT,
        },
        context(fetchImpl),
      ),
    "EXPORT_IDEMPOTENCY_FAILURE",
    502,
  );
});

Deno.test("RPC timeout maps safely", async () => {
  const fetchImpl: FetchImplementation = (_input, init) =>
    new Promise((_resolve, reject) => {
      init?.signal?.addEventListener("abort", () => {
        reject(new DOMException("aborted", "AbortError"));
      });
    });

  await assertRejects(
    () =>
      claimExportOperation(
        {
          operationType: "export_match",
          tournamentId: TOURNAMENT_ID,
          matchId: MATCH_ID,
          payloadFingerprint: FINGERPRINT,
        },
        {
          ...context(fetchImpl),
          timeoutMs: 1,
        },
      ),
    "EXPORT_IDEMPOTENCY_FAILURE",
    502,
  );
});

Deno.test("write-start transition sends exact RPC body", async () => {
  const { fetchImpl, calls } = makeFetch(() => responseJson("write_started"));

  await markExportOperationWriteStarted(
    OPERATION_ID,
    LEASE_TOKEN,
    context(fetchImpl),
  );

  assertEquals(
    calls[0].url.pathname,
    "/rest/v1/rpc/mark_export_operation_write_started",
  );
  assertEquals(calls[0].body, {
    p_operation_id: OPERATION_ID,
    p_lease_token: LEASE_TOKEN,
  });
});

Deno.test("success transition sends rows and optional standings count", async () => {
  const { fetchImpl, calls } = makeFetch(() => responseJson("succeeded"));

  await completeExportOperationSuccess(
    OPERATION_ID,
    LEASE_TOKEN,
    12,
    4,
    context(fetchImpl),
  );

  assertEquals(
    calls[0].url.pathname,
    "/rest/v1/rpc/complete_export_operation_success",
  );
  assertEquals(calls[0].body, {
    p_operation_id: OPERATION_ID,
    p_lease_token: LEASE_TOKEN,
    p_rows_written: 12,
    p_exported_match_count: 4,
  });
});

Deno.test("retryable-failure transition sends only safe failure code metadata", async () => {
  const { fetchImpl, calls } = makeFetch(() =>
    responseJson("retryable_failure")
  );

  await markExportOperationRetryableFailure(
    OPERATION_ID,
    LEASE_TOKEN,
    "GOOGLE_API_RATE_LIMITED",
    context(fetchImpl),
  );

  assertEquals(
    calls[0].url.pathname,
    "/rest/v1/rpc/mark_export_operation_retryable_failure",
  );
  assertEquals(calls[0].body, {
    p_operation_id: OPERATION_ID,
    p_lease_token: LEASE_TOKEN,
    p_failure_code: "GOOGLE_API_RATE_LIMITED",
  });
});

Deno.test("uncertain transition sends only safe failure code metadata", async () => {
  const { fetchImpl, calls } = makeFetch(() =>
    responseJson("outcome_uncertain")
  );

  await markExportOperationOutcomeUncertain(
    OPERATION_ID,
    LEASE_TOKEN,
    "GOOGLE_MATCH_EXPORT_FAILURE",
    context(fetchImpl),
  );

  assertEquals(
    calls[0].url.pathname,
    "/rest/v1/rpc/mark_export_operation_outcome_uncertain",
  );
  assertEquals(calls[0].body, {
    p_operation_id: OPERATION_ID,
    p_lease_token: LEASE_TOKEN,
    p_failure_code: "GOOGLE_MATCH_EXPORT_FAILURE",
  });
});

Deno.test("unexpected transition result fails closed", async () => {
  const { fetchImpl } = makeFetch(() => responseJson("wrong_state"));

  await assertRejects(
    () =>
      markExportOperationWriteStarted(
        OPERATION_ID,
        LEASE_TOKEN,
        context(fetchImpl),
      ),
    "EXPORT_IDEMPOTENCY_FAILURE",
    502,
  );
});

Deno.test("invalid identifiers and fingerprint are rejected before network", async () => {
  const { fetchImpl, calls } = makeFetch(() => responseJson([claimRow()]));

  await assertRejects(
    () =>
      claimExportOperation(
        {
          operationType: "export_match",
          tournamentId: TOURNAMENT_ID,
          matchId: MATCH_ID,
          payloadFingerprint: "INVALID",
        },
        context(fetchImpl),
      ),
    "EXPORT_IDEMPOTENCY_FAILURE",
    502,
  );

  assertEquals(calls.length, 0);
});

Deno.test("invalid failure code is rejected before network", async () => {
  const { fetchImpl, calls } = makeFetch(() =>
    responseJson("retryable_failure")
  );

  await assertRejects(
    () =>
      markExportOperationRetryableFailure(
        OPERATION_ID,
        LEASE_TOKEN,
        "raw upstream message",
        context(fetchImpl),
      ),
    "EXPORT_IDEMPOTENCY_FAILURE",
    502,
  );

  assertEquals(calls.length, 0);
});
