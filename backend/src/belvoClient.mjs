const WIDGET_SCOPES = "read_institutions,write_links,read_consents,write_consents,write_consent_callback,delete_consents";
const CONSENT_PERMISSIONS = ["REGISTER", "ACCOUNTS", "CREDIT_CARDS", "CREDIT_OPERATIONS"];
const FETCH_RESOURCES = ["ACCOUNTS", "TRANSACTIONS"];

export class BelvoClient {
  constructor(config, fetchImpl = fetch) {
    this.config = config;
    this.fetch = fetchImpl;
  }

  async request(path, options = {}) {
    const auth = Buffer.from(`${this.config.belvoSecretId}:${this.config.belvoSecretPassword}`).toString("base64");
    const response = await this.fetch(new URL(path, this.config.belvoBaseUrl), {
      ...options,
      headers: {
        Authorization: `Basic ${auth}`,
        Accept: "application/json",
        ...(options.body ? { "Content-Type": "application/json" } : {}),
        ...(options.headers ?? {}),
      },
    });

    const text = await response.text();
    const payload = text ? JSON.parse(text) : null;
    if (!response.ok) {
      const error = new Error(`Belvo respondeu ${response.status}`);
      error.status = response.status;
      error.payload = payload;
      throw error;
    }
    return payload;
  }

  async createWidgetSession({ name, cpf, externalId }) {
    const payload = {
      id: this.config.belvoSecretId,
      password: this.config.belvoSecretPassword,
      scopes: WIDGET_SCOPES,
      fetch_resources: FETCH_RESOURCES,
      widget: {
        openfinance_feature: "consent_link_creation",
        callback_urls: {
          success: this.config.callbackSuccess,
          exit: this.config.callbackExit,
          event: this.config.callbackEvent,
        },
        consent: {
          purpose: this.config.consentPurpose,
          terms_and_conditions_url: this.config.termsUrl,
          permissions: CONSENT_PERMISSIONS,
          identification_info: [
            {
              type: "CPF",
              number: cpf,
              name,
            },
          ],
        },
        branding: {
          company_icon: this.config.companyIconUrl,
          company_logo: this.config.companyLogoUrl,
          company_name: this.config.companyName,
          company_terms_url: this.config.termsUrl,
          social_proof: true,
        },
      },
    };

    const token = await this.request("/api/token/", {
      method: "POST",
      body: JSON.stringify(payload),
    });

    const params = new URLSearchParams({
      access_token: token.access,
      locale: "pt",
      integration_type: "openfinance",
      institution_types: "retail",
      country_codes: "BR",
      access_mode: "recurrent",
      resources: "OWNERS,ACCOUNTS,TRANSACTIONS",
    });
    if (externalId) params.set("external_id", externalId);

    return {
      widgetUrl: `https://widget.belvo.io/?${params.toString()}`,
      expiresInSeconds: 600,
    };
  }

  listAccounts(linkId) {
    return this.request(`/api/accounts/?link=${encodeURIComponent(linkId)}&page_size=1000`);
  }

  listTransactions(linkId, dateFrom, dateTo) {
    const params = new URLSearchParams({ link: linkId, page_size: "1000" });
    if (dateFrom) params.set("value_date__gte", dateFrom);
    if (dateTo) params.set("value_date__lte", dateTo);
    return this.request(`/api/transactions/?${params.toString()}`);
  }

  deleteLink(linkId) {
    return this.request(`/api/links/${encodeURIComponent(linkId)}/`, {
      method: "DELETE",
      headers: { "X-Belvo-Request-Mode": "async" },
    });
  }
}

export function normalizeCpf(value) {
  return String(value ?? "").replace(/\D/g, "");
}

export function isValidCpf(value) {
  const cpf = normalizeCpf(value);
  if (cpf.length !== 11 || /^(\d)\1{10}$/.test(cpf)) return false;
  const digit = (length) => {
    let sum = 0;
    for (let i = 0; i < length; i += 1) sum += Number(cpf[i]) * (length + 1 - i);
    const remainder = (sum * 10) % 11;
    return remainder === 10 ? 0 : remainder;
  };
  return digit(9) === Number(cpf[9]) && digit(10) === Number(cpf[10]);
}
