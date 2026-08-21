import http from "node:http";
import crypto from "node:crypto";
import { loadConfig } from "./config.mjs";
import { BelvoClient, isValidCpf, normalizeCpf } from "./belvoClient.mjs";
import { createSessionManager, isValidSessionSubject } from "./auth.mjs";
import { SlidingWindowRateLimiter } from "./rateLimit.mjs";

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const config = loadConfig();
const belvo = new BelvoClient(config);
const sessions = createSessionManager({
  accessCode: config.appAccessCode,
  signingKey: config.sessionSigningKey,
  ttlSeconds: config.sessionTtlSeconds,
});
const authLimiter = new SlidingWindowRateLimiter({
  maxAttempts: config.authMaxAttempts,
  windowMs: config.authWindowMs,
});
const linkReadiness = new Map();
const processedWebhookIds = new Set();

function json(res, status, body, headers = {}) {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(payload),
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff",
    "Referrer-Policy": "no-referrer",
    ...headers,
  });
  res.end(payload);
}

async function readJson(req) {
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > 32 * 1024) throw Object.assign(new Error("body_too_large"), { status: 413 });
    chunks.push(chunk);
  }
  if (!chunks.length) return {};
  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8"));
  } catch {
    throw Object.assign(new Error("invalid_json"), { status: 400 });
  }
}

function bearer(req) {
  const header = req.headers.authorization ?? "";
  return header.startsWith("Bearer ") ? header.slice(7).trim() : "";
}

function safeEqual(a, b) {
  const left = crypto.createHash("sha256").update(String(a ?? "")).digest();
  const right = crypto.createHash("sha256").update(String(b ?? "")).digest();
  return crypto.timingSafeEqual(left, right);
}

function requireSession(req, res) {
  const session = sessions.verify(bearer(req));
  if (!session) {
    json(res, 401, { error: "unauthorized" });
    return null;
  }
  return session;
}

function linkIdFrom(pathname, suffix) {
  const match = pathname.match(new RegExp(`^/v1/open-finance/links/([^/]+)/${suffix}$`));
  const id = match ? decodeURIComponent(match[1]) : null;
  return id && UUID_PATTERN.test(id) ? id : null;
}

function assertStateOwnership(session, state) {
  if (state?.externalId && state.externalId !== session.sub) {
    throw Object.assign(new Error("not_found"), { status: 404 });
  }
}

async function requireOwnedLink(session, linkId) {
  if (!UUID_PATTERN.test(linkId)) throw Object.assign(new Error("not_found"), { status: 404 });
  let link;
  try {
    link = await belvo.getLink(linkId);
  } catch (error) {
    if (error?.status === 404) throw Object.assign(new Error("not_found"), { status: 404 });
    throw error;
  }
  if (!link || link.external_id !== session.sub) throw Object.assign(new Error("not_found"), { status: 404 });
  const current = linkReadiness.get(linkId) ?? {};
  current.externalId = session.sub;
  linkReadiness.set(linkId, current);
  return link;
}

function remoteKey(req) {
  return req.socket.remoteAddress || "unknown";
}

