import { EdgeFunctionError } from "./errors.ts";
import {
  type FetchImplementation,
  fetchWithTimeout,
  UpstreamTimeoutError,
} from "./http.ts";

export const GOOGLE_SHEETS_SCOPE =
  "https://www.googleapis.com/auth/spreadsheets";
export const GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
export const GOOGLE_SHEETS_TIMEOUT_MS = 10_000;
export const GOOGLE_TOKEN_TIMEOUT_MS = 10_000;

export interface GoogleConfig {
  clientEmail: string;
  privateKey: string;
  spreadsheetId: string;
}

export function readGoogleConfig(
  getEnv: (name: string) => string | undefined,
): GoogleConfig {
  const clientEmail = getEnv("GOOGLE_SHEETS_CLIENT_EMAIL");
  const privateKey = getEnv("GOOGLE_SHEETS_PRIVATE_KEY");
  const spreadsheetId = getEnv("GOOGLE_SHEETS_SPREADSHEET_ID");
  if (!clientEmail || !privateKey || !spreadsheetId) {
    throw new EdgeFunctionError("GOOGLE_CONFIG_MISSING");
  }
  return { clientEmail, privateKey, spreadsheetId };
}

export function normalizePrivateKey(privateKey: string): string {
  return privateKey.replaceAll("\\n", "\n");
}

function copyUint8ArrayToArrayBuffer(source: Uint8Array): ArrayBuffer {
  const buffer = new ArrayBuffer(source.byteLength);
  const copy = new Uint8Array(buffer);
  copy.set(source);
  return copy.buffer;
}

function pemToDer(privateKey: string): Uint8Array {
  const normalized = normalizePrivateKey(privateKey);
  const match = normalized.match(
    /^\s*-----BEGIN PRIVATE KEY-----([\s\S]+?)-----END PRIVATE KEY-----\s*$/,
  );
  if (!match) throw new EdgeFunctionError("GOOGLE_CREDENTIAL_INVALID");
  const base64 = match[1].replace(/\s/g, "");
  if (
    !base64 || base64.length % 4 === 1 || !/^[A-Za-z0-9+/]+={0,2}$/.test(base64)
  ) {
    throw new EdgeFunctionError("GOOGLE_CREDENTIAL_INVALID");
  }
  try {
    const binary = atob(base64);
    return Uint8Array.from(binary, (character) => character.charCodeAt(0));
  } catch {
    throw new EdgeFunctionError("GOOGLE_CREDENTIAL_INVALID");
  }
}

