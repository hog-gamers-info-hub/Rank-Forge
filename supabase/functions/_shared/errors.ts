export type ErrorCode =
  | "METHOD_NOT_ALLOWED"
  | "INVALID_JSON"
  | "INVALID_OPERATION"
  | "UNAUTHORIZED"
  | "SUPABASE_AUTH_FAILURE"
  | "GOOGLE_CONFIG_MISSING"
  | "GOOGLE_CREDENTIAL_INVALID"
  | "GOOGLE_JWT_SIGNING_FAILURE"
  | "GOOGLE_TOKEN_FAILURE"
  | "GOOGLE_TOKEN_RESPONSE_INVALID"
  | "GOOGLE_SHEETS_ACCESS_DENIED"
  | "GOOGLE_SHEETS_NOT_FOUND"
  | "GOOGLE_API_RATE_LIMITED"
  | "UPSTREAM_TIMEOUT"
  | "GOOGLE_API_FAILURE"
  | "INVALID_MATCH_EXPORT_PAYLOAD"
  | "TOURNAMENT_NOT_FOUND_OR_FORBIDDEN"
  | "MATCH_NOT_FOUND_OR_FORBIDDEN"
  | "MATCH_NOT_FINALIZED"
  | "MATCH_EXPORT_DATA_MISMATCH"
  | "GOOGLE_SHEET_SCHEMA_MISMATCH"
  | "GOOGLE_MATCH_EXPORT_FAILURE"
  | "GOOGLE_MATCH_EXPORT_RESPONSE_INVALID"
  | "SUPABASE_DATA_FAILURE"
  | "INVALID_STANDINGS_EXPORT_PAYLOAD"
  | "NO_FINALIZED_MATCHES"
  | "STANDINGS_EXPORT_DATA_MISMATCH"
  | "GOOGLE_STANDINGS_SHEET_SCHEMA_MISMATCH"
  | "GOOGLE_STANDINGS_EXPORT_FAILURE"
  | "GOOGLE_STANDINGS_EXPORT_RESPONSE_INVALID"
  | "EXPORT_IN_PROGRESS"
  | "EXPORT_OUTCOME_UNCERTAIN"
  | "EXPORT_IDEMPOTENCY_FAILURE"
  | "EXPORT_VERIFICATION_NOT_FOUND"
  | "EXPORT_VERIFICATION_CONFLICT"
  | "EXPORT_VERIFICATION_FAILURE"
  | "EXPORT_VERIFICATION_RANGE_EXCEEDED"
  | "INTERNAL_ERROR";

const CLIENT_MESSAGES: Record<ErrorCode, string> = {
  METHOD_NOT_ALLOWED: "Only POST requests are supported.",
  INVALID_JSON: "The request body must be valid JSON.",
  INVALID_OPERATION: "The requested operation is not supported.",
  UNAUTHORIZED: "Authentication is required.",
  SUPABASE_AUTH_FAILURE: "Authentication could not be verified.",
  GOOGLE_CONFIG_MISSING: "Google Sheets configuration is incomplete.",
  GOOGLE_CREDENTIAL_INVALID: "Google credentials are invalid.",
  GOOGLE_JWT_SIGNING_FAILURE: "Google authentication could not be prepared.",
  GOOGLE_TOKEN_FAILURE: "Google authentication failed.",
  GOOGLE_TOKEN_RESPONSE_INVALID:
    "Google returned an invalid authentication response.",
  GOOGLE_SHEETS_ACCESS_DENIED:
    "Access to the configured spreadsheet was denied.",
  GOOGLE_SHEETS_NOT_FOUND: "The configured spreadsheet was not found.",
  GOOGLE_API_RATE_LIMITED: "Google API rate limit exceeded.",
  UPSTREAM_TIMEOUT: "An upstream service timed out.",
  GOOGLE_API_FAILURE: "Google Sheets could not verify spreadsheet access.",
  INVALID_MATCH_EXPORT_PAYLOAD: "The match export payload is invalid.",
  TOURNAMENT_NOT_FOUND_OR_FORBIDDEN: "The tournament could not be found.",
  MATCH_NOT_FOUND_OR_FORBIDDEN: "The match could not be found.",
  MATCH_NOT_FINALIZED: "Only finalized matches can be exported.",
  MATCH_EXPORT_DATA_MISMATCH:
    "The match export data does not match finalized records.",
  GOOGLE_SHEET_SCHEMA_MISMATCH:
    "The Match Results worksheet header is invalid.",
  GOOGLE_MATCH_EXPORT_FAILURE: "Google Sheets could not export the match.",
  GOOGLE_MATCH_EXPORT_RESPONSE_INVALID:
    "Google Sheets returned an invalid export response.",
  SUPABASE_DATA_FAILURE: "Finalized match data could not be verified.",
  INVALID_STANDINGS_EXPORT_PAYLOAD: "The standings export payload is invalid.",
  NO_FINALIZED_MATCHES: "The tournament has no finalized matches to export.",
  STANDINGS_EXPORT_DATA_MISMATCH:
    "The standings export data does not match finalized records.",
  GOOGLE_STANDINGS_SHEET_SCHEMA_MISMATCH:
    "The Tournament Standings worksheet header is invalid.",
  GOOGLE_STANDINGS_EXPORT_FAILURE:
    "Google Sheets could not export the standings.",
  GOOGLE_STANDINGS_EXPORT_RESPONSE_INVALID:
    "Google Sheets returned an invalid standings export response.",
  EXPORT_IN_PROGRESS: "An identical export is already in progress.",
  EXPORT_OUTCOME_UNCERTAIN:
    "The previous export outcome is uncertain and cannot be retried safely.",
  EXPORT_IDEMPOTENCY_FAILURE:
    "The export operation state could not be updated safely.",
  EXPORT_VERIFICATION_NOT_FOUND:
    "No matching exported rows were found. The export outcome remains uncertain.",
  EXPORT_VERIFICATION_CONFLICT:
    "The exported rows could not be reconciled safely.",
  EXPORT_VERIFICATION_FAILURE: "The exported rows could not be verified.",
  EXPORT_VERIFICATION_RANGE_EXCEEDED:
    "The worksheet exceeds the supported verification range.",
  INTERNAL_ERROR: "The request could not be completed.",
};

