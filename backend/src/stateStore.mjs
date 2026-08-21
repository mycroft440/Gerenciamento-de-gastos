import { mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { DatabaseSync } from "node:sqlite";

function boolean(value) {
  return Number(value ?? 0) === 1;
}

function toState(row) {
  if (!row) return null;
  return {
    linkId: row.link_id,
    externalId: row.external_id || null,
    accountsReady: boolean(row.accounts_ready),
    transactionsReady: boolean(row.transactions_ready),
    deletionPending: boolean(row.deletion_pending),
    deleted: boolean(row.deleted),
    accountsError: row.accounts_error || null,
    transactionsError: row.transactions_error || null,
    lastWebhookAt: row.last_webhook_at || null,
    updatedAt: row.updated_at || null,
  };
}

export class OpenFinanceStateStore {
  constructor(path = ":memory:") {
    if (path !== ":memory:") mkdirSync(dirname(resolve(path)), { recursive: true });
    this.db = new DatabaseSync(path, { timeout: 5000 });
    this.db.exec(`
      PRAGMA journal_mode = WAL;
      PRAGMA foreign_keys = ON;
      CREATE TABLE IF NOT EXISTS link_state (
        link_id TEXT PRIMARY KEY,
        external_id TEXT,
        accounts_ready INTEGER NOT NULL DEFAULT 0 CHECK (accounts_ready IN (0, 1)),
        transactions_ready INTEGER NOT NULL DEFAULT 0 CHECK (transactions_ready IN (0, 1)),
        deletion_pending INTEGER NOT NULL DEFAULT 0 CHECK (deletion_pending IN (0, 1)),
        deleted INTEGER NOT NULL DEFAULT 0 CHECK (deleted IN (0, 1)),
        accounts_error TEXT,
        transactions_error TEXT,
        last_webhook_at TEXT,
        updated_at TEXT NOT NULL
      ) STRICT;
      CREATE TABLE IF NOT EXISTS webhook_event (
        webhook_id TEXT PRIMARY KEY,
        received_at TEXT NOT NULL
      ) STRICT;
      CREATE INDEX IF NOT EXISTS webhook_event_received_at_idx ON webhook_event(received_at);
    `);

    this.getStatement = this.db.prepare(`SELECT * FROM link_state WHERE link_id = ?`);
    this.insertState = this.db.prepare(`
      INSERT INTO link_state (link_id, external_id, updated_at)
      VALUES (?, ?, ?)
      ON CONFLICT(link_id) DO NOTHING
    `);
    this.setOwnerStatement = this.db.prepare(`
      UPDATE link_state SET external_id = ?, updated_at = ? WHERE link_id = ?
    `);
    this.resourceSuccessStatement = {
      ACCOUNTS: this.db.prepare(`
        UPDATE link_state
        SET accounts_ready = 1, accounts_error = NULL, last_webhook_at = ?, updated_at = ?
        WHERE link_id = ?
      `),
      TRANSACTIONS: this.db.prepare(`
        UPDATE link_state
        SET transactions_ready = 1, transactions_error = NULL, last_webhook_at = ?, updated_at = ?
        WHERE link_id = ?
      `),
    };
    this.resourceErrorStatement = {
      ACCOUNTS: this.db.prepare(`
        UPDATE link_state
        SET accounts_ready = 0, accounts_error = ?, last_webhook_at = ?, updated_at = ?
        WHERE link_id = ?
      `),
      TRANSACTIONS: this.db.prepare(`
        UPDATE link_state
        SET transactions_ready = 0, transactions_error = ?, last_webhook_at = ?, updated_at = ?
        WHERE link_id = ?
      `),
    };
    this.pendingDeleteStatement = this.db.prepare(`
      UPDATE link_state
      SET deletion_pending = 1, deleted = 0, updated_at = ?
      WHERE link_id = ?
    `);
    this.deletedStatement = this.db.prepare(`
      UPDATE link_state
      SET deletion_pending = 0, deleted = 1,
          accounts_ready = 0, transactions_ready = 0,
          accounts_error = NULL, transactions_error = NULL,
          last_webhook_at = ?, updated_at = ?
      WHERE link_id = ?
    `);
    this.insertWebhookStatement = this.db.prepare(`
      INSERT INTO webhook_event (webhook_id, received_at) VALUES (?, ?)
      ON CONFLICT(webhook_id) DO NOTHING
    `);
    this.pruneWebhookStatement = this.db.prepare(`DELETE FROM webhook_event WHERE received_at < ?`);
  }

  get(linkId) {
    return toState(this.getStatement.get(linkId));
  }

  ensure(linkId, externalId = null, now = new Date().toISOString()) {
    this.insertState.run(linkId, externalId, now);
    if (externalId) this.bindOwner(linkId, externalId, now);
    return this.get(linkId);
  }

  bindOwner(linkId, externalId, now = new Date().toISOString()) {
    const current = this.get(linkId);
    if (current?.externalId && current.externalId !== externalId) {
      const error = new Error("link_owner_conflict");
      error.code = "LINK_OWNER_CONFLICT";
      throw error;
    }
    if (!current) this.insertState.run(linkId, externalId, now);
    this.setOwnerStatement.run(externalId, now, linkId);
    return this.get(linkId);
  }

  markHistorical(linkId, externalId, resource, errors = [], now = new Date().toISOString()) {
    const normalizedResource = String(resource ?? "").toUpperCase();
    if (!this.resourceSuccessStatement[normalizedResource]) return this.get(linkId);
    this.ensure(linkId, externalId || null, now);
    if (externalId) this.bindOwner(linkId, externalId, now);
    if (errors.length) {
      this.resourceErrorStatement[normalizedResource].run(errors.join(","), now, now, linkId);
    } else {
      this.resourceSuccessStatement[normalizedResource].run(now, now, linkId);
    }
    return this.get(linkId);
  }

  markDeletionPending(linkId, externalId, now = new Date().toISOString()) {
    this.ensure(linkId, externalId || null, now);
    if (externalId) this.bindOwner(linkId, externalId, now);
    this.pendingDeleteStatement.run(now, linkId);
    return this.get(linkId);
  }

  markDeleted(linkId, externalId = null, now = new Date().toISOString()) {
    this.ensure(linkId, externalId || null, now);
    if (externalId) this.bindOwner(linkId, externalId, now);
    this.deletedStatement.run(now, now, linkId);
    return this.get(linkId);
  }

  recordWebhook(webhookId, now = new Date().toISOString()) {
    const result = this.insertWebhookStatement.run(webhookId, now);
    return Number(result.changes) === 1;
  }

  pruneWebhooks(beforeIso) {
    return Number(this.pruneWebhookStatement.run(beforeIso).changes);
  }

  close() {
    this.db.close();
  }
}
