import {
  EdgeFunctionError,
  errorResponse,
  jsonResponse,
} from "../_shared/errors.ts";
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
  verifyMatchResultsHeader,
} from "../_shared/googleMatchExport.ts";
import {
  appendTournamentStandings,
  GOOGLE_STANDINGS_APPEND_TIMEOUT_MS,
  GOOGLE_STANDINGS_HEADER_TIMEOUT_MS,
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
    googleToken?: number;
    googleSheets?: number;
    googleHeader?: number;
    googleAppend?: number;
    googleStandingsHeader?: number;
    googleStandingsAppend?: number;
  };
}

type ParsedOperation =
  | { operation: "verify_connection" }
  | MatchExportRequest
  | StandingsExportRequest;

const readEnvironment: EnvironmentReader = (name) => Deno.env.get(name);

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

  const rowsWritten = await appendMatchResults(
    google.accessToken,
    google.spreadsheetId,
    toGoogleSheetValues(operation),
    {
      fetchImpl,
      timeoutMs: dependencies.timeouts?.googleAppend ??
        GOOGLE_APPEND_TIMEOUT_MS,
    },
  );

  return jsonResponse({
    ok: true,
    operation: "export_match",
    tournament_id: operation.tournament_id,
    match_id: operation.match_id,
    rows_written: rowsWritten,
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

  const rowsWritten = await appendTournamentStandings(
    google.accessToken,
    google.spreadsheetId,
    toStandingsGoogleSheetValues(operation),
    {
      fetchImpl,
      timeoutMs: dependencies.timeouts?.googleStandingsAppend ??
        GOOGLE_STANDINGS_APPEND_TIMEOUT_MS,
    },
  );

  return jsonResponse({
    ok: true,
    operation: "export_standings",
    tournament_id: operation.tournament_id,
    exported_match_count: official.exportedMatchCount,
    rows_written: rowsWritten,
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
