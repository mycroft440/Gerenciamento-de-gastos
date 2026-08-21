function env(name, fallback = undefined) {
  const value = process.env[name] ?? fallback;
  if (value === undefined || value === "") throw new Error(`Variável obrigatória ausente: ${name}`);
  return value;
}

function secret(name, minimumLength) {
  const value = env(name);
  if (value.length < minimumLength) throw new Error(`${name} deve ter pelo menos ${minimumLength} caracteres`);
  return value;
}

function stableSubject(name) {
  const value = env(name);
  if (!/^[A-Za-z0-9_-]{16,128}$/.test(value)) {
    throw new Error(`${name} deve ter entre 16 e 128 caracteres e usar apenas letras, números, _ ou -`);
  }
  return value;
}

function httpsUrl(name, fallback) {
  const raw = env(name, fallback);
  let parsed;
  try {
    parsed = new URL(raw);
  } catch {
    throw new Error(`${name} deve ser uma URL válida`);
  }
  if (parsed.protocol !== "https:") throw new Error(`${name} deve usar HTTPS`);
  return parsed.toString().replace(/\/$/, "");
}

function positiveInt(name, fallback, min, max) {
  const value = Number(process.env[name] ?? fallback);
  if (!Number.isInteger(value) || value < min || value > max) {
    throw new Error(`${name} deve ser um inteiro entre ${min} e ${max}`);
  }
  return value;
}

function callback(name, fallback) {
  const value = env(name, fallback);
  let parsed;
  try {
    parsed = new URL(value);
  } catch {
    throw new Error(`${name} deve ser uma URL/deep link válido`);
  }
  if (!parsed.protocol || parsed.protocol === "http:") throw new Error(`${name} não pode usar HTTP sem TLS`);
  return value;
}

function statePath() {
  const value = process.env.STATE_DB_PATH ?? "./data/openfinance.sqlite";
  if (!value.trim() || value.includes("\0")) throw new Error("STATE_DB_PATH inválido");
  return value;
}

function deploymentMode() {
  const value = process.env.DEPLOYMENT_MODE ?? "personal";
  if (value !== "personal") {
    throw new Error(
      "DEPLOYMENT_MODE suporta somente 'personal'. Distribuição multiusuário exige autenticação individual antes de ser habilitada."
    );
  }
  return value;
}

export function loadConfig() {
  const belvoBaseUrl = httpsUrl("BELVO_BASE_URL", "https://sandbox.belvo.com");
  const belvoHost = new URL(belvoBaseUrl).hostname;
  if (!new Set(["sandbox.belvo.com", "api.belvo.com"]).has(belvoHost)) {
    throw new Error("BELVO_BASE_URL deve apontar para sandbox.belvo.com ou api.belvo.com");
  }

  const consentPurpose = process.env.CONSENT_PURPOSE ??
    "Consolidar suas contas, receitas e despesas para exibir um painel pessoal de gerenciamento financeiro.";
  if (consentPurpose.length < 10 || consentPurpose.length > 140) {
    throw new Error("CONSENT_PURPOSE deve ter entre 10 e 140 caracteres");
  }

  const staleDays = positiveInt("BELVO_STALE_DAYS", 90, 1, 365);

  return {
    deploymentMode: deploymentMode(),
    personalSubject: stableSubject("PERSONAL_SUBJECT"),
    port: positiveInt("PORT", 8080, 1, 65535),
    belvoBaseUrl,
    belvoSecretId: env("BELVO_SECRET_ID"),
    belvoSecretPassword: env("BELVO_SECRET_PASSWORD"),
    belvoTimeoutMs: positiveInt("BELVO_TIMEOUT_MS", 20_000, 1_000, 60_000),
    belvoStaleIn: `${staleDays}d`,
    appAccessCode: secret("APP_ACCESS_CODE", 12),
    sessionSigningKey: secret("SESSION_SIGNING_KEY", 32),
    sessionTtlSeconds: positiveInt("SESSION_TTL_SECONDS", 900, 300, 3600),
    webhookAuthToken: secret("BELVO_WEBHOOK_AUTH_TOKEN", 32),
    authMaxAttempts: positiveInt("AUTH_MAX_ATTEMPTS", 5, 1, 50),
    authWindowMs: positiveInt("AUTH_WINDOW_MS", 300_000, 10_000, 3_600_000),
    stateDbPath: statePath(),
    companyName: process.env.COMPANY_NAME ?? "Gerenciamento de Gastos",
    companyIconUrl: httpsUrl("COMPANY_ICON_URL"),
    companyLogoUrl: httpsUrl("COMPANY_LOGO_URL"),
    termsUrl: httpsUrl("TERMS_URL"),
    callbackSuccess: callback("CALLBACK_SUCCESS", "gerenciamentogastos://success"),
    callbackExit: callback("CALLBACK_EXIT", "gerenciamentogastos://exit"),
    callbackEvent: callback("CALLBACK_EVENT", "gerenciamentogastos://error"),
    consentPurpose,
  };
}
