# Configuração do Open Finance real

O app/backend está preparado para Belvo OFDA no Brasil em **modo pessoal**. Nenhuma credencial Belvo deve ser colocada no APK.

## 1. Sandbox primeiro

Obtenha no painel da Belvo:

- `BELVO_SECRET_ID`
- `BELVO_SECRET_PASSWORD`

Comece com:

```text
BELVO_BASE_URL=https://sandbox.belvo.com
DEPLOYMENT_MODE=personal
```

A passagem para produção exige acesso/certificação da Belvo. Depois de aprovado, a URL passa para:

```text
BELVO_BASE_URL=https://api.belvo.com
```

## 2. Identidade visual e termos

Publique por HTTPS e configure:

```text
COMPANY_ICON_URL=https://seu-dominio.com/icon.svg
COMPANY_LOGO_URL=https://seu-dominio.com/logo.svg
TERMS_URL=https://seu-dominio.com/termos
```

Não publique uma política de privacidade genérica sem preencher/revisar `PRIVACY_TEMPLATE.md`.

## 3. Segredos do backend

Gere valores fortes e independentes:

```text
APP_ACCESS_CODE=
SESSION_SIGNING_KEY=
BELVO_WEBHOOK_AUTH_TOKEN=
```

O código pessoal é digitado no app, não é persistido no Android e gera uma sessão curta vinculada ao `external_id` aleatório do app.

A versão atual aceita somente `DEPLOYMENT_MODE=personal`. Multiusuário está bloqueado até existir autenticação individual.

## 4. Webhook Belvo

Hospede o backend em HTTPS e cadastre:

```text
POST https://api.seu-dominio.com/webhooks/belvo
Authorization: Bearer SEU_BELVO_WEBHOOK_AUTH_TOKEN
```

Não coloque o token no path/URL.

O backend só marca transações como prontas depois do `historical_update` correspondente sem erro. Webhooks são deduplicados por ID e o estado técnico sobrevive a reinícios via SQLite.

## 5. Persistência do backend

O container usa Node 24.16 e grava o banco técnico em:

```text
/data/openfinance.sqlite
```

Monte `/data` em volume persistente. O banco não guarda CPF nem extratos; contém IDs de conexão/usuário técnico, readiness, exclusão e deduplicação de webhooks.

Variáveis completas estão em `backend/.env.example`.

O endpoint de saúde é:

```text
GET /health
```

## 6. Apontar o Android para o backend

```bash
gradle test :app:assembleDebug -PBACKEND_BASE_URL=https://api.seu-dominio.com
```

HTTP é rejeitado pelo app. Sem a propriedade, a conexão real fica desabilitada.

## 7. Fluxo atual no app

1. Abra **Contas**.
2. Informe o código de acesso pessoal do backend.
3. Informe nome completo e CPF.
4. Toque em **Conectar banco**.
5. Escolha a instituição no Hosted Widget e conceda o consentimento.
6. O callback retorna um `link.id`, mas o app só o salva depois que o backend confirma que `link.external_id` pertence à sessão.
7. Aguarde o histórico ficar disponível.
8. Toque em **Atualizar painel** para ler o que já foi disponibilizado pelo provedor.
9. Repita o fluxo para adicionar outras instituições.

**Atualizar painel não força atualização no banco.** Links recorrentes são atualizados pela Belvo conforme a frequência contratada; o comportamento padrão do provedor não deve ser apresentado como tempo real. A carga inicial pode trazer até cerca de 365 dias conforme o recurso/instituição.

## 8. Remoção

A exclusão é assíncrona:

1. o app solicita a remoção;
2. o backend envia DELETE assíncrono à Belvo;
3. a conexão continua registrada como pendente;
4. o webhook `link_deleted` confirma a exclusão;
5. ao atualizar o painel, o app remove o `link.id` local confirmado como apagado.

## Segurança implementada

- segredos Belvo somente no servidor;
- nome/CPF não persistidos pelo backend;
- sessão curta vinculada ao `external_id`;
- propriedade do link validada server-side;
- rate limit de autenticação;
- webhook Bearer;
- timeouts e paginação com restrição de origem;
- DTO mínimo de transações;
- SQLite apenas para metadados técnicos;
- WebView sem acesso a arquivo/conteúdo local, sem mixed content e com Safe Browsing;
- callbacks forjados não conseguem apropriar um link de outro `external_id`.

## Antes de distribuição pública

O esquema `gerenciamentogastos://` permanece aceitável apenas no MVP pessoal porque a propriedade do link é revalidada no backend. Para distribuição pública, migre para **Android App Links HTTPS verificados**, publique `assetlinks.json`, implemente autenticação individual e conclua todos os gates de `PRODUCTION_CHECKLIST.md`.
