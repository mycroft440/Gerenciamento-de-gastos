import test from "node:test";
import assert from "node:assert/strict";
import { SlidingWindowRateLimiter } from "../src/rateLimit.mjs";

test("bloqueia após o limite e libera ao expirar a janela", () => {
  const limiter = new SlidingWindowRateLimiter({ maxAttempts: 2, windowMs: 1000 });
  assert.equal(limiter.consume("ip", 0).allowed, true);
  assert.equal(limiter.consume("ip", 100).allowed, true);
  const blocked = limiter.consume("ip", 200);
  assert.equal(blocked.allowed, false);
  assert.equal(blocked.retryAfterSeconds, 1);
  assert.equal(limiter.consume("ip", 1101).allowed, true);
});

test("reset limpa tentativas após login bem-sucedido", () => {
  const limiter = new SlidingWindowRateLimiter({ maxAttempts: 1, windowMs: 1000 });
  assert.equal(limiter.consume("ip", 0).allowed, true);
  assert.equal(limiter.consume("ip", 1).allowed, false);
  limiter.reset("ip");
  assert.equal(limiter.consume("ip", 2).allowed, true);
});
