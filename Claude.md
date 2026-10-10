# ChamadosPrediais — App Android (TCC)

Aplicativo Android do **Sistema de Chamados de Manutenção Predial**, TCC do Técnico em Informática da FIEC.
Responsável pelo app: Gabriel. O back-end (Spring Boot, codinome "Kipper") é mantido por outra parte da equipe
no repositório `Lipetice/ManuPred_BE` — mudanças lá devem ser combinadas com a equipe, não feitas por conta própria.

Responda sempre em **português do Brasil**.

## Stack

- Kotlin, Views em XML com **View Binding** (sem Compose nas telas reais)
- Retrofit 2.11 + Gson, OkHttp 4.12 (+ logging-interceptor), Coroutines (`lifecycleScope`)
- Login com Google via **Credential Manager** (`androidx.credentials` + `googleid`)
- Firebase **apenas** para notificações push (FCM). Não há Firebase Auth no projeto.
- Token de sessão em SharedPreferences (`TokenManager`)
- `namespace`: `com.br.fiec.chamados` · `applicationId`: `com.br.fiec.chamadosprediais`
- minSdk 34, compileSdk/targetSdk 37

## Estrutura

```
app/src/main/java/com/br/fiec/chamados/
├── data/
│   ├── TokenManager.kt              # JWT do servidor em SharedPreferences
│   ├── model/AuthModels.kt          # DTOs de autenticação
│   ├── model/SolicitacaoModels.kt   # DTOs de chamados (espelham o back-end V4)
│   └── network/
│       ├── ApiService.kt            # rotas Retrofit
│       ├── AuthInterceptor.kt       # injeta "Authorization: Bearer <jwt>" (exceto /api/v1/auth/)
│       ├── RetrofitClient.kt        # BASE_URL = http://localhost:8080/ (via adb reverse)
│       └── MyFirebaseMessagingService.kt  # pacote declarado: com.br.fiec.chamados.network
├── ui/login/LoginActivity.kt        # launcher; e-mail/senha + Google
├── ui/chamados/                     # Lista, Detalhe, NovoChamado, ChamadoAdapter
└── MainActivity.kt + ui/theme/      # template Compose não utilizado (não registrado no manifest)
```

## Convenções obrigatórias

- **View Binding em tudo.** Nunca usar `findViewById`. Activities: `XxxBinding.inflate(layoutInflater)`;
  adapters: ViewHolder recebe o binding (`ItemChamadoBinding`).
- Chamadas de rede em `lifecycleScope.launch { try { ... } catch { ... } finally { mostrarCarregando(false) } }`,
  com mensagem amigável ao usuário em caso de erro.
- Rotas no `ApiService` sempre com o prefixo `api/v1/...`.
- **DTOs espelham o back-end.** Ao mudar um DTO, confira o record Java correspondente no back-end.
  UUID e LocalDateTime chegam como `String`.
- **Enums vindos da API devem ser anuláveis** nos DTOs (`status: StatusSolicitacao?`): o Gson atribui `null`
  a valores desconhecidos mesmo em campos não-nulos, o que causaria NullPointerException. Exibir com
  `rotuloOuPadrao()`.
- Não manter `usesCleartextTraffic` no manifest (decisão do projeto). Em desenvolvimento usa-se `adb reverse`.
- Segredos fora do código: `WEB_CLIENT_ID` vem de `local.properties` → `BuildConfig.WEB_CLIENT_ID`.
  Nunca commitar `local.properties`.

## Contrato com o back-end (branch V4)

Autenticação (rotas públicas):
- `POST /api/v1/auth/login` `{ email, password }` → `{ token }`
- `POST /api/v1/auth/register` `{ nome, email, password, fcmToken? }`
- `POST /api/v1/auth/google` `{ idToken }` → `{ token }` — o back-end valida o idToken com
  `GoogleIdTokenVerifier` (aud == Web Client ID); se o usuário não existir, ele é cadastrado automaticamente.
  Erro → HTTP 401 com `{ "erro": "mensagem" }`.

Autenticadas (Bearer JWT, validade de **30 minutos**, sem refresh):
- `GET /api/v1/users/me` → `{ email, nome }` (ainda não devolve `id` nem `role`)
- `GET /api/v1/admin/tipos` → tipos de chamado cadastrados no banco (`id, nome, icone, slaHoras`)
- `GET /api/v1/solicitacoes/search?page&size&sort=createdAt,desc&status&...` → `Page<SolicitacaoResponseDTO>`
- `GET /api/v1/solicitacoes/{id}`
- `POST /api/v1/solicitacoes` multipart: parte `dados` (JSON `{ tipoId, localId?, localizacaoProblema, descricao, numeroPatrimonio? }`) + partes `anexos` (imagens)
- Outras já existentes no back-end e ainda não usadas no app: `/{id}/comentarios` (GET/POST), `/{id}/historico`,
  `/{id}/status`, `/{id}/prioridade`, `/{id}/tecnico`, `/{id}/urgente`, `/{id}/concluir`, `/{id}/validar`,
  `/{id}/rejeitar`, `/{id}/ajudantes/{ajudanteId}`
- Status: `ABERTO, EM_ANDAMENTO, AGUARDANDO_ASSISTENCIA_EXTERNA, CONCLUIDO, FINALIZADA, REJEITADA, CANCELADO`
- Prioridade: `BAIXA, MEDIA, ALTA, URGENTE`
- URLs de anexos são relativas (`/uploads/...`): concatenar com a BASE_URL para exibir.

## Login com Google (fluxo definido pelo professor)

1. Credential Manager com `setServerClientId(BuildConfig.WEB_CLIENT_ID)` (Client ID do tipo **Web**).
2. Extrai o `idToken` de `GoogleIdTokenCredential` e envia em `POST /api/v1/auth/google`.
3. Salva o JWT devolvido no `TokenManager`; o `AuthInterceptor` usa nas demais requisições.

O Web Client ID do app e o do back-end (`app.google.web-client-id`) precisam ser **iguais** e do mesmo
projeto Google Cloud em que está o Android Client ID (pacote + SHA-1 da máquina). O app usa o projeto
`kipper-56bb0`. Cada computador tem um SHA-1 de debug diferente (`./gradlew signingReport`), que precisa
estar cadastrado no Firebase.

## Rodando localmente

1. Back-end na porta 8080 (MySQL na 3310 por padrão).
2. `adb reverse tcp:8080 tcp:8080`
3. O banco começa sem tipos de chamado: cadastre ao menos um em `POST /api/v1/admin/tipos` (usuário ADMIN) via Swagger
   (`http://localhost:8080/swagger-ui.html`).
4. Build: `./gradlew assembleDebug` — após mudar `local.properties`, fazer Rebuild (o valor entra no BuildConfig).

## Pendências conhecidas (ordem sugerida)

1. Testar ponta a ponta a adaptação à V4 e o login com Google (alinhar Web Client ID).
2. Enviar o token FCM ao back-end após o login (falta endpoint no back-end para atualizar `fcmToken`).
3. Abas de comentários e histórico na tela de detalhe.
4. Tratar 401/403 de forma centralizada (hoje só a lista redireciona ao login).
5. Painel Kanban do funcionário, painel do supervisor e área administrativa
   (dependem de `/users/me` devolver `id` e `role`).

## Relatório do TCC

O relatório de desenvolvimento (`Relatorio_TCC_Chamados_Prediais.docx`) fica fora do repositório e precisa
estar sempre atualizado. Ao concluir qualquer mudança relevante, termine com um resumo curto do que foi feito,
qual funcionalidade mudou de status e qual problema técnico foi resolvido, para registrar no relatório.