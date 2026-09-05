import {
  EdgeFunctionError,
  errorResponse,
  jsonResponse,
} from "../_shared/errors.ts";
import {
  deleteOwnedStorageObjects,
  verifyOwnedStorageEmpty,
} from "../_shared/accountDeletionStorage.ts";
import {
  beginAccountDeletion,
  purgeAccountData,
  readOwnedTournamentIds,
  verifyAccountDatabasePurge,
} from "../_shared/accountDeletionSupabase.ts";
import {
  createGoogleServiceAccountAssertion,
  exchangeGoogleToken,
  type JwtSigner,
  readGoogleConfig,
} from "../_shared/google.ts";
import { deleteTournamentExportRows } from "../_shared/googleAccountDeletion.ts";
import { type FetchImplementation, fetchWithTimeout } from "../_shared/http.ts";
import {
  parseBearerToken,
  readSupabaseConfig,
  readValidatedSupabaseUserId,
} from "../_shared/supabaseAuth.ts";

export const SUPABASE_AUTH_TIMEOUT_MS = 10_000;
export const SUPABASE_ACCOUNT_TIMEOUT_MS = 10_000;
export const GOOGLE_TOKEN_TIMEOUT_MS = 10_000;
export const GOOGLE_SHEETS_TIMEOUT_MS = 10_000;
export const STORAGE_TIMEOUT_MS = 10_000;
export const AUTH_DELETE_TIMEOUT_MS = 10_000;

type EnvironmentReader = (name: string) => string | undefined;

export interface HandlerDependencies {
  env?: EnvironmentReader;
  fetchImpl?: FetchImplementation;
  signer?: JwtSigner;
  timeouts?: {
    supabaseAuth?: number;
    supabaseAccount?: number;
    googleToken?: number;
    googleSheets?: number;
    storage?: number;
    authDelete?: number;
  };
}

interface SupabaseServerConfig {
  url: string;
  serviceRoleKey: string;
}

const readEnvironment: EnvironmentReader = (name) => Deno.env.get(name);
const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "https://hog-gamers-info-hub.github.io",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "authorization, content-type",
  "Vary": "Origin",
  "Cache-Control": "no-store",
  "X-Content-Type-Options": "nosniff",
  "Referrer-Policy": "no-referrer",
};

function readSupabaseServerConfig(
  getEnv: EnvironmentReader,
): SupabaseServerConfig {
  const url = getEnv("SUPABASE_URL");
  const serviceRoleKey = getEnv("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !serviceRoleKey) {
    throw new EdgeFunctionError("INTERNAL_ERROR");
  }
  return { url: url.replace(/\/$/, ""), serviceRoleKey };
}

function withCors(response: Response): Response {
  const headers = new Headers(response.headers);
  for (const [name, value] of Object.entries(CORS_HEADERS)) {
    headers.set(name, value);
  }
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers,
  });
}

async function parseRequestBody(request: Request): Promise<void> {
  let text: string;
  try {
    text = await request.text();
  } catch {
    throw new EdgeFunctionError("INVALID_JSON");
  }

  if (text.trim().length === 0) return;

  let body: unknown;
  try {
    body = JSON.parse(text);
  } catch {
    throw new EdgeFunctionError("INVALID_JSON");
  }

  if (
    typeof body !== "object" ||
    body === null ||
    Array.isArray(body) ||
    Object.keys(body).length !== 0
  ) {
    throw new EdgeFunctionError("INVALID_ACCOUNT_DELETE_REQUEST");
  }
}

function sameTournamentScope(
  first: readonly string[],
  second: readonly string[],
): boolean {
  return first.length === second.length &&
    first.every((tournamentId, index) => tournamentId === second[index]);
}

async function deleteGoogleRows(
  tournamentIds: readonly string[],
  getEnv: EnvironmentReader,
  fetchImpl: FetchImplementation,
  signer: JwtSigner | undefined,
  dependencies: HandlerDependencies,
): Promise<void> {
  if (tournamentIds.length === 0) return;

  try {
    const googleConfig = readGoogleConfig(getEnv);
    const assertion = await createGoogleServiceAccountAssertion(googleConfig, {
      signer,
    });
    const accessToken = await exchangeGoogleToken(assertion, {
      fetchImpl,
      timeoutMs: dependencies.timeouts?.googleToken ?? GOOGLE_TOKEN_TIMEOUT_MS,
    });
    await deleteTournamentExportRows(
      accessToken,
      googleConfig.spreadsheetId,
      tournamentIds,
      {
        fetchImpl,
        timeoutMs: dependencies.timeouts?.googleSheets ??
          GOOGLE_SHEETS_TIMEOUT_MS,
      },
    );
  } catch {
    throw new EdgeFunctionError("GOOGLE_CLEANUP_FAILED");
  }
}

