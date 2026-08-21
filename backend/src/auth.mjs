import crypto from "node:crypto";

const SUBJECT_PATTERN = /^[A-Za-z0-9_-]{3,256}$/;

function b64url(value) {
  return Buffer.from(value).toString("base64url");
}

function sign(payload, key) {
  return crypto.createHmac("sha256", key).update(payload).digest("base64url");
}

function digest(value) {
  return crypto.createHash("sha256").update(String(value ?? ""), "utf8").digest();
}

function safeEqualText(left, right) {
  return crypto.timingSafeEqual(digest(left), digest(right));
}

export function isValidSessionSubject(value) {
  return SUBJECT_PATTERN.test(String(value ?? ""));
}

export function createSessionManager({ accessCode, signingKey, ttlSeconds = 900 }) {
  function issue(providedCode, subject) {
    if (!isValidSessionSubject(subject) || !safeEqualText(accessCode, providedCode)) return null;

    const now = Math.floor(Date.now() / 1000);
    const body = b64url(JSON.stringify({
      ver: 1,
      sub: subject,
      iat: now,
      exp: now + ttlSeconds,
      jti: crypto.randomUUID(),
    }));
    return `${body}.${sign(body, signingKey)}`;
  }

  function verify(token) {
    if (!token || typeof token !== "string") return null;
    const parts = token.split(".");
    if (parts.length !== 2 || !parts[0] || !parts[1]) return null;
    const [body, signature] = parts;
    if (!safeEqualText(signature, sign(body, signingKey))) return null;

    try {
      const payload = JSON.parse(Buffer.from(body, "base64url").toString("utf8"));
      const now = Math.floor(Date.now() / 1000);
      if (payload.ver !== 1 || !isValidSessionSubject(payload.sub)) return null;
      if (!Number.isInteger(payload.iat) || !Number.isInteger(payload.exp)) return null;
      if (payload.iat > now + 60 || payload.exp <= now || payload.exp <= payload.iat) return null;
      return payload;
    } catch {
      return null;
    }
  }

  return { issue, verify, ttlSeconds };
}
