const TURNSTILE_VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
const MAX_DESCRIPTION_LENGTH = 4000;
const MAX_EMAIL_LENGTH = 254;
const MAX_TOKEN_LENGTH = 2048;
const MAX_REQUEST_BYTES = 52 * 1024 * 1024;
const MAX_IMAGE_BYTES = 10 * 1024 * 1024;
const MAX_VIDEO_BYTES = 50 * 1024 * 1024;
const RATE_LIMIT_WINDOW_MS = 15 * 60 * 1000;
const RATE_LIMIT_MAX_SUBMISSIONS = 5;

const CATEGORY_VALUES = {
  problem: new Set([
    "calculation-ocr",
    "match-results-standings",
    "app-crash",
    "login-cloud-sync",
    "export-download",
    "other",
  ]),
  improvement: new Set([
    "calculation-ocr",
    "match-results-standings",
    "templates-export",
    "app-design-usability",
    "other",
  ]),
  general: new Set(["excellent", "good", "average", "poor"]),
};

const IMAGE_TYPES = new Set(["image/jpeg", "image/png", "image/gif", "image/webp"]);
const VIDEO_TYPES = new Set([
  "video/mp4",
  "video/webm",
  "video/quicktime",
  "video/3gpp",
]);

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

function configured(env) {
  return (
    env.FEEDBACK_SUBMISSIONS_ENABLED === "true" &&
    env.FEEDBACK_DB &&
    env.FEEDBACK_MEDIA &&
    typeof env.TURNSTILE_SECRET_KEY === "string" &&
    env.TURNSTILE_SECRET_KEY &&
    typeof env.TURNSTILE_SITE_KEY === "string" &&
    env.TURNSTILE_SITE_KEY &&
    typeof env.FEEDBACK_RATE_LIMIT_SALT === "string" &&
    env.FEEDBACK_RATE_LIMIT_SALT &&
    Boolean(configuredOrigin(env.FEEDBACK_ALLOWED_ORIGIN))
  );
}

function originIsAllowed(request, env) {
  const origin = configuredOrigin(env.FEEDBACK_ALLOWED_ORIGIN);
  return Boolean(origin) && request.headers.get("Origin") === origin;
}

function responseHeaders(request, env) {
  const headers = {
    "Cache-Control": "no-store",
    "Content-Type": "application/json; charset=utf-8",
  };
  const origin = configuredOrigin(env.FEEDBACK_ALLOWED_ORIGIN);
  if (originIsAllowed(request, env)) {
    headers["Access-Control-Allow-Origin"] = origin;
    headers["Access-Control-Allow-Methods"] = "POST, OPTIONS";
    headers["Access-Control-Allow-Headers"] = "Content-Type, Accept";
    headers.Vary = "Origin";
  }
  return headers;
}

function jsonResponse(request, env, status, code, message) {
  return new Response(JSON.stringify({ error: { code, message } }), {
    status,
    headers: responseHeaders(request, env),
  });
}

function formText(formData, name) {
  const value = formData.get(name);
  return typeof value === "string" ? value : "";
}

function isSafeSubmissionId(value) {
  return /^[A-Za-z0-9_-]{16,100}$/.test(value);
}

function isValidEmail(value) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}

function isFile(value) {
  return Boolean(
    value &&
    typeof value === "object" &&
    typeof value.arrayBuffer === "function" &&
    typeof value.size === "number" &&
    typeof value.type === "string",
  );
}

function startsWithBytes(bytes, expected, offset = 0) {
  return expected.every((byte, index) => bytes[offset + index] === byte);
}

