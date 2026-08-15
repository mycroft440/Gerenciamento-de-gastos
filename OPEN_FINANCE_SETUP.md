# Configuração do Open Finance real

O código do aplicativo e do backend já está preparado para a Belvo OFDA (Open Finance Data Aggregation) no Brasil. Nenhuma credencial da Belvo deve ser colocada no APK.

## 1. Criar as credenciais Belvo

No painel da Belvo, crie/obtenha:

- `BELVO_SECRET_ID`
- `BELVO_SECRET_PASSWORD`

Comece com o sandbox:

```text
BELVO_BASE_URL=https://sandbox.belvo.com
```

Quando a conta estiver habilitada para produção, use:

```text
BELVO_BASE_URL=https://api.belvo.com
```

## 2. Publicar os arquivos exigidos pelo widget

A Belvo exige URLs públicas para a identidade visual e para os termos/política usados no consentimento.

Configure:

```text
COMPANY_ICON_URL=https://seu-dominio.com/icon.svg
COMPANY_LOGO_URL=https://seu-dominio.com/logo.svg
TERMS_URL=https://seu-dominio.com/termos
```

## 3. Proteger o backend

Gere valores fortes e diferentes para:

```text
APP_ACCESS_CODE=seu-codigo-de-acesso-pessoal
SESSION_SIGNING_KEY=uma-chave-aleatoria-longa
BELVO_WEBHOOK_TOKEN=outro-token-aleatorio-longo
```

O `APP_ACCESS_CODE` é digitado no app e não é salvo pelo aplicativo. O backend devolve uma sessão curta assinada.

## 4. Configurar o webhook na Belvo

Depois de hospedar o backend em HTTPS, configure no painel da Belvo a URL:

```text
https://api.seu-dominio.com/webhooks/belvo/SEU_BELVO_WEBHOOK_TOKEN
```

O app somente busca transações depois que o backend recebe o `historical_update` de `TRANSACTIONS` para o `link_id`.

## 5. Hospedar o backend

O diretório `backend/` inclui um `Dockerfile` e não possui dependências npm de produção. Qualquer plataforma que execute containers Node 22 pode hospedá-lo.

Variáveis completas estão em `backend/.env.example`.

O endpoint de saúde é:

```text
GET /health
```

## 6. Apontar o APK para o backend

O Android recebe a URL do backend na compilação:

```bash
gradle test :app:assembleDebug -PBACKEND_BASE_URL=https://api.seu-dominio.com
```

Sem essa propriedade, o APK compila normalmente, porém a tela de Contas informa que a conexão real ainda não está configurada.

## 7. Fluxo no aplicativo

1. Abra **Contas**.
2. Informe o código de acesso do backend.
3. Informe nome completo e CPF.
4. Toque em **Conectar banco**.
5. Escolha a instituição no Hosted Widget da Belvo e conceda o consentimento.
6. O app recebe e salva somente o `link.id` da conexão.
7. Após o webhook histórico, toque em **Sincronizar**.
8. As transações reais substituem os dados de demonstração no painel durante a sessão do app.

## Segurança implementada

- Secret ID e Secret Password da Belvo ficam apenas no servidor.
- O CPF não é gravado no app nem no backend desta versão; é encaminhado para a criação do consentimento.
- O código de acesso do backend fica apenas em memória no Android.
- A sessão do backend expira em 30 minutos.
- A URL de webhook possui um token aleatório próprio.
- O WebView bloqueia acesso a arquivos/conteúdo local e mixed content.
- O APK não recebe o `refresh` token da Belvo; recebe somente a URL do Hosted Widget contendo o access token temporário.

## Limites atuais antes de produção pública

Este projeto é apropriado para um MVP pessoal/sandbox. Antes de distribuir para vários usuários, devem ser adicionados autenticação individual por usuário, banco de dados persistente para conexões/status de webhooks, rate limiting distribuído, App Links HTTPS verificados e observabilidade sem dados financeiros sensíveis em logs.
