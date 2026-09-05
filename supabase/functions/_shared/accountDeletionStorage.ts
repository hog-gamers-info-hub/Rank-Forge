import { EdgeFunctionError } from "./errors.ts";
import {
  type FetchImplementation,
  fetchWithTimeout,
  UpstreamTimeoutError,
} from "./http.ts";

export interface AccountDeletionStorageOptions {
  supabaseUrl: string;
  serviceRoleKey: string;
  fetchImpl?: FetchImplementation;
  timeoutMs: number;
}

export const ACCOUNT_STORAGE_BUCKETS = [
  "custom-designs",
  "match-screenshots",
  "ocr-screenshots",
] as const;

const LIST_PAGE_SIZE = 1000;
const DELETE_BATCH_SIZE = 100;
const MAX_LIST_PAGES_PER_DIRECTORY = 10_000;

interface StorageListEntry {
  name: string;
  id: string | null;
}

function storageFailure(): never {
  throw new EdgeFunctionError("STORAGE_CLEANUP_FAILED");
}

function headers(
  serviceRoleKey: string,
  contentType = false,
): HeadersInit {
  return {
    Accept: "application/json",
    Authorization: `Bearer ${serviceRoleKey}`,
    apikey: serviceRoleKey,
    ...(contentType ? { "Content-Type": "application/json" } : {}),
  };
}

function isSafeEntryName(name: unknown): name is string {
  return typeof name === "string" &&
    name.length > 0 &&
    name !== "." &&
    name !== ".." &&
    !name.includes("/") &&
    !name.includes("\\");
}

function parseListEntries(payload: unknown): StorageListEntry[] {
  if (!Array.isArray(payload)) storageFailure();

  return payload.map((entry) => {
    if (
      typeof entry !== "object" ||
      entry === null ||
      Array.isArray(entry) ||
      !isSafeEntryName((entry as Record<string, unknown>).name) ||
      !(
        typeof (entry as Record<string, unknown>).id === "string" ||
        (entry as Record<string, unknown>).id === null
      )
    ) {
      storageFailure();
    }

    const value = entry as Record<string, unknown>;
    return { name: value.name as string, id: value.id as string | null };
  });
}

async function request(
  input: string | URL,
  init: RequestInit,
  options: AccountDeletionStorageOptions,
): Promise<Response> {
  try {
    const response = await fetchWithTimeout(
      options.fetchImpl ?? fetch,
      input,
      init,
      options.timeoutMs,
    );
    if (!response.ok) storageFailure();
    return response;
  } catch (error) {
    if (error instanceof EdgeFunctionError) throw error;
    if (error instanceof UpstreamTimeoutError) storageFailure();
    storageFailure();
  }
}

async function listDirectory(
  bucket: string,
  prefix: string,
  options: AccountDeletionStorageOptions,
): Promise<StorageListEntry[]> {
  const entries: StorageListEntry[] = [];
  for (
    let page = 0, offset = 0;
    page < MAX_LIST_PAGES_PER_DIRECTORY;
    page += 1
  ) {
    const url = `${
      options.supabaseUrl.replace(/\/$/, "")
    }/storage/v1/object/list/${encodeURIComponent(bucket)}`;
    const response = await request(
      url,
      {
        method: "POST",
        headers: headers(options.serviceRoleKey, true),
        body: JSON.stringify({
          prefix,
          limit: LIST_PAGE_SIZE,
          offset,
          sortBy: { column: "name", order: "asc" },
        }),
      },
      options,
    );

    let payload: unknown;
    try {
      payload = await response.json();
    } catch {
      storageFailure();
    }
    const current = parseListEntries(payload);
    entries.push(...current);
    if (current.length < LIST_PAGE_SIZE) return entries;
    offset += current.length;
  }

  storageFailure();
}

async function listAllFiles(
  bucket: string,
  rootPrefix: string,
  options: AccountDeletionStorageOptions,
): Promise<string[]> {
  const directories = [rootPrefix];
  const visited = new Set<string>();
  const files = new Set<string>();

  while (directories.length > 0) {
    const prefix = directories.shift() as string;
    if (visited.has(prefix)) continue;
    visited.add(prefix);

    for (const entry of await listDirectory(bucket, prefix, options)) {
      const path = `${prefix}${entry.name}`;
      if (entry.id === null) {
        directories.push(`${path}/`);
      } else {
        files.add(path);
      }
    }
  }

  return [...files].sort();
}

async function deleteFiles(
  bucket: string,
  files: readonly string[],
  options: AccountDeletionStorageOptions,
): Promise<void> {
  for (let index = 0; index < files.length; index += DELETE_BATCH_SIZE) {
    const batch = files.slice(index, index + DELETE_BATCH_SIZE);
    await request(
      `${options.supabaseUrl.replace(/\/$/, "")}/storage/v1/object/${
        encodeURIComponent(bucket)
      }`,
      {
        method: "DELETE",
        headers: headers(options.serviceRoleKey, true),
        body: JSON.stringify({ prefixes: batch }),
      },
      options,
    );
  }
}

export function accountStoragePrefixes(userId: string): string[] {
  return ACCOUNT_STORAGE_BUCKETS.map(() => `users/${userId}/`);
}

export async function deleteOwnedStorageObjects(
  userId: string,
  options: AccountDeletionStorageOptions,
): Promise<void> {
  const prefixes = accountStoragePrefixes(userId);
  const filesByBucket = new Map<string, string[]>();

  for (let index = 0; index < ACCOUNT_STORAGE_BUCKETS.length; index += 1) {
    filesByBucket.set(
      ACCOUNT_STORAGE_BUCKETS[index],
      await listAllFiles(
        ACCOUNT_STORAGE_BUCKETS[index],
        prefixes[index],
        options,
      ),
    );
  }

  for (const bucket of ACCOUNT_STORAGE_BUCKETS) {
    await deleteFiles(bucket, filesByBucket.get(bucket) ?? [], options);
  }

  for (let index = 0; index < ACCOUNT_STORAGE_BUCKETS.length; index += 1) {
    const remaining = await listAllFiles(
      ACCOUNT_STORAGE_BUCKETS[index],
      prefixes[index],
      options,
    );
    if (remaining.length > 0) storageFailure();
  }
}

export async function verifyOwnedStorageEmpty(
  userId: string,
  options: AccountDeletionStorageOptions,
): Promise<void> {
  const prefixes = accountStoragePrefixes(userId);
  for (let index = 0; index < ACCOUNT_STORAGE_BUCKETS.length; index += 1) {
    const files = await listAllFiles(
      ACCOUNT_STORAGE_BUCKETS[index],
      prefixes[index],
      options,
    );
    if (files.length > 0) storageFailure();
  }
}
