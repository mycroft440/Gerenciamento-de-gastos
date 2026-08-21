import test from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, rmSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { OpenFinanceStateStore } from "../src/stateStore.mjs";

const LINK_ID = "00000000-0000-4000-8000-000000000001";

test("estado histórico e deduplicação sobrevivem a reinício", () => {
  const dir = mkdtempSync(join(tmpdir(), "gastos-state-"));
  const path = join(dir, "state.sqlite");
  try {
    const first = new OpenFinanceStateStore(path);
    first.markHistorical(LINK_ID, "device-123", "TRANSACTIONS", []);
    assert.equal(first.recordWebhook("webhook-1"), true);
    first.close();

    const second = new OpenFinanceStateStore(path);
    const state = second.get(LINK_ID);
    assert.equal(state.externalId, "device-123");
    assert.equal(state.transactionsReady, true);
    assert.equal(second.hasWebhook("webhook-1"), true);
    assert.equal(second.recordWebhook("webhook-1"), false);
    second.close();
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("mantém erros separados por recurso e tombstone de exclusão", () => {
  const store = new OpenFinanceStateStore(":memory:");
  store.markHistorical(LINK_ID, "device-123", "ACCOUNTS", ["provider_error"]);
  store.markHistorical(LINK_ID, "device-123", "TRANSACTIONS", []);
  let state = store.get(LINK_ID);
  assert.equal(state.accountsReady, false);
  assert.equal(state.accountsError, "provider_error");
  assert.equal(state.transactionsReady, true);
  assert.equal(state.transactionsError, null);

  store.markDeletionPending(LINK_ID, "device-123");
  store.markDeleted(LINK_ID, "device-123");
  state = store.get(LINK_ID);
  assert.equal(state.deleted, true);
  assert.equal(state.deletionPending, false);
  assert.equal(state.transactionsReady, false);
  store.close();
});

test("não permite reatribuir um link a outro external id", () => {
  const store = new OpenFinanceStateStore(":memory:");
  store.bindOwner(LINK_ID, "device-123");
  assert.throws(() => store.bindOwner(LINK_ID, "device-999"), /link_owner_conflict/);
  store.close();
});