async function hasExpectedMediaSignature(file) {
  const bytes = new Uint8Array(await file.slice(0, 16).arrayBuffer());

  if (file.type === "image/jpeg") return startsWithBytes(bytes, [0xff, 0xd8, 0xff]);
  if (file.type === "image/png") return startsWithBytes(bytes, [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  if (file.type === "image/gif") return startsWithBytes(bytes, [0x47, 0x49, 0x46, 0x38]);
  if (file.type === "image/webp") {
    return startsWithBytes(bytes, [0x52, 0x49, 0x46, 0x46]) && startsWithBytes(bytes, [0x57, 0x45, 0x42, 0x50], 8);
  }
  if (file.type === "video/webm") return startsWithBytes(bytes, [0x1a, 0x45, 0xdf, 0xa3]);
  if (VIDEO_TYPES.has(file.type)) return startsWithBytes(bytes, [0x66, 0x74, 0x79, 0x70], 4);
  return false;
}

async function validateAttachment(file) {
  if (!isFile(file) || !file.size) {
    return { valid: false };
  }

  const isImage = IMAGE_TYPES.has(file.type);
  const isVideo = VIDEO_TYPES.has(file.type);
  const maxBytes = isImage ? MAX_IMAGE_BYTES : MAX_VIDEO_BYTES;
  if ((!isImage && !isVideo) || file.size > maxBytes) {
    return { valid: false };
  }

  return { valid: await hasExpectedMediaSignature(file), isImage, isVideo };
}

function mediaExtension(type) {
  return {
    "image/jpeg": "jpg",
    "image/png": "png",
    "image/gif": "gif",
    "image/webp": "webp",
    "video/mp4": "mp4",
    "video/webm": "webm",
    "video/quicktime": "mov",
    "video/3gpp": "3gp",
  }[type];
}

async function verifyTurnstile(env, token, submissionId) {
  let response;
  try {
    response = await fetch(TURNSTILE_VERIFY_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        secret: env.TURNSTILE_SECRET_KEY,
        response: token,
        idempotency_key: submissionId,
      }),
    });
  } catch {
    return { valid: false, unavailable: true };
  }

  if (!response.ok) {
    return { valid: false, unavailable: true };
  }

  let result;
  try {
    result = await response.json();
  } catch {
    return { valid: false, unavailable: true };
  }

  const origin = configuredOrigin(env.FEEDBACK_ALLOWED_ORIGIN);
  if (!origin) return { valid: false, unavailable: true };
  const expectedHostname = new URL(origin).hostname;

  return {
    valid: result?.success === true && result?.hostname === expectedHostname,
    unavailable: false,
  };
}

async function hashedRateLimitKey(request, env) {
  const source = request.headers.get("CF-Connecting-IP") || "anonymous";
  const input = new TextEncoder().encode(`${env.FEEDBACK_RATE_LIMIT_SALT}:${source}`);
  const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", input));
  return `ip:${Array.from(digest, (byte) => byte.toString(16).padStart(2, "0")).join("")}`;
}

async function rateLimitAllows(request, env) {
  const bucketKey = await hashedRateLimitKey(request, env);
  const windowStart = Math.floor(Date.now() / RATE_LIMIT_WINDOW_MS) * RATE_LIMIT_WINDOW_MS;

  const statements = [
    env.FEEDBACK_DB
      .prepare(
        `INSERT INTO feedback_rate_limits (bucket_key, window_start, request_count)
         VALUES (?, ?, 1)
         ON CONFLICT(bucket_key) DO UPDATE SET
           window_start = excluded.window_start,
           request_count = CASE
             WHEN feedback_rate_limits.window_start = excluded.window_start
               THEN feedback_rate_limits.request_count + 1
             ELSE 1
           END`,
      )
      .bind(bucketKey, windowStart),
    env.FEEDBACK_DB
      .prepare(
        `SELECT request_count
         FROM feedback_rate_limits
         WHERE bucket_key = ? AND window_start = ?
         LIMIT 1`,
      )
      .bind(bucketKey, windowStart),
    env.FEEDBACK_DB
      .prepare(
        `DELETE FROM feedback_rate_limits
         WHERE bucket_key IN (
           SELECT bucket_key
           FROM feedback_rate_limits
           WHERE window_start < ?
           ORDER BY window_start
           LIMIT 100
         )`,
      )
      .bind(windowStart),
  ];

  const results = await env.FEEDBACK_DB.batch(statements);
  if (
    !Array.isArray(results) ||
    results.length !== 3 ||
    results.some((result) => result?.success !== true)
  ) {
    throw new Error("Rate-limit batch failed");
  }

  const row = results[1]?.results?.[0];
  const requestCount = Number(row?.request_count);
  if (!Number.isSafeInteger(requestCount)) {
    throw new Error("Rate-limit count was unavailable");
  }

  return requestCount <= RATE_LIMIT_MAX_SUBMISSIONS;
}

