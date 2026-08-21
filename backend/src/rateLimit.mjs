export class SlidingWindowRateLimiter {
  constructor({ maxAttempts = 5, windowMs = 5 * 60_000 } = {}) {
    this.maxAttempts = maxAttempts;
    this.windowMs = windowMs;
    this.entries = new Map();
  }

  consume(key, now = Date.now()) {
    const cutoff = now - this.windowMs;
    const history = (this.entries.get(key) ?? []).filter((timestamp) => timestamp > cutoff);
    if (history.length >= this.maxAttempts) {
      const retryAfterMs = Math.max(1, history[0] + this.windowMs - now);
      this.entries.set(key, history);
      return { allowed: false, retryAfterSeconds: Math.ceil(retryAfterMs / 1000) };
    }
    history.push(now);
    this.entries.set(key, history);
    return { allowed: true, retryAfterSeconds: 0 };
  }

  reset(key) {
    this.entries.delete(key);
  }
}
