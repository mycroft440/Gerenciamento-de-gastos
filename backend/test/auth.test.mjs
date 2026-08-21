import test from "node:test";
import assert from "node:assert/strict";
import { createSessionManager, isValidSessionSubject } from "../src/auth.mjs";

test("sessão só é emitida com código correto e external id válido", () => {
  const sessions = createSessionManager({
    accessCode: "codigo-super-forte",
    signingKey: "0123456789abcdef0123456789abcdef",
    ttlSeconds: 900,
  });

  assert.equal(isValidSessionSubject("device_123-abc"), true);
  assert.equal(isValidSessionSubject("nome com espaço"), false);
  assert.equal(sessions.issue("errado", "device_123-abc"), null);
  assert.equal(sessions.issue("codigo-super-forte", "nome com espaço"), null);

  const token = sessions.issue("codigo-super-forte", "device_123-abc");
  const payload = sessions.verify(token);
  assert.equal(payload.sub, "device_123-abc");
  assert.equal(payload.ver, 1);
});

test("assinatura adulterada é rejeitada", () => {
  const sessions = createSessionManager({
    accessCode: "codigo-super-forte",
    signingKey: "0123456789abcdef0123456789abcdef",
    ttlSeconds: 900,
  });
  const token = sessions.issue("codigo-super-forte", "device-123");
  assert.equal(sessions.verify(`${token}x`), null);
});
