import assert from "node:assert/strict";
import { test } from "node:test";
import { webcrypto } from "node:crypto";
import { readFile } from "node:fs/promises";

if (!globalThis.crypto) globalThis.crypto = webcrypto;

const feedbackSource = await readFile(new URL("../functions/api/feedback.js", import.meta.url), "utf8");
const configSource = await readFile(new URL("../functions/api/feedback-config.js", import.meta.url), "utf8");
const feedback = await import(`data:text/javascript,${encodeURIComponent(feedbackSource)}`);
const feedbackConfig = await import(`data:text/javascript,${encodeURIComponent(configSource)}`);

const ORIGIN = "https://feedback.point-iq.com";

function createD1({ insertError = false } = {}) {
  const submissions = new Map();
  const buckets = new Map();
  const batchCalls = [];

  function bindStatement(sql, args) {
    return {
      sql,
      args,
      async first() {
        if (sql.startsWith("SELECT id FROM feedback_submissions")) {
          return submissions.has(args[0]) ? { id: "existing" } : null;
        }
        throw new Error(`Unexpected first() query: ${sql}`);
      },
      async run() {
        if (!sql.includes("INSERT INTO feedback_submissions")) {
          throw new Error(`Unexpected run() query: ${sql}`);
        }
        if (insertError) throw new Error("D1 insert unavailable");
        if (submissions.has(args[1])) {
          throw new Error("UNIQUE constraint failed: feedback_submissions.client_submission_id");
        }
        submissions.set(args[1], { id: args[0] });
        return { success: true, results: [], meta: { changes: 1 } };
      },
    };
  }

  return {
    batchCalls,
    prepare(sql) {
      return { bind: (...args) => bindStatement(sql, args) };
    },
    async batch(statements) {
      assert.equal(statements.length, 3);
      const [upsert, select, cleanup] = statements;
      assert.match(upsert.sql, /ON CONFLICT\(bucket_key\)/);
      assert.doesNotMatch(upsert.sql, /RETURNING/i);
      assert.match(select.sql, /SELECT request_count/);
      assert.match(cleanup.sql, /DELETE FROM feedback_rate_limits/);
      batchCalls.push(statements.map((statement) => statement.sql));

      const [bucketKey, windowStart] = upsert.args;
      const previous = buckets.get(bucketKey);
      const requestCount = previous?.windowStart === windowStart ? previous.count + 1 : 1;
      buckets.set(bucketKey, { windowStart, count: requestCount });

      for (const [key, bucket] of buckets) {
        if (bucket.windowStart < cleanup.args[0]) buckets.delete(key);
      }

      return [
        { success: true, results: [], meta: { changes: 1 } },
        { success: true, results: [{ request_count: requestCount }] },
        { success: true, results: [], meta: { changes: 0 } },
      ];
    },
  };
}

function createMedia() {
  const stored = new Map();
  const deleted = [];
  return {
    stored,
    deleted,
    async put(key, value) {
      stored.set(key, value);
    },
    async delete(key) {
      deleted.push(key);
      stored.delete(key);
    },
  };
}

function createEnv(options = {}) {
  return {
    FEEDBACK_SUBMISSIONS_ENABLED: "true",
    FEEDBACK_DB: createD1(options),
    FEEDBACK_MEDIA: createMedia(),
    TURNSTILE_SECRET_KEY: "test-secret",
    TURNSTILE_SITE_KEY: "test-site-key",
    FEEDBACK_RATE_LIMIT_SALT: "test-salt",
    FEEDBACK_ALLOWED_ORIGIN: ORIGIN,
  };
}

function createRequest({ submissionId = crypto.randomUUID(), ip = "203.0.113.10", attachment = false } = {}) {
  const form = new FormData();
  form.set("category", "problem");
  form.set("categoryValue", "app-crash");
  form.set("description", "The test app crashed after opening match results.");
  form.set("email", "");
  form.set("submissionId", submissionId);
  form.set("turnstileToken", "test-turnstile-token");
  if (attachment) {
    form.append(
      "attachment",
      new Blob([new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])], {
        type: "image/png",
      }),
      "crash.png",
    );
  }

  return new Request(`${ORIGIN}/api/feedback`, {
    method: "POST",
    headers: {
      Origin: ORIGIN,
      "CF-Connecting-IP": ip,
    },
    body: form,
  });
}

const originalFetch = globalThis.fetch;
globalThis.fetch = async () =>
  new Response(JSON.stringify({ success: true, hostname: "feedback.point-iq.com" }), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });

test.after(() => {
  globalThis.fetch = originalFetch;
});

test("streamed oversized bodies are rejected before multipart parsing", async () => {
  const env = createEnv();
  const stream = new ReadableStream({
    start(controller) {
      controller.enqueue(new Uint8Array(52 * 1024 * 1024 + 1));
      controller.close();
    },
  });
  const request = new Request(`${ORIGIN}/api/feedback`, {
    method: "POST",
    headers: {
      Origin: ORIGIN,
      "Content-Type": "multipart/form-data; boundary=test",
    },
    body: stream,
    duplex: "half",
  });

  const response = await feedback.onRequest({ request, env });
  assert.equal(response.status, 413);
});

test("rate limiting uses the D1 batch contract and shared counter", async () => {
  const env = createEnv();
  const statuses = [];

  for (let index = 0; index < 6; index += 1) {
    const response = await feedback.onRequest({
      request: createRequest({ ip: "203.0.113.20" }),
      env,
    });
    statuses.push(response.status);
  }

  assert.deepEqual(statuses, [201, 201, 201, 201, 201, 429]);
  assert.equal(env.FEEDBACK_DB.batchCalls.length, 6);
  assert.equal(env.FEEDBACK_DB.batchCalls[0].length, 3);
});

test("failed D1 insertion attempts to remove the uploaded R2 object", async () => {
  const env = createEnv({ insertError: true });
  const response = await feedback.onRequest({
    request: createRequest({ attachment: true }),
    env,
  });

  assert.equal(response.status, 503);
  assert.equal(env.FEEDBACK_MEDIA.deleted.length, 1);
});

test("invalid origins disable the configuration endpoint", async () => {
  const invalidEnvironment = {
    ...createEnv(),
    FEEDBACK_ALLOWED_ORIGIN: "http://feedback.point-iq.com",
  };
  const invalidResponse = await feedbackConfig.onRequest({
    request: new Request(`${ORIGIN}/api/feedback-config`),
    env: invalidEnvironment,
  });
  assert.equal(invalidResponse.status, 200);
  assert.deepEqual(await invalidResponse.json(), { enabled: false, siteKey: null });

  const validResponse = await feedbackConfig.onRequest({
    request: new Request(`${ORIGIN}/api/feedback-config`),
    env: { ...createEnv(), FEEDBACK_ALLOWED_ORIGIN: `${ORIGIN}/` },
  });
  assert.equal(validResponse.status, 200);
  assert.deepEqual(await validResponse.json(), { enabled: true, siteKey: "test-site-key" });
});

test("submissions remain fail-closed when disabled", async () => {
  const env = { ...createEnv(), FEEDBACK_SUBMISSIONS_ENABLED: "false" };
  const response = await feedback.onRequest({ request: createRequest(), env });
  assert.equal(response.status, 503);
});