const STATUS_BY_CODE: Record<ErrorCode, number> = {
  METHOD_NOT_ALLOWED: 405,
  INVALID_JSON: 400,
  INVALID_OPERATION: 400,
  UNAUTHORIZED: 401,
  SUPABASE_AUTH_FAILURE: 502,
  GOOGLE_CONFIG_MISSING: 500,
  GOOGLE_CREDENTIAL_INVALID: 500,
  GOOGLE_JWT_SIGNING_FAILURE: 500,
  GOOGLE_TOKEN_FAILURE: 502,
  GOOGLE_TOKEN_RESPONSE_INVALID: 502,
  GOOGLE_SHEETS_ACCESS_DENIED: 403,
  GOOGLE_SHEETS_NOT_FOUND: 404,
  GOOGLE_API_RATE_LIMITED: 429,
  UPSTREAM_TIMEOUT: 504,
  GOOGLE_API_FAILURE: 502,
  INVALID_MATCH_EXPORT_PAYLOAD: 400,
  TOURNAMENT_NOT_FOUND_OR_FORBIDDEN: 404,
  MATCH_NOT_FOUND_OR_FORBIDDEN: 404,
  MATCH_NOT_FINALIZED: 409,
  MATCH_EXPORT_DATA_MISMATCH: 409,
  GOOGLE_SHEET_SCHEMA_MISMATCH: 409,
  GOOGLE_MATCH_EXPORT_FAILURE: 502,
  GOOGLE_MATCH_EXPORT_RESPONSE_INVALID: 502,
  SUPABASE_DATA_FAILURE: 502,
  INVALID_STANDINGS_EXPORT_PAYLOAD: 400,
  NO_FINALIZED_MATCHES: 409,
  STANDINGS_EXPORT_DATA_MISMATCH: 409,
  GOOGLE_STANDINGS_SHEET_SCHEMA_MISMATCH: 409,
  GOOGLE_STANDINGS_EXPORT_FAILURE: 502,
  GOOGLE_STANDINGS_EXPORT_RESPONSE_INVALID: 502,
  EXPORT_IN_PROGRESS: 409,
  EXPORT_OUTCOME_UNCERTAIN: 409,
  EXPORT_IDEMPOTENCY_FAILURE: 502,
  EXPORT_VERIFICATION_NOT_FOUND: 409,
  EXPORT_VERIFICATION_CONFLICT: 409,
  EXPORT_VERIFICATION_FAILURE: 502,
  EXPORT_VERIFICATION_RANGE_EXCEEDED: 409,
  INTERNAL_ERROR: 500,
};

export class EdgeFunctionError extends Error {
  readonly code: ErrorCode;
  readonly status: number;

  constructor(code: ErrorCode) {
    super(code);
    this.name = "EdgeFunctionError";
    this.code = code;
    this.status = STATUS_BY_CODE[code];
  }
}

export function errorResponse(error: unknown): Response {
  const safeError = error instanceof EdgeFunctionError
    ? error
    : new EdgeFunctionError("INTERNAL_ERROR");

  return jsonResponse({
    ok: false,
    error: {
      code: safeError.code,
      message: CLIENT_MESSAGES[safeError.code],
    },
  }, safeError.status);
}

export function jsonResponse(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
