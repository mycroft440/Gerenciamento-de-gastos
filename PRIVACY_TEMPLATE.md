# Modelo de Política de Privacidade

> **Modelo técnico — não publicar sem revisão jurídica e preenchimento dos campos do controlador.** Este arquivo descreve o comportamento atual do software para facilitar a elaboração da política final; não constitui aconselhamento jurídico nem afirma conformidade automática com a LGPD.

## 1. Controlador

Preencher antes da publicação:

- Nome/razão social: **[PREENCHER]**
- CNPJ/CPF, se aplicável: **[PREENCHER]**
- Endereço/país: **[PREENCHER]**
- Contato de privacidade: **[PREENCHER]**
- Encarregado/DPO, se aplicável: **[PREENCHER]**

## 2. Finalidade do aplicativo

O aplicativo consolida movimentações financeiras autorizadas pelo usuário por meio do Open Finance para apresentar saldo do período, entradas, saídas, categorias e histórico de transações.

## 3. Dados tratados pelo fluxo atual

### Nome completo e CPF

São informados na tela de conexão e enviados ao backend exclusivamente para criar o consentimento Open Finance na Belvo. O backend desta versão não persiste nome nem CPF.

### Identificador local (`external_id`)

É um UUID aleatório gerado pelo app. Não contém CPF, e-mail ou nome. É usado para vincular sessões e conexões Open Finance ao mesmo dispositivo/perfil local.

### Identificador da conexão (`link.id`)

É armazenado localmente para que o app possa consultar o estado da conexão e carregar dados autorizados. Sozinho, ele não substitui a autenticação exigida pelo backend.

### Movimentações financeiras

O backend consulta transações autorizadas e devolve ao app somente os campos necessários para o painel: identificador, descrição, valor, moeda, direção, data, origem e status. O objeto bruto retornado pelo provedor não é repassado ao APK.

Nesta versão, as transações ficam somente em memória no Android e são descartadas quando o processo do app é encerrado. Não existe cache local persistente de extratos.

### Metadados técnicos do backend

O backend persiste apenas informações necessárias para continuidade dos webhooks e exclusão de conexões, como `link_id`, `external_id`, readiness, erros técnicos, IDs de webhooks e timestamps. CPF e extratos não são gravados nesse banco.

## 4. Terceiros envolvidos

O fluxo Open Finance utiliza:

- a instituição financeira escolhida pelo usuário;
- Belvo, como provedora da infraestrutura de Open Finance/OFDA;
- o provedor de hospedagem escolhido para o backend.

A política final precisa identificar corretamente os operadores/suboperadores realmente contratados e refletir os termos vigentes de cada um.

## 5. Segurança

O projeto usa HTTPS para a comunicação do aplicativo, sessões curtas, segregação de segredos no backend, autenticação do webhook, validação de propriedade das conexões e minimização dos dados enviados ao APK. Veja `SECURITY.md`.

## 6. Retenção

Definir juridicamente antes da publicação. No código atual:

- nome e CPF não são persistidos pelo backend;
- transações não são persistidas no Android;
- metadados técnicos de conexão permanecem enquanto necessários para administrar o consentimento/exclusão;
- IDs de webhooks antigos são podados pelo backend;
- a retenção configurada no provedor (`BELVO_STALE_DAYS`) deve ser alinhada à política final e ao contrato vigente.

## 7. Revogação e exclusão da conexão

O usuário pode solicitar a remoção de uma conexão pelo aplicativo. O backend solicita a exclusão assíncrona do link na Belvo e mantém o identificador local até receber confirmação `link_deleted`. Depois da confirmação, a conexão é removida localmente na próxima atualização do painel.

A política final deve explicar também como o titular solicita exclusão de eventuais dados mantidos fora desse fluxo técnico.

## 8. Bases legais e direitos do titular

**[PREENCHER APÓS ANÁLISE JURÍDICA]**. Não inferir automaticamente uma base legal apenas pelo consentimento Open Finance. A política final deve identificar as bases aplicáveis e informar como o titular exerce os direitos previstos na legislação pertinente.

## 9. Transferências internacionais

**[AVALIAR E PREENCHER]** de acordo com os fornecedores e regiões efetivamente utilizados na implantação.

## 10. Google Play

Antes de publicar no Google Play, a declaração de Segurança dos dados deve refletir o comportamento real do app e de todos os terceiros/SDKs. O formulário precisa considerar, entre outros, dados pessoais de identificação e informações financeiras/transacionais transmitidas para fora do dispositivo.

## 11. Alterações

Definir versão e data efetiva da política publicada e manter histórico de alterações relevantes.
