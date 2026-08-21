import test from "node:test";
import assert from "node:assert/strict";
import { BelvoClient, isValidCpf, normalizeCpf } from "../src/belvoClient.mjs";

const config = {
  belvoBaseUrl: "https://sandbox.belvo.com",
  belvoSecretId: "secret-id",
  belvoSecretPassword: "secret-password",
  belvoTimeoutMs: 20_000,
  belvoStaleIn: "90d",
  callbackSuccess: "gerenciamentogastos://success",
  callbackExit: "gerenciamentogastos://exit",
  callbackEvent: "gerenciamentogastos://error",
  consentPurpose: "Gerenciar finanças pessoais",
  termsUrl: "https://example.com/terms",
  companyIconUrl: "https://example.com/icon.svg",
  companyLogoUrl: "https://example.com/logo.svg",
  companyName: "Gerenciamento de Gastos",
};

test("normaliza e valida CPF", () => {
  assert.equal(normalizeCpf("529.982.247-25"), "52998224725");
  assert.equal(isValidCpf("529.982.247-25"), true);
  assert.equal(isValidCpf("111.111.111-11"), false);
});

test("cria sessão OFDA sem expor segredo na URL do widget e minimiza recursos", async () => {
  let captured;
  const fetchImpl = async (url, options) => {
    captured = { url: String(url), options };
    return new Response(JSON.stringify({ access: "temporary-access", refresh: "temporary-refresh" }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  };

  const client = new BelvoClient(config, fetchImpl);
  const result = await client.createWidgetSession({
    name: "Pessoa Teste",
    cpf: "52998224725",
    externalId: "device-123",
  });

  assert.equal(captured.url, "https://sandbox.belvo.com/api/token/");
  const body = JSON.parse(captured.options.body);
  assert.equal(body.widget.openfinance_feature, "consent_link_creation");
  assert.deepEqual(body.fetch_resources, ["ACCOUNTS", "TRANSACTIONS"]);
  assert.equal(body.stale_in, "90d");
  assert.equal(body.widget.consent.identification_info[0].number, "52998224725");
  assert.match(result.widgetUrl, /access_token=temporary-access/);
  assert.match(result.widgetUrl, /external_id=device-123/);
  assert.match(result.widgetUrl, /resources=ACCOUNTS%2CTRANSACTIONS/);
  assert.doesNotMatch(result.widgetUrl, /secret-password/);
});

test("segue paginação de transações e consolida resultados", async () => {
  const seen = [];
  const fetchImpl = async (url) => {
    seen.push(String(url));
    if (String(url).includes("page=2")) {
      return new Response(JSON.stringify({ count: 2, next: null, previous: "x", results: [{ id: "b" }] }), { status: 200 });
    }
    return new Response(JSON.stringify({
      count: 2,
      next: "https://sandbox.belvo.com/api/transactions/?page=2",
      previous: null,
      results: [{ id: "a" }],
    }), { status: 200 });
  };

  const client = new BelvoClient(config, fetchImpl);
  const result = await client.listTransactions("00000000-0000-4000-8000-000000000001");

  assert.equal(result.count, 2);
  assert.deepEqual(result.results.map((item) => item.id), ["a", "b"]);
  assert.equal(seen.length, 2);
});

test("recusa paginação que tenta sair da origem da Belvo", async () => {
  const fetchImpl = async () => new Response(JSON.stringify({
    count: 1,
    next: "https://attacker.invalid/steal",
    previous: null,
    results: [{ id: "a" }],
  }), { status: 200 });

  const client = new BelvoClient(config, fetchImpl);
  await assert.rejects(
    () => client.listAccounts("00000000-0000-4000-8000-000000000001"),
    /origem inesperada/
  );
});
