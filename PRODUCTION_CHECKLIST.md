# Checklist de Produção

Este arquivo é um **gate de publicação**. O fato de o CI estar verde não significa que o aplicativo esteja autorizado para distribuição pública.

## A. MVP pessoal / sandbox

- [ ] Criar credenciais Belvo Sandbox.
- [ ] Hospedar backend em HTTPS.
- [ ] Montar volume persistente em `/data` para o SQLite.
- [ ] Configurar `DEPLOYMENT_MODE=personal`.
- [ ] Gerar e preservar um `PERSONAL_SUBJECT` aleatório, sem PII, no backend.
- [ ] Definir segredos fortes em secret manager.
- [ ] Publicar termos, ícone e logo exigidos pelo Hosted Widget.
- [ ] Configurar webhook `POST /webhooks/belvo` com `Authorization: Bearer`.
- [ ] Compilar o APK com `BACKEND_BASE_URL` HTTPS real.
- [ ] Testar conexão, carga histórica, atualização do painel e exclusão no sandbox.
- [ ] Testar reinício do backend e continuidade do estado persistido.
- [ ] Testar reinstalação do Android: cache local vazio deve recuperar os links pelo `PERSONAL_SUBJECT` sem reconectar o banco.
- [ ] Confirmar que trocar `PERSONAL_SUBJECT` é tratado como criação de outro perfil técnico, não como rotação comum.

## B. Uso pessoal com dados reais

Além da seção A:

- [ ] Solicitar acesso ao ambiente de produção da Belvo.
- [ ] Concluir o processo de certificação exigido pela Belvo.
- [ ] Obter credenciais de produção e trocar `BELVO_BASE_URL` para `https://api.belvo.com`.
- [ ] Configurar webhook de produção.
- [ ] Definir política real de retenção e revisar `BELVO_STALE_DAYS`.
- [ ] Fazer backup criptografado/teste de restauração do volume técnico e da configuração `PERSONAL_SUBJECT`.
- [ ] Configurar monitoramento de disponibilidade e alertas sem registrar dados financeiros.
- [ ] Definir rotina de rotação de segredos sem alterar acidentalmente o subject estável.

## C. BLOQUEADORES para distribuição pública/multiusuário

**Não publicar para vários usuários enquanto qualquer item desta seção estiver aberto.**

### Identidade e autorização

- [ ] Substituir `APP_ACCESS_CODE` compartilhado por autenticação individual.
- [ ] Associar cada sessão a um identificador estável server-side do usuário autenticado.
- [ ] Gerar o `external_id` por usuário no backend/banco de identidade; nunca confiar em um subject fornecido pelo cliente.
- [ ] Implementar revogação de sessões/dispositivos e recuperação de conta.
- [ ] Migrar rate limiting para armazenamento compartilhado caso existam múltiplas instâncias.

### Callbacks e domínio

- [ ] Possuir domínio HTTPS de produção.
- [ ] Migrar callbacks do esquema `gerenciamentogastos://` para Android App Links HTTPS.
- [ ] Adicionar `android:autoVerify="true"` no manifest da variante de produção.
- [ ] Publicar `/.well-known/assetlinks.json` no domínio sem redirects e com fingerprint do certificado correto.
- [ ] Validar success/exit/error end-to-end com o domínio verificado.

### Infraestrutura

- [ ] Definir banco/arquitetura de estado compatível com a quantidade de instâncias e usuários.
- [ ] Definir backup, restauração, retenção e exclusão.
- [ ] Colocar segredos/configuração privada em secret manager com controle de acesso e rotação.
- [ ] TLS válido, HSTS no proxy de borda e política de atualização de dependências/runtime.
- [ ] Observabilidade com redaction e política de acesso aos logs.
- [ ] Plano de incidentes e canal privado para vulnerabilidades.

### Privacidade / LGPD

- [ ] Definir controlador e contato de privacidade.
- [ ] Revisar juridicamente `PRIVACY_TEMPLATE.md` e publicar a política final em HTTPS.
- [ ] Definir bases legais e processo de atendimento aos direitos do titular.
- [ ] Mapear fornecedores/suboperadores, retenção e eventual transferência internacional.
- [ ] Garantir que exclusão/revogação tenha procedimento operacional documentado.
- [ ] Verificar se termos e política publicados correspondem exatamente ao comportamento da versão enviada.

### Google Play

- [ ] Configurar conta e Play App Signing; proteger a chave de upload.
- [ ] Gerar AAB de release assinado apontando para o backend de produção.
- [ ] Preencher a seção **Segurança dos dados** de acordo com o tráfego real do app e terceiros.
- [ ] Preencher a **Declaração de funcionalidades financeiras** no Play Console.
- [ ] Revisar a Política de Serviços Financeiros do Google Play para o(s) país(es) alvo.
- [ ] Disponibilizar URL pública da política de privacidade.
- [ ] Fazer teste fechado antes de promover para produção.

## D. Critérios técnicos do CI

Uma alteração de código só está apta a revisão final se:

- backend syntax check passa;
- todos os testes Node passam;
- container de produção constrói;
- testes JVM Android passam;
- `lintDebug` passa sem erros;
- APK debug constrói;
- build release otimizado (`assembleRelease`) constrói.

Assinatura de release não ocorre no CI público deste repositório porque a chave de upload nunca deve ser versionada.
