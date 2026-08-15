function env(name, fallback = undefined) {
  const value = process.env[name] ?? fallback;
  if (value === undefined || value === "") {
    throw new Error(`Variável obrigatória ausente: ${name}`);
  }
  return value;
}

export function loadConfig() {
  return {
    port: Number(process.env.PORT ?? 8080),
    belvoBaseUrl: process.env.BELVO_BASE_URL ?? "https://sandbox.belvo.com",
    belvoSecretId: env("BELVO_SECRET_ID"),
    belvoSecretPassword: env("BELVO_SECRET_PASSWORD"),
    appAccessCode: env("APP_ACCESS_CODE"),
    sessionSigningKey: env("SESSION_SIGNING_KEY"),
    companyName: process.env.COMPANY_NAME ?? "Gerenciamento de Gastos",
    companyIconUrl: env("COMPANY_ICON_URL"),
    companyLogoUrl: env("COMPANY_LOGO_URL"),
    termsUrl: env("TERMS_URL"),
    callbackSuccess: process.env.CALLBACK_SUCCESS ?? "gerenciamentogastos://success",
    callbackExit: process.env.CALLBACK_EXIT ?? "gerenciamentogastos://exit",
    callbackEvent: process.env.CALLBACK_EVENT ?? "gerenciamentogastos://error",
    consentPurpose:
      process.env.CONSENT_PURPOSE ??
      "Consolidar suas contas, receitas e despesas para exibir um painel pessoal de gerenciamento financeiro.",
  };
}
