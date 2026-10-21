const REQUIRED_CONFIG_KEYS = [
  "FEEDBACK_DB",
  "FEEDBACK_MEDIA",
  "TURNSTILE_SECRET_KEY",
  "TURNSTILE_SITE_KEY",
  "FEEDBACK_RATE_LIMIT_SALT",
  "FEEDBACK_ALLOWED_ORIGIN",
];

function configuredOrigin(value) {
  if (typeof value !== "string" || value.trim() === "") return "";

  try {
    const parsed = new URL(value);
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

function isConfigured(env) {
  return (
    env.FEEDBACK_SUBMISSIONS_ENABLED === "true" &&
    REQUIRED_CONFIG_KEYS.filter((key) => key !== "FEEDBACK_ALLOWED_ORIGIN")
      .every((key) => Boolean(env[key])) &&
    Boolean(configuredOrigin(env.FEEDBACK_ALLOWED_ORIGIN))
  );
}

function allowedOrigin(request, env) {
  const origin = request.headers.get("Origin");
  const originValue = configuredOrigin(env.FEEDBACK_ALLOWED_ORIGIN);

  return !origin || (originValue && origin === originValue);
}

function jsonResponse(body, status, env) {
  const headers = {
    "Cache-Control": "no-store",
    "Content-Type": "application/json; charset=utf-8",
  };
  const origin = configuredOrigin(env.FEEDBACK_ALLOWED_ORIGIN);
  if (origin) {
    headers["Access-Control-Allow-Origin"] = origin;
    headers.Vary = "Origin";
  }
  return new Response(JSON.stringify(body), { status, headers });
}

export function onRequest(context) {
  const { request, env } = context;

  if (request.method !== "GET") {
    return jsonResponse({ enabled: false, siteKey: null }, 405, env);
  }

  if (!allowedOrigin(request, env)) {
    return jsonResponse({ enabled: false, siteKey: null }, 403, env);
  }

  const enabled = isConfigured(env);
  return jsonResponse(
    {
      enabled,
      siteKey: enabled ? env.TURNSTILE_SITE_KEY : null,
    },
    200,
    env,
  );
}
