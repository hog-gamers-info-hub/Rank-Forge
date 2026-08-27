import {
  type FetchImplementation,
  fetchWithTimeout,
  UpstreamTimeoutError,
} from "../_shared/http.ts";

export const SUPABASE_STANDINGS_TIMEOUT_MS = 10_000;

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

interface PublishedStandingRow {
  displayOrder: number;
  teamSlotNumber: number;
  teamName: string | null;
  totalPoints: number;
  totalPositionPoints: number;
  totalKillPoints: number;
  firstPlaceFinishes: number;
  latestMatchPlacement: number | null;
  matchesIncluded: number;
  isCompleteTie: boolean;
}

const CORS_ORIGIN = "https://hog-gamers-info-hub.github.io";
const COMMON_HEADERS = {
  "Access-Control-Allow-Origin": CORS_ORIGIN,
  "Access-Control-Allow-Methods": "GET, OPTIONS",
  "Vary": "Origin",
  "Cache-Control": "no-store",
  "X-Content-Type-Options": "nosniff",
  "Referrer-Policy": "no-referrer",
};
const JSON_HEADERS = {
  ...COMMON_HEADERS,
  "Content-Type": "application/json; charset=utf-8",
};

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function readSupabaseServerConfig(
  getEnv: EnvironmentReader,
): SupabaseServerConfig {
  const url = getEnv("SUPABASE_URL");
  const serviceRoleKey = getEnv("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !serviceRoleKey) {
    throw new Error("Supabase server configuration is incomplete.");
  }

  return {
    url: url.replace(/\/$/, ""),
    serviceRoleKey,
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isInteger(value: unknown): value is number {
  return typeof value === "number" && Number.isInteger(value);
}

function parseStandingRow(value: unknown): PublishedStandingRow | null {
  if (!isRecord(value)) return null;

  const teamName = value.teamName;
  const latestMatchPlacement = value.latestMatchPlacement;
  if (
    !isInteger(value.displayOrder) ||
    !isInteger(value.teamSlotNumber) ||
    !(typeof teamName === "string" || teamName === null) ||
    !isInteger(value.totalPoints) ||
    !isInteger(value.totalPositionPoints) ||
    !isInteger(value.totalKillPoints) ||
    !isInteger(value.firstPlaceFinishes) ||
    !(isInteger(latestMatchPlacement) || latestMatchPlacement === null) ||
    !isInteger(value.matchesIncluded) ||
    typeof value.isCompleteTie !== "boolean"
  ) {
    return null;
  }

  return {
    displayOrder: value.displayOrder,
    teamSlotNumber: value.teamSlotNumber,
    teamName,
    totalPoints: value.totalPoints,
    totalPositionPoints: value.totalPositionPoints,
    totalKillPoints: value.totalKillPoints,
    firstPlaceFinishes: value.firstPlaceFinishes,
    latestMatchPlacement,
    matchesIncluded: value.matchesIncluded,
    isCompleteTie: value.isCompleteTie,
  };
}

function parseStandings(value: unknown): PublishedStandingRow[] | null {
  if (!Array.isArray(value) || value.length > 12) return null;

  const rows = value.map(parseStandingRow);
  return rows.every((row): row is PublishedStandingRow => row !== null)
    ? rows
    : null;
}

async function readPublishedStandings(
  token: string,
  config: SupabaseServerConfig,
  fetchImpl: FetchImplementation,
  timeoutMs: number,
): Promise<PublishedStandingRow[] | null> {
  const url = new URL(
    `${config.url}/rest/v1/tournament_standings_shares`,
  );
  url.searchParams.set("select", "standings");
  url.searchParams.set("share_token", `eq.${token}`);
  url.searchParams.set("limit", "2");

  let response: Response;
  try {
    response = await fetchWithTimeout(
      fetchImpl,
      url,
      {
        method: "GET",
        headers: {
          Accept: "application/json",
          Authorization: `Bearer ${config.serviceRoleKey}`,
          apikey: config.serviceRoleKey,
        },
      },
      timeoutMs,
    );
  } catch (error) {
    if (error instanceof UpstreamTimeoutError) {
      throw new Error("Supabase standings lookup timed out.");
    }
    throw new Error("Supabase standings lookup failed.");
  }

  if (!response.ok) {
    throw new Error("Supabase standings lookup failed.");
  }

  let payload: unknown;
  try {
    payload = await response.json();
  } catch {
    throw new Error("Supabase standings response was invalid.");
  }

  if (!Array.isArray(payload)) {
    throw new Error("Supabase standings response was invalid.");
  }
  if (payload.length === 0) return null;
  if (payload.length !== 1 || !isRecord(payload[0])) {
    throw new Error("Supabase standings response was invalid.");
  }

  const rows = parseStandings(payload[0].standings);
  if (rows === null) {
    throw new Error("Published standings snapshot was invalid.");
  }
  return rows;
}

function jsonResponse(
  body: Record<string, unknown>,
  status: number,
): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: JSON_HEADERS,
  });
}

function notFoundResponse(): Response {
  return jsonResponse({ error: "not_found" }, 404);
}

function internalErrorResponse(): Response {
  return jsonResponse({ error: "standings_unavailable" }, 500);
}

export async function handleRequest(
  request: Request,
  dependencies: HandlerDependencies = {},
): Promise<Response> {
  if (request.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: COMMON_HEADERS });
  }

  if (request.method !== "GET") {
    return jsonResponse({ error: "method_not_allowed" }, 405);
  }

  const token = new URL(request.url).searchParams.get("token");
  if (token === null || !UUID_PATTERN.test(token)) {
    return notFoundResponse();
  }

  try {
    const getEnv = dependencies.env ?? ((name: string) => Deno.env.get(name));
    const config = readSupabaseServerConfig(getEnv);
    const rows = await readPublishedStandings(
      token,
      config,
      dependencies.fetchImpl ?? fetch,
      dependencies.timeoutMs ?? SUPABASE_STANDINGS_TIMEOUT_MS,
    );
    return rows === null
      ? notFoundResponse()
      : jsonResponse({ standings: rows }, 200);
  } catch {
    return internalErrorResponse();
  }
}

if (import.meta.main) {
  Deno.serve((request) => handleRequest(request));
}