function isDuplicateError(error) {
  return String(error?.message || "").includes("feedback_submissions.client_submission_id");
}

async function existingSubmission(env, submissionId) {
  const row = await env.FEEDBACK_DB
    .prepare("SELECT id FROM feedback_submissions WHERE client_submission_id = ? LIMIT 1")
    .bind(submissionId)
    .first();
  return Boolean(row);
}

async function readRequestWithLimit(request) {
  const rawContentLength = request.headers.get("Content-Length");
  if (rawContentLength !== null) {
    if (!/^\d+$/.test(rawContentLength)) return { invalid: true };
    const contentLength = Number(rawContentLength);
    if (!Number.isSafeInteger(contentLength)) return { invalid: true };
    if (contentLength > MAX_REQUEST_BYTES) return { tooLarge: true };
  }

  if (!request.body) return { invalid: true };

  const reader = request.body.getReader();
  const state = { invalid: false, tooLarge: false, totalBytes: 0 };
  const boundedBody = new ReadableStream({
    async pull(controller) {
      try {
        const { done, value } = await reader.read();
        if (done) {
          controller.close();
          return;
        }
        if (!(value instanceof Uint8Array)) {
          state.invalid = true;
          controller.error(new Error("Invalid request body"));
          return;
        }

        state.totalBytes += value.byteLength;
        if (state.totalBytes > MAX_REQUEST_BYTES) {
          state.tooLarge = true;
          try {
            await reader.cancel();
          } catch {
            // The body is already being rejected; cancellation is best effort.
          }
          controller.error(new Error("Request body is too large"));
          return;
        }
        controller.enqueue(value);
      } catch (error) {
        state.invalid = true;
        controller.error(error);
      }
    },
    async cancel(reason) {
      await reader.cancel(reason);
    },
  });
  const headers = new Headers(request.headers);
  headers.delete("Content-Length");
  return {
    state,
    request: new Request(request.url, {
      method: request.method,
      headers,
      body: boundedBody,
      duplex: "half",
    }),
  };
}

async function cleanupAttachment(bucket, key) {
  if (!key) return true;
  try {
    await bucket.delete(key);
    return true;
  } catch {
    console.error("feedback_attachment_cleanup_failed");
    return false;
  }
}

