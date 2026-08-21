import http from "node:http";
import crypto from "node:crypto";
import { loadConfig } from "./config.mjs";
import { BelvoClient, isValidCpf, normalizeCpf } from "./belvoClient.mjs";
import { createSessionManager } from "./auth.mjs";
import { SlidingWindowRateLimiter } from "./rateLimit.mjs";
import { OpenFinanceStateStore } from "./stateStore.mjs";
import { toTransactionPage } from "./transactionView.mjs";

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;
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
const stateStore = new OpenFinanceStateStore(config.stateDbPath);
stateStore.pruneWebhooks(new Date(Date.now() - 30 * 24 * 60 * 60_000).toISOString());

function json(res, status, body, headers = {}) {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(payload),
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff",
    "X-Frame-Options": "DENY",
    "Content-Security-Policy": "default-src 'none'; frame-ancestors 'none'",
    "Cross-Origin-Resource-Policy": "same-origin",
    "Referrer-Policy": "no-referrer",
    "Permissions-Policy": "camera=(), microphone=(), geolocation=()",
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
  try {
    stateStore.bindOwner(linkId, session.sub);
  } catch (error) {
    if (error?.code === "LINK_OWNER_CONFLICT") throw Object.assign(new Error("not_found"), { status: 404 });
    throw error;
  }
  return link;
}

function remoteKey(req) {
  return req.socket.remoteAddress || "unknown";
}

function historicalErrors(body) {
  return Array.isArray(body?.data?.errors)
    ? body.data.errors.map((item) => String(item?.code ?? "unknown_error")).slice(0, 20)
    : [];
}

function effectiveWebhookOwner(linkId, externalId) {
  const state = stateStore.get(linkId);
  if (state?.externalId) {
    if (externalId && externalId !== state.externalId) return null;
    return state.externalId;
  }
  return externalId === config.personalSubject ? config.personalSubject : null;
}

function validIsoDate(value) {
  if (!DATE_PATTERN.test(value)) return false;
  const parsed = new Date(`${value}T00:00:00.000Z`);
  return !Number.isNaN(parsed.valueOf()) && parsed.toISOString().slice(0, 10) === value;
}

