import { EdgeFunctionError } from "./errors.ts";
import {
  type FetchImplementation,
  fetchWithTimeout,
  UpstreamTimeoutError,
} from "./http.ts";

export interface AccountDeletionSupabaseConfig {
  url: string;
  serviceRoleKey: string;
}

export interface AccountDeletionSupabaseOptions {
  config: AccountDeletionSupabaseConfig;
  fetchImpl?: FetchImplementation;
  timeoutMs: number;
}

export interface AccountPurgeCounts {
  deletedTournaments: number;
  deletedCustomDesigns: number;
  deletedDeletionReceipts: number;
  deletedExportOperations: number;
}

export interface AccountDeletionBarrier {
  state: "deleting";
  activeExportOperations: number;
}

const TOURNAMENT_PAGE_SIZE = 1000;
const MAX_TOURNAMENT_PAGES = 10_000;
const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const ACCOUNT_REFERENCE_COLUMNS = [
  ["tournaments", "owner_id"],
  ["custom_design_templates", "user_id"],
  ["deletion_receipts", "owner_id"],
  ["export_operations", "owner_id"],
  ["matches", "finalized_by"],
  ["match_correction_audit_entries", "corrected_by"],
  ["match_lobby_screenshot_assets", "owner_id"],
  ["match_result_screenshot_assets", "owner_id"],
  ["match_screenshot_metadata", "owner_id"],
] as const;

function databaseFailure(): never {
  throw new EdgeFunctionError("DATABASE_PURGE_FAILED");
}

function assertUserId(userId: string): void {
  if (!UUID_PATTERN.test(userId)) databaseFailure();
}

function serviceHeaders(
  config: AccountDeletionSupabaseConfig,
  contentType = false,
): HeadersInit {
  return {
    Accept: "application/json",
    Authorization: `Bearer ${config.serviceRoleKey}`,
    apikey: config.serviceRoleKey,
    ...(contentType ? { "Content-Type": "application/json" } : {}),
  };
}

async function requestJson(
  input: string | URL,
  init: RequestInit,
  options: AccountDeletionSupabaseOptions,
): Promise<unknown> {
  let response: Response;
  try {
    response = await fetchWithTimeout(
      options.fetchImpl ?? fetch,
      input,
      init,
      options.timeoutMs,
    );
  } catch (error) {
    if (error instanceof UpstreamTimeoutError) databaseFailure();
    databaseFailure();
  }

  if (!response.ok) databaseFailure();

  try {
    return await response.json();
  } catch {
    databaseFailure();
  }
}

function restUrl(
  config: AccountDeletionSupabaseConfig,
  table: string,
  column: string,
  userId: string,
): URL {
  const url = new URL(`${config.url}/rest/v1/${table}`);
  url.searchParams.set("select", column);
  url.searchParams.set(column, `eq.${userId}`);
  url.searchParams.set("limit", "1");
  return url;
}

export async function readOwnedTournamentIds(
  userId: string,
  options: AccountDeletionSupabaseOptions,
): Promise<string[]> {
  assertUserId(userId);

  const ids = new Set<string>();
  for (let page = 0, offset = 0; page < MAX_TOURNAMENT_PAGES; page += 1) {
    const url = new URL(`${options.config.url}/rest/v1/tournaments`);
    url.searchParams.set("select", "id");
    url.searchParams.set("owner_id", `eq.${userId}`);
    url.searchParams.set("order", "id.asc");
    url.searchParams.set("limit", String(TOURNAMENT_PAGE_SIZE));
    url.searchParams.set("offset", String(offset));

    const payload = await requestJson(
      url,
      { method: "GET", headers: serviceHeaders(options.config) },
      options,
    );
    if (!Array.isArray(payload)) databaseFailure();

    for (const row of payload) {
      if (
        typeof row !== "object" ||
        row === null ||
        Array.isArray(row) ||
        typeof (row as Record<string, unknown>).id !== "string" ||
        !UUID_PATTERN.test((row as Record<string, unknown>).id as string)
      ) {
        databaseFailure();
      }
      ids.add(((row as Record<string, unknown>).id as string).toLowerCase());
    }

    if (payload.length < TOURNAMENT_PAGE_SIZE) {
      return [...ids].sort();
    }
    offset += payload.length;
  }

  databaseFailure();
}

export async function beginAccountDeletion(
  userId: string,
  options: AccountDeletionSupabaseOptions,
): Promise<AccountDeletionBarrier> {
  assertUserId(userId);

  const payload = await requestJson(
    `${options.config.url}/rest/v1/rpc/begin_account_deletion`,
    {
      method: "POST",
      headers: serviceHeaders(options.config, true),
      body: JSON.stringify({ p_user_id: userId }),
    },
    options,
  );

  if (
    !Array.isArray(payload) ||
    payload.length !== 1 ||
    typeof payload[0] !== "object" ||
    payload[0] === null ||
    Array.isArray(payload[0])
  ) {
    databaseFailure();
  }

  const row = payload[0] as Record<string, unknown>;
  if (row.state !== "deleting") databaseFailure();

  return {
    state: "deleting",
    activeExportOperations: parseCount(row.active_export_operations),
  };
}

function parseCount(value: unknown): number {
  if (
    typeof value !== "number" ||
    !Number.isSafeInteger(value) ||
    value < 0
  ) {
    databaseFailure();
  }
  return value;
}

export async function purgeAccountData(
  userId: string,
  options: AccountDeletionSupabaseOptions,
): Promise<AccountPurgeCounts> {
  assertUserId(userId);

  const payload = await requestJson(
    `${options.config.url}/rest/v1/rpc/purge_account_data`,
    {
      method: "POST",
      headers: serviceHeaders(options.config, true),
      body: JSON.stringify({ p_user_id: userId }),
    },
    options,
  );

  if (
    !Array.isArray(payload) ||
    payload.length !== 1 ||
    typeof payload[0] !== "object" ||
    payload[0] === null ||
    Array.isArray(payload[0])
  ) {
    databaseFailure();
  }

  const row = payload[0] as Record<string, unknown>;
  return {
    deletedTournaments: parseCount(row.deleted_tournaments),
    deletedCustomDesigns: parseCount(row.deleted_custom_designs),
    deletedDeletionReceipts: parseCount(row.deleted_deletion_receipts),
    deletedExportOperations: parseCount(row.deleted_export_operations),
  };
}

export async function verifyAccountDatabasePurge(
  userId: string,
  options: AccountDeletionSupabaseOptions,
): Promise<void> {
  assertUserId(userId);

  for (const [table, column] of ACCOUNT_REFERENCE_COLUMNS) {
    const payload = await requestJson(
      restUrl(options.config, table, column, userId),
      { method: "GET", headers: serviceHeaders(options.config) },
      options,
    );
    if (!Array.isArray(payload) || payload.length !== 0) {
      databaseFailure();
    }
  }
}
