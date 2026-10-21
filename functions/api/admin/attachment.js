import { authenticateAdmin } from "../../_lib/admin-auth.js";

const ATTACHMENT_KEY_PATTERN = /^feedback\/[A-Za-z0-9_-]{1,64}\/[A-Za-z0-9_-]{1,64}\.(jpg|png|gif|webp|mp4|webm|mov|3gp)$/i;
const CONTENT_TYPES = {
  jpg: "image/jpeg",
  png: "image/png",
  gif: "image/gif",
  webp: "image/webp",
  mp4: "video/mp4",
  webm: "video/webm",
  mov: "video/quicktime",
  "3gp": "video/3gpp",
};

function json(status, body) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Cache-Control": "no-store",
      "Content-Type": "application/json; charset=utf-8",
      "X-Content-Type-Options": "nosniff",
    },
  });
}

function authResponse(auth) {
  return json(auth.status, { error: { code: "admin_auth_failed", message: "Not authorized." } });
}

export async function onRequest(context) {
  const auth = await authenticateAdmin(context.request, context.env);
  if (!auth.ok) return authResponse(auth);
  if (context.request.method !== "GET") return json(405, { error: { code: "method_not_allowed", message: "Method not allowed." } });
  if (!context.env.FEEDBACK_DB || !context.env.FEEDBACK_MEDIA) {
    return json(503, { error: { code: "admin_storage_not_configured", message: "Attachments are unavailable." } });
  }

  const key = new URL(context.request.url).searchParams.get("key") || "";
  if (!ATTACHMENT_KEY_PATTERN.test(key)) return json(404, { error: { code: "attachment_not_found", message: "Attachment not found." } });

  let row;
  try {
    row = await context.env.FEEDBACK_DB
      .prepare("SELECT attachment_key FROM feedback_submissions WHERE attachment_key = ? LIMIT 1")
      .bind(key)
      .first();
  } catch {
    return json(503, { error: { code: "admin_attachment_query_failed", message: "Attachment is unavailable." } });
  }
  if (!row) return json(404, { error: { code: "attachment_not_found", message: "Attachment not found." } });

  let object;
  try {
    object = await context.env.FEEDBACK_MEDIA.get(key);
  } catch {
    return json(503, { error: { code: "admin_attachment_read_failed", message: "Attachment is unavailable." } });
  }
  if (!object) return json(404, { error: { code: "attachment_not_found", message: "Attachment not found." } });

  const extension = key.slice(key.lastIndexOf(".") + 1).toLowerCase();
  const headers = {
    "Cache-Control": "no-store",
    "Content-Type": CONTENT_TYPES[extension],
    "Content-Disposition": "inline",
    "X-Content-Type-Options": "nosniff",
  };
  if (Number.isSafeInteger(object.size) && object.size >= 0) headers["Content-Length"] = String(object.size);
  return new Response(object.body, { status: 200, headers });
}
