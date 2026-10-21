import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { after, test } from "node:test";
import { generateKeyPair, sign } from "node:crypto";
import { promisify } from "node:util";
import { pathToFileURL } from "node:url";
import vm from "node:vm";

const generateKeyPairAsync = promisify(generateKeyPair);
const root = new URL("../", import.meta.url);
const adminPage = await import(new URL("functions/admin.js", root));
const feedbackApi = await import(new URL("functions/api/admin/feedback.js", root));
const attachmentApi = await import(new URL("functions/api/admin/attachment.js", root));

const TEAM_ORIGIN = "https://pointiq.cloudflareaccess.com";
const AUDIENCE = "pointiq-feedback-admin";
const ADMIN_EMAIL = "owner@example.com";

const { privateKey, publicKey } = await generateKeyPairAsync("rsa", { modulusLength: 2048 });
const publicJwk = publicKey.export({ format: "jwk" });
publicJwk.kid = "admin-test-key";

function base64Url(value) {
  return Buffer.from(value).toString("base64url");
}

async function makeToken(overrides = {}) {
  const header = { alg: "RS256", kid: "admin-test-key", typ: "JWT" };
  const payload = {
    iss: TEAM_ORIGIN,
    aud: [AUDIENCE],
    email: ADMIN_EMAIL,
    exp: Math.floor(Date.now() / 1000) + 300,
    ...overrides,
  };
  const encodedHeader = base64Url(JSON.stringify(header));
  const encodedPayload = base64Url(JSON.stringify(payload));
  const signingInput = `${encodedHeader}.${encodedPayload}`;
  const signature = await new Promise((resolve, reject) => {
    sign("RSA-SHA256", Buffer.from(signingInput), privateKey, (error, result) => (
      error ? reject(error) : resolve(result.toString("base64url"))
    ));
  });
  return `${signingInput}.${signature}`;
}

function env(overrides = {}) {
  return {
    FEEDBACK_ACCESS_TEAM_DOMAIN: TEAM_ORIGIN,
    FEEDBACK_ACCESS_AUD: AUDIENCE,
    FEEDBACK_ADMIN_EMAIL: ADMIN_EMAIL,
    ...overrides,
  };
}

function request(path, token, options = {}) {
  const headers = new Headers(options.headers);
  if (token) headers.set("Cf-Access-Jwt-Assertion", token);
  return new Request(`https://feedback.point-iq.com${path}`, {
    method: options.method || "GET",
    headers,
  });
}

function context(routeRequest, routeEnv) {
  return { request: routeRequest, env: routeEnv };
}

const originalFetch = globalThis.fetch;
globalThis.fetch = async () => new Response(JSON.stringify({ keys: [publicJwk] }), {
  status: 200,
  headers: { "Content-Type": "application/json" },
});

test("admin page rejects unauthenticated and serves valid Access JWT requests", async () => {
  const missing = await adminPage.onRequest(context(request("/admin"), env()));
  assert.equal(missing.status, 401);

  const valid = await makeToken();
  const response = await adminPage.onRequest(context(request("/admin", valid), env()));
  assert.equal(response.status, 200);
  assert.match(await response.text(), /PointIQ Feedback/);
});

test("admin auth rejects forged, expired, and wrong-email JWTs", async () => {
  const valid = await makeToken();
  const forgedPayload = base64Url(JSON.stringify({
    iss: TEAM_ORIGIN,
    aud: [AUDIENCE],
    email: "forged@example.com",
    exp: Math.floor(Date.now() / 1000) + 300,
  }));
  const validParts = valid.split(".");
  const forged = `${validParts[0]}.${forgedPayload}.${validParts[2]}`;
  assert.equal((await adminPage.onRequest(context(request("/admin", forged), env()))).status, 401);

  const expired = await makeToken({ exp: Math.floor(Date.now() / 1000) - 1 });
  assert.equal((await adminPage.onRequest(context(request("/admin", expired), env()))).status, 401);

  const wrongAudience = await makeToken({ aud: ["other-application"] });
  assert.equal((await adminPage.onRequest(context(request("/admin", wrongAudience), env()))).status, 403);

  const wrongIssuer = await makeToken({ iss: "https://other.cloudflareaccess.com" });
  assert.equal((await adminPage.onRequest(context(request("/admin", wrongIssuer), env()))).status, 403);

  const wrongEmail = await makeToken({ email: "other@example.com" });
  assert.equal((await adminPage.onRequest(context(request("/admin", wrongEmail), env()))).status, 403);
});

test("missing Access configuration fails closed", async () => {
  const token = await makeToken();
  const response = await adminPage.onRequest(context(request("/admin", token), env({ FEEDBACK_ACCESS_AUD: "" })));
  assert.equal(response.status, 503);
});

