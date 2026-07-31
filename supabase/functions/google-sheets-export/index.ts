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
import { type FetchImplementation } from "../_shared/http.ts";
import {
  parseBearerToken,
  readSupabaseConfig,
  validateSupabaseUser,
} from "../_shared/supabaseAuth.ts";

export const SUPABASE_AUTH_TIMEOUT_MS = 10_000;
export type EnvironmentReader = (name: string) => string | undefined;

export interface HandlerDependencies {
  env?: EnvironmentReader;
  fetchImpl?: FetchImplementation;
  clock?: () => number;
  signer?: JwtSigner;
  timeouts?: {
    supabaseAuth?: number;
    googleToken?: number;
    googleSheets?: number;
  };
}

const readEnvironment: EnvironmentReader = (name) => Deno.env.get(name);

function isVerifyConnectionRequest(value: unknown): boolean {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    return false;
  }
  return Object.keys(value).length === 1 &&
    (value as { operation?: unknown }).operation === "verify_connection";
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
    if (!isVerifyConnectionRequest(body)) {
      throw new EdgeFunctionError("INVALID_OPERATION");
    }

    const accessToken = parseBearerToken(request.headers.get("authorization"));
    const getEnv = dependencies.env ?? readEnvironment;
    const supabaseConfig = readSupabaseConfig(getEnv);
    const fetchImpl = dependencies.fetchImpl ?? fetch;
    const timeouts = dependencies.timeouts ?? {};
    await validateSupabaseUser(accessToken, supabaseConfig, {
      fetchImpl,
      timeoutMs: timeouts.supabaseAuth ?? SUPABASE_AUTH_TIMEOUT_MS,
    });

    const googleConfig = readGoogleConfig(getEnv);
    const assertion = await createGoogleServiceAccountAssertion(googleConfig, {
      issuedAtSeconds: dependencies.clock?.() ?? Date.now() / 1000,
      signer: dependencies.signer,
    });
    const googleAccessToken = await exchangeGoogleToken(assertion, {
      fetchImpl,
      timeoutMs: timeouts.googleToken ?? GOOGLE_TOKEN_TIMEOUT_MS,
    });
    await verifySpreadsheetAccess(
      googleAccessToken,
      googleConfig.spreadsheetId,
      {
        fetchImpl,
        timeoutMs: timeouts.googleSheets ?? GOOGLE_SHEETS_TIMEOUT_MS,
      },
    );
    return jsonResponse({
      ok: true,
      operation: "verify_connection",
      spreadsheet_access: "verified",
    });
  } catch (error) {
    return errorResponse(error);
  }
}

if (import.meta.main) Deno.serve((request) => handleRequest(request));
