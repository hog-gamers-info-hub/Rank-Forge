import {
  EdgeFunctionError,
  type ErrorCode,
  errorResponse,
  jsonResponse,
} from "../_shared/errors.ts";
import { createExportPayloadFingerprint } from "../_shared/exportFingerprint.ts";
import {
  claimExportOperation,
  completeExportOperationSuccess,
  type ExportOperationContext,
  markExportOperationOutcomeUncertain,
  markExportOperationRetryableFailure,
  markExportOperationWriteStarted,
  resolveExportOperationVerifiedSuccess,
  SUPABASE_EXPORT_OPERATION_TIMEOUT_MS,
} from "../_shared/exportOperationState.ts";
import {
  GOOGLE_EXPORT_VERIFICATION_TIMEOUT_MS,
  reconcileUncertainExport,
  verifyAppendedExportRows,
} from "../_shared/exportVerification.ts";
import {
  createGoogleServiceAccountAssertion,
  exchangeGoogleToken,
  GOOGLE_SHEETS_TIMEOUT_MS,
  GOOGLE_TOKEN_TIMEOUT_MS,
  type JwtSigner,
  readGoogleConfig,
  verifySpreadsheetAccess,
} from "../_shared/google.ts";
import {
  appendMatchResults,
  GOOGLE_APPEND_TIMEOUT_MS,
  GOOGLE_HEADER_TIMEOUT_MS,
  MATCH_RESULTS_WORKSHEET,
  verifyMatchResultsHeader,
} from "../_shared/googleMatchExport.ts";
import {
  appendTournamentStandings,
  GOOGLE_STANDINGS_APPEND_TIMEOUT_MS,
  GOOGLE_STANDINGS_HEADER_TIMEOUT_MS,
  TOURNAMENT_STANDINGS_WORKSHEET,
  verifyTournamentStandingsHeader,
} from "../_shared/googleStandingsExport.ts";
import { type FetchImplementation } from "../_shared/http.ts";
import {
  type MatchExportRequest,
  parseMatchExportRequest,
  toGoogleSheetValues,
} from "../_shared/matchExport.ts";
import { validateOfficialMatchExport } from "../_shared/officialMatchExport.ts";
import { validateOfficialStandingsExport } from "../_shared/officialStandingsExport.ts";
import {
  parseStandingsExportRequest,
  type StandingsExportRequest,
  toStandingsGoogleSheetValues,
} from "../_shared/standingsExport.ts";
import {
  readOfficialMatchResults,
  readOfficialMatchResultsForMatches,
  readOfficialPlayers,
  readOfficialTeamSlots,
  readVisibleMatch,
  readVisibleTournament,
  readVisibleTournamentMatches,
  SUPABASE_DATA_TIMEOUT_MS,
} from "../_shared/supabaseData.ts";
import {
  parseBearerToken,
  readSupabaseConfig,
  validateSupabaseUser,
} from "../_shared/supabaseAuth.ts";

export const SUPABASE_AUTH_TIMEOUT_MS = 10_000;
export const SUPABASE_TOURNAMENT_TIMEOUT_MS = SUPABASE_DATA_TIMEOUT_MS;
export const SUPABASE_MATCH_TIMEOUT_MS = SUPABASE_DATA_TIMEOUT_MS;
export const SUPABASE_MATCH_RESULTS_TIMEOUT_MS = SUPABASE_DATA_TIMEOUT_MS;
export const SUPABASE_TEAM_SLOTS_TIMEOUT_MS = SUPABASE_DATA_TIMEOUT_MS;
export const SUPABASE_PLAYERS_TIMEOUT_MS = SUPABASE_DATA_TIMEOUT_MS;
export const SUPABASE_TOURNAMENT_MATCHES_TIMEOUT_MS = SUPABASE_DATA_TIMEOUT_MS;
export const SUPABASE_STANDINGS_MATCH_RESULTS_TIMEOUT_MS =
  SUPABASE_DATA_TIMEOUT_MS;

export type EnvironmentReader = (name: string) => string | undefined;