function historicalErrors(body) {
  return Array.isArray(body?.data?.errors)
    ? body.data.errors.map((item) => String(item?.code ?? "unknown_error"))
    : [];
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host ?? "localhost"}`);

  try {
    if (req.method === "GET" && url.pathname === "/health") {
      return json(res, 200, {
        ok: true,
        provider: "belvo",
        environment: config.belvoBaseUrl.includes("sandbox") ? "sandbox" : "production",
      });
    }

    if (req.method === "POST" && url.pathname === "/v1/auth/session") {
      const key = remoteKey(req);
      const limit = authLimiter.consume(key);
      if (!limit.allowed) {
        return json(res, 429, { error: "too_many_attempts" }, { "Retry-After": String(limit.retryAfterSeconds) });
      }
      const body = await readJson(req);
      const externalId = String(body.externalId ?? "");
      if (!isValidSessionSubject(externalId)) return json(res, 400, { error: "invalid_external_id" });
      const token = sessions.issue(body.accessCode, externalId);
      if (!token) return json(res, 401, { error: "invalid_access_code" });
      authLimiter.reset(key);
      return json(res, 200, { token, expiresInSeconds: sessions.ttlSeconds });
    }

    if (req.method === "POST" && url.pathname === "/v1/open-finance/widget-session") {
      const session = requireSession(req, res);
      if (!session) return;
      const body = await readJson(req);
      const name = String(body.name ?? "").trim();
      const cpf = normalizeCpf(body.cpf);
      if (name.length < 3 || name.length > 120) return json(res, 400, { error: "invalid_name" });
      if (!isValidCpf(cpf)) return json(res, 400, { error: "invalid_cpf" });
      const widgetSession = await belvo.createWidgetSession({ name, cpf, externalId: session.sub });
      return json(res, 201, widgetSession);
    }

    const statusLinkId = linkIdFrom(url.pathname, "status");
    if (req.method === "GET" && statusLinkId) {
      const session = requireSession(req, res);
      if (!session) return;
      const state = linkReadiness.get(statusLinkId) ?? {};
      assertStateOwnership(session, state);
      if (!state.deleted) await requireOwnedLink(session, statusLinkId);
      const refreshed = linkReadiness.get(statusLinkId) ?? state;
      return json(res, 200, {
        linkId: statusLinkId,
        accountsReady: Boolean(refreshed.accountsReady),
        transactionsReady: Boolean(refreshed.transactionsReady),
        deletionPending: Boolean(refreshed.deletionPending),
        deleted: Boolean(refreshed.deleted),
        lastError: refreshed.lastError ?? null,
        lastWebhookAt: refreshed.lastWebhookAt ?? null,
      });
    }

    const accountsLinkId = linkIdFrom(url.pathname, "accounts");
    if (req.method === "GET" && accountsLinkId) {
      const session = requireSession(req, res);
      if (!session) return;
      await requireOwnedLink(session, accountsLinkId);
      return json(res, 200, await belvo.listAccounts(accountsLinkId));
    }

    const transactionsLinkId = linkIdFrom(url.pathname, "transactions");
    if (req.method === "GET" && transactionsLinkId) {
      const session = requireSession(req, res);
      if (!session) return;
      await requireOwnedLink(session, transactionsLinkId);
      const dateFrom = url.searchParams.get("dateFrom");
      const dateTo = url.searchParams.get("dateTo");
      return json(res, 200, await belvo.listTransactions(transactionsLinkId, dateFrom, dateTo));
    }

    const deleteMatch = url.pathname.match(/^\/v1\/open-finance\/links\/([^/]+)$/);
    if (req.method === "DELETE" && deleteMatch) {
      const session = requireSession(req, res);
      if (!session) return;
      const linkId = decodeURIComponent(deleteMatch[1]);
      await requireOwnedLink(session, linkId);
      const deletion = await belvo.deleteLink(linkId);
      const current = linkReadiness.get(linkId) ?? {};
      current.externalId = session.sub;
      current.deletionPending = true;
      current.deleted = false;
      current.lastError = null;
      linkReadiness.set(linkId, current);
      return json(res, 202, { deletionRequested: true, requestId: deletion?.request_id ?? null });
    }

    if (req.method === "POST" && url.pathname === "/webhooks/belvo") {
      const supplied = bearer(req);
      if (!supplied || !safeEqual(supplied, config.webhookAuthToken)) return json(res, 404, { error: "not_found" });
      const body = await readJson(req);
      const webhookId = String(body.webhook_id ?? "");
      if (webhookId && processedWebhookIds.has(webhookId)) return json(res, 202, { accepted: true, duplicate: true });

      const linkId = String(body.link_id ?? "");
      const externalId = String(body.external_id ?? "");
      const webhookType = String(body.webhook_type ?? "").toUpperCase();
      const webhookCode = String(body.webhook_code ?? "");
      if (linkId && UUID_PATTERN.test(linkId)) {
        const current = linkReadiness.get(linkId) ?? {};
        if (isValidSessionSubject(externalId)) current.externalId = externalId;
        const errors = historicalErrors(body);
        if (webhookCode === "link_deleted") {
          current.deletionPending = false;
          current.deleted = true;
          current.accountsReady = false;
          current.transactionsReady = false;
          current.lastError = null;
        } else if (webhookCode === "historical_update") {
          if (errors.length) {
            current.lastError = errors.join(",");
          } else {
            if (webhookType === "ACCOUNTS") current.accountsReady = true;
            if (webhookType === "TRANSACTIONS") current.transactionsReady = true;
            current.lastError = null;
          }
        }
        current.lastWebhookAt = new Date().toISOString();
        linkReadiness.set(linkId, current);
      }
      if (webhookId) {
        processedWebhookIds.add(webhookId);
        if (processedWebhookIds.size > 10_000) processedWebhookIds.delete(processedWebhookIds.values().next().value);
      }
      return json(res, 202, { accepted: true });
    }

    return json(res, 404, { error: "not_found" });
  } catch (error) {
    if (error?.status && error.status < 500) return json(res, error.status, { error: error.message });
    if (error?.status) {
      console.error("Provider request failed", { status: error.status });
      return json(res, 502, { error: "open_finance_provider_error" });
    }
    if (error?.name === "TimeoutError" || error?.name === "AbortError") {
      return json(res, 504, { error: "open_finance_provider_timeout" });
    }
    console.error("Unhandled backend error", { message: error?.message });
    return json(res, 500, { error: "internal_error" });
  }
});

server.requestTimeout = 35_000;
server.headersTimeout = 10_000;
server.keepAliveTimeout = 5_000;
server.listen(config.port, "0.0.0.0", () => {
  console.log(`Gerenciamento de Gastos backend listening on :${config.port}`);
});
