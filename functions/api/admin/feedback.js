import { authenticateAdmin } from "../../_lib/admin-auth.js";

const PAGE_SIZE = 25;
const MAX_CURSOR_LENGTH = 512;

function jsonHeaders() {
  return {
    "Cache-Control": "no-store",
    "Content-Type": "application/json; charset=utf-8",
    "X-Content-Type-Options": "nosniff",
  };
}

function json(status, body) {
  return new Response(JSON.stringify(body), { status, headers: jsonHeaders() });
}

function authResponse(auth) {
  return json(auth.status, { error: { code: "admin_auth_failed", message: "Not authorized." } });
}

function decodeCursor(value) {
  if (typeof value !== "string" || value.length === 0 || value.length > MAX_CURSOR_LENGTH || !/^[A-Za-z0-9_-]+$/.test(value)) {
    return null;
  }
  try {
    const padding = "=".repeat((4 - (value.length % 4)) % 4);
    const binary = atob(value.replace(/-/g, "+").replace(/_/g, "/") + padding);
    const parsed = JSON.parse(new TextDecoder().decode(Uint8Array.from(binary, (character) => character.charCodeAt(0))));
    if (
      !parsed ||
      typeof parsed.createdAt !== "string" ||
      parsed.createdAt.length === 0 ||
      parsed.createdAt.length > 64 ||
      typeof parsed.id !== "string" ||
      !/^[A-Za-z0-9_-]{1,100}$/.test(parsed.id)
    ) {
      return null;
    }
    return { createdAt: parsed.createdAt, id: parsed.id };
  } catch {
    return null;
  }
}

function encodeCursor(value) {
  const bytes = new TextEncoder().encode(JSON.stringify(value));
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function attachmentKind(key) {
  return /\.(mp4|webm|mov|3gp)$/i.test(key) ? "video" : "image";
}

export async function onRequest(context) {
  const auth = await authenticateAdmin(context.request, context.env);
  if (!auth.ok) return authResponse(auth);
  if (context.request.method !== "GET") return json(405, { error: { code: "method_not_allowed", message: "Method not allowed." } });
  if (!context.env.FEEDBACK_DB) return json(503, { error: { code: "admin_db_not_configured", message: "Feedback is unavailable." } });

  const url = new URL(context.request.url);
  const rawCursor = url.searchParams.get("cursor");
  const cursor = rawCursor === null ? null : decodeCursor(rawCursor);
  if (rawCursor !== null && !cursor) return json(400, { error: { code: "invalid_cursor", message: "Invalid pagination cursor." } });

  const statement = cursor
    ? context.env.FEEDBACK_DB.prepare(
      `SELECT id, created_at, category, category_value, description, email, attachment_key
       FROM feedback_submissions
       WHERE created_at < ? OR (created_at = ? AND id < ?)
       ORDER BY created_at DESC, id DESC
       LIMIT ?`,
    ).bind(cursor.createdAt, cursor.createdAt, cursor.id, PAGE_SIZE + 1)
    : context.env.FEEDBACK_DB.prepare(
      `SELECT id, created_at, category, category_value, description, email, attachment_key
       FROM feedback_submissions
       ORDER BY created_at DESC, id DESC
       LIMIT ?`,
    ).bind(PAGE_SIZE + 1);

  let result;
  try {
    result = await statement.all();
  } catch {
    return json(503, { error: { code: "admin_feedback_query_failed", message: "Feedback is unavailable." } });
  }
  if (!result || result.success !== true || !Array.isArray(result.results)) {
    return json(503, { error: { code: "admin_feedback_query_failed", message: "Feedback is unavailable." } });
  }

  const hasMore = result.results.length > PAGE_SIZE;
  const rows = hasMore ? result.results.slice(0, PAGE_SIZE) : result.results;
  const items = rows.map((row) => ({
    createdAt: row.created_at,
    category: row.category,
    categoryValue: row.category_value,
    description: row.description,
    email: row.email || null,
    attachmentUrl: row.attachment_key ? `/api/admin/attachment?key=${encodeURIComponent(row.attachment_key)}` : null,
    attachmentKind: row.attachment_key ? attachmentKind(row.attachment_key) : null,
  }));
  const lastRow = rows[rows.length - 1];

  return json(200, {
    items,
    nextCursor: hasMore && lastRow ? encodeCursor({ createdAt: lastRow.created_at, id: lastRow.id }) : null,
  });
}
