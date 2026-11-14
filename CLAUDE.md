<!--
SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
SPDX-License-Identifier: Apache-2.0
-->

# CLAUDE.md — idejo Android app (`org.idejo.app`)

Read this file before every change, and update it in the same pull request when a change
alters anything it says (architecture, conventions, commands, decisions, milestones).

## What the app is

The official Android client for idejo forums. It works in **idejo ID mode** (signed in to
a group's members' inbox, my.idejo.org, opening on its Forums / Topics / Chats tabs, with
every joined forum one tap away) or **single-forum mode** (one forum, typed by address).
Inside a forum it is idejo — top bar with logo, breadcrumbs and user menu, the sidebar of
categories and followed topics, live topics of growing messages, the composer — built as a
native Compose app in each forum's colours.

## Specifications — the source of truth

- `idejo Android — Spec Sheet.md` (this repository): every app requirement, with stable IDs
  (MD, SI, SH, TH, BR, TV, CO, MA, IB, PC, NT, ST, OF, DL, AX, SY, MO, RS, IX, app NF, LI).
- idejo's `docs/api-guide.md` and the forum's `/api/v1/openapi.json`: the API contract.
- idejo's `docs/member-guide.md`: how every feature behaves and reads on the web. When the
  app spec is silent, the app does what the member guide says, with the same words.
- idejo's *Spec Sheet v1* (GM, TP, NO, PN, AX…) and the *Idejo Hub — Spec Sheet* (inbox,
  directory, idejo ID): background, cited as "idejo GM-03", "Hub ID-05".

Sibling checkouts on the steward's machine: `../Idejo` (the forum) and `../idejo-hub`.
Read them for behaviour; never copy their code (see Licence).

If the spec and these documents disagree, or a requirement is unclear, **stop and ask**
rather than guess. Record each answer under "Steward decisions" below.

## The one rule

**The app talks to forums only through idejo's public API v1** — `/.well-known/idejo`,
`/app/authorize` in a Custom Tab, `/api/v1/*`, the socket `/api/v1/socket`, and the forum's
`/logo` and image URLs the API returns. Never scrape HTML, never call web routes, never
embed the web app in a WebView.

- If a feature needs something the API does not offer, **stop and ask**. The fix is a new
  documented endpoint in idejo (spec section 22, IX-xx), never a workaround in the app.
- **The server owns the rules.** The growing-message window, slow mode, permissions, rate
  limits, automatic punctuation: the app sends what the member did and shows what the
  server answers. Draw the countdown from `GET /topics/{id}/composer` (with the server's
  `now`); never compute whether a message may start. Show the server's error `message` text.
- **Ignore what you don't know.** Unknown JSON fields, events and error codes must not
  crash or break anything (API v1 is additive only).
- A token goes only to the host that issued it. Every forum is its own world: own HTTP
  client, socket, cache, theme and token.

## Stack

- Kotlin (latest stable), coroutines, Flow; JDK 17; Gradle with `gradle/libs.versions.toml`,
  every version pinned.
- minSdk **26**, compile and target the latest stable SDK.
- Jetpack Compose, Material 3, Material 3 adaptive; Navigation Compose with typed routes.
- Hilt; OkHttp + Retrofit + kotlinx.serialization (`ignoreUnknownKeys = true`,
  `coerceInputValues = true`).
- Our own Phoenix Channels v2 client (`core/realtime`) over OkHttp's WebSocket.
- Room (cache), DataStore (settings), Android Keystore AES-GCM (tokens, push keys),
  WorkManager (drafts, trimming), Coil 3 (images).
- commonmark-java + GFM extensions + our idejo extensions (`core/markdown`), rendered to
  Compose; no HTML ever.
- AndroidX Browser (Custom Tabs) for sign-in, admin, account deletion and outside links.
- UnifiedPush connector; the embedded FCM distributor **only** in the `play` flavour.
- Tests: JUnit, Turbine, MockWebServer, Compose UI tests, Roborazzi; end-to-end against
  idejo's Docker image (`ghcr.io/callisto-rpg/idejo`).

No new dependency without a licence check: Apache-2.0, MIT, BSD or similar, accepted by
F-Droid, and nothing proprietary in `foss`. No analytics, crash reporters, ads or tracking
SDKs in any flavour (SY-03).

## Architecture

