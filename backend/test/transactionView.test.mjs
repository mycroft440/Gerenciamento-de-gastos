import test from "node:test";
import assert from "node:assert/strict";
import { toTransactionPage, toTransactionView } from "../src/transactionView.mjs";

test("expõe somente campos necessários da transação", () => {
  const view = toTransactionView({
    id: "tx-1",
    description: "Mercado",
    amount: 50.25,
    local_currency_amount: 50.25,
    currency: "BRL",
    type: "OUTFLOW",
    value_date: "2026-08-20",
    status: "PROCESSED",
    account: {
      id: "account-secret",
      balance: 9999,
      institution: { name: "banco_x", internal: "nao-expor" },
    },
    link: "link-secret",
    internal_identification: "internal-secret",
  });

  assert.deepEqual(view, {
    id: "tx-1",
    description: "Mercado",
    amount: 50.25,
    local_currency_amount: 50.25,
    currency: "BRL",
    type: "OUTFLOW",
    value_date: "2026-08-20",
    source: "banco_x",
    status: "PROCESSED",
  });
  assert.equal("account" in view, false);
  assert.equal("link" in view, false);
  assert.equal("internal_identification" in view, false);
});

test("descarta registros sem direção, valor ou data confiáveis", () => {
  const page = toTransactionPage({
    results: [
      { id: "a", amount: 1, type: null, value_date: "2026-08-20" },
      { id: "b", amount: -1, type: "OUTFLOW", value_date: "2026-08-20" },
      { id: "c", amount: 2, type: "INFLOW", value_date: "data-invalida" },
      { id: "ok", amount: 3, type: "INFLOW", value_date: "2026-08-20" },
    ],
  });
  assert.equal(page.count, 1);
  assert.equal(page.results[0].id, "ok");
});
