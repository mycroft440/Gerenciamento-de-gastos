import crypto from "node:crypto";

function b64url(value) {
  return Buffer.from(value).toString("base64url");
}

function sign(payload, key) {
  return crypto.createHmac("sha256", key).update(payload).digest("base64url");
}

export function createSessionManager({ accessCode, signingKey, ttlSeconds = 1800 }) {
  function issue(providedCode) {
    const expected = Buffer.from(accessCode);
    const provided = Buffer.from(String(providedCode ?? ""));
    if (expected.length !== provided.length || !crypto.timingSafeEqual(expected, provided)) {
      return null;
    }

    const now = Math.floor(Date.now() / 1000);
    const body = b64url(JSON.stringify({ iat: now, exp: now + ttlSeconds }));
    return `${body}.${sign(body, signingKey)}`;
  }

  function verify(token) {
    if (!token || !token.includes(".")) return false;
    const [body, signature] = token.split(".");
    const expectedSignature = sign(body, signingKey);
    const a = Buffer.from(signature);
    const b = Buffer.from(expectedSignature);
    if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) return false;

    try {
      const payload = JSON.parse(Buffer.from(body, "base64url").toString("utf8"));
      return Number(payload.exp) > Math.floor(Date.now() / 1000);
    } catch {
      return false;
    }
  }

  return { issue, verify, ttlSeconds };
}