```
app/                     Application, MainActivity, OAuth callback activity, navigation host,
                         App Links, push receiver, flavours (foss/ and play/ source sets)
core/model/              Kotlin data classes for API resources (pure JVM)
core/api/                Retrofit services per forum, discovery, OAuth (PKCE), error mapping
                         to ApiError(code, message, details) (pure JVM)
core/realtime/           Phoenix Channels client: socket, channels, heartbeat, rejoin with
                         backoff, catch-up cursors (pure JVM)
core/testing/            FakeIdejo: an in-process idejo on MockWebServer (discovery, PKCE
                         sign-in, reading, the growing message, catch-up, the socket), for
                         tests only (NF-08) (pure JVM)
core/data/               Repositories, Room database per forum, DataStore, Keystore,
                         ForumRegistry (forums held, mode, tokens), drafts
core/markdown/           idejo dialect parser + Compose renderer (TV-20 to TV-24)
core/designsystem/       IdejoTheme (forum colours → M3 scheme, TH-02), fonts, components:
                         TopBar, Breadcrumbs, CategoryMark, Dot, Bubble, TopicRow, Message;
                         depends on core/model to draw its resources (TopicItem, dots)
feature/onboarding/      welcome, add forum, sign-in, connect forums (MD, SI)
feature/shell/           forum frame, logo menu, user menu, left sidebar, right sidebar (SH, RS)
feature/browse/          home, category, tags, search, unread, rules, help (BR)
feature/topic/           topic page, messages, jump card, live updates (TV, MA)
feature/composer/        composer state machine, editor, graphical composer, commands (CO)
feature/inbox/           Forums / Topics / Chats (IB), conversations (PC)
feature/notifications/   in-app bubbles, UnifiedPush, channels (NT)
feature/settings/        profile and account, app settings (ST)
feature/moderation/      M6 (MO)
build-logic/convention/  the convention plugins every module applies (idejo.jvm.library,
                         idejo.android.library, .compose, .feature, .hilt, .application,
                         idejo.licensee); SDK levels and JVM from gradle/libs.versions.toml
```

Rules:

- `core/model`, `core/api` and `core/realtime` are plain Kotlin/JVM modules: no Android
  imports, fast unit tests.
- Features depend on `core/*`, never on each other; navigation between features goes
  through routes in `app`.
- One `ForumSession` per forum (Hilt scoped by forum id): its HTTP client with its token,
  its socket, its Room database, its theme. The active forum is a `StateFlow` the shell
  reads; switching forum swaps the session and cross-fades the theme.
- Screens: a stateless `@Composable XScreen(state, onEvent)` plus an `XViewModel` exposing
  `StateFlow<XUiState>`; repositories return `Flow` from Room and refresh from the network
  (cache-first, OF-01).
- The composer is a state machine in `feature/composer` (`ComposerState`: text, sent
  range, unsent ranges, open message id, server composer state, pending commit, offline
  draft). Every transition has a unit test named after its CO/GM ID.

### Growing-message invariants — never break these

These come from idejo GM-01 to GM-15 and the API guide section 6:

1. A commit sends the **whole** open message as shown (`body_md`), never a diff.
2. Enter = `break: false`; Ctrl+Enter, Enter×3 or "Send & start new message" =
   `break: true`. The soft keyboard's newline is intercepted and never inserted; Shift+Enter
   (hardware) and **New line** insert `\n`.
3. The first keystroke of a new message sends `typing` once; the server's clock decides.
4. Sent text keeps higher contrast than unsent; editing sent text makes the changed part
   unsent until the next Enter. Nothing typed is ever lost, also on errors and offline.
5. At most one open message per topic per author; the composer is that message, in place.
6. The app never decides slow mode itself: it shows the server's state and errors.
7. The topic channel stays joined while writing; going to the background closes the open
   message after 30 s on the server — do not fight it.
8. TalkBack hears a growing message when it opens and when it closes, never per append.

## Conventions

- **Requirement IDs** in test names: `fun `CO-03 enter three times completes the message`()`.
  Every ID in the spec has at least one test (NF-08).
- **Strings**: every visible string in `res/values/strings.xml` (plurals in `<plurals>`),
  the web's wording from the member guide wherever the web has one. Never hard-coded text.
