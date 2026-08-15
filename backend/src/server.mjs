import http from "node:http";
import crypto from "node:crypto";
import { loadConfig } from "./config.mjs";
import { BelvoClient, isValidCpf, normalizeCpf } from "./belvoClient.mjs";
import { createSessionManager } from "./auth.mjs";

const config = loadConfig();
const belvo = new BelvoClient(config);
const sessions = createSessionManager({
  accessCode: config.appAccessCode,
  signingKey: config.sessionSigningKey,
});
const linkReadiness = new Map();
const webhookToken = process.env.BELVO_WEBHOOK_TOKEN ?? "";

function json(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(payload),
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff",
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
  return header.startsWith("Bearer ") ? header.slice(7) : "";
}

function requireSession(req, res) {
  if (!sessions.verify(bearer(req))) {
    json(res, 401, { error: "unauthorized" });
    return false;
  }
  return true;
}

function safeEqual(a, b) {
  const left = Buffer.from(String(a));
  const right = Buffer.from(String(b));
  return left.length === right.length && crypto.timingSafeEqual(left, right);
}

function linkIdFrom(pathname, suffix) {
  const match = pathname.match(new RegExp(`^/v1/open-finance/links/([^/]+)/${suffix}$`));
  return match ? decodeURIComponent(match[1]) : null;
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host ?? "localhost"}`);

  try {
    if (req.method === "GET" && url.pathname === "/health") {
      return json(res, 200, { ok: true, provider: "belvo", environment: config.belvoBaseUrl.includes("sandbox") ? "sandbox" : "production" });
    }

    if (req.method === "POST" && url.pathname === "/v1/auth/session") {
      const body = await readJson(req);
      const token = sessions.issue(body.accessCode);
      if (!token) return json(res, 401, { error: "invalid_access_code" });
      return json(res, 200, { token, expiresInSeconds: sessions.ttlSeconds });
    }

    if (req.method === "POST" && url.pathname === "/v1/open-finance/widget-session") {
      if (!requireSession(req, res)) return;
      const body = await readJson(req);
      const name = String(body.name ?? "").trim();
      const cpf = normalizeCpf(body.cpf);
      if (name.length < 3 || name.length > 120) return json(res, 400, { error: "invalid_name" });
      if (!isValidCpf(cpf)) return json(res, 400, { error: "invalid_cpf" });
      const session = await belvo.createWidgetSession({
        name,
        cpf,
        externalId: body.externalId ? String(body.externalId).slice(0, 100) : undefined,
      });
      return json(res, 201, session);
    }

    const statusLinkId = linkIdFrom(url.pathname, "status");
    if (req.method === "GET" && statusLinkId) {
      if (!requireSession(req, res)) return;
      const state = linkReadiness.get(statusLinkId) ?? {};
      return json(res, 200, {
        linkId: statusLinkId,
        accountsReady: Boolean(state.accountsReady),
        transactionsReady: Boolean(state.transactionsReady),
        lastWebhookAt: state.lastWebhookAt ?? null,
      });
    }

    const accountsLinkId = linkIdFrom(url.pathname, "accounts");
    if (req.method === "GET" && accountsLinkId) {
      if (!requireSession(req, res)) return;
      return json(res, 200, await belvo.listAccounts(accountsLinkId));
    }

    const transactionsLinkId = linkIdFrom(url.pathname, "transactions");
    if (req.method === "GET" && transactionsLinkId) {
      if (!requireSession(req, res)) return;
      const dateFrom = url.searchParams.get("dateFrom");
      const dateTo = url.searchParams.get("dateTo");
      return json(res, 200, await belvo.listTransactions(transactionsLinkId, dateFrom, dateTo));
    }

    const deleteMatch = url.pathname.match(/^\/v1\/open-finance\/links\/([^/]+)$/);
    if (req.method === "DELETE" && deleteMatch) {
      if (!requireSession(req, res)) return;
      const linkId = decodeURIComponent(deleteMatch[1]);
      await belvo.deleteLink(linkId);
      linkReadiness.delete(linkId);
      return json(res, 200, { deleted: true });
    }

    const webhookMatch = url.pathname.match(/^\/webhooks\/belvo\/([^/]+)$/);
    if (req.method === "POST" && webhookMatch) {
      if (!webhookToken || !safeEqual(webhookMatch[1], webhookToken)) {
        return json(res, 404, { error: "not_found" });
      }
      const body = await readJson(req);
      const linkId = String(body.link_id ?? "");
      const webhookType = String(body.webhook_type ?? "").toUpperCase();
      if (linkId) {
        const current = linkReadiness.get(linkId) ?? {};
        if (body.webhook_code === "historical_update") {
          if (webhookType === "ACCOUNTS") current.accountsReady = true;
          if (webhookType === "TRANSACTIONS") current.transactionsReady = true;
        }
        current.lastWebhookAt = new Date().toISOString();
        linkReadiness.set(linkId, current);
      }
      return json(res, 202, { accepted: true });
    }

    return json(res, 404, { error: "not_found" });
  } catch (error) {
    if (error?.status && error.status < 500) {
      return json(res, error.status, { error: error.message });
    }
    if (error?.status) {
      console.error("Provider request failed", { status: error.status });
      return json(res, 502, { error: "open_finance_provider_error" });
    }
    console.error("Unhandled backend error", { message: error?.message });
    return json(res, 500, { error: "internal_error" });
  }
});

server.listen(config.port, "0.0.0.0", () => {
  console.log(`Gerenciamento de Gastos backend listening on :${config.port}`);
});