async function hardDeleteAuthUser(
  userId: string,
  config: SupabaseServerConfig,
  fetchImpl: FetchImplementation,
  timeoutMs: number,
): Promise<void> {
  try {
    const response = await fetchWithTimeout(
      fetchImpl,
      `${config.url}/auth/v1/admin/users/${encodeURIComponent(userId)}`,
      {
        method: "DELETE",
        headers: {
          Accept: "application/json",
          Authorization: `Bearer ${config.serviceRoleKey}`,
          apikey: config.serviceRoleKey,
        },
      },
      timeoutMs,
    );

    if (response.ok || response.status === 404) return;
  } catch {
    throw new EdgeFunctionError("ACCOUNT_DELETE_FAILED");
  }

  throw new EdgeFunctionError("ACCOUNT_DELETE_FAILED");
}

export async function handleRequest(
  request: Request,
  dependencies: HandlerDependencies = {},
): Promise<Response> {
  try {
    if (request.method === "OPTIONS") {
      return withCors(new Response(null, { status: 204 }));
    }
    if (request.method !== "POST") {
      throw new EdgeFunctionError("METHOD_NOT_ALLOWED");
    }

    await parseRequestBody(request);

    const accessToken = parseBearerToken(
      request.headers.get("authorization"),
    );
    const getEnv = dependencies.env ?? readEnvironment;
    const publicConfig = readSupabaseConfig(getEnv);
    const fetchImpl = dependencies.fetchImpl ?? fetch;
    const userId = await readValidatedSupabaseUserId(
      accessToken,
      publicConfig,
      {
        fetchImpl,
        timeoutMs: dependencies.timeouts?.supabaseAuth ??
          SUPABASE_AUTH_TIMEOUT_MS,
      },
    );

    if (!UUID_PATTERN.test(userId)) {
      throw new EdgeFunctionError("UNAUTHORIZED");
    }

    const serverConfig = readSupabaseServerConfig(getEnv);
    const supabaseOptions = {
      config: serverConfig,
      fetchImpl,
      timeoutMs: dependencies.timeouts?.supabaseAccount ??
        SUPABASE_ACCOUNT_TIMEOUT_MS,
    };
    const deletionBarrier = await beginAccountDeletion(
      userId,
      supabaseOptions,
    );
    if (deletionBarrier.activeExportOperations > 0) {
      throw new EdgeFunctionError("DATABASE_PURGE_FAILED");
    }

    const tournamentIds = await readOwnedTournamentIds(userId, supabaseOptions);

    await deleteGoogleRows(
      tournamentIds,
      getEnv,
      fetchImpl,
      dependencies.signer,
      dependencies,
    );

    const storageOptions = {
      supabaseUrl: serverConfig.url,
      serviceRoleKey: serverConfig.serviceRoleKey,
      fetchImpl,
      timeoutMs: dependencies.timeouts?.storage ?? STORAGE_TIMEOUT_MS,
    };
    await deleteOwnedStorageObjects(userId, storageOptions);

    const currentTournamentIds = await readOwnedTournamentIds(
      userId,
      supabaseOptions,
    );
    if (!sameTournamentScope(tournamentIds, currentTournamentIds)) {
      throw new EdgeFunctionError("DATABASE_PURGE_FAILED");
    }

    await purgeAccountData(userId, supabaseOptions);
    await verifyAccountDatabasePurge(userId, supabaseOptions);
    await verifyOwnedStorageEmpty(userId, storageOptions);
    await hardDeleteAuthUser(
      userId,
      serverConfig,
      fetchImpl,
      dependencies.timeouts?.authDelete ?? AUTH_DELETE_TIMEOUT_MS,
    );

    return withCors(jsonResponse({ ok: true }));
  } catch (error) {
    return withCors(errorResponse(error));
  }
}

if (import.meta.main) {
  Deno.serve((request) => handleRequest(request));
}
