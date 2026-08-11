import {
  EdgeFunctionError,
  errorResponse,
  jsonResponse,
} from "../_shared/errors.ts";
import {
  type FetchImplementation,
  fetchWithTimeout,
  UpstreamTimeoutError,
} from "../_shared/http.ts";

export const SUPABASE_REGISTRATION_STATUS_TIMEOUT_MS = 10_000;
export const MAX_EMAIL_LENGTH = 320;

type EnvironmentReader = (name: string) => string | undefined;

export interface HandlerDependencies {
  env?: EnvironmentReader;
  fetchImpl?: FetchImplementation;
  timeoutMs?: number;
}

interface SupabaseServerConfig {
  url: string;
  serviceRoleKey: string;
}

const INVALID_REQUEST_MESSAGE =
  "The email registration status request is invalid.";

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function readSupabaseServerConfig(
  getEnv: EnvironmentReader,
): SupabaseServerConfig {
  const url = getEnv("SUPABASE_URL");
  const serviceRoleKey = getEnv("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !serviceRoleKey) {
    throw new EdgeFunctionError("INTERNAL_ERROR");
  }

  return {
    url: url.replace(/\/$/, ""),
    serviceRoleKey,
  };
}

function parseEmail(value: unknown): string | null {
  if (!isRecord(value) || typeof value.email !== "string") {
    return null;
  }

  const normalizedEmail = value.email.trim().toLowerCase();
  if (
    normalizedEmail.length === 0 || normalizedEmail.length > MAX_EMAIL_LENGTH
  ) {
    return null;
  }

  return normalizedEmail;
}

async function readRegistrationStatus(
  email: string,
  config: SupabaseServerConfig,
  fetchImpl: FetchImplementation,
  timeoutMs: number,
): Promise<boolean> {
  let response: Response;
  try {
    response = await fetchWithTimeout(
      fetchImpl,
      `${config.url}/rest/v1/rpc/signup_email_is_registered`,
      {
        method: "POST",
        headers: {
          Accept: "application/json",
          Authorization: `Bearer ${config.serviceRoleKey}`,
          apikey: config.serviceRoleKey,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ p_email: email }),
      },
      timeoutMs,
    );
  } catch (error) {
    if (error instanceof UpstreamTimeoutError) {
      throw new EdgeFunctionError("UPSTREAM_TIMEOUT");
    }
    throw new EdgeFunctionError("SUPABASE_DATA_FAILURE");
  }

  if (!response.ok) {
    throw new EdgeFunctionError("SUPABASE_DATA_FAILURE");
  }

  try {
    const payload: unknown = await response.json();
    if (typeof payload !== "boolean") {
      throw new EdgeFunctionError("SUPABASE_DATA_FAILURE");
    }
    return payload;
  } catch (error) {
    if (error instanceof EdgeFunctionError) {
      throw error;
    }
    throw new EdgeFunctionError("SUPABASE_DATA_FAILURE");
  }
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

    const email = parseEmail(body);
    if (email === null) {
      return jsonResponse({
        ok: false,
        error: {
          code: "INVALID_REGISTRATION_STATUS_PAYLOAD",
          message: INVALID_REQUEST_MESSAGE,
        },
      }, 400);
    }
    const getEnv = dependencies.env ?? ((name: string) => Deno.env.get(name));
    const config = readSupabaseServerConfig(getEnv);
    const registered = await readRegistrationStatus(
      email,
      config,
      dependencies.fetchImpl ?? fetch,
      dependencies.timeoutMs ?? SUPABASE_REGISTRATION_STATUS_TIMEOUT_MS,
    );

    return jsonResponse({ registered });
  } catch (error) {
    return errorResponse(error);
  }
}

if (import.meta.main) {
  Deno.serve((request) => handleRequest(request));
}
