import test from "node:test";
import assert from "node:assert/strict";
import { BelvoClient, isValidCpf, normalizeCpf } from "../src/belvoClient.mjs";

const config = {
  belvoBaseUrl: "https://sandbox.belvo.com",
  belvoSecretId: "secret-id",
  belvoSecretPassword: "secret-password",
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

test("cria sessão OFDA sem expor segredo na URL do widget", async () => {
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
  assert.equal(body.widget.consent.identification_info[0].number, "52998224725");
  assert.match(result.widgetUrl, /access_token=temporary-access/);
  assert.doesNotMatch(result.widgetUrl, /secret-password/);
});