export interface HandlerDependencies {
  env?: EnvironmentReader;
  fetchImpl?: FetchImplementation;
  clock?: () => number;
  signer?: JwtSigner;
  timeouts?: {
    supabaseAuth?: number;
    supabaseTournament?: number;
    supabaseMatch?: number;
    supabaseMatchResults?: number;
    supabaseTeamSlots?: number;
    supabasePlayers?: number;
    supabaseTournamentMatches?: number;
    supabaseStandingsMatchResults?: number;
    supabaseExportOperation?: number;
    googleToken?: number;
    googleSheets?: number;
    googleHeader?: number;
    googleAppend?: number;
    googleVerification?: number;
    googleStandingsHeader?: number;
    googleStandingsAppend?: number;
  };
}

type ParsedOperation =
  | { operation: "verify_connection" }
  | MatchExportRequest
  | StandingsExportRequest;

const readEnvironment: EnvironmentReader = (name) => Deno.env.get(name);

const DEFINITIVE_APPEND_FAILURES = new Set([
  "GOOGLE_SHEETS_ACCESS_DENIED",
  "GOOGLE_SHEETS_NOT_FOUND",
  "GOOGLE_API_RATE_LIMITED",
]);

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" &&
    value !== null &&
    !Array.isArray(value);
}

function parseOperation(value: unknown): ParsedOperation {
  if (!isRecord(value)) {
    throw new EdgeFunctionError("INVALID_OPERATION");
  }

  if (value.operation === "verify_connection") {
    if (Object.keys(value).length !== 1) {
      throw new EdgeFunctionError("INVALID_OPERATION");
    }

    return { operation: "verify_connection" };
  }

  if (value.operation === "export_match") {
    return parseMatchExportRequest(value);
  }

  if (value.operation === "export_standings") {
    return parseStandingsExportRequest(value);
  }

  throw new EdgeFunctionError("INVALID_OPERATION");
}

function exportFailureCode(error: unknown): ErrorCode {
  return error instanceof EdgeFunctionError ? error.code : "INTERNAL_ERROR";
}

function exportOperationContext(
  accessToken: string,
  supabaseConfig: ReturnType<typeof readSupabaseConfig>,
  fetchImpl: FetchImplementation,
  dependencies: HandlerDependencies,
): ExportOperationContext {
  return {
    config: supabaseConfig,
    accessToken,
    fetchImpl,
    timeoutMs: dependencies.timeouts?.supabaseExportOperation ??
      SUPABASE_EXPORT_OPERATION_TIMEOUT_MS,
  };
}

async function markRetryableAndRethrow(
  operationId: string,
  leaseToken: string,
  error: unknown,
  context: ExportOperationContext,
): Promise<never> {
  await markExportOperationRetryableFailure(
    operationId,
    leaseToken,
    exportFailureCode(error),
    context,
  );

  throw error;
}

async function markUncertainAndThrow(
  operationId: string,
  leaseToken: string,
  failureCode: string,
  responseCode: ErrorCode,
  context: ExportOperationContext,
): Promise<never> {
  try {
    await markExportOperationOutcomeUncertain(
      operationId,
      leaseToken,
      failureCode,
      context,
    );
  } catch {
    // Once a Google append may have been accepted, never surface an error that
    // encourages an immediate blind retry. The durable write_started lease
    // still blocks competing writes and becomes outcome_uncertain on expiry.
  }

  throw new EdgeFunctionError(responseCode);
}

async function createGoogleAccessToken(
  getEnv: EnvironmentReader,
  fetchImpl: FetchImplementation,
  dependencies: HandlerDependencies,
): Promise<{
  accessToken: string;
  spreadsheetId: string;
}> {
  const googleConfig = readGoogleConfig(getEnv);
  const assertion = await createGoogleServiceAccountAssertion(googleConfig, {
    issuedAtSeconds: dependencies.clock?.() ?? Date.now() / 1000,
    signer: dependencies.signer,
  });
  const googleAccessToken = await exchangeGoogleToken(assertion, {
    fetchImpl,
    timeoutMs: dependencies.timeouts?.googleToken ??
      GOOGLE_TOKEN_TIMEOUT_MS,
  });

  return {
    accessToken: googleAccessToken,
    spreadsheetId: googleConfig.spreadsheetId,
  };
}

async function handleVerifyConnection(
  getEnv: EnvironmentReader,
  fetchImpl: FetchImplementation,
  dependencies: HandlerDependencies,
): Promise<Response> {
  const google = await createGoogleAccessToken(
    getEnv,
    fetchImpl,
    dependencies,
  );

  await verifySpreadsheetAccess(
    google.accessToken,
    google.spreadsheetId,
    {
      fetchImpl,
      timeoutMs: dependencies.timeouts?.googleSheets ??
        GOOGLE_SHEETS_TIMEOUT_MS,
    },
  );

  return jsonResponse({
    ok: true,
    operation: "verify_connection",
    spreadsheet_access: "verified",
  });
}