test("feedback API returns stable newest-first pages and a cursor", async () => {
  const token = await makeToken();
  const rows = Array.from({ length: 26 }, (_, index) => ({
    id: `id-${String(index).padStart(2, "0")}`,
    created_at: new Date(Date.UTC(2026, 9, 21, 12, 0, 0) - index * 60_000).toISOString(),
    category: index === 1 ? "problem" : "general",
    category_value: index === 1 ? "app-crash" : "good",
    description: index === 1 ? "Problem report" : `Feedback ${index}`,
    email: index === 1 ? "owner@example.com" : null,
    attachment_key: index === 1 ? "feedback/id-01/file.mp4" : null,
  }));
  let preparedSql = "";
  const db = {
    prepare(sql) {
      preparedSql = sql;
      return { bind: (...bindings) => ({ all: async () => ({ success: true, results: bindings.length === 1 ? rows : rows.slice(25) }) }) };
    },
  };
  const response = await feedbackApi.onRequest(context(request("/api/admin/feedback", token), env({ FEEDBACK_DB: db })));
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.items.length, 25);
  assert.equal(body.items[0].description, "Feedback 0");
  assert.equal(body.items[1].attachmentKind, "video");
  assert.ok(body.nextCursor);
  assert.match(preparedSql, /ORDER BY created_at DESC, id DESC/);

  const nextPage = await feedbackApi.onRequest(context(request(`/api/admin/feedback?cursor=${encodeURIComponent(body.nextCursor)}`, token), env({ FEEDBACK_DB: db })));
  assert.equal(nextPage.status, 200);
  const nextBody = await nextPage.json();
  assert.equal(nextBody.items.length, 1);
  assert.equal(nextBody.nextCursor, null);

  const invalidCursor = await feedbackApi.onRequest(context(request("/api/admin/feedback?cursor=bad!", token), env({ FEEDBACK_DB: db })));
  assert.equal(invalidCursor.status, 400);
});

test("attachment API requires auth and reads only a D1-referenced private object", async () => {
  const key = "feedback/id-1/file.png";
  const db = {
    prepare() {
      return { bind: () => ({ first: async () => ({ attachment_key: key }) }) };
    },
  };
  const media = {
    get: async (requestedKey) => ({ size: 4, body: new Blob(["data"]).stream(), requestedKey }),
  };
  const unauthenticated = await attachmentApi.onRequest(context(request(`/api/admin/attachment?key=${encodeURIComponent(key)}`), env({ FEEDBACK_DB: db, FEEDBACK_MEDIA: media })));
  assert.equal(unauthenticated.status, 401);

  const token = await makeToken();
  const response = await attachmentApi.onRequest(context(request(`/api/admin/attachment?key=${encodeURIComponent(key)}`, token), env({ FEEDBACK_DB: db, FEEDBACK_MEDIA: media })));
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("cache-control"), "no-store");
  assert.equal(response.headers.get("content-type"), "image/png");
  assert.equal(await response.text(), "data");

  const missing = await attachmentApi.onRequest(context(request("/api/admin/attachment?key=feedback%2Fother%2Fmissing.png", token), env({
    FEEDBACK_DB: { prepare: () => ({ bind: () => ({ first: async () => null }) }) },
    FEEDBACK_MEDIA: media,
  })));
  assert.equal(missing.status, 404);
});

test("a single feedback entry keeps Load more hidden and repeated clicks do not refetch", async () => {
  const source = await readFile(new URL("functions/admin.js", root), "utf8");
  assert.match(source, /#load-more\[hidden\]\s*\{\s*display:\s*none !important;/);
  const script = source.match(/<script>\s*([\s\S]*?)\s*<\/script>/)?.[1];
  assert.ok(script);

  class FakeElement {
    constructor() {
      this.children = [];
      this.listeners = new Map();
      this.hidden = false;
      this.disabled = false;
      this._textContent = "";
    }

    append(...children) {
      this.children.push(...children);
    }

    addEventListener(type, listener) {
      this.listeners.set(type, listener);
    }

    click() {
      this.listeners.get("click")?.();
    }

    set textContent(value) {
      this._textContent = String(value);
      if (this._textContent === "") this.children = [];
    }

    get textContent() {
      return this._textContent;
    }
  }

  const entries = new FakeElement();
  const loadMore = new FakeElement();
  loadMore.hidden = true;
  const status = new FakeElement();
  const elements = { entries, "load-more": loadMore, status };
  const document = {
    getElementById: (id) => elements[id],
    createElement: () => new FakeElement(),
  };
  let fetchCount = 0;
  const fetch = async () => {
    fetchCount += 1;
    return new Response(JSON.stringify({
      items: [{ createdAt: "2026-10-21T12:00:00.000Z", category: "general", categoryValue: "good", description: "One entry", email: null, attachmentUrl: null, attachmentKind: null }],
      nextCursor: null,
    }), { status: 200, headers: { "Content-Type": "application/json" } });
  };

  vm.runInNewContext(script, {
    document,
    fetch,
    URL,
    window: { location: { origin: "https://feedback.point-iq.com" } },
  });
  await new Promise((resolve) => setImmediate(resolve));

  assert.equal(fetchCount, 1);
  assert.equal(entries.children.length, 1);
  assert.equal(loadMore.hidden, true);

  loadMore.click();
  loadMore.click();
  assert.equal(fetchCount, 1);
  assert.equal(entries.children.length, 1);
});

test("admin page renders feedback through textContent instead of HTML injection", async () => {
  const source = await readFile(new URL("functions/admin.js", root), "utf8");
  assert.match(source, /element\.textContent = value/);
  assert.doesNotMatch(source, /innerHTML/);
});

after(() => {
  globalThis.fetch = originalFetch;
});
