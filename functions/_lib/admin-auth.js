const ACCESS_JWT_HEADER = "Cf-Access-Jwt-Assertion";
const CERTIFICATE_CACHE_TTL_MS = 5 * 60 * 1000;
const MAX_JWT_LENGTH = 16 * 1024;

let cachedCertificates = null;
let cachedCertificateOrigin = "";
let cachedCertificatesUntil = 0;
let certificateRequest = null;

function configuredTeamOrigin(value) {
  if (typeof value !== "string" || value.trim() === "") return "";

  try {
    const parsed = new URL(value.trim());
    if (
      parsed.protocol !== "https:" ||
      parsed.username ||
      parsed.password ||
      parsed.pathname !== "/" ||
      parsed.search ||
      parsed.hash
    ) {
      return "";
    }
    return parsed.origin;
  } catch {
    return "";
  }
}

function configuredEmail(value) {
  if (typeof value !== "string") return "";
  const email = value.trim().toLowerCase();
  return email && email.length <= 254 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) ? email : "";
}

export function getAdminAuthConfig(env) {
  const teamOrigin = configuredTeamOrigin(env?.FEEDBACK_ACCESS_TEAM_DOMAIN);
  const audience = typeof env?.FEEDBACK_ACCESS_AUD === "string"
    ? env.FEEDBACK_ACCESS_AUD.trim()
    : "";
  const administratorEmail = configuredEmail(env?.FEEDBACK_ADMIN_EMAIL);

  if (!teamOrigin || !audience || !administratorEmail) return null;
  return { teamOrigin, audience, administratorEmail };
}

function failure(status, code) {
  return { ok: false, status, code };
}

function decodeBase64Url(value) {
  if (typeof value !== "string" || !/^[A-Za-z0-9_-]+$/.test(value)) return null;
  try {
    const padding = "=".repeat((4 - (value.length % 4)) % 4);
    const binary = atob(value.replace(/-/g, "+").replace(/_/g, "/") + padding);
    return Uint8Array.from(binary, (character) => character.charCodeAt(0));
  } catch {
    return null;
  }
}

function decodeJsonSegment(value) {
  const bytes = decodeBase64Url(value);
  if (!bytes) return null;
  try {
    return JSON.parse(new TextDecoder().decode(bytes));
  } catch {
    return null;
  }
}

function decodeJwt(token) {
  if (typeof token !== "string" || token.length > MAX_JWT_LENGTH) return null;
  const parts = token.split(".");
  if (parts.length !== 3 || parts.some((part) => part.length === 0)) return null;

  const header = decodeJsonSegment(parts[0]);
  const payload = decodeJsonSegment(parts[1]);
  const signature = decodeBase64Url(parts[2]);
  if (!header || !payload || !signature) return null;
  return { header, payload, signature, signingInput: `${parts[0]}.${parts[1]}` };
}

function isUsableJwk(jwk) {
  return Boolean(
    jwk &&
    jwk.kty === "RSA" &&
    (!jwk.alg || jwk.alg === "RS256") &&
    typeof jwk.kid === "string" &&
    jwk.kid.length > 0 &&
    typeof jwk.n === "string" &&
    typeof jwk.e === "string",
  );
}

async function fetchCertificates(teamOrigin) {
  let response;
  try {
    response = await fetch(`${teamOrigin}/cdn-cgi/access/certs`, {
      headers: { Accept: "application/json" },
    });
  } catch {
    throw new Error("certificate_fetch_failed");
  }
  if (!response.ok) throw new Error("certificate_fetch_failed");

  let body;
  try {
    body = await response.json();
  } catch {
    throw new Error("certificate_response_invalid");
  }
  if (!Array.isArray(body?.keys)) throw new Error("certificate_response_invalid");

  const certificates = new Map();
  for (const jwk of body.keys) {
    if (isUsableJwk(jwk)) certificates.set(jwk.kid, jwk);
  }
  if (certificates.size === 0) throw new Error("certificate_response_invalid");
  return certificates;
}

async function getCertificates(teamOrigin, forceRefresh = false) {
  const now = Date.now();
  if (
    !forceRefresh &&
    cachedCertificateOrigin === teamOrigin &&
    cachedCertificates &&
    cachedCertificatesUntil > now
  ) {
    return cachedCertificates;
  }

  if (!certificateRequest) {
    certificateRequest = fetchCertificates(teamOrigin)
      .then((certificates) => {
        cachedCertificates = certificates;
        cachedCertificateOrigin = teamOrigin;
        cachedCertificatesUntil = Date.now() + CERTIFICATE_CACHE_TTL_MS;
        return certificates;
      })
      .finally(() => {
        certificateRequest = null;
      });
  }
  return certificateRequest;
}

async function verifySignature(jwt, jwk) {
  try {
    const key = await crypto.subtle.importKey(
      "jwk",
      jwk,
      { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
      false,
      ["verify"],
    );
    return await crypto.subtle.verify(
      { name: "RSASSA-PKCS1-v1_5" },
      key,
      jwt.signature,
      new TextEncoder().encode(jwt.signingInput),
    );
  } catch {
    return false;
  }
}

function audienceMatches(value, expected) {
  return value === expected || (Array.isArray(value) && value.includes(expected));
}

export async function authenticateAdmin(request, env) {
  const config = getAdminAuthConfig(env);
  if (!config) return failure(503, "admin_auth_not_configured");

  const token = request.headers.get(ACCESS_JWT_HEADER);
  if (!token) return failure(401, "admin_auth_required");

  const jwt = decodeJwt(token);
  if (!jwt || jwt.header.alg !== "RS256" || typeof jwt.header.kid !== "string") {
    return failure(401, "admin_jwt_invalid");
  }

  let certificates;
  try {
    certificates = await getCertificates(config.teamOrigin);
  } catch {
    return failure(503, "admin_key_fetch_failed");
  }

  let jwk = certificates.get(jwt.header.kid);
  if (!jwk) {
    try {
      certificates = await getCertificates(config.teamOrigin, true);
      jwk = certificates.get(jwt.header.kid);
    } catch {
      return failure(503, "admin_key_fetch_failed");
    }
  }
  if (!jwk || !(await verifySignature(jwt, jwk))) {
    return failure(401, "admin_jwt_invalid");
  }

  const now = Math.floor(Date.now() / 1000);
  const issuerIsValid = jwt.payload.iss === config.teamOrigin || jwt.payload.iss === `${config.teamOrigin}/`;
  if (!issuerIsValid) return failure(403, "admin_jwt_issuer_invalid");
  if (!audienceMatches(jwt.payload.aud, config.audience)) return failure(403, "admin_jwt_audience_invalid");
  if (typeof jwt.payload.exp !== "number" || !Number.isFinite(jwt.payload.exp) || jwt.payload.exp <= now) {
    return failure(401, "admin_jwt_expired");
  }
  if (typeof jwt.payload.nbf === "number" && Number.isFinite(jwt.payload.nbf) && jwt.payload.nbf > now) {
    return failure(401, "admin_jwt_not_active");
  }

  const email = typeof jwt.payload.email === "string" ? jwt.payload.email.trim().toLowerCase() : "";
  if (!email || email !== config.administratorEmail) return failure(403, "admin_email_not_allowed");

  return { ok: true, claims: jwt.payload };
}
