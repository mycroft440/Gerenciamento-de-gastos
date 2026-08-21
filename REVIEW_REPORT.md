# Relatório de Revisão Crítico ↔ Executor

Revisão iniciada em agosto de 2026 sobre o MVP `Gerenciamento de Gastos`. O processo foi feito por blocos: o Crítico identifica falhas e critérios de aceitação; o Executor corrige; o bloco é reavaliado antes de avançar.

## Status dos blocos

| Bloco | Status | Principais resultados |
| --- | --- | --- |
| Segurança / Open Finance | Aprovado para MVP pessoal | vínculo de sessão/link, webhook Bearer, rate limit, timeouts, minimização de dados, exclusão assíncrona e persistência técnica |
| Backend / API | Aprovado para MVP pessoal | paginação segura, erros de upstream isolados, validação de datas, DTO mínimo e SQLite persistente |
| Android / persistência | Aprovado para MVP pessoal | `BigDecimal`, vários bancos, filtro mensal, não mistura demo/real, moeda/status e cleanup de cliente/WebView |
| UX / fluxos | Aprovado para MVP pessoal | linguagem de “Atualizar painel”, identificação das instituições, sem promessa de tempo real, carga histórica informada e remoção segura |
| Testes / CI | Aprovado | testes Node/JVM, syntax check, lint, Docker, debug e release otimizado |
| Produção / compliance | Aprovado com gates externos | limitações públicas transformadas em bloqueios explícitos; ver `PRODUCTION_CHECKLIST.md` |

## Problemas relevantes encontrados e corrigidos

1. callback podia fornecer `link.id` sem prova de propriedade;
2. uma sessão poderia tentar consultar um link arbitrário;
3. webhook usava segredo na URL;
4. autenticação não possuía limitação de tentativas;
5. readiness/exclusão sumiam após reinício do backend;
6. JSON bruto da Belvo expunha mais campos ao APK que o necessário;
7. erros HTTP do provedor eram confundidos com erros do usuário;
8. paginação podia seguir origem inesperada;
9. exclusão assíncrona era apresentada como concluída cedo demais;
10. instituição da transação era lida do caminho errado;
11. valores monetários usavam `Double`;
12. dashboard mensal somava histórico fora do mês;
13. somente uma conexão bancária era suportada;
14. reinício do Android podia mostrar dados demo apesar de haver banco real salvo;
15. moedas estrangeiras podiam contaminar totais em BRL;
16. executor de rede/WebView não tinham ciclo de vida suficientemente explícito;
17. UX usava “sincronizar” como se o botão forçasse atualização no banco;
18. CI não executava lint nem validava container/release;
19. runtime Node deixava SQLite em estágio experimental;
20. pipeline utilizava Actions/runtimes obsoletos e instalava ferramentas Android desnecessárias;
21. documentação antiga ainda descrevia webhook por token na URL e Node 22;
22. não havia separação formal entre MVP pessoal e distribuição pública;
23. `.gitignore` não impedia versionamento acidental de `.env`, SQLite e keystores;
24. conexões múltiplas eram exibidas apenas por UUID, permitindo remoção do banco errado;
25. APIs Android deprecated geravam warnings no build do WebView/locale;
26. build release não era exercitado pelo CI.

## Decisões deliberadas

### Transações não são cacheadas localmente

O aplicativo mantém transações reais apenas em memória nesta versão. Isso reduz persistência de dados financeiros sensíveis no dispositivo, mas significa que o usuário precisa carregar o painel novamente ao reabrir o app. Cache offline futuro deve ser implementado com modelo de ameaça e proteção em repouso apropriados.

### Autenticação permanece pessoal

`APP_ACCESS_CODE` é aceitável somente para uso pessoal/controlado. O backend rejeita qualquer `DEPLOYMENT_MODE` diferente de `personal`. O Crítico considera isso **não aprovado para multiusuário**, por design, até a autenticação individual ser implementada.

### Deep link customizado permanece somente no MVP pessoal

O backend valida a propriedade do `link.id`, reduzindo o impacto de callback forjado. Para distribuição pública, App Links HTTPS verificados são gate obrigatório.

### Nome da instituição é persistido, não dados de conta

Para evitar remoção cega por UUID, o app persiste somente um rótulo derivado do identificador institucional da Belvo. Links antigos sem rótulo ficam com o botão Remover desativado até serem identificados pelo backend.

## Definição de “Crítico satisfeito”

O Crítico considera o **código do MVP pessoal** satisfatório quando:

- não existe problema conhecido crítico/alto/médio sem correção ou decisão explícita;
- o CI final está integralmente verde;
- limitações que dependem de infraestrutura/contratos não são apresentadas como funcionalidades prontas;
- publicação pública é bloqueada documental e tecnicamente enquanto os pré-requisitos de identidade, domínio, privacidade e distribuição não forem atendidos.

A aprovação deste relatório **não equivale a certificação da Belvo, parecer jurídico/LGPD ou aprovação do Google Play**.
