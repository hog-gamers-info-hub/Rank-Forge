import {
  EdgeFunctionError,
  errorResponse,
  jsonResponse,
} from "../_shared/errors.ts";

export const MAX_EMAIL_LENGTH = 320;

const INVALID_REQUEST_MESSAGE =
  "The email registration status request is invalid.";

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
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

export async function handleRequest(request: Request): Promise<Response> {
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

    if (parseEmail(body) === null) {
      return jsonResponse({
        ok: false,
        error: {
          code: "INVALID_REGISTRATION_STATUS_PAYLOAD",
          message: INVALID_REQUEST_MESSAGE,
        },
      }, 400);
    }

    // Keep the legacy response contract without disclosing account existence.
    return jsonResponse({ registered: false });
  } catch (error) {
    return errorResponse(error);
  }
}

if (import.meta.main) {
  Deno.serve((request) => handleRequest(request));
}