export async function onRequest(context) {
  const { request, env } = context;

  if (request.method === "OPTIONS") {
    if (!configured(env) || !originIsAllowed(request, env)) {
      return jsonResponse(request, env, 403, "forbidden", "Request not allowed.");
    }
    return new Response(null, { status: 204, headers: responseHeaders(request, env) });
  }

  if (request.method !== "POST") {
    return jsonResponse(request, env, 405, "method_not_allowed", "Request not allowed.");
  }
  if (!configured(env)) {
    return jsonResponse(request, env, 503, "service_unavailable", "Feedback submission is unavailable.");
  }
  if (!originIsAllowed(request, env)) {
    return jsonResponse(request, env, 403, "forbidden", "Request not allowed.");
  }

  const boundedRequest = await readRequestWithLimit(request);
  if (boundedRequest.tooLarge) {
    return jsonResponse(request, env, 413, "request_too_large", "The request is too large.");
  }
  if (boundedRequest.invalid) {
    return jsonResponse(request, env, 400, "invalid_request", "The request could not be processed.");
  }
  if (!(boundedRequest.request.headers.get("Content-Type") || "").toLowerCase().startsWith("multipart/form-data")) {
    return jsonResponse(request, env, 400, "invalid_request", "The request could not be processed.");
  }

  let formData;
  try {
    formData = await boundedRequest.request.formData();
  } catch {
    if (boundedRequest.state?.tooLarge) {
      return jsonResponse(request, env, 413, "request_too_large", "The request is too large.");
    }
    return jsonResponse(request, env, 400, "invalid_request", "The request could not be processed.");
  }

  const category = formText(formData, "category");
  const categoryValue = formText(formData, "categoryValue");
  const description = formText(formData, "description").trim();
  const email = formText(formData, "email").trim();
  const submissionId = formText(formData, "submissionId");
  const turnstileToken = formText(formData, "turnstileToken");
  const attachmentValues = formData.getAll("attachment");
  const attachment = formData.get("attachment");

  if (
    !CATEGORY_VALUES[category] ||
    !CATEGORY_VALUES[category].has(categoryValue) ||
    !description ||
    description.length > MAX_DESCRIPTION_LENGTH ||
    email.length > MAX_EMAIL_LENGTH ||
    (email && !isValidEmail(email)) ||
    !isSafeSubmissionId(submissionId) ||
    !turnstileToken ||
    turnstileToken.length > MAX_TOKEN_LENGTH
  ) {
    return jsonResponse(request, env, 400, "invalid_request", "Check the form and try again.");
  }

  if (attachmentValues.length > 1 || (category !== "problem" && attachment !== null)) {
    return jsonResponse(request, env, 400, "invalid_attachment", "The attachment is not allowed for this form.");
  }

  let attachmentValidation = null;
  if (category === "problem" && attachment !== null) {
    attachmentValidation = await validateAttachment(attachment);
    if (!attachmentValidation.valid) {
      return jsonResponse(request, env, 400, "invalid_attachment", "The attachment is not supported.");
    }
  }

  const turnstile = await verifyTurnstile(env, turnstileToken, submissionId);
  if (turnstile.unavailable) {
    return jsonResponse(request, env, 503, "service_unavailable", "Feedback submission is unavailable.");
  }
  if (!turnstile.valid) {
    return jsonResponse(request, env, 400, "turnstile_failed", "Security verification failed. Try again.");
  }

  try {
    if (!(await rateLimitAllows(request, env))) {
      return jsonResponse(request, env, 429, "rate_limited", "Too many submissions. Try again later.");
    }
    if (await existingSubmission(env, submissionId)) {
      return jsonResponse(request, env, 409, "duplicate_submission", "This submission was already received.");
    }
  } catch {
    return jsonResponse(request, env, 503, "service_unavailable", "Feedback submission is unavailable.");
  }

  const id = crypto.randomUUID();
  const createdAt = new Date().toISOString();
  let attachmentKey = null;

  try {
    if (attachmentValidation) {
      const extension = mediaExtension(attachment.type);
      attachmentKey = `feedback/${id}/${crypto.randomUUID()}.${extension}`;
      await env.FEEDBACK_MEDIA.put(attachmentKey, attachment.stream(), {
        httpMetadata: { contentType: attachment.type },
      });
    }

    await env.FEEDBACK_DB
      .prepare(
        `INSERT INTO feedback_submissions
          (id, client_submission_id, category, category_value, description, email, attachment_key, status, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, 'new', ?)`,
      )
      .bind(
        id,
        submissionId,
        category,
        categoryValue,
        description,
        email || null,
        attachmentKey,
        createdAt,
      )
      .run();
  } catch (error) {
    await cleanupAttachment(env.FEEDBACK_MEDIA, attachmentKey);
    if (isDuplicateError(error)) {
      return jsonResponse(request, env, 409, "duplicate_submission", "This submission was already received.");
    }
    return jsonResponse(request, env, 503, "service_unavailable", "Feedback submission is unavailable.");
  }

  return new Response(JSON.stringify({ ok: true }), {
    status: 201,
    headers: responseHeaders(request, env),
  });
}
