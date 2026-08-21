const WIDGET_SCOPES = "read_institutions,write_links,read_consents,write_consents,write_consent_callback,delete_consents";
const CONSENT_PERMISSIONS = ["REGISTER", "ACCOUNTS", "CREDIT_CARDS", "CREDIT_OPERATIONS"];
const FETCH_RESOURCES = ["ACCOUNTS", "TRANSACTIONS"];
const DECIMAL_SOURCE_KEYS = new Set(["amount", "local_currency_amount"]);

export class BelvoProviderError extends Error {
  constructor(message, { providerStatus = 502, payload = null, cause = undefined } = {}) {
    super(message, { cause });
    this.name = "BelvoProviderError";
    this.status = 502;
    this.providerStatus = providerStatus;
    this.payload = payload;
    this.isProviderError = true;
  }
}

function parsePayload(text) {
  if (!text) return null;
  try {
    return JSON.parse(text, (key, value, context) => {
      if (
        DECIMAL_SOURCE_KEYS.has(key) &&
        typeof value === "number" &&
        context?.source &&
        /^\d{1,15}(?:\.\d{1,4})?$/.test(context.source)
      ) {
        return context.source;
      }
      return value;
    });
  } catch (cause) {
    throw new BelvoProviderError("Resposta inválida do provedor Open Finance", { cause });
  }
}

export class BelvoClient {
  constructor(config, fetchImpl = fetch) {
    this.config = config;
    this.fetch = fetchImpl;
    this.baseOrigin = new URL(config.belvoBaseUrl).origin;
  }

  async request(path, options = {}) {
    const url = new URL(path, this.config.belvoBaseUrl);
    if (url.origin !== this.baseOrigin) {
      throw new BelvoProviderError("Paginação do provedor apontou para origem inesperada");
    }

    const auth = Buffer.from(`${this.config.belvoSecretId}:${this.config.belvoSecretPassword}`).toString("base64");
    const response = await this.fetch(url, {
      ...options,
      signal: options.signal ?? AbortSignal.timeout(this.config.belvoTimeoutMs ?? 20_000),
      headers: {
        Authorization: `Basic ${auth}`,
        Accept: "application/json",
        ...(options.body ? { "Content-Type": "application/json" } : {}),
        ...(options.headers ?? {}),
      },
    });

    const text = await response.text();
    const payload = parsePayload(text);
    if (!response.ok) {
      throw new BelvoProviderError(`Belvo respondeu ${response.status}`, {
        providerStatus: response.status,
        payload,
      });
    }
    return payload;
  }

  async createWidgetSession({ name, cpf, externalId }) {
    const payload = {
      id: this.config.belvoSecretId,
      password: this.config.belvoSecretPassword,
      scopes: WIDGET_SCOPES,
      fetch_resources: FETCH_RESOURCES,
      stale_in: this.config.belvoStaleIn ?? "90d",
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
          identification_info: [{ type: "CPF", number: cpf, name }],
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

    const token = await this.request("/api/token/", { method: "POST", body: JSON.stringify(payload) });
    if (!token?.access || typeof token.access !== "string") {
      throw new BelvoProviderError("Belvo não retornou access token do widget");
    }

    const params = new URLSearchParams({
      access_token: token.access,
      locale: "pt",
      integration_type: "openfinance",
      institution_types: "retail",
      country_codes: "BR",
      access_mode: "recurrent",
      resources: "ACCOUNTS,TRANSACTIONS",
      external_id: externalId,
    });

    return { widgetUrl: `https://widget.belvo.io/?${params.toString()}`, expiresInSeconds: 600 };
  }

  async getLink(linkId) {
    try {
      return await this.request(`/api/links/${encodeURIComponent(linkId)}/`);
    } catch (error) {
      if (error?.isProviderError && error.providerStatus === 404) return null;
      throw error;
    }
  }

  async listAll(path, maxPages = 100) {
    const results = [];
    let next = path;
    let pages = 0;
    while (next) {
      if (++pages > maxPages) {
        throw new BelvoProviderError("Paginação do provedor excedeu o limite de segurança");
      }
      const page = await this.request(next);
      if (Array.isArray(page)) {
        results.push(...page);
        break;
      }
      if (!page || !Array.isArray(page.results)) {
        throw new BelvoProviderError("Formato de paginação inesperado do provedor");
      }
      results.push(...page.results);
      next = page.next || null;
    }
    return { count: results.length, next: null, previous: null, results };
  }

  listLinksByExternalId(externalId) {
    const params = new URLSearchParams({ external_id: externalId, page_size: "100" });
    return this.listAll(`/api/links/?${params.toString()}`);
  }

  listAccounts(linkId) {
    return this.listAll(`/api/accounts/?link=${encodeURIComponent(linkId)}&page_size=1000`);
  }

  listTransactions(linkId, dateFrom, dateTo) {
    const params = new URLSearchParams({ link: linkId, page_size: "1000" });
    if (dateFrom) params.set("value_date__gte", dateFrom);
    if (dateTo) params.set("value_date__lte", dateTo);
    return this.listAll(`/api/transactions/?${params.toString()}`);
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
