import { EdgeFunctionError } from "./errors.ts";
import { type FetchImplementation, fetchWithTimeout } from "./http.ts";
import type { SupabaseConfig } from "./supabaseAuth.ts";

export const SUPABASE_EXPORT_OPERATION_TIMEOUT_MS = 10_000;

export type ExportOperationType = "export_match" | "export_standings";
export type ExportOperationState =
  | "in_progress"
  | "write_started"
  | "succeeded"
  | "retryable_failure"
  | "outcome_uncertain";
export type ExportClaimOutcome =
  | "claimed"
  | "replayed"
  | "in_progress"
  | "outcome_uncertain";

export interface ExportOperationContext {
  config: SupabaseConfig;
  accessToken: string;
  fetchImpl?: FetchImplementation;
  timeoutMs?: number;
}

export interface ClaimExportOperationInput {
  operationType: ExportOperationType;
  tournamentId: string;
  matchId: string | null;
  payloadFingerprint: string;
}

export interface ExportOperationClaim {
  outcome: ExportClaimOutcome;
  operationId: string;
  leaseToken: string | null;
  state: ExportOperationState;
  attemptCount: number;
  rowsWritten: number | null;
  exportedMatchCount: number | null;
}

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const FINGERPRINT_PATTERN = /^[0-9a-f]{64}$/;
const FAILURE_CODE_PATTERN = /^[A-Z][A-Z0-9_]{0,79}$/;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isInteger(value: unknown): value is number {
  return typeof value === "number" && Number.isInteger(value);
}

function invalidState(): never {
  throw new EdgeFunctionError("EXPORT_IDEMPOTENCY_FAILURE");
}

function rpcUrl(context: ExportOperationContext, functionName: string): URL {
  return new URL(
    `${context.config.url.replace(/\/$/, "")}/rest/v1/rpc/${functionName}`,
  );
}

async function callRpc(
  context: ExportOperationContext,
  functionName: string,
  body: Readonly<Record<string, unknown>>,
): Promise<unknown> {
  const fetchImpl = context.fetchImpl ?? fetch;
  let response: Response;

  try {
    response = await fetchWithTimeout(
      fetchImpl,
      rpcUrl(context, functionName),
      {
        method: "POST",
        headers: {
          Accept: "application/json",
          "Content-Type": "application/json",
          Authorization: `Bearer ${context.accessToken}`,
          apikey: context.config.anonKey,
        },
        body: JSON.stringify(body),
      },
      context.timeoutMs ?? SUPABASE_EXPORT_OPERATION_TIMEOUT_MS,
    );
  } catch {
    throw new EdgeFunctionError("EXPORT_IDEMPOTENCY_FAILURE");
  }

  if (!response.ok) {
    throw new EdgeFunctionError("EXPORT_IDEMPOTENCY_FAILURE");
  }

  try {
    return await response.json();
  } catch {
    throw new EdgeFunctionError("EXPORT_IDEMPOTENCY_FAILURE");
  }
}

function parseClaim(
  value: unknown,
  input: ClaimExportOperationInput,
): ExportOperationClaim {
  if (!Array.isArray(value) || value.length !== 1 || !isRecord(value[0])) {
    invalidState();
  }

  const row = value[0];
  const outcome = row.outcome;
  const operationId = row.operation_id;
  const leaseToken = row.lease_token;
  const state = row.state;
  const attemptCount = row.attempt_count;
  const rowsWritten = row.rows_written;
  const exportedMatchCount = row.exported_match_count;

  if (
    !(
      outcome === "claimed" ||
      outcome === "replayed" ||
      outcome === "in_progress" ||
      outcome === "outcome_uncertain"
    ) ||
    typeof operationId !== "string" ||
    !UUID_PATTERN.test(operationId) ||
    !(
      leaseToken === null ||
      (typeof leaseToken === "string" && UUID_PATTERN.test(leaseToken))
    ) ||
    !(
      state === "in_progress" ||
      state === "write_started" ||
      state === "succeeded" ||
      state === "retryable_failure" ||
      state === "outcome_uncertain"
    ) ||
    !isInteger(attemptCount) ||
    attemptCount < 1 ||
    !(rowsWritten === null || rowsWritten === 12) ||
    !(
      exportedMatchCount === null ||
      (
        isInteger(exportedMatchCount) &&
        exportedMatchCount >= 1 &&
        exportedMatchCount <= 10
      )
    )
  ) {
    invalidState();
  }

  if (
    outcome === "claimed" &&
    !(
      state === "in_progress" &&
      leaseToken !== null &&
      rowsWritten === null &&
      exportedMatchCount === null
    )
  ) {
    invalidState();
  }

  if (
    outcome === "replayed" &&
    !(
      state === "succeeded" &&
      leaseToken === null &&
      rowsWritten === 12 &&
      (
        (
          input.operationType === "export_match" &&
          exportedMatchCount === null
        ) ||
        (
          input.operationType === "export_standings" &&
          exportedMatchCount !== null
        )
      )
    )
  ) {
    invalidState();
  }

  if (
    outcome === "in_progress" &&
    !(
      (state === "in_progress" || state === "write_started") &&
      leaseToken === null &&
      rowsWritten === null &&
      exportedMatchCount === null
    )
  ) {
    invalidState();
  }

  if (
    outcome === "outcome_uncertain" &&
    !(
      state === "outcome_uncertain" &&
      leaseToken === null &&
      rowsWritten === null &&
      exportedMatchCount === null
    )
  ) {
    invalidState();
  }

  return {
    outcome,
    operationId,
    leaseToken,
    state,
    attemptCount,
    rowsWritten,
    exportedMatchCount,
  };
}

