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

## 3. Identidade pessoal e segredos do backend

Gere valores fortes e independentes:

```text
PERSONAL_SUBJECT=
APP_ACCESS_CODE=
SESSION_SIGNING_KEY=
BELVO_WEBHOOK_AUTH_TOKEN=
```

`PERSONAL_SUBJECT` é o identificador técnico estável do único usuário do modo pessoal. Gere uma vez, sem nome, CPF, e-mail, telefone ou outro dado pessoal. Exemplo:

```bash
openssl rand -hex 16
```

Regras importantes:

- mantenha o mesmo `PERSONAL_SUBJECT` entre reinícios, reinstalações do Android e trocas de aparelho;
- não coloque esse valor no APK;
- não reutilize `APP_ACCESS_CODE` ou outra senha como `PERSONAL_SUBJECT`;
- não troque o valor enquanto existirem links Open Finance que você queira reencontrar;
- a Belvo recebe esse valor como `external_id`, permitindo listar os links associados a esse perfil.

O código pessoal é digitado no app, não é persistido no Android e gera uma sessão curta cujo `sub` é o `PERSONAL_SUBJECT` configurado no servidor. O app não escolhe nem envia o subject da sessão.

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

Monte `/data` em volume persistente. O banco não guarda CPF nem extratos; contém IDs de conexão/subject técnico, readiness, exclusão e deduplicação de webhooks.

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
6. O Hosted Widget cria o link usando o `PERSONAL_SUBJECT` do servidor como `external_id`.
7. O callback retorna um `link.id`, mas o app só o salva depois que o backend confirma que `link.external_id` pertence à sessão.
8. Aguarde o histórico ficar disponível.
9. Toque em **Atualizar painel** para reconciliar os links e ler o que já foi disponibilizado pelo provedor.
10. Repita o fluxo para adicionar outras instituições.

**Atualizar painel não força atualização no banco.** Links recorrentes são atualizados pela Belvo conforme a frequência contratada; o comportamento padrão do provedor não deve ser apresentado como tempo real. A carga inicial pode trazer até cerca de 365 dias conforme o recurso/instituição.

## 8. Reinstalação ou troca de aparelho

Os `link.id` locais são apenas cache/referência de conveniência. A fonte de recuperação é o `PERSONAL_SUBJECT` do backend.

Depois de reinstalar o app:

1. configure/use o mesmo backend e o mesmo `PERSONAL_SUBJECT`;
2. abra **Contas**;
3. informe o código pessoal;
4. toque em **Recuperar conexões e atualizar**;
5. o backend chama a lista de links filtrada pelo `external_id` estável e devolve apenas ID, instituição e status mínimos;
6. o app reconstrói o conjunto local de links e volta a carregar o painel.

Trocar `PERSONAL_SUBJECT` cria, na prática, outro perfil técnico e impede essa recuperação automática dos links antigos.

## 9. Remoção

A exclusão é assíncrona:

1. o app solicita a remoção;
2. o backend envia DELETE assíncrono à Belvo;
3. a conexão continua registrada como pendente;
4. o webhook `link_deleted` confirma a exclusão;
5. ao atualizar o painel, o app reconcilia a lista e remove referências locais que já não pertencem ao perfil.

## Segurança implementada

- segredos Belvo somente no servidor;
- nome/CPF não persistidos pelo backend;
- `PERSONAL_SUBJECT` somente no servidor e sem PII;
- sessão curta vinculada ao `PERSONAL_SUBJECT`;
- o Android não escolhe o subject da sessão;
- propriedade do link validada server-side;
- recuperação de links filtrada por `external_id` e reduzida a DTO mínimo;
- rate limit de autenticação;
- webhook Bearer;
- timeouts e paginação com restrição de origem;
- DTO mínimo de transações;
- SQLite apenas para metadados técnicos;
- WebView sem acesso a arquivo/conteúdo local, sem mixed content e com Safe Browsing;
- callbacks forjados não conseguem apropriar um link de outro `external_id`.

## Antes de distribuição pública

O esquema `gerenciamentogastos://` permanece aceitável apenas no MVP pessoal porque a propriedade do link é revalidada no backend. Para distribuição pública, migre para **Android App Links HTTPS verificados**, publique `assetlinks.json`, implemente autenticação individual e conclua todos os gates de `PRODUCTION_CHECKLIST.md`.