export async function importGooglePrivateKey(
  privateKey: string,
): Promise<CryptoKey> {
  const der = pemToDer(privateKey);
  try {
    return await crypto.subtle.importKey(
      "pkcs8",
      copyUint8ArrayToArrayBuffer(der),
      { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
      false,
      ["sign"],
    );
  } catch {
    throw new EdgeFunctionError("GOOGLE_CREDENTIAL_INVALID");
  }
}

export function base64UrlEncode(value: string | Uint8Array): string {
  const bytes = typeof value === "string"
    ? new TextEncoder().encode(value)
    : value;
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(
    /=+$/,
    "",
  );
}

export function buildGoogleJwtHeader(): { alg: "RS256"; typ: "JWT" } {
  return { alg: "RS256", typ: "JWT" };
}

export function buildGoogleJwtClaims(
  clientEmail: string,
  issuedAtSeconds: number,
): {
  iss: string;
  scope: typeof GOOGLE_SHEETS_SCOPE;
  aud: typeof GOOGLE_TOKEN_ENDPOINT;
  iat: number;
  exp: number;
} {
  const iat = Math.floor(issuedAtSeconds);
  return {
    iss: clientEmail,
    scope: GOOGLE_SHEETS_SCOPE,
    aud: GOOGLE_TOKEN_ENDPOINT,
    iat,
    exp: iat + 3600,
  };
}

export type JwtSigner = (
  signingInput: Uint8Array,
  privateKey: string,
) => Promise<Uint8Array>;

export async function signGoogleJwt(
  signingInput: Uint8Array,
  privateKey: string,
): Promise<Uint8Array> {
  const key = await importGooglePrivateKey(privateKey);
  try {
    const signature = await crypto.subtle.sign(
      { name: "RSASSA-PKCS1-v1_5" },
      key,
      copyUint8ArrayToArrayBuffer(signingInput),
    );
    return new Uint8Array(signature);
  } catch {
    throw new EdgeFunctionError("GOOGLE_JWT_SIGNING_FAILURE");
  }
}

export async function createGoogleServiceAccountAssertion(
  config: GoogleConfig,
  options: { issuedAtSeconds?: number; signer?: JwtSigner } = {},
): Promise<string> {
  const issuedAtSeconds = Math.floor(
    options.issuedAtSeconds ?? Date.now() / 1000,
  );
  const encodedHeader = base64UrlEncode(JSON.stringify(buildGoogleJwtHeader()));
  const encodedClaims = base64UrlEncode(
    JSON.stringify(buildGoogleJwtClaims(config.clientEmail, issuedAtSeconds)),
  );
  const signingText = `${encodedHeader}.${encodedClaims}`;
  try {
    const signature = await (options.signer ?? signGoogleJwt)(
      new TextEncoder().encode(signingText),
      config.privateKey,
    );
    return `${signingText}.${base64UrlEncode(signature)}`;
  } catch (error) {
    if (error instanceof EdgeFunctionError) throw error;
    throw new EdgeFunctionError("GOOGLE_JWT_SIGNING_FAILURE");
  }
}

export async function exchangeGoogleToken(
  assertion: string,
  options: { fetchImpl?: FetchImplementation; timeoutMs: number },
): Promise<string> {
  const fetchImpl = options.fetchImpl ?? fetch;
  const form = new URLSearchParams({
    grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
    assertion,
  });
  let response: Response;
  try {
    response = await fetchWithTimeout(
      fetchImpl,
      GOOGLE_TOKEN_ENDPOINT,
      {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: form.toString(),
      },
      options.timeoutMs,
    );
  } catch (error) {
    if (error instanceof UpstreamTimeoutError) {
      throw new EdgeFunctionError("UPSTREAM_TIMEOUT");
    }
    throw new EdgeFunctionError("GOOGLE_TOKEN_FAILURE");
  }
  if (!response.ok) throw new EdgeFunctionError("GOOGLE_TOKEN_FAILURE");
  try {
    const payload = await response.json() as {
      access_token?: unknown;
      token_type?: unknown;
    };
    if (
      typeof payload.access_token !== "string" ||
      payload.access_token.length === 0 ||
      typeof payload.token_type !== "string" ||
      payload.token_type.toLowerCase() !== "bearer"
    ) {
      throw new EdgeFunctionError("GOOGLE_TOKEN_RESPONSE_INVALID");
    }
    return payload.access_token;
  } catch (error) {
    if (error instanceof EdgeFunctionError) throw error;
    throw new EdgeFunctionError("GOOGLE_TOKEN_RESPONSE_INVALID");
  }
}

export async function verifySpreadsheetAccess(
  accessToken: string,
  spreadsheetId: string,
  options: { fetchImpl?: FetchImplementation; timeoutMs: number },
): Promise<void> {
  const fetchImpl = options.fetchImpl ?? fetch;
  const url = `https://sheets.googleapis.com/v4/spreadsheets/${
    encodeURIComponent(spreadsheetId)
  }?fields=spreadsheetId`;
  let response: Response;
  try {
    response = await fetchWithTimeout(
      fetchImpl,
      url,
      { method: "GET", headers: { Authorization: `Bearer ${accessToken}` } },
      options.timeoutMs,
    );
  } catch (error) {
    if (error instanceof UpstreamTimeoutError) {
      throw new EdgeFunctionError("UPSTREAM_TIMEOUT");
    }
    throw new EdgeFunctionError("GOOGLE_API_FAILURE");
  }
  if (response.ok) return;
  if (response.status === 403) {
    throw new EdgeFunctionError("GOOGLE_SHEETS_ACCESS_DENIED");
  }
  if (response.status === 404) {
    throw new EdgeFunctionError("GOOGLE_SHEETS_NOT_FOUND");
  }
  if (response.status === 429) {
    throw new EdgeFunctionError("GOOGLE_API_RATE_LIMITED");
  }
  throw new EdgeFunctionError("GOOGLE_API_FAILURE");
}
