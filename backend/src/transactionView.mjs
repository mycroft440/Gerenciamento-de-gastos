const NON_NEGATIVE_DECIMAL = /^\d{1,15}(?:\.\d{1,4})?$/;

function decimalText(value) {
  if (typeof value === "string" && NON_NEGATIVE_DECIMAL.test(value)) return value;
  if (typeof value === "number" && Number.isFinite(value) && value >= 0) {
    const text = String(value);
    return NON_NEGATIVE_DECIMAL.test(text) ? text : null;
  }
  return null;
}

export function toTransactionView(transaction) {
  if (!transaction || typeof transaction !== "object") return null;
  const type = transaction.type === "INFLOW" || transaction.type === "OUTFLOW" ? transaction.type : null;
  const amount = decimalText(transaction.amount);
  const localAmount = decimalText(transaction.local_currency_amount);
  const valueDate = typeof transaction.value_date === "string" ? transaction.value_date : "";
  if (!transaction.id || !type || amount === null || !/^\d{4}-\d{2}-\d{2}$/.test(valueDate)) return null;

  const institution = transaction.account?.institution;
  const source = institution?.display_name || institution?.name || "Open Finance";
  const providerCurrency = typeof transaction.currency === "string" && /^[A-Z]{3}$/.test(transaction.currency)
    ? transaction.currency
    : null;
  const effectiveAmount = localAmount ?? amount;
  const effectiveCurrency = localAmount !== null ? "BRL" : (providerCurrency ?? "BRL");
  const status = ["PENDING", "PROCESSED", "UNCATEGORIZED"].includes(transaction.status)
    ? transaction.status
    : null;

  return {
    id: String(transaction.id),
    description: String(transaction.description || "Movimentação bancária").slice(0, 500),
    amount: effectiveAmount,
    currency: effectiveCurrency,
    type,
    value_date: valueDate,
    source: String(source).slice(0, 160),
    status,
  };
}

export function toTransactionPage(providerPage) {
  const raw = Array.isArray(providerPage) ? providerPage : providerPage?.results;
  const results = Array.isArray(raw) ? raw.map(toTransactionView).filter(Boolean) : [];
  return {
    count: results.length,
    results,
  };
}