async function handleExportMatch(
  operation: MatchExportRequest,
  accessToken: string,
  supabaseConfig: ReturnType<typeof readSupabaseConfig>,
  getEnv: EnvironmentReader,
  fetchImpl: FetchImplementation,
  dependencies: HandlerDependencies,
): Promise<Response> {
  const tournament = await readVisibleTournament(
    operation.tournament_id,
    {
      config: supabaseConfig,
      accessToken,
      fetchImpl,
      timeoutMs: dependencies.timeouts?.supabaseTournament ??
        SUPABASE_TOURNAMENT_TIMEOUT_MS,
    },
  );

  const match = await readVisibleMatch(
    operation.match_id,
    {
      config: supabaseConfig,
      accessToken,
      fetchImpl,
      timeoutMs: dependencies.timeouts?.supabaseMatch ??
        SUPABASE_MATCH_TIMEOUT_MS,
    },
  );

  const matchResults = await readOfficialMatchResults(
    operation.match_id,
    {
      config: supabaseConfig,
      accessToken,
      fetchImpl,
      timeoutMs: dependencies.timeouts?.supabaseMatchResults ??
        SUPABASE_MATCH_RESULTS_TIMEOUT_MS,
    },
  );

  const teamSlots = await readOfficialTeamSlots(
    operation.tournament_id,
    {
      config: supabaseConfig,
      accessToken,
      fetchImpl,
      timeoutMs: dependencies.timeouts?.supabaseTeamSlots ??
        SUPABASE_TEAM_SLOTS_TIMEOUT_MS,
    },
  );

  const players = await readOfficialPlayers(
    teamSlots.map((teamSlot) => teamSlot.id),
    {
      config: supabaseConfig,
      accessToken,
      fetchImpl,
      timeoutMs: dependencies.timeouts?.supabasePlayers ??
        SUPABASE_PLAYERS_TIMEOUT_MS,
    },
  );

  validateOfficialMatchExport(operation, {
    tournament,
    match,
    matchResults,
    teamSlots,
    players,
  });

  const matchValues = toGoogleSheetValues(operation);
  const payloadFingerprint = await createExportPayloadFingerprint(operation);
  const stateContext = exportOperationContext(
    accessToken,
    supabaseConfig,
    fetchImpl,
    dependencies,
  );
  const claim = await claimExportOperation(
    {
      operationType: "export_match",
      tournamentId: operation.tournament_id,
      matchId: operation.match_id,
      payloadFingerprint,
    },
    stateContext,
  );

  if (claim.outcome === "replayed") {
    return jsonResponse({
      ok: true,
      operation: "export_match",
      tournament_id: operation.tournament_id,
      match_id: operation.match_id,
      rows_written: claim.rowsWritten,
    });
  }

  if (claim.outcome === "in_progress") {
    throw new EdgeFunctionError("EXPORT_IN_PROGRESS");
  }

  if (claim.outcome === "outcome_uncertain") {
    const google = await createGoogleAccessToken(
      getEnv,
      fetchImpl,
      dependencies,
    );

    await verifyMatchResultsHeader(
      google.accessToken,
      google.spreadsheetId,
      {
        fetchImpl,
        timeoutMs: dependencies.timeouts?.googleHeader ??
          GOOGLE_HEADER_TIMEOUT_MS,
      },
    );

    const reconciliation = await reconcileUncertainExport(
      google.accessToken,
      google.spreadsheetId,
      {
        worksheetName: MATCH_RESULTS_WORKSHEET,
        candidateIndexes: [0, 1, 2, 4],
      },
      matchValues,
      {
        fetchImpl,
        timeoutMs: dependencies.timeouts?.googleVerification ??
          GOOGLE_EXPORT_VERIFICATION_TIMEOUT_MS,
      },
    );

    if (reconciliation === "verified_success") {
      await resolveExportOperationVerifiedSuccess(
        claim.operationId,
        null,
        stateContext,
      );

      return jsonResponse({
        ok: true,
        operation: "export_match",
        tournament_id: operation.tournament_id,
        match_id: operation.match_id,
        rows_written: 12,
      });
    }

    throw new EdgeFunctionError("EXPORT_VERIFICATION_NOT_FOUND");
  }

  if (claim.leaseToken === null) {
    throw new EdgeFunctionError("EXPORT_IDEMPOTENCY_FAILURE");
  }

  const operationId = claim.operationId;
  const leaseToken = claim.leaseToken;

  let google: {
    accessToken: string;
    spreadsheetId: string;
  };

  try {
    google = await createGoogleAccessToken(
      getEnv,
      fetchImpl,
      dependencies,
    );

    await verifyMatchResultsHeader(
      google.accessToken,
      google.spreadsheetId,
      {
        fetchImpl,
        timeoutMs: dependencies.timeouts?.googleHeader ??
          GOOGLE_HEADER_TIMEOUT_MS,
      },
    );
  } catch (error) {
    return await markRetryableAndRethrow(
      operationId,
      leaseToken,
      error,
      stateContext,
    );
  }

  await markExportOperationWriteStarted(
    operationId,
    leaseToken,
    stateContext,
  );

  let appendResult: {
    rowsWritten: 12;
    updatedRange: string;
  };

  try {
    appendResult = await appendMatchResults(
      google.accessToken,
      google.spreadsheetId,
      matchValues,
      {
        fetchImpl,
        timeoutMs: dependencies.timeouts?.googleAppend ??
          GOOGLE_APPEND_TIMEOUT_MS,
      },
    );
  } catch (error) {
    if (
      error instanceof EdgeFunctionError &&
      DEFINITIVE_APPEND_FAILURES.has(error.code)
    ) {
      return await markRetryableAndRethrow(
        operationId,
        leaseToken,
        error,
        stateContext,
      );
    }

    return await markUncertainAndThrow(
      operationId,
      leaseToken,
      exportFailureCode(error),
      error instanceof EdgeFunctionError &&
        error.code === "GOOGLE_MATCH_EXPORT_RESPONSE_INVALID"
        ? "EXPORT_VERIFICATION_FAILURE"
        : "EXPORT_OUTCOME_UNCERTAIN",
      stateContext,
    );
  }

  try {
    await verifyAppendedExportRows(
      google.accessToken,
      google.spreadsheetId,
      appendResult.updatedRange,
      matchValues,
      {
        fetchImpl,
        timeoutMs: dependencies.timeouts?.googleVerification ??
          GOOGLE_EXPORT_VERIFICATION_TIMEOUT_MS,
      },
    );
  } catch (error) {
    const failureCode = error instanceof EdgeFunctionError
      ? error.code
      : "EXPORT_VERIFICATION_FAILURE";

    return await markUncertainAndThrow(
      operationId,
      leaseToken,
      failureCode,
      failureCode,
      stateContext,
    );
  }

  try {
    await completeExportOperationSuccess(
      operationId,
      leaseToken,
      appendResult.rowsWritten,
      null,
      stateContext,
    );
  } catch {
    return await markUncertainAndThrow(
      operationId,
      leaseToken,
      "EXPORT_IDEMPOTENCY_FAILURE",
      "EXPORT_OUTCOME_UNCERTAIN",
      stateContext,
    );
  }

  return jsonResponse({
    ok: true,
    operation: "export_match",
    tournament_id: operation.tournament_id,
    match_id: operation.match_id,
    rows_written: appendResult.rowsWritten,
  });
}