function linkView(link) {
  if (!link || !UUID_PATTERN.test(String(link.id ?? "")) || link.external_id !== config.personalSubject) return null;
  return {
    id: String(link.id),
    institution: typeof link.institution === "string" ? link.institution.slice(0, 160) : null,
    status: typeof link.status === "string" ? link.status.slice(0, 32) : null,
  };
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host ?? "localhost"}`);

  try {
    if (req.method === "GET" && url.pathname === "/health") {
      return json(res, 200, {
        ok: true,
        provider: "belvo",
        environment: config.belvoBaseUrl.includes("sandbox") ? "sandbox" : "production",
        deploymentMode: config.deploymentMode,
      });
    }

    if (req.method === "POST" && url.pathname === "/v1/auth/session") {
      const key = remoteKey(req);
      const limit = authLimiter.consume(key);
      if (!limit.allowed) {
        return json(res, 429, { error: "too_many_attempts" }, { "Retry-After": String(limit.retryAfterSeconds) });
      }
      const body = await readJson(req);
      const token = sessions.issue(body.accessCode, config.personalSubject);
      if (!token) return json(res, 401, { error: "invalid_access_code" });
      authLimiter.reset(key);
      return json(res, 200, { token, expiresInSeconds: sessions.ttlSeconds });
    }

    if (req.method === "GET" && url.pathname === "/v1/open-finance/links") {
      const session = requireSession(req, res);
      if (!session) return;
      const providerPage = await belvo.listLinksByExternalId(session.sub);
      const links = [];
      for (const raw of providerPage?.results ?? []) {
        const view = linkView(raw);
        if (!view || raw.external_id !== session.sub) continue;
        try {
          stateStore.bindOwner(view.id, session.sub);
          links.push(view);
        } catch (error) {
          if (error?.code !== "LINK_OWNER_CONFLICT") throw error;
        }
      }
      return json(res, 200, { count: links.length, links });
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
      const state = stateStore.get(statusLinkId);
      assertStateOwnership(session, state);
      const link = state?.deleted ? null : await requireOwnedLink(session, statusLinkId);
      const refreshed = stateStore.get(statusLinkId) ?? {};
      return json(res, 200, {
        linkId: statusLinkId,
        institution: typeof link?.institution === "string" ? link.institution : null,
        accountsReady: Boolean(refreshed.accountsReady),
        transactionsReady: Boolean(refreshed.transactionsReady),
        deletionPending: Boolean(refreshed.deletionPending),
        deleted: Boolean(refreshed.deleted),
        accountsError: refreshed.accountsError ?? null,
        transactionsError: refreshed.transactionsError ?? null,
        lastWebhookAt: refreshed.lastWebhookAt ?? null,
      });
    }

    const transactionsLinkId = linkIdFrom(url.pathname, "transactions");
    if (req.method === "GET" && transactionsLinkId) {
      const session = requireSession(req, res);
      if (!session) return;
      await requireOwnedLink(session, transactionsLinkId);
      const dateFrom = url.searchParams.get("dateFrom");
      const dateTo = url.searchParams.get("dateTo");
      if (dateFrom && !validIsoDate(dateFrom)) return json(res, 400, { error: "invalid_date_from" });
      if (dateTo && !validIsoDate(dateTo)) return json(res, 400, { error: "invalid_date_to" });
      if (dateFrom && dateTo && dateFrom > dateTo) return json(res, 400, { error: "invalid_date_range" });
      const providerPage = await belvo.listTransactions(transactionsLinkId, dateFrom, dateTo);
      return json(res, 200, toTransactionPage(providerPage));
    }

    const deleteMatch = url.pathname.match(/^\/v1\/open-finance\/links\/([^/]+)$/);
    if (req.method === "DELETE" && deleteMatch) {
      const session = requireSession(req, res);
      if (!session) return;
      const linkId = decodeURIComponent(deleteMatch[1]);
      await requireOwnedLink(session, linkId);
      const deletion = await belvo.deleteLink(linkId);
      stateStore.markDeletionPending(linkId, session.sub);
      return json(res, 202, { deletionRequested: true, requestId: deletion?.request_id ?? null });
    }

    if (req.method === "POST" && url.pathname === "/webhooks/belvo") {
      const supplied = bearer(req);
      if (!supplied || !safeEqual(supplied, config.webhookAuthToken)) return json(res, 404, { error: "not_found" });
      const body = await readJson(req);
      const webhookId = String(body.webhook_id ?? "");
      if (!webhookId || webhookId.length > 128) return json(res, 400, { error: "invalid_webhook_id" });
      if (stateStore.hasWebhook(webhookId)) return json(res, 202, { accepted: true, duplicate: true });

      const linkId = String(body.link_id ?? "");
      const externalId = String(body.external_id ?? "");
      const webhookType = String(body.webhook_type ?? "").toUpperCase();
      const webhookCode = String(body.webhook_code ?? "");
      if (linkId && UUID_PATTERN.test(linkId)) {
        const owner = effectiveWebhookOwner(linkId, externalId);
        if (stateStore.get(linkId)?.externalId && !owner) {
          return json(res, 202, { accepted: false, reason: "owner_mismatch" });
        }
        const errors = historicalErrors(body);
        if (webhookCode === "link_deleted") {
          stateStore.markDeleted(linkId, owner);
        } else if (webhookCode === "historical_update") {
          stateStore.markHistorical(linkId, owner, webhookType, errors);
        }
      }
      stateStore.recordWebhook(webhookId);
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

function shutdown(signal) {
  console.log(`Recebido ${signal}; encerrando backend.`);
  server.close(() => {
    stateStore.close();
    process.exit(0);
  });
  setTimeout(() => process.exit(1), 10_000).unref();
}

process.once("SIGTERM", () => shutdown("SIGTERM"));
process.once("SIGINT", () => shutdown("SIGINT"));
server.listen(config.port, "0.0.0.0", () => {
  console.log(`Gerenciamento de Gastos backend listening on :${config.port}`);
});
