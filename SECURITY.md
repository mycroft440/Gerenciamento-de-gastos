# Segurança

## Escopo suportado

A versão atual suporta **uso pessoal / MVP controlado**. O backend inicia apenas com `DEPLOYMENT_MODE=personal` porque a autenticação atual usa um único código de acesso. Distribuição pública multiusuário exige autenticação individual antes de ser habilitada.

## Princípios aplicados

- credenciais Belvo existem somente no backend;
- o APK recebe apenas sessão curta do backend e URL temporária do Hosted Widget;
- sessão do backend é vinculada ao `external_id` aleatório do app;
- cada `link.id` é validado contra `link.external_id` antes de leitura ou exclusão;
- CPF e nome são usados somente para criar o consentimento e não são persistidos por este backend;
- transações não são persistidas pelo app nesta versão: ficam em memória até o processo ser encerrado;
- a API devolve um DTO mínimo de transações e não repassa o objeto bruto da Belvo;
- estado técnico de webhooks/readiness é persistido em SQLite, sem CPF ou extratos;
- webhook exige `Authorization: Bearer` com segredo independente;
- login tem rate limiting e sessões curtas;
- requisições ao provedor têm timeout, paginação limitada e bloqueio de mudança de origem;
- HTTP sem TLS é bloqueado no Android;
- o WebView do consentimento bloqueia mixed content, acesso a arquivo/conteúdo local e esquemas perigosos;
- backup Android está desabilitado.

## Segredos

Nunca versionar:

- `BELVO_SECRET_ID`;
- `BELVO_SECRET_PASSWORD`;
- `APP_ACCESS_CODE`;
- `SESSION_SIGNING_KEY`;
- `BELVO_WEBHOOK_AUTH_TOKEN`;
- chaves de assinatura Android.

Em ambiente hospedado, use o secret manager da plataforma. Rotacione imediatamente qualquer segredo suspeito de exposição. A rotação de `SESSION_SIGNING_KEY` invalida sessões existentes; a rotação do token de webhook exige atualizar também a configuração na Belvo.

## Persistência

O SQLite do backend armazena somente:

- `link_id`;
- `external_id` aleatório;
- flags de readiness/exclusão;
- códigos técnicos de erro;
- IDs/timestamps de webhooks.

O volume `/data` do container deve ser persistente. Em produção pessoal, faça backup criptografado do volume conforme a política de retenção definida pelo operador.

## Logs

Não registrar CPF, nome, saldo, descrição de transação, corpo completo de webhook, tokens ou segredos. Erros do provedor devem ser registrados apenas com metadados técnicos não sensíveis.

## Bloqueadores para distribuição pública

Antes de habilitar vários usuários, são obrigatórios no mínimo:

1. autenticação individual e autorização server-side por usuário;
2. rate limiting compartilhado entre instâncias;
3. banco de dados adequado à topologia de produção e plano de backup/restauração;
4. App Links HTTPS verificados para callbacks;
5. política de privacidade final com controlador, contato e bases legais definidos;
6. gestão/rotação centralizada de segredos;
7. observabilidade com redaction de dados financeiros;
8. assinatura de release e controles de distribuição.

Veja `PRODUCTION_CHECKLIST.md`.

## Relato de vulnerabilidade

Não coloque CPF, credenciais, tokens, extratos ou outros dados financeiros em issues públicas. Antes de publicar o app, configure um canal privado de segurança do mantenedor e documente-o aqui.
