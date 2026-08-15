import test from "node:test";
import assert from "node:assert/strict";
import { BelvoClient } from "../src/belvoClient.mjs";

const config = {
  belvoBaseUrl: "https://sandbox.belvo.com",
  belvoSecretId: "id",
  belvoSecretPassword: "password",
};

test("exclusão do link usa o modo assíncrono recomendado pela Belvo", async () => {
  let request;
  const client = new BelvoClient(config, async (url, options) => {
    request = { url: String(url), options };
    return new Response(JSON.stringify({ request_id: "delete-request" }), {
      status: 202,
      headers: { "Content-Type": "application/json" },
    });
  });

  const result = await client.deleteLink("00000000-0000-0000-0000-000000000001");

  assert.equal(request.options.method, "DELETE");
  assert.equal(request.options.headers["X-Belvo-Request-Mode"], "async");
  assert.equal(result.request_id, "delete-request");
});
