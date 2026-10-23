import { handleRequest } from "./index.ts";

function assertEquals(
  actual: unknown,
  expected: unknown,
  message = "values differ",
): void {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(
      `${message}: ${JSON.stringify(actual)} !== ${JSON.stringify(expected)}`,
    );
  }
}

function request(method = "POST", body?: string): Request {
  const init: RequestInit = {
    method,
    headers: { "Content-Type": "application/json" },
  };
  if (method === "POST" && body !== undefined) {
    init.body = body;
  }
  return new Request(
    "https://functions.example/signup-email-registration-status",
    init,
  );
}

async function responseJson(response: Response): Promise<unknown> {
  return await response.json();
}

Deno.test(
  "existing, unconfirmed, and unknown emails receive the same compatibility response",
  async () => {
    for (const email of [
      "confirmed@example.test",
      "unconfirmed@example.test",
      "unknown@example.test",
    ]) {
      const response = await handleRequest(
        request("POST", JSON.stringify({ email })),
      );

      assertEquals(response.status, 200);
      assertEquals(await responseJson(response), { registered: false });
    }
  },
);

Deno.test("valid requests do not require server credentials or database access", async () => {
  const response = await handleRequest(
    request("POST", JSON.stringify({ email: "user@example.test" })),
  );

  assertEquals(response.status, 200);
  assertEquals(await responseJson(response), { registered: false });
});

Deno.test("malformed requests retain the existing safe error contract", async () => {
  const invalidJson = await handleRequest(request("POST", "{"));
  assertEquals(invalidJson.status, 400);
  assertEquals((await responseJson(invalidJson)) as Record<string, unknown>, {
    ok: false,
    error: {
      code: "INVALID_JSON",
      message: "The request body must be valid JSON.",
    },
  });

  const invalidPayload = await handleRequest(
    request("POST", JSON.stringify({ email: "" })),
  );
  assertEquals(invalidPayload.status, 400);
  assertEquals(
    (await responseJson(invalidPayload)) as Record<string, unknown>,
    {
      ok: false,
      error: {
        code: "INVALID_REGISTRATION_STATUS_PAYLOAD",
        message: "The email registration status request is invalid.",
      },
    },
  );

  const methodNotAllowed = await handleRequest(request("GET"));
  assertEquals(methodNotAllowed.status, 405);
  assertEquals(
    (await responseJson(methodNotAllowed)) as Record<string, unknown>,
    {
      ok: false,
      error: {
        code: "METHOD_NOT_ALLOWED",
        message: "Only POST requests are supported.",
      },
    },
  );
});