- **Colours**: only `MaterialTheme.colorScheme` roles and `IdejoTheme` extras (category,
  role, dot, bubble and inline-link colours, `LinkKind`). Never a literal colour in a
  feature module (TH-02).
- **Typography**: Fraunces for titles, Rubik for text, via `IdejoTheme.typography`.
- **Accessibility**: content descriptions on every icon button, headings marked, 48 dp
  targets, every gesture has a button (AX-04), reduced motion respected.
- **Errors**: map every failure to `ApiError`; show `message`; branch only on `code`.
  `not_found` means "missing or not visible" — never say which.
- **Logging**: never log tokens, codes, verifiers, push keys or message bodies.
- **Formatting**: ktlint and detekt clean; Compose lint rules on.
- **Files**: SPDX header on every source file:
  ```kotlin
  // SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
  // SPDX-License-Identifier: Apache-2.0
  ```

## Licence

Apache-2.0 (LI-01), REUSE-compliant. idejo is AGPL-3.0: **never copy its code, styles,
scripts or templates** into this repository (LI-02). Read them to understand behaviour,
then write it anew. Fonts (Fraunces, Rubik: OFL-1.1) and icons come from their upstream
sources and are credited in `NOTICE` and `REUSE.toml`. Contributions under idejo's CLA
(LI-03).

## Workflow

- One requirement or fix per pull request, reviewed by a person before merge.
- AI-assisted commits carry the trailer `Assisted-by: Claude Code`.
- Before opening a PR: the full check below passes; screenshots updated when the UI changed.
- Human review required for anything touching sign-in, tokens, Keystore, push decryption,
  WebViews or link handling.
- A change that needs a new idejo endpoint: stop, describe it as an IX item in the spec, and
  ask the steward.

## Commands

```sh
./gradlew assembleFossDebug                 # build the F-Droid/GitHub flavour
./gradlew assemblePlayDebug                 # build the Play flavour
./gradlew assembleFossRelease               # release build with R8 (unsigned)
./gradlew installFossDebug                  # install on a connected device or emulator
./gradlew test                              # unit tests: every module, both flavours
./gradlew :core:testing:test                # the API guide's flows against FakeIdejo
./gradlew verifyRoborazziDebug              # screenshot tests (recordRoborazziDebug to update)
./gradlew ktlintCheck detekt                # style and static analysis (whole repository)
./gradlew ktlintFormat                      # fix what ktlint can fix
./gradlew lint lintPlayDebug                # Android Lint: every module, and the play flavour
./gradlew licensee                          # licences of every dependency, every variant
reuse lint                                  # licence headers (REUSE)
./gradlew check                             # all of the above but reuse and lintPlayDebug
```

Needs JDK 17 and the Android SDK (`ANDROID_HOME`, or `sdk.dir` in `local.properties`).
ktlint (with the Compose rules) and detekt run as their pinned command-line tools from the
root build, over every `.kt` and `.kts` file; detekt is 1.23.8, its latest stable (2.0 is
still alpha). Android Lint fails on warnings; only the "newer version available" checks
are off. Screenshots (Roborazzi on Robolectric, SDK 34: Robolectric's later sandboxes need
Java 21) live in each module's `src/test/screenshots`. `FakeIdejo` (core/testing) is the
fake server of NF-08: depend on it with `testImplementation(project(":core:testing"))`,
never from main code. Commands for instrumented and end-to-end tests (against idejo's
Docker image) are added with them.

## Steward decisions

- 2026-11-13 — Initial decisions, recorded in the spec's section 2: Compose + Material 3
  with idejo's identity; minSdk 26; first-launch choice of idejo ID or single-forum mode;
  inbox tabs as home in idejo ID mode; the inbox's Topics put together in the app; connect
  every joined forum at setup; theme from `GET /server` (IX-01); full-screen logo menu,
  user menu and sidebar; Enter×3, graphical composer and hardware keys; editor follows the
  member's graphical-composer setting; adaptive large screens; Play, F-Droid and GitHub;
  UnifiedPush with embedded FCM in Play; moderation after parity; offline reading and
  drafts; verified App Links; native embeds with tap-to-load; idejo's languages; right
  sidebar after parity (activity, recent places, presence); Apache-2.0.
- 2026-11-14 — The repository is `callisto-rpg/idejo-android-app` (GitHub Actions). `main`
  started with an empty commit; CLAUDE.md and the spec came in with PR 1.
