import { EdgeFunctionError } from "./errors.ts";
import {
  type FetchImplementation,
  fetchWithTimeout,
  UpstreamTimeoutError,
} from "./http.ts";

export interface SupabaseConfig {
  url: string;
  anonKey: string;
}

export function readSupabaseConfig(
  getEnv: (name: string) => string | undefined,
): SupabaseConfig {
  const url = getEnv("SUPABASE_URL");
  const anonKey = getEnv("SUPABASE_ANON_KEY");
  if (!url || !anonKey) throw new EdgeFunctionError("INTERNAL_ERROR");
  return { url: url.replace(/\/$/, ""), anonKey };
}

export function parseBearerToken(value: string | null): string {
  const match = value?.trim().match(/^Bearer\s+([^\s]+)$/i);
  if (!match) throw new EdgeFunctionError("UNAUTHORIZED");
  return match[1];
}

export async function readValidatedSupabaseUserId(
  accessToken: string,
  config: SupabaseConfig,
  options: { fetchImpl?: FetchImplementation; timeoutMs: number },
): Promise<string> {
  const fetchImpl = options.fetchImpl ?? fetch;
  let response: Response;
  try {
    response = await fetchWithTimeout(
      fetchImpl,
      `${config.url}/auth/v1/user`,
      {
        method: "GET",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          apikey: config.anonKey,
        },
      },
      options.timeoutMs,
    );
  } catch (error) {
    if (error instanceof UpstreamTimeoutError) {
      throw new EdgeFunctionError("UPSTREAM_TIMEOUT");
    }
    throw new EdgeFunctionError("SUPABASE_AUTH_FAILURE");
  }

  if (response.status === 401 || response.status === 403) {
    throw new EdgeFunctionError("UNAUTHORIZED");
  }
  if (!response.ok) throw new EdgeFunctionError("SUPABASE_AUTH_FAILURE");

  try {
    const payload = await response.json() as { id?: unknown };
    if (typeof payload.id !== "string" || payload.id.length === 0) {
      throw new EdgeFunctionError("UNAUTHORIZED");
    }
    return payload.id;
  } catch (error) {
    if (error instanceof EdgeFunctionError) throw error;
    throw new EdgeFunctionError("UNAUTHORIZED");
  }
}

export async function validateSupabaseUser(
  accessToken: string,
  config: SupabaseConfig,
  options: { fetchImpl?: FetchImplementation; timeoutMs: number },
): Promise<void> {
  await readValidatedSupabaseUserId(accessToken, config, options);
}
