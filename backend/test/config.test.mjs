import test from "node:test";
import assert from "node:assert/strict";
import { loadConfig } from "../src/config.mjs";

const REQUIRED_ENV = {
  BELVO_SECRET_ID: "belvo-id",
  BELVO_SECRET_PASSWORD: "belvo-password",
  APP_ACCESS_CODE: "codigo-pessoal-forte",
  SESSION_SIGNING_KEY: "0123456789abcdef0123456789abcdef",
  BELVO_WEBHOOK_AUTH_TOKEN: "abcdef0123456789abcdef0123456789",
  PERSONAL_SUBJECT: "personal_subject_1234567890",
  COMPANY_ICON_URL: "https://example.com/icon.svg",
  COMPANY_LOGO_URL: "https://example.com/logo.svg",
  TERMS_URL: "https://example.com/termos",
};

function withEnv(extra, block) {
  const keys = [...new Set([...Object.keys(REQUIRED_ENV), ...Object.keys(extra)])];
  const previous = new Map(keys.map((key) => [key, process.env[key]]));
  Object.assign(process.env, REQUIRED_ENV, extra);
  try {
    return block();
  } finally {
    for (const [key, value] of previous) {
      if (value === undefined) delete process.env[key];
      else process.env[key] = value;
    }
  }
}

test("modo pessoal é o único modo habilitado enquanto não há autenticação individual", () => {
  const config = withEnv({ DEPLOYMENT_MODE: "personal" }, () => loadConfig());
  assert.equal(config.deploymentMode, "personal");
  assert.equal(config.personalSubject, "personal_subject_1234567890");

  assert.throws(
    () => withEnv({ DEPLOYMENT_MODE: "multiuser" }, () => loadConfig()),
    /somente 'personal'/
  );
});

test("PERSONAL_SUBJECT é obrigatório, estável e não aceita PII livre", () => {
  const previous = process.env.PERSONAL_SUBJECT;
  try {
    Object.assign(process.env, REQUIRED_ENV);
    delete process.env.PERSONAL_SUBJECT;
    assert.throws(() => loadConfig(), /PERSONAL_SUBJECT/);

    process.env.PERSONAL_SUBJECT = "curto";
    assert.throws(() => loadConfig(), /16 e 128 caracteres/);

    process.env.PERSONAL_SUBJECT = "nome pessoa@example.com";
    assert.throws(() => loadConfig(), /letras, números, _ ou -/);
  } finally {
    if (previous === undefined) delete process.env.PERSONAL_SUBJECT;
    else process.env.PERSONAL_SUBJECT = previous;
  }
});