- 2026-11-14 — The CLA check runs on pull requests (contributor-assistant, idejo's own
  `docs/legal/CLA.md`, signatures in this repository's `cla-signatures` branch).
- 2026-11-14 — The launcher icon is the idejo logo from `Logo2Small.svg` (CC-BY-4.0,
  Mariano Pictet, as idejo's logos): its path unchanged, gold on idejo's light background
  `#fdfcf6`, inside the adaptive icon's safe zone; the monochrome icon is the same path.
- 2026-11-14 — Backups: none until SY-02 (`allowBackup="false"`, and every domain excluded
  in `data_extraction_rules.xml` and `backup_rules.xml`).
- 2026-11-14 — The socket's token travels as Phoenix's `auth_token`, in
  `Sec-WebSocket-Protocol: phoenix, base64url.bearer.phx.<token>`, never in the address
  (SI-04, API guide §7). The token is in **standard** Base64 without padding: Phoenix decodes
  it with `Base.decode64!(…, padding: false)` despite the prefix's name. idejo reads it from
  `connect_info.auth_token` (callisto-rpg/Idejo#357, DV-06); a token in the connect params is
  refused. The fake server decodes it as strictly as Phoenix.
- 2026-11-14 — M1 and later milestones overlap: the later milestone wins. M1's logo menu has
  SH-10, SH-11 and SH-14 explaining MD-03 (SH-12, SH-13, SH-15 wait for M4). Its user menu has
  Theme, Tutorial bubbles, Sign out, App settings (version and licences only), Accounts and
  Admin, without Profile and account (M3). The user card has Ignore, without Private Message
  (M2).
- 2026-11-14 — The Room cache comes in M5 (OF-01). In M1, a DataStore file holds the forum:
  its address, discovery, theme (TH-06), the sealed token and the member's choices. Pages
  loaded stay on screen after signing out; nothing else persists across launches.
- 2026-11-14 — idejo's socket change goes first in the M1 stack. M1's live features are
  tested against FakeIdejo, which reads the token as idejo does.
- 2026-11-14 — Getting started (BR-11) is the web's getting-started card, not the welcome
  card: idejo adds `intro_due` to `GET /me` and `POST /me/intro` (`hide`) (IX-07,
  callisto-rpg/Idejo#360). `POST /me/welcome` marks the welcome card (Rules & Roles, BR-07).
- 2026-11-14 — Help (BR-08) reads the `!` commands from `GET /server`'s `commands`, and
  plugins' `help` (IX-08, callisto-rpg/Idejo#361); the keys, signs and formats are the app's
  own strings in the web's words.
- 2026-11-14 — The home page's **View** works as on the web: idejo answers
  `GET /topics/active?expanded=true` with previews (callisto-rpg/Idejo#359).
- 2026-11-14 — Inline links (TV-21): idejo gives each mention `source`, how it is written in
  `body_md`, lowercased, and lists `&forum` links as `forum` mentions
  (callisto-rpg/Idejo#358). `core/markdown` draws a mention's `text` wherever its `source`
  stands as a word, ignoring case, outside code and links; on older servers it falls back
  to `text`. The app never reads the signs `@ + # / $ &` itself.
- 2026-11-14 — Filter by tag (BR-04) groups the tags as the web: those of every category,
  then each category's in the home page's order, a tag of several categories under each.
  idejo lets a tag belong to several categories and `GET /tags` gives `category_ids`
  (IX-10, callisto-rpg/Idejo#363); tag names stay unique across the server.

## Milestones (spec §26)

- [x] M0 — Foundations
- [ ] **M1 — Read a forum**
- [ ] M2 — Write
- [ ] M3 — Notifications and settings
- [ ] M4 — idejo ID mode
- [ ] M5 — 1.0, web parity
- [ ] M6 — Moderation
- [ ] M7 — Right sidebar

Current milestone: **M1 — Read a forum** (in progress: core/data, onboarding, shell,
markdown, browse: home, category, Filter by tag, Search, Unread, Rules & Roles, Help, the
welcome and getting-started cards). The app needs idejo with callisto-rpg/Idejo#357 (the
socket), #358 (inline links), #359 (the home page's Expanded view), #360 (Getting started),
#361 (Help's commands) and #363 (tags' categories) (Steward decisions, 2026-11-14).