async function handleExportStandings(
  operation: StandingsExportRequest,
  accessToken: string,
  supabaseConfig: ReturnType<typeof readSupabaseConfig>,
  getEnv: EnvironmentReader,
  fetchImpl: FetchImplementation,
  dependencies: HandlerDependencies,
): Promise<Response> {
  const tournament = await readVisibleTournament(
    operation.tournament_id,
    {
      config: supabaseConfig,
      accessToken,
      fetchImpl,
      timeoutMs: dependencies.timeouts?.supabaseTournament ??
        SUPABASE_TOURNAMENT_TIMEOUT_MS,
    },
  );

  const matches = await readVisibleTournamentMatches(
    operation.tournament_id,
    {
      config: supabaseConfig,
      accessToken,
      fetchImpl,
      timeoutMs: dependencies.timeouts?.supabaseTournamentMatches ??
        SUPABASE_TOURNAMENT_MATCHES_TIMEOUT_MS,
    },
  );

  const finalizedMatchIds = matches
    .filter((match) => match.status === "finalized")
    .map((match) => match.id);

  if (finalizedMatchIds.length === 0) {
    throw new EdgeFunctionError("NO_FINALIZED_MATCHES");
  }

  const matchResults = await readOfficialMatchResultsForMatches(
    finalizedMatchIds,
    {
      config: supabaseConfig,
      accessToken,
      fetchImpl,
      timeoutMs: dependencies.timeouts?.supabaseStandingsMatchResults ??
        SUPABASE_STANDINGS_MATCH_RESULTS_TIMEOUT_MS,
    },
  );

  const teamSlots = await readOfficialTeamSlots(
    operation.tournament_id,
    {
      config: supabaseConfig,
      accessToken,
      fetchImpl,
      timeoutMs: dependencies.timeouts?.supabaseTeamSlots ??
        SUPABASE_TEAM_SLOTS_TIMEOUT_MS,
    },
  );

  const players = await readOfficialPlayers(
    teamSlots.map((teamSlot) => teamSlot.id),
    {
      config: supabaseConfig,
      accessToken,
      fetchImpl,
      timeoutMs: dependencies.timeouts?.supabasePlayers ??
        SUPABASE_PLAYERS_TIMEOUT_MS,
    },
  );

  const official = validateOfficialStandingsExport(operation, {
    tournament,
    matches,
    matchResults,
    teamSlots,
    players,
  });

  const standingsValues = toStandingsGoogleSheetValues(operation);
  const payloadFingerprint = await createExportPayloadFingerprint(operation);
  const stateContext = exportOperationContext(
    accessToken,
    supabaseConfig,
    fetchImpl,
    dependencies,
  );
  const claim = await claimExportOperation(
    {
      operationType: "export_standings",
      tournamentId: operation.tournament_id,
      matchId: null,
      payloadFingerprint,
    },
    stateContext,
  );

  if (claim.outcome === "replayed") {
    if (claim.exportedMatchCount === null) {
      throw new EdgeFunctionError("EXPORT_IDEMPOTENCY_FAILURE");
    }

    return jsonResponse({
      ok: true,
      operation: "export_standings",
      tournament_id: operation.tournament_id,
      exported_match_count: claim.exportedMatchCount,
      rows_written: claim.rowsWritten,
    });
  }

  if (claim.outcome === "in_progress") {
    throw new EdgeFunctionError("EXPORT_IN_PROGRESS");
  }

  if (claim.outcome === "outcome_uncertain") {
    const google = await createGoogleAccessToken(
      getEnv,
      fetchImpl,
      dependencies,
    );

    await verifyTournamentStandingsHeader(
      google.accessToken,
      google.spreadsheetId,
      {
        fetchImpl,
        timeoutMs: dependencies.timeouts?.googleStandingsHeader ??
          GOOGLE_STANDINGS_HEADER_TIMEOUT_MS,
      },
    );

    const reconciliation = await reconcileUncertainExport(
      google.accessToken,
      google.spreadsheetId,
      {
        worksheetName: TOURNAMENT_STANDINGS_WORKSHEET,
        candidateIndexes: [0, 1, 2, 4],
      },
      standingsValues,
      {
        fetchImpl,
        timeoutMs: dependencies.timeouts?.googleVerification ??
          GOOGLE_EXPORT_VERIFICATION_TIMEOUT_MS,
      },
    );

    if (reconciliation === "verified_success") {
      await resolveExportOperationVerifiedSuccess(
        claim.operationId,
        official.exportedMatchCount,
        stateContext,
      );

      return jsonResponse({
        ok: true,
        operation: "export_standings",
        tournament_id: operation.tournament_id,
        exported_match_count: official.exportedMatchCount,
        rows_written: 12,
      });
    }

    throw new EdgeFunctionError("EXPORT_VERIFICATION_NOT_FOUND");
  }

  if (claim.leaseToken === null) {
    throw new EdgeFunctionError("EXPORT_IDEMPOTENCY_FAILURE");
  }

  const operationId = claim.operationId;
  const leaseToken = claim.leaseToken;

  let google: {
    accessToken: string;
    spreadsheetId: string;
  };

  try {
    google = await createGoogleAccessToken(
      getEnv,
      fetchImpl,
      dependencies,
    );

    await verifyTournamentStandingsHeader(
      google.accessToken,
      google.spreadsheetId,
      {
        fetchImpl,
        timeoutMs: dependencies.timeouts?.googleStandingsHeader ??
          GOOGLE_STANDINGS_HEADER_TIMEOUT_MS,
      },
    );
  } catch (error) {
    return await markRetryableAndRethrow(
      operationId,
      leaseToken,
      error,
      stateContext,
    );
  }

  await markExportOperationWriteStarted(
    operationId,
    leaseToken,
    stateContext,
  );

  let appendResult: {
    rowsWritten: 12;
    updatedRange: string;
  };

  try {
    appendResult = await appendTournamentStandings(
      google.accessToken,
      google.spreadsheetId,
      standingsValues,
      {
        fetchImpl,
        timeoutMs: dependencies.timeouts?.googleStandingsAppend ??
          GOOGLE_STANDINGS_APPEND_TIMEOUT_MS,
      },
    );
  } catch (error) {
    if (
      error instanceof EdgeFunctionError &&
      DEFINITIVE_APPEND_FAILURES.has(error.code)
    ) {
      return await markRetryableAndRethrow(
        operationId,
        leaseToken,
        error,
        stateContext,
      );
    }

    return await markUncertainAndThrow(
      operationId,
      leaseToken,
      exportFailureCode(error),
      error instanceof EdgeFunctionError &&
        error.code === "GOOGLE_STANDINGS_EXPORT_RESPONSE_INVALID"
        ? "EXPORT_VERIFICATION_FAILURE"
        : "EXPORT_OUTCOME_UNCERTAIN",
      stateContext,
    );
  }

  try {
    await verifyAppendedExportRows(
      google.accessToken,
      google.spreadsheetId,
      appendResult.updatedRange,
      standingsValues,
      {
        fetchImpl,
        timeoutMs: dependencies.timeouts?.googleVerification ??
          GOOGLE_EXPORT_VERIFICATION_TIMEOUT_MS,
      },
    );
  } catch (error) {
    const failureCode = error instanceof EdgeFunctionError
      ? error.code
      : "EXPORT_VERIFICATION_FAILURE";

    return await markUncertainAndThrow(
      operationId,
      leaseToken,
      failureCode,
      failureCode,
      stateContext,
    );
  }

  try {
    await completeExportOperationSuccess(
      operationId,
      leaseToken,
      appendResult.rowsWritten,
      official.exportedMatchCount,
      stateContext,
    );
  } catch {
    return await markUncertainAndThrow(
      operationId,
      leaseToken,
      "EXPORT_IDEMPOTENCY_FAILURE",
      "EXPORT_OUTCOME_UNCERTAIN",
      stateContext,
    );
  }

  return jsonResponse({
    ok: true,
    operation: "export_standings",
    tournament_id: operation.tournament_id,
    exported_match_count: official.exportedMatchCount,
    rows_written: appendResult.rowsWritten,
  });
}

