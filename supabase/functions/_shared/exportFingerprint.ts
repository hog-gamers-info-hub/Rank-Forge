import {
  MATCH_EXPORT_COLUMNS,
  type MatchExportRequest,
} from "./matchExport.ts";
import {
  STANDINGS_EXPORT_COLUMNS,
  type StandingsExportRequest,
} from "./standingsExport.ts";

export type ExportRequest = MatchExportRequest | StandingsExportRequest;

const FINGERPRINT_FORMAT = "rank_forge_export_fingerprint_v1";

function bytesToLowerHex(bytes: Uint8Array): string {
  return Array.from(
    bytes,
    (byte) => byte.toString(16).padStart(2, "0"),
  ).join("");
}

function canonicalMatchFingerprintInput(
  request: MatchExportRequest,
): unknown[] {
  return [
    FINGERPRINT_FORMAT,
    "export_match",
    request.tournament_id,
    request.match_id,
    MATCH_EXPORT_COLUMNS,
    request.rows.map((row) =>
      MATCH_EXPORT_COLUMNS.map((column) =>
        column === "participation_status"
          ? row.participation_status ?? "PARTICIPATED"
          : row[column] ?? null
      )
    ),
  ];
}

function canonicalStandingsFingerprintInput(
  request: StandingsExportRequest,
): unknown[] {
  return [
    FINGERPRINT_FORMAT,
    "export_standings",
    request.tournament_id,
    null,
    STANDINGS_EXPORT_COLUMNS,
    request.rows.map((row) =>
      STANDINGS_EXPORT_COLUMNS.map((column) => row[column] ?? null)
    ),
  ];
}

export function canonicalizeExportForFingerprint(
  request: ExportRequest,
): string {
  const canonical = request.operation === "export_match"
    ? canonicalMatchFingerprintInput(request)
    : canonicalStandingsFingerprintInput(request);

  return JSON.stringify(canonical);
}

export async function createExportPayloadFingerprint(
  request: ExportRequest,
): Promise<string> {
  const canonical = canonicalizeExportForFingerprint(request);
  const digest = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(canonical),
  );

  return bytesToLowerHex(new Uint8Array(digest));
}
