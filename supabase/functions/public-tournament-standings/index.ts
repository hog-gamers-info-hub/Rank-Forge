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

const NOT_FOUND_HTML = `<!doctype html>
<html lang="en">
<head><meta charset="utf-8"><title>Not found</title></head>
<body><h1>Not found</h1></body>
</html>`;

const INTERNAL_ERROR_HTML = `<!doctype html>
<html lang="en">
<head><meta charset="utf-8"><title>Standings unavailable</title></head>
<body><h1>Standings unavailable</h1></body>
</html>`;

const HTML_HEADERS = {
  "Content-Type": "text/html; charset=utf-8",
  "X-Content-Type-Options": "nosniff",
  "Referrer-Policy": "no-referrer",
  "Cache-Control": "no-store",
  "Content-Security-Policy":
    "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
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

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (character) => {
    switch (character) {
      case "&":
        return "&amp;";
      case "<":
        return "&lt;";
      case ">":
        return "&gt;";
      case '"':
        return "&quot;";
      default:
        return "&#39;";
    }
  });
}

function rankClass(displayOrder: number): string {
  return displayOrder >= 1 && displayOrder <= 3 ? ` rank-${displayOrder}` : "";
}

function metric(
  label: string,
  value: number | string,
  className = "",
): string {
  return `<div class="metric${className}"><div class="metric-label">${
    escapeHtml(label)
  }</div><div class="metric-value">${escapeHtml(String(value))}</div></div>`;
}

function renderStandingRow(row: PublishedStandingRow): string {
  const trimmedTeamName = row.teamName?.trim();
  const teamLabel = trimmedTeamName
    ? `Team Name - ${trimmedTeamName}`
    : `Team slot - ${row.teamSlotNumber}`;
  const latestPlacement = row.latestMatchPlacement === null
    ? "—"
    : row.latestMatchPlacement;
  const completeTie = row.isCompleteTie
    ? '<p class="complete-tie">Complete tie; displayed in Team Slot order.</p>'
    : "";

  return `<article class="standing-card${rankClass(row.displayOrder)}">
  <div class="standing-heading">
    <div class="rank-badge${
    rankClass(row.displayOrder)
  }">${row.displayOrder}</div>
    <div class="team-label">${escapeHtml(teamLabel)}</div>
  </div>
  <div class="divider"></div>
  <div class="metrics">
    ${metric("Kill points", row.totalKillPoints)}
    ${metric("Position points", row.totalPositionPoints)}
    ${metric("Total points", row.totalPoints, " total-points")}
  </div>
  <div class="metrics secondary-metrics">
    ${metric("1st place finishes", row.firstPlaceFinishes)}
    ${metric("Latest placement", latestPlacement)}
    ${metric("Matches included", row.matchesIncluded)}
  </div>
  ${completeTie}
</article>`;
}

function renderHtml(rows: readonly PublishedStandingRow[]): string {
  const content = rows.length === 0
    ? `<section class="empty-state">
  <h2>No finalized matches yet</h2>
  <p>Finalize a match to see tournament standings.</p>
</section>`
    : `<section class="standings-list">${
      rows.map(renderStandingRow).join("\n")
    }</section>`;

  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Tournament standings</title>
  <style>
    :root {
      color-scheme: light;
      font-family: Arial, Helvetica, sans-serif;
      background: #f4faff;
      color: #071b3e;
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      min-height: 100vh;
      background: linear-gradient(180deg, #fdfeff 0%, #f4faff 100%);
      padding: 24px;
    }
    main { width: min(100%, 680px); margin: 0 auto; }
    h1 {
      margin: 0;
      color: #071b3e;
      font-size: 22px;
      line-height: 26px;
      font-weight: 700;
    }
    .info-banner {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-top: 18px;
      padding: 11px 12px;
      border: 1px solid #cfe0ff;
      border-radius: 18px;
      background: #f5f8ff;
      color: #071b3e;
      font-size: 15px;
      line-height: 21px;
    }
    .info-icon {
      flex: 0 0 auto;
      color: #176af7;
      font-size: 22px;
      line-height: 22px;
    }
    .standings-list { display: grid; gap: 12px; margin-top: 20px; }
    .standing-card {
      padding: 14px;
      border: 1px solid #d9e6f7;
      border-radius: 18px;
      background: #ffffff;
      box-shadow: 0 2px 5px rgba(7, 27, 62, 0.08);
    }
    .standing-heading { display: flex; align-items: center; gap: 12px; }
    .rank-badge {
      display: grid;
      flex: 0 0 28px;
      width: 28px;
      height: 38px;
      place-items: center;
      border-radius: 10px;
      background: #eff4fa;
      color: #071b3e;
      font-size: 17px;
      font-weight: 700;
    }
    .rank-badge.rank-1 { background: #fff2c7; color: #c28a00; }
    .rank-badge.rank-2 { background: #e8f0ff; color: #176af7; }
    .rank-badge.rank-3 { background: #ffebdd; color: #d46b2c; }
    .team-label {
      overflow-wrap: anywhere;
      color: #607393;
      font-size: 15px;
      line-height: 21px;
      font-weight: 600;
    }
    .divider { height: 1px; margin: 8px 0 12px; background: #e2eaf4; }
    .metrics { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
    .secondary-metrics { margin-top: 14px; }
    .metric-label {
      color: #607393;
      font-size: 11px;
      line-height: 14px;
    }
    .metric-value {
      margin-top: 4px;
      color: #071b3e;
      font-size: 18px;
      line-height: 21px;
      font-weight: 700;
    }
    .total-points .metric-value { font-size: 22px; line-height: 25px; }
    .standing-card.rank-1 .total-points .metric-value { color: #c28a00; }
    .standing-card.rank-2 .total-points .metric-value { color: #176af7; }
    .standing-card.rank-3 .total-points .metric-value { color: #d46b2c; }
    .complete-tie {
      margin: 10px 0 0;
      color: #607393;
      font-size: 13px;
      line-height: 18px;
    }
    .empty-state { margin-top: 28px; }
    .empty-state h2 { margin: 0; font-size: 28px; line-height: 34px; }
    .empty-state p { margin: 8px 0 0; color: #607393; font-size: 16px; line-height: 24px; }
    @media (max-width: 430px) {
      body { padding: 18px; }
      .metrics { gap: 7px; }
      .metric-label { min-height: 28px; }
    }
  </style>
</head>
<body>
  <main>
    <h1>Tournament standings</h1>
    <div class="info-banner"><span class="info-icon" aria-hidden="true">✓</span><span>Only finalized matches are included in these standings.</span></div>
    ${content}
  </main>
</body>
</html>`;
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

function notFoundResponse(): Response {
  return new Response(NOT_FOUND_HTML, { status: 404, headers: HTML_HEADERS });
}

function internalErrorResponse(): Response {
  return new Response(INTERNAL_ERROR_HTML, {
    status: 500,
    headers: HTML_HEADERS,
  });
}

export async function handleRequest(
  request: Request,
  dependencies: HandlerDependencies = {},
): Promise<Response> {
  if (request.method !== "GET") {
    return new Response("Method Not Allowed", {
      status: 405,
      headers: { "Content-Type": "text/plain; charset=utf-8" },
    });
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
      : new Response(renderHtml(rows), { status: 200, headers: HTML_HEADERS });
  } catch {
    return internalErrorResponse();
  }
}

if (import.meta.main) {
  Deno.serve((request) => handleRequest(request));
}
