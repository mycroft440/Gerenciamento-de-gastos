# Gerenciamento de Gastos

Aplicativo Android nativo para consolidar receitas e despesas e conectar contas bancárias pelo Open Finance brasileiro.

## Estado atual

O projeto já contém:

- painel com saldo, entradas e saídas;
- gastos agrupados automaticamente por categoria;
- pesquisa de transações;
- modo demonstrativo para desenvolvimento sem credenciais;
- integração real preparada com Belvo OFDA (Open Finance Brasil);
- Hosted Widget para consentimento bancário;
- retorno por deep link e armazenamento local do `link.id`;
- backend próprio para proteger as credenciais Belvo;
- autenticação de curta duração entre app e backend;
- webhook para aguardar a carga histórica antes de buscar movimentações;
- importação das transações reais para o painel;
- desconexão/revogação do link;
- testes Android e backend no GitHub Actions;
- Dockerfile para hospedar o backend.

> O código do fluxo real está implementado, mas a conexão com um banco só é ativada depois de configurar credenciais Belvo, URLs públicas de termos/logo, webhook e uma URL HTTPS para o backend. Veja `OPEN_FINANCE_SETUP.md`.

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

- Node.js 22
- APIs HTTP nativas do Node, sem framework ou dependências npm de produção
- Container Docker
- Belvo Open Finance Data Aggregation (OFDA) Brasil

## Arquitetura

```text
Android
  ├── Resumo
  ├── Transações
  └── Contas / Hosted Widget
             │
             ▼
     Backend HTTPS próprio
       ├── sessão curta do app
       ├── criação do Widget Access Token
       ├── contas/transações
       └── webhook histórico
             │
             ▼
        Belvo OFDA Brasil
             │
             ▼
      Instituição financeira
```

As credenciais `BELVO_SECRET_ID` e `BELVO_SECRET_PASSWORD` nunca ficam no APK. O celular recebe apenas a URL temporária do Hosted Widget e, após o consentimento, o identificador da conexão (`link.id`).

## Build

O workflow `.github/workflows/android.yml` executa em paralelo:

- testes do backend no Node 22;
- testes Android;
- compilação do APK debug;
- upload do APK como artefato do GitHub Actions.

Para gerar um APK que realmente converse com o backend:

```bash
gradle test :app:assembleDebug -PBACKEND_BASE_URL=https://api.seu-dominio.com
```

Sem `BACKEND_BASE_URL`, o APK continua compilável e o modo de demonstração permanece disponível.

## Configuração do Open Finance

Siga `OPEN_FINANCE_SETUP.md` para:

1. obter as credenciais Belvo;
2. configurar sandbox/produção;
3. publicar termos, ícone e logo;
4. hospedar o backend em HTTPS;
5. cadastrar o webhook;
6. apontar o APK para o backend.

## Próximas etapas

1. Hospedar o backend e configurar a conta Belvo.
2. Adicionar persistência local das transações reais para uso offline.
3. Persistir no servidor o estado dos webhooks/conexões.
4. Melhorar categorização e permitir correção manual.
5. Adicionar orçamento, metas, recorrências e alertas.
6. Para distribuição multiusuário, substituir o código de acesso pessoal por autenticação individual e App Links HTTPS verificados.
