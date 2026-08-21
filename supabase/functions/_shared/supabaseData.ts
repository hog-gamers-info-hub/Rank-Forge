import { EdgeFunctionError } from "./errors.ts";
import {
  type FetchImplementation,
  fetchWithTimeout,
  UpstreamTimeoutError,
} from "./http.ts";
import type { SupabaseConfig } from "./supabaseAuth.ts";

export const SUPABASE_DATA_TIMEOUT_MS = 10_000;

export interface SupabaseDataContext {
  config: SupabaseConfig;
  accessToken: string;
  fetchImpl?: FetchImplementation;
  timeoutMs?: number;
}

export interface OfficialTournament {
  id: string;
  name: string;
}

export interface OfficialMatch {
  id: string;
  tournament_id: string;
  match_number: number;
  status: string;
  finalized_at: string | null;
}

export interface OfficialMatchResult {
  id: string;
  match_id: string;
  team_slot_id: string;
  placement: number | null;
  kills: number;
  participation_status?: "PARTICIPATED" | "NO_SHOW";
  review_status: string;
}

export interface OfficialTeamSlot {
  id: string;
  tournament_id: string;
  slot_number: number;
  team_name: string | null;
}

export interface OfficialPlayer {
  id: string;
  team_slot_id: string;
  display_name: string;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isString(value: unknown): value is string {
  return typeof value === "string" && value.length > 0;
}

function isInteger(value: unknown): value is number {
  return typeof value === "number" && Number.isInteger(value);
}

function createRestUrl(
  config: SupabaseConfig,
  table: string,
  parameters: Readonly<Record<string, string>>,
): URL {
  const url = new URL(`${config.url}/rest/v1/${table}`);

  for (const [name, value] of Object.entries(parameters)) {
    url.searchParams.set(name, value);
  }

  return url;
}

async function readRows(
  context: SupabaseDataContext,
  table: string,
  parameters: Readonly<Record<string, string>>,
): Promise<unknown[]> {
  const fetchImpl = context.fetchImpl ?? fetch;
  const url = createRestUrl(context.config, table, parameters);
  let response: Response;

  try {
    response = await fetchWithTimeout(
      fetchImpl,
      url,
      {
        method: "GET",
        headers: {
          Accept: "application/json",
          Authorization: `Bearer ${context.accessToken}`,
          apikey: context.config.anonKey,
        },
      },
      context.timeoutMs ?? SUPABASE_DATA_TIMEOUT_MS,
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

    if (!Array.isArray(payload)) {
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

function parseTournament(value: unknown): OfficialTournament {
  if (
    !isRecord(value) ||
    !isString(value.id) ||
    typeof value.name !== "string"
  ) {
    throw new EdgeFunctionError("SUPABASE_DATA_FAILURE");
  }

  return { id: value.id, name: value.name };
}

function parseMatch(value: unknown): OfficialMatch {
  if (
    !isRecord(value) ||
    !isString(value.id) ||
    !isString(value.tournament_id) ||
    !isInteger(value.match_number) ||
    typeof value.status !== "string" ||
    !(typeof value.finalized_at === "string" || value.finalized_at === null)
  ) {
    throw new EdgeFunctionError("SUPABASE_DATA_FAILURE");
  }

  return {
    id: value.id,
    tournament_id: value.tournament_id,
    match_number: value.match_number,
    status: value.status,
    finalized_at: value.finalized_at,
  };
}

function parseMatchResult(value: unknown): OfficialMatchResult {
  if (
    !isRecord(value) ||
    !isString(value.id) ||
    !isString(value.match_id) ||
    !isString(value.team_slot_id) ||
    !(isInteger(value.placement) || value.placement === null) ||
    !isInteger(value.kills) ||
    typeof value.review_status !== "string"
  ) {
    throw new EdgeFunctionError("SUPABASE_DATA_FAILURE");
  }

  return {
    id: value.id,
    match_id: value.match_id,
    team_slot_id: value.team_slot_id,
    placement: value.placement,
    kills: value.kills,
    participation_status: value.participation_status === "NO_SHOW"
      ? "NO_SHOW"
      : "PARTICIPATED",
    review_status: value.review_status,
  };
}

function parseTeamSlot(value: unknown): OfficialTeamSlot {
  if (
    !isRecord(value) ||
    !isString(value.id) ||
    !isString(value.tournament_id) ||
    !isInteger(value.slot_number) ||
    !(typeof value.team_name === "string" || value.team_name === null)
  ) {
    throw new EdgeFunctionError("SUPABASE_DATA_FAILURE");
  }

  return {
    id: value.id,
    tournament_id: value.tournament_id,
    slot_number: value.slot_number,
    team_name: value.team_name,
  };
}

function parsePlayer(value: unknown): OfficialPlayer {
  if (
    !isRecord(value) ||
    !isString(value.id) ||
    !isString(value.team_slot_id) ||
    typeof value.display_name !== "string"
  ) {
    throw new EdgeFunctionError("SUPABASE_DATA_FAILURE");
  }

  return {
    id: value.id,
    team_slot_id: value.team_slot_id,
    display_name: value.display_name,
  };
}

export async function readVisibleTournament(
  tournamentId: string,
  context: SupabaseDataContext,
): Promise<OfficialTournament> {
  const rows = await readRows(context, "tournaments", {
    select: "id,name",
    id: `eq.${tournamentId}`,
    limit: "2",
  });

  if (rows.length === 0) {
    throw new EdgeFunctionError("TOURNAMENT_NOT_FOUND_OR_FORBIDDEN");
  }

  if (rows.length !== 1) {
    throw new EdgeFunctionError("SUPABASE_DATA_FAILURE");
  }

  return parseTournament(rows[0]);
}

export async function readVisibleMatch(
  matchId: string,
  context: SupabaseDataContext,
): Promise<OfficialMatch> {
  const rows = await readRows(context, "matches", {
    select: "id,tournament_id,match_number,status,finalized_at",
    id: `eq.${matchId}`,
    limit: "2",
  });

  if (rows.length === 0) {
    throw new EdgeFunctionError("MATCH_NOT_FOUND_OR_FORBIDDEN");
  }

  if (rows.length !== 1) {
    throw new EdgeFunctionError("SUPABASE_DATA_FAILURE");
  }

  return parseMatch(rows[0]);
}

export async function readVisibleTournamentMatches(
  tournamentId: string,
  context: SupabaseDataContext,
): Promise<OfficialMatch[]> {
  const rows = await readRows(context, "matches", {
    select: "id,tournament_id,match_number,status,finalized_at",
    tournament_id: `eq.${tournamentId}`,
    order: "match_number.asc,id.asc",
  });

  return rows.map(parseMatch);
}

export async function readOfficialMatchResults(
  matchId: string,
  context: SupabaseDataContext,
): Promise<OfficialMatchResult[]> {
  const rows = await readRows(context, "match_results", {
    select:
      "id,match_id,team_slot_id,participation_status,placement,kills,review_status",
    match_id: `eq.${matchId}`,
    order: "placement.asc.nullslast",
  });

  return rows.map(parseMatchResult);
}

export async function readOfficialMatchResultsForMatches(
  matchIds: readonly string[],
  context: SupabaseDataContext,
): Promise<OfficialMatchResult[]> {
  if (matchIds.length === 0) {
    return [];
  }

  const rows = await readRows(context, "match_results", {
    select:
      "id,match_id,team_slot_id,participation_status,placement,kills,review_status",
    match_id: `in.(${matchIds.join(",")})`,
    order: "match_id.asc,placement.asc.nullslast",
  });

  return rows.map(parseMatchResult);
}

export async function readOfficialTeamSlots(
  tournamentId: string,
  context: SupabaseDataContext,
): Promise<OfficialTeamSlot[]> {
  const rows = await readRows(context, "tournament_team_slots", {
    select: "id,tournament_id,slot_number,team_name",
    tournament_id: `eq.${tournamentId}`,
    order: "slot_number.asc",
  });

  return rows.map(parseTeamSlot);
}

export async function readOfficialPlayers(
  teamSlotIds: readonly string[],
  context: SupabaseDataContext,
): Promise<OfficialPlayer[]> {
  if (teamSlotIds.length === 0) {
    return [];
  }

  const rows = await readRows(context, "players", {
    select: "id,team_slot_id,display_name",
    team_slot_id: `in.(${teamSlotIds.join(",")})`,
    order: "team_slot_id.asc,display_name.asc",
  });

  return rows.map(parsePlayer);
}