export async function handleRequest(
  request: Request,
  dependencies: HandlerDependencies = {},
): Promise<Response> {
  try {
    if (request.method !== "POST") {
      throw new EdgeFunctionError("METHOD_NOT_ALLOWED");
    }

    let body: unknown;

    try {
      body = await request.json();
    } catch {
      throw new EdgeFunctionError("INVALID_JSON");
    }

    const operation = parseOperation(body);
    const accessToken = parseBearerToken(
      request.headers.get("authorization"),
    );
    const getEnv = dependencies.env ?? readEnvironment;
    const supabaseConfig = readSupabaseConfig(getEnv);
    const fetchImpl = dependencies.fetchImpl ?? fetch;

    await validateSupabaseUser(accessToken, supabaseConfig, {
      fetchImpl,
      timeoutMs: dependencies.timeouts?.supabaseAuth ??
        SUPABASE_AUTH_TIMEOUT_MS,
    });

    if (operation.operation === "verify_connection") {
      return await handleVerifyConnection(
        getEnv,
        fetchImpl,
        dependencies,
      );
    }

    if (operation.operation === "export_match") {
      return await handleExportMatch(
        operation,
        accessToken,
        supabaseConfig,
        getEnv,
        fetchImpl,
        dependencies,
      );
    }

    return await handleExportStandings(
      operation,
      accessToken,
      supabaseConfig,
      getEnv,
      fetchImpl,
      dependencies,
    );
  } catch (error) {
    return errorResponse(error);
  }
}

if (import.meta.main) {
  Deno.serve((request) => handleRequest(request));
}