async function transition(
  context: ExportOperationContext,
  functionName: string,
  body: Readonly<Record<string, unknown>>,
  expectedResult: string,
): Promise<void> {
  const result = await callRpc(context, functionName, body);

  if (result !== expectedResult) {
    invalidState();
  }
}

function validateOperationIdAndLease(
  operationId: string,
  leaseToken: string,
): void {
  if (
    !UUID_PATTERN.test(operationId) ||
    !UUID_PATTERN.test(leaseToken)
  ) {
    invalidState();
  }
}

function validateFailureCode(failureCode: string): void {
  if (!FAILURE_CODE_PATTERN.test(failureCode)) {
    invalidState();
  }
}

export async function claimExportOperation(
  input: ClaimExportOperationInput,
  context: ExportOperationContext,
): Promise<ExportOperationClaim> {
  if (
    !UUID_PATTERN.test(input.tournamentId) ||
    !FINGERPRINT_PATTERN.test(input.payloadFingerprint) ||
    (
      input.operationType === "export_match" &&
      (input.matchId === null || !UUID_PATTERN.test(input.matchId))
    ) ||
    (
      input.operationType === "export_standings" &&
      input.matchId !== null
    )
  ) {
    invalidState();
  }

  const result = await callRpc(
    context,
    "claim_export_operation",
    {
      p_operation_type: input.operationType,
      p_tournament_id: input.tournamentId,
      p_match_id: input.matchId,
      p_payload_fingerprint: input.payloadFingerprint,
    },
  );

  return parseClaim(result, input);
}

export async function markExportOperationWriteStarted(
  operationId: string,
  leaseToken: string,
  context: ExportOperationContext,
): Promise<void> {
  validateOperationIdAndLease(operationId, leaseToken);

  await transition(
    context,
    "mark_export_operation_write_started",
    {
      p_operation_id: operationId,
      p_lease_token: leaseToken,
    },
    "write_started",
  );
}

export async function completeExportOperationSuccess(
  operationId: string,
  leaseToken: string,
  rowsWritten: number,
  exportedMatchCount: number | null,
  context: ExportOperationContext,
): Promise<void> {
  validateOperationIdAndLease(operationId, leaseToken);

  if (
    rowsWritten !== 12 ||
    !(
      exportedMatchCount === null ||
      (
        Number.isInteger(exportedMatchCount) &&
        exportedMatchCount >= 1 &&
        exportedMatchCount <= 10
      )
    )
  ) {
    invalidState();
  }

  await transition(
    context,
    "complete_export_operation_success",
    {
      p_operation_id: operationId,
      p_lease_token: leaseToken,
      p_rows_written: rowsWritten,
      p_exported_match_count: exportedMatchCount,
    },
    "succeeded",
  );
}

export async function markExportOperationRetryableFailure(
  operationId: string,
  leaseToken: string,
  failureCode: string,
  context: ExportOperationContext,
): Promise<void> {
  validateOperationIdAndLease(operationId, leaseToken);
  validateFailureCode(failureCode);

  await transition(
    context,
    "mark_export_operation_retryable_failure",
    {
      p_operation_id: operationId,
      p_lease_token: leaseToken,
      p_failure_code: failureCode,
    },
    "retryable_failure",
  );
}

export async function markExportOperationOutcomeUncertain(
  operationId: string,
  leaseToken: string,
  failureCode: string,
  context: ExportOperationContext,
): Promise<void> {
  validateOperationIdAndLease(operationId, leaseToken);
  validateFailureCode(failureCode);

  await transition(
    context,
    "mark_export_operation_outcome_uncertain",
    {
      p_operation_id: operationId,
      p_lease_token: leaseToken,
      p_failure_code: failureCode,
    },
    "outcome_uncertain",
  );
}
