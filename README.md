# Gerenciamento de Gastos

Aplicativo Android nativo para consolidar receitas e despesas e conectar várias contas bancárias por Open Finance no Brasil.

## Estado atual

O código está em nível de **MVP pessoal/controlado**. Ele não deve ser tratado como pronto para distribuição pública multiusuário; os gates externos e arquiteturais estão em `PRODUCTION_CHECKLIST.md`.

O projeto contém:

- painel mensal com saldo líquido, entradas e saídas **processadas** em BRL;
- movimentações `PENDING` visíveis e somadas separadamente, fora dos totais processados;
- valores monetários em `BigDecimal`;
- navegação entre meses;
- gastos processados agrupados por categoria;
- pesquisa e filtro de transações;
- suporte a várias conexões Open Finance;
- identificação da instituição antes de permitir remoção de uma conexão;
- recuperação de conexões após reinstalação usando um `PERSONAL_SUBJECT` estável no backend;
- reconciliação dos `link.id` locais com os links associados ao `external_id` na Belvo;
- consolidação das movimentações das conexões carregadas;
- tratamento explícito de moedas estrangeiras e transações pendentes;
- modo demonstrativo apenas quando não existe conexão real salva;
- Belvo OFDA / Open Finance Brasil;
- Hosted Widget para consentimento;
- validação server-side de que cada `link.id` pertence ao `external_id` da sessão;
- backend próprio para proteger credenciais Belvo;
- sessões curtas, rate limiting, timeout e minimização de dados;
- webhook autenticado por Bearer e deduplicado;
- persistência SQLite somente para estado técnico de webhooks/readiness;
- exclusão assíncrona acompanhada até `link_deleted`;
- Dockerfile do backend;
- CI com testes, lint, build do container, APK debug e build release otimizado.

> Nome e CPF não são persistidos pelo backend desta versão. As transações reais também não são cacheadas de forma persistente no Android: ficam em memória e precisam ser carregadas novamente após reabrir o app.

## Identidade pessoal estável

No modo pessoal, o `external_id` não é mais criado no aparelho. O servidor exige `PERSONAL_SUBJECT`, um identificador técnico aleatório, sem PII, gerado uma vez e preservado durante a vida das conexões Open Finance.

Isso permite que o backend consulte `/api/links/?external_id=...` na Belvo e reencontre links que não estejam mais no armazenamento local, inclusive após reinstalação do aplicativo. **Trocar `PERSONAL_SUBJECT` enquanto existirem links faz o novo perfil deixar de encontrá-los.**

O app remove automaticamente o antigo `external_id` gerado localmente por versões anteriores; a identidade de autorização passa a existir somente no backend.

## Stack

### Android

- Kotlin
- Jetpack Compose + Material 3
- AGP 9.3.0
- Gradle 9.5.0
- JDK 17
- compileSdk / targetSdk 36
- Compose BOM 2026.06.00

### Backend

- Node.js 24.16+
- `node:http` e `node:sqlite`, sem framework/dependências npm de produção
- container Node 24.16 Alpine
- Belvo Open Finance Data Aggregation (OFDA) Brasil

## Arquitetura

```text
Android
  ├── Resumo mensal
  ├── Transações
  └── Contas / Hosted Widget
             │
             ▼
     Backend HTTPS próprio
       ├── PERSONAL_SUBJECT estável
       ├── sessão curta vinculada ao subject pessoal
       ├── recuperação/reconciliação de links
       ├── criação do Widget Access Token
       ├── validação de propriedade dos links
       ├── DTO mínimo de transações
       ├── exclusão assíncrona
       └── webhook + SQLite técnico
             │
             ▼
        Belvo OFDA Brasil
             │
             ▼
      Instituição financeira
```

As credenciais `BELVO_SECRET_ID` e `BELVO_SECRET_PASSWORD` nunca ficam no APK.

## Totais financeiros

Os totais mensais e as categorias consideram transações em BRL que já não estejam com status `PENDING`. Uma transação `PENDING` é mostrada na lista, mas fica em um cartão separado de entradas/saídas pendentes até a instituição informar que foi processada. Movimentações sem valor convertido para BRL também não são misturadas aos totais em reais.

## Atualização dos dados

O botão **Atualizar painel** primeiro recupera/reconcilia as conexões ligadas ao `PERSONAL_SUBJECT` e depois consulta o que já está disponível no provedor. Ele não promete nem força coleta bancária em tempo real. A frequência de atualização de links recorrentes depende da configuração contratada com a Belvo. A carga histórica inicial pode abranger até aproximadamente 365 dias conforme o recurso/instituição.

Se o armazenamento local estiver vazio após uma reinstalação, o mesmo botão aparece como **Recuperar conexões e atualizar**; após informar o código pessoal do backend, ele repovoa os `link.id` locais a partir do servidor.

## Build e CI

O workflow `.github/workflows/android.yml` valida em paralelo:

- syntax check e testes do backend no Node 24.16;
- build do container;
- testes JVM Android;
- `lintDebug`;
- APK debug;
- `assembleRelease` com minificação/shrink de recursos.

Para gerar um APK apontando para o backend:

```bash
gradle test :app:assembleDebug -PBACKEND_BASE_URL=https://api.seu-dominio.com
```

Sem `BACKEND_BASE_URL`, o app continua compilável para demonstração, mas a conexão Open Finance real fica desabilitada.

## Open Finance

Consulte `OPEN_FINANCE_SETUP.md` para configurar sandbox, backend, webhook e Belvo.

## Segurança, privacidade e produção

- `SECURITY.md` — modelo de ameaça, segredos, persistência e limites.
- `PRIVACY_TEMPLATE.md` — inventário técnico de dados para elaboração da política final.
- `PRODUCTION_CHECKLIST.md` — gates obrigatórios para dados reais/publicação.
- `REVIEW_REPORT.md` — relatório da revisão Crítico ↔ Executor.

## Limite intencional

O backend aceita somente `DEPLOYMENT_MODE=personal`. Para vários usuários é obrigatório substituir o código compartilhado por autenticação individual, migrar callbacks para App Links HTTPS verificados e cumprir os demais gates de `PRODUCTION_CHECKLIST.md`.
