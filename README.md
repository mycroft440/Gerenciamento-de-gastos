# Gerenciamento de Gastos

MVP Android nativo para consolidar entradas e saídas, categorizar transações e preparar a conexão futura com instituições financeiras via Open Finance.

## Estado atual

O aplicativo já contém:

- painel mensal com saldo, entradas e saídas;
- gastos agrupados automaticamente por categoria;
- pesquisa de transações;
- origem da transação (banco/conta);
- tela de contas com simulação de conexão;
- classificador simples por palavras-chave;
- contrato `OpenFinanceGateway` para substituir a fonte demo por um agregador real;
- teste unitário do classificador;
- GitHub Actions para testar e gerar APK debug.

> **Importante:** a versão atual usa somente dados de demonstração. Não solicita senha bancária, não armazena credenciais e não faz conexão real com bancos.

## Stack

- Kotlin / Android nativo
- Jetpack Compose + Material 3
- AGP 9.3.0
- Gradle 9.5.0
- JDK 17
- compileSdk / targetSdk 37
- Compose BOM 2026.08.00

## Arquitetura inicial

```text
UI (Compose)
  ├── Resumo
  ├── Transações
  └── Contas
       ↓
FinanceRepository
       ↓
OpenFinanceGateway
       ├── DemoOpenFinanceGateway (agora)
       └── Provedor/agregador Open Finance (futuro)
```

A camada `OpenFinanceGateway` existe para evitar acoplar a interface do aplicativo a um provedor específico. Quando o agregador for escolhido, a implementação demo poderá ser trocada pela implementação real sem reescrever as telas.

## Build

O projeto foi preparado para compilar pelo GitHub Actions. O workflow instala o SDK Android necessário, executa testes e gera `app-debug.apk` como artefato.

Workflow: `.github/workflows/android.yml`

## Próximas etapas

1. Escolher o agregador/provedor de Open Finance.
2. Criar backend seguro para OAuth/consentimento e tokens.
3. Persistir contas e transações localmente.
4. Sincronizar transações reais.
5. Melhorar regras de categorização e permitir correção manual.
6. Adicionar orçamento, metas, recorrências e alertas.

## Segurança

Nunca colocar segredo de API, certificado, token ou senha bancária dentro do APK. Credenciais de servidor devem ficar no backend/secret manager. O aplicativo cliente deve receber apenas os dados e tokens estritamente necessários para o fluxo autorizado.
