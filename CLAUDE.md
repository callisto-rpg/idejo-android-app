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
- UnifiedPush connector and its embedded FCM distributor (no Firebase SDK), in the one build.
- Tests: JUnit, Turbine, MockWebServer, Compose UI tests, Roborazzi; end-to-end against
  idejo's Docker image (`ghcr.io/callisto-rpg/idejo`).

No new dependency without a licence check: Apache-2.0, MIT, BSD or similar, accepted by
F-Droid, and nothing proprietary. No analytics, crash reporters, ads or tracking SDKs
(SY-03).

## Architecture

```
app/                     Application, MainActivity, OAuth callback activity, navigation host,
                         App Links (one build for Play, F-Droid and GitHub: no flavours)
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
- `GET /me` has one copy per forum, `ForumSession.me` (`MeRepository`): the shell, the topic
  page, the composer and Profile and account read its `state`; `update(MePatch)` sends
  `PATCH /me` and publishes the server's answer, so a choice reaches open pages at once.
- Screens: a stateless `@Composable XScreen(state, onEvent)` plus an `XViewModel` exposing
  `StateFlow<XUiState>`; repositories return `Flow` from Room and refresh from the network
  (cache-first, OF-01).
- The composer is a state machine in `feature/composer` (`ComposerState`: text, sent
  range, unsent ranges, open message id, server composer state, pending commit, offline
  draft). Every transition has a unit test named after its CO/GM ID.

### Adding a settings section

Profile and account (ST) is one route, `Place.ProfileAndAccount(section)`, and one list,
`SETTINGS_SECTIONS` in `feature/settings/.../ProfileAndAccount.kt`. **Writing** (ST-02) is
the reference; copy it, never invent a new shape.

1. Copy `feature/settings/.../writing/` to `<name>/` (package `...settings.<name>`) and
   rename `Writing` → `<Name>` in both files: `<Name>Screen.kt` (`<Name>UiState`,
   `<Name>Event`, `<Name>Route`, `<Name>Screen(state, onEvent)`, a `@Preview`) and
   `<Name>ViewModel.kt`. Keep the SPDX header on every file.
2. UiState: `loading`, `loadProblem`, one **nullable** field per server field (null = the
   server did not send it: hide the row, never guess), `saving`, `problem`. Event: one
   class per control, plus `Retry`.
3. ViewModel: read `session.me.state` (`MeRepository`, the forum's one copy of `GET /me`);
   `load()` calls `session.me.refresh()`. A change goes through `change(MePatch(field =
   value)) { optimistic copy }`: shown at once, `PATCH /me` with only that field, back to
   how it was on failure with the `ApiProblem` kept in `problem`. Never compute a rule the
   server owns; branch only on `ApiError.code`. A field `MePatch`/`Me` lacks: add it there
   (nullable), in `FakeIdejo`'s `PATCH me` route and its `Reader`, and in `choicesOf`.
4. Screen: only `parts/SettingsParts.kt` (`SectionHeader`, `SwitchRow`, `RadioGroupRow`,
   `ActionRow`, `LoadingState`, `ErrorState`, `ProblemText`); `ProblemText` already shows
   the server's `message`, or "You are offline: nothing was changed." No literal colours.
5. Register: one `SettingsSection(key, group, title, feature?) { <Name>Route(it) }` in
   `SETTINGS_SECTIONS`, in the web's order (its tabs: Profile, Interface, Messages and
   notifications, Account). `feature` names discovery's feature when the forum may lack it;
   `offered` tests anything else in discovery (Delete account: `account.deletion_url`).
   A section not built on `GET /me` (a list, a link) keeps the same UiState/Event/Screen
   shape: Ignored users (ST-08) and Email notifications (ST-03) read their own endpoints,
   Delete account (ST-06) only discovery. A section of many like switches may use one
   event with an enum (`EmailNotificationsEvent.Set(EmailChoice, on)`).
6. Strings: in `feature/settings/src/main/res/values/strings.xml`, prefixed `<name>_`, in
   the web's words (idejo's member guide and settings page).
7. Tests: `<name>/<Name>ViewModelTest.kt` with `SignedInForum` (loads, patches only the
   field, a refusal reverts with the server's message, offline reverts, a server without
   the field hides the row), each named after its requirement ID; and two `shoot(...)`
   lines (light and dark) in `ProfileAndAccountScreenshotTest`, then
   `./gradlew :feature:settings:recordRoborazziDebug`.
8. Run `./gradlew check` and `reuse lint`. A section that needs an endpoint idejo lacks:
   stop and ask (IX).

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
./gradlew assembleDebug                     # build the app (one build for every store)
./gradlew assembleRelease                   # release build with R8 (unsigned)
./gradlew installDebug                      # install on a connected device or emulator
./gradlew test                              # unit tests: every module
./gradlew :core:testing:test                # the API guide's flows against FakeIdejo
./gradlew verifyRoborazziDebug              # screenshot tests (recordRoborazziDebug to update)
./gradlew ktlintCheck detekt                # style and static analysis (whole repository)
./gradlew ktlintFormat                      # fix what ktlint can fix
./gradlew lint                              # Android Lint: every module
./gradlew licensee                          # licences of every dependency, every variant
reuse lint                                  # licence headers (REUSE)
./gradlew check                             # all of the above but reuse
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
- 2026-11-14 — Long topics (TV-05) show "N earlier / N later messages" as the web: idejo's
  message window counts them, `earlier` and `later` (IX-09, callisto-rpg/Idejo#362).
- 2026-11-14 — Filter by tag (BR-04) groups the tags as the web: those of every category,
  then each category's in the home page's order, a tag of several categories under each.
  idejo lets a tag belong to several categories and `GET /tags` gives `category_ids`
  (IX-10, callisto-rpg/Idejo#363); tag names stay unique across the server.
- 2026-11-14 — A message's topics started from it and messages moved away from below it
  show as the web's thin banners, with **Continue topic** / **Open topic** (steward request
  to idejo, callisto-rpg/Idejo#364; `moved_after`, IX-11). A poll's emoji shows when the
  forum names it (`GET /server`'s `emoji` and `default_reactions`); the full emoji list
  comes with reactions (MA-03).
- 2026-11-14 — Mentions glow (TV-07) as the web's: unread messages from the first unread
  on, by others, @mentioning the member (red) or +mentioning one of their roles (blue),
  each once per page when half of it, or a third of the screen, is shown; the tags naming
  the member pulse. Roles are matched by slug: idejo gives `GET /me`'s roles theirs
  (IX-12, callisto-rpg/Idejo#368).
- 2026-11-14 — Inline links' cards (TV-22) as the web's, from API calls the app already
  has (`GET /roles/{slug}`, a topic's `first_message_id`). In M1 a role's card has no
  **Open** (no role page in the app; its members open their user cards), a category's no
  **New topic** (M2). Another forum (`&forum` links, TV-26's `forum` blocks) shows as idejo
  ID mode will, with **Join** and **Open** disabled until M4.
- 2026-11-14 — The image viewer (TV-25) takes the image from the app's image cache (the
  client without a token). **Share** hands a copy through a FileProvider limited to the
  cache's `shared/` folder; **Save** writes where the member picks in the system's file
  picker, so the app asks for no storage permission.
- 2026-11-14 — Plugin blocks (TV-26): `video` and `iframe` load only on a tap, in a
  WebView with JavaScript on (players need it; steward decision), no JavaScript bridge, no
  file or content access, no new windows, third-party cookies off and https only; whatever
  the member follows from it opens outside the app. A message's `plugins` give `block` as
  the kind of block (a string), as idejo sends it.
- 2026-11-15 — A topic started from a message shows the web's "Continued from {name} in
  “{topic}”" under its header, with the message's first words, from `GET /messages/{id}`
  and `GET /topics/{id}` (no new endpoint); hidden when the member may not view it or a
  moderator deleted it, as on the web.
- 2026-11-15 — The jump button (SH-04) as the web's navigator: First message, First unread
  (when something is unread), Last message, My last message (from the topic's
  `my_last_message_id`, IX-13, callisto-rpg/Idejo#369; hidden on servers without it), then
  the pinned messages (author and 60 characters). It shows once the header has scrolled
  away; at ≥ 1200 dp it is a card beside the topic (SH-60).
- 2026-11-15 — Large screens (SH-60 to SH-62): ≥ 840 dp docks the sidebar; a separating
  fold puts the sidebar and the page on either side of the hinge (book: sidebar left;
  tabletop: page above), from `material3-adaptive`'s window posture. Visible focus is the
  theme's indication: Material's ripple plus a 2 dp ring in the forum's primary colour. A
  right-click does what a long-press does. The composer's keys (CO-03) come with M2.
- 2026-11-15 — **Tutorial bubbles** (SH-21) is one choice for the whole app, kept on the
  device (`AppState.tutorialBubbles`, `AppChoices`); idejo's API has no such setting.
  Theme stays per forum.
- 2026-11-15 — Category icons (TH-05): the steward released idejo's icons under CC-BY-4.0
  (`graphics/category-icons` in idejo, callisto-rpg/Idejo#370). The app bundles the same
  SVGs (`core/designsystem/src/main/assets/category-icons`) and draws them natively: the
  tile's `#6567e7` becomes the category's `mark_colour` (IX-14), the shapes' `#ffffff`
  white or `#111111`. An icon the app lacks (a newer server's) shows the initial. To
  update the icons, copy idejo's folder over.
- 2026-11-15 — The sidebar's followed topics come from `GET /me/followed` (IX-02,
  callisto-rpg/Idejo#371), in the order followed, ten pages of 100 at most; a server
  without it (404) gets the spec's fallback (three pages of each category).
- 2026-11-15 — M1 closes (steward). Its parts that belong to later milestones wait for
  them, under the "later milestone wins" rule: SH-36 (Private Conversations and All my
  topics at the sidebar's foot: M2 and M4), SH-03 (the logo's dot for news in other
  forums: M4), SH-12, SH-13 and SH-15 (the logo menu's forum list, shortcuts and
  long-press: M4).
- 2026-11-15 — The graphical composer (CO-10, CO-11) and the always-shown message controls
  (MA-01) follow the member's settings, which idejo's `GET /me` gives (`graphical_composer`,
  `controls_on_mobile`, `controls_on_desktop`; IX-15, callisto-rpg/Idejo#372). A server
  without them gets the graphical composer on (new accounts' default) and controls on a tap.
- 2026-11-15 — Private conversations (PC-01, PC-03, PC-04) use idejo's API as it is: the
  list and its cards from `GET /conversations` (read again on `user:me`'s
  `conversation.updated`), **Private Message** from `POST /conversations`, and a
  conversation opens on the topic page through the topic paths (`/topics/{id}`, messages,
  changes, composer), which take conversation ids with the `private` scope; its channel is
  `conversation:<id>` (`Place.Topic.private`). It is named after the other participants
  (`GET /conversations/{id}`), has no Follow and no New Topic. The list page lives in
  `feature/inbox`. "Members can send me private messages" sat in App settings until Profile
  and account (M3), where it is now its own section. PC-02 (a chat service) waits for M4
  with IX-04.
- 2026-11-15 — Profile and account (ST) is one typed route with a section key,
  `Place.ProfileAndAccount(section)`, so a new section touches its own folder, one entry in
  `SETTINGS_SECTIONS` and its strings, never `app` or the shell. Its overview groups the
  sections under the web's tabs, listing only those that exist. On windows of 840 dp and
  more (SH-60) the overview stays at the left and the section shows beside it, a plain
  two-pane row (no new dependency). Writing follows the app spec: the open topic follows
  at once (the web reloads).
- 2026-11-18 — Push (NT-02 to NT-06) is UnifiedPush's connector 3.x, which keeps the Web
  Push keys in the Keystore and decrypts the pushes itself; one registration per forum,
  its `instance` the forum's origin, ended when the forum is signed out of. The embedded
  FCM distributor signs with the forum's VAPID key, which idejo now gives in
  `GET /server`'s `push_vapid_key` (IX-16, callisto-rpg/Idejo#373; steward choice over a
  push gateway). Each forum is a group of notification channels, one per kind; while the
  app is on screen the live bubbles stand for the pushes (only the test push shows). A
  tapped notification opens only addresses on its own forum; any other opens its home page.
- 2026-11-18 — Android 13's notification permission (NT-07) is asked from the web's banner,
  in its words today ("Push notifications not enabled…", **Enable** with its tutorial
  bubble, **Dismiss**, which the spec calls **Not now**), on category and topic pages under
  the top bar, on the web's conditions: push on, the welcome card seen, a topic followed
  (writing in one follows it), not dismissed in 14 days. idejo's API has no banner flag, so
  the dismissal is kept on the device (`AppState.pushBarDismissedAt`), for every forum.
  Refused twice, Android no longer asks: **Enable** then opens its notification settings.
- 2026-11-18 — App settings (ST-20 to ST-22) are kept on the device for every forum
  (`AppState`, `AppChoices`). The push service is chosen from UnifiedPush's list of
  distributors (the embedded FCM among them, as "Google Play services"), not the
  connector's picker, which shows only external ones. Text size scales message bodies over
  Android's font size (`MessageTextSize`); haptics off silences Compose's feedback; reduce
  motion adds to Android's animation setting (`IdejoTheme(display = …)`). The cache limit
  (100 MB to 1 GB, 200 MB by default) sizes Coil's disk cache at the next start; **Clear
  cache** empties the cache folder but the composer's camera pictures.
- 2026-11-19 — idejo ID mode (MD-02) signs in to the group's inbox (`my.idejo.org` unless
  "Use another idejo group") as to any forum: its own token, `AppState.inbox`, `AppMode.IdejoId`.
  Each joined forum (`GET /me/communities` on the inbox) is connected with its own token
  through its own sign-in page in a Custom Tab, one at a time (`GroupForums`; the idejo ID
  session makes each one tap). One session is open at a time, the inbox's (`Launch.Inbox`)
  or a forum's; Back on a forum's first page leads to the inbox. Single-forum mode is
  unchanged. Push registers the open forum, and the inbox (below, 2026-11-22).
- 2026-11-19 — In idejo ID mode a forum with a chat service (IX-04, callisto-rpg/Idejo#374:
  discovery's `community.chat_url` and `inbox_url`, user cards' `idejo_id`, the inbox's
  `POST /conversations` `{subject}`) shows **Private Message** as the web does, by the idejo
  ID, and starts the conversation on the inbox, which opens at it in **Chats**; outside idejo
  ID mode the web's link opens in a Custom Tab. The inbox's chats use the topic page as any
  forum's conversations, so the inbox's session is `SessionManager.active` while it is shown
  (its socket connects and push may register it).
- 2026-11-19 — The logo menu in idejo ID mode (SH-12 to SH-15): **Your forums** lists the
  forums held, then the group's joined forums the app does not hold, with **Connect**, which
  opens the inbox's Forums and connects there (features never depend on each other). A
  forum's dot is the strongest reason among its unread topics, read when the menu opens.
  **Find more forums** stands under **Add a forum** only (SH-13 and SH-14 name it twice).
  SH-15's **Mark all read** (Could) waits: idejo's API has no call to mark a forum read.
  Removing another forum than the one open keeps it open. Single-forum mode's **Add a
  forum** keeps its note and offers **Sign in with idejo ID**, which keeps the forum.
- 2026-11-19 — Mode (MD-04): from one forum, **idejo ID** is its sign-in, which keeps the
  forum and its token; leaving idejo ID mode keeps one forum, chosen in the confirmation
  that names the others and the inbox, whose tokens are revoked and records forgotten (no
  per-forum cache exists before M5's Room). The last place (MD-05) is each forum's page,
  a topic at the message on top of the screen, and the inbox's tab, restored once per
  launch for the session restored, never over a tapped notification.
- 2026-11-19 — Linked members (ST-07): idejo's `GET /me` does not say whether the member came
  with their idejo ID, so a forum counts as linked when the inbox's `GET /me/communities`
  lists it. Its Profile and account says the settings apply only there, with **Settings for
  all forums**, the inbox's own Profile and account. There, **Save for all forums** (IX-17,
  callisto-rpg/Idejo#375, idejo 0.3.19) shows under each section while the inbox's `GET /me`
  says `everywhere` (`SaveForAllForums`, through each section screen's `everywhere` slot,
  filled by its route): one button for all of Profile (`profile`, `bio`, `avatar`), since
  the app saves each change at once; never the username, which the web sends only after
  the password.
- 2026-11-20 — The sidebar (SH-30) has no button in the top bar: it opens from **Sidebar**
  in the forum (logo) menu, under **Home** (`LogoMenu`'s `onSidebar`), or by a swipe to the
  right. Docked (SH-60, a fold) the menu has no such entry, as the bar had no button.
- 2026-11-20 — The sidebar follows the finger (SH-30). While it is shut, the back gesture
  (predictive back, from either edge; `enableOnBackInvokedCallback`) pulls it in and opens
  it; the back button still goes back. A drag to the right anywhere on the page (a category,
  a topic) opens it too; a drag to the left anywhere on it closes it. Opening and
  closing are one drag (`SidebarFrame`'s `Sliding`): the sidebar follows the finger and holds
  while it rests; letting go, the way it last moved decides, right open, left shut. A sideways swipe a child scroller leaves over (the home
  page's tabs, swiped right on **Categories**) pulls the sidebar too (`rememberPulling`, a
  nested scroll connection). Reduced motion keeps the drag, without the settling animation.
- 2026-11-20 — The home page (BR-01, BR-03, BR-06) is three tabs as the inbox's, in the
  forum's colours: **Categories** (cards two to a row on a phone, more on wider screens,
  `GridCells.Adaptive(150.dp)`), **Active topics** and **Unread** (`UnreadRoute` without
  its title). The top bar has no **Unread** button any more (SH-01); `Place.Unread` stays
  for links and notifications.
- 2026-11-20 — The sidebar over the page has no title: its head is the forum's logo and
  name, and at the right the idejo button (`ic_idejo`, idejoButton1.svg, as tall as the forum's
  logo beside it, 34 dp, in its own gold), which opens the forum menu; back or a swipe
  left closes it (no close button). **All categories**' links start
  rolled up (SH-35, `ForumRecord.linksRolled = true` for a forum added from now on).
- 2026-11-20 — The top bar (SH-01, SH-10, SH-20): a tap on the forum's logo opens its home
  page, or there the sidebar (nothing when docked); a pull down on the bar opens the forum
  menu, from the member's picture the user menu (`pullDown` in `MenuPanel`, seen before
  anything under the bar). The forum menu's button is the sidebar's idejo button.
- 2026-11-20 — The forum menu's **This forum** (SH-10, SH-11): the forum's name opens the
  home page on **Categories** (any way home from the frame does: `HomeRoute`'s
  `categoriesCalls`); no **Home** entry. One line of buttons: **Followed topics** (the
  sidebar, not when docked), **Invite** (the system's share sheet with the forum's address,
  formerly Share link) and **Support** (greyed: nothing behind it yet); when the labels do
  not fit, the leftmost lose theirs first (`ForumButtons`).
- 2026-11-20 — TRIAL, not yet decided: the sidebar over the page, the forum menu and the
  user menu as frosted glass, their background at 60 % opacity and what is behind blurred
  as they come in (Android 12+).
  All of it is `SidebarGlass.kt`; `SidebarGlass.ON = false` turns it off.
- 2026-11-20 — Private conversations live only on the group's inbox (idejo 0.3.21,
  callisto-rpg/Idejo#381: a forum hosts none, its discovery has no `private_messages`, so
  ForumConversations, the user card's own **Private Message** and the Private messages
  section turn off by themselves). **Private Conversations** left the sidebar's foot
  (SH-36) for the forum (logo) menu: idejo ID mode keeps SH-13's Chats; single-forum mode on
  a forum with a chat service (`community.chat_url`) shows it greyed with **Connect**
  (`UnavailableItem`, `LogoMenu`'s `chatService`), which is **Sign in with idejo ID**.
- 2026-11-22 — The forum menu (SH-10) comes down under the top bar, which stays in view with
  its breadcrumbs (`MenuPanel`), with no close button. It is as tall as its contents,
  at most the rest of the screen (where they scroll), so the page shows, blurred, below a
  short one; at its foot the sidebar's glass line and a pill. It moves as the sidebar does
  (`Sliding`): a pull down on the top bar brings it with the finger, a drag up on it (or on
  its contents once they cannot scroll) takes it back, and letting go, the way the finger
  last moved decides. A tap below it, or back, closes it.
- 2026-11-22 — The user menu (SH-20) behaves and looks as the forum menu (steward request): the
  same `MenuPanel` under the top bar, with its own `Sliding`; a pull down from the member's
  picture brings it with the finger, a tap on the picture opens it, and it has no close button.
  **Accounts** opens in it, with **Back** at its top.
- 2026-11-22 — The Getting started card (BR-11, IX-07) is gone, on the web and in the app:
  only the welcome card (BR-07) shows over the forum. idejo still answers `intro_due` (false)
  and `POST /me/intro` for older apps; the app reads neither.
- 2026-11-22 — The onboarding tour (steward request; IX-18, idejo's `GET /me` `onboarding` and
  `POST /me/onboarded`), as the web's with the app's gestures, over the whole frame: the page
  under its background colour at 50 % but for the step's target, glowing, a card with an
  arrow at it, every other touch stopped (`TourOverlay` in feature/shell). Steps
  (`tourStep`): **Swipe to open the sidebar** (a hand swiping right; the drag opens it),
  **Open the {topic} topic** (its row in the sidebar), **Type “Hi, I’m {name}” then press
  Enter** and, once sent, the question **and type Enter×3** (the composer; a lone Enter shows
  "Hit Enter three times in quick succession" for 2 s), then "You've completed your first
  message!…" at **All categories** with **Close** (2026-11-22, steward); that last card stops
  no touch, and pulling down the forum menu or moving the sidebar ends it, as Close does.
  `OnboardingTour` (core/data, `ForumSession.tour`) holds the state; the composer reports to
  it (features never depend on each other); targets mark themselves with `Modifier.tourTarget`
  (core/designsystem, `LocalTourTargets`). The tour is finished on the server when the message
  closes; the last card stays until it ends.
- 2026-11-22 — The launcher icon is now the steward's `AndroidIcon1.png` (the quote marks and
  the i, gold, on the same `#fdfcf6`): the picture is the icon's visible 72 dp, centred in the
  108 dp foreground with the i carried down through the bleed
  (`drawable-nodpi/ic_launcher_foreground.png`); the monochrome icon is its shape in white.
- 2026-11-22 — One build for Google Play, F-Droid and GitHub (steward request): no `foss` and
  `play` flavours. Every build embeds UnifiedPush's FCM distributor
  (`org.unifiedpush.android:embedded-fcm-distributor`, Apache-2.0, no Firebase SDK; the
  `foss-embedded-fcm-distributor` the steward named is deprecated into it). It uses Google
  Play services or microG where present, signed with the forum's VAPID key (IX-16), so no
  gateway and no Firebase config; the connector lists it only there and prefers an installed
  distributor. App settings → Notifications (NT-06): off, **Enable push notifications**
  (Android 13's permission, then `tryUseCurrentOrDefaultDistributor`); nothing at all, a
  dialog offers Sunup (`market://`, else UnifiedPush's list of distributors) and enabling
  is tried again on returning; **Not now** leaves push off (the app does not poll, NT-09).
  On, it names the distributor, lists the others and has **Turn off**
  (`AppState.pushOff`: every forum's registration deleted, `UnifiedPush.removeDistributor`,
  no forum registers until enabled again).
- 2026-11-22 — Private messages are pushed in idejo ID mode (steward request): the inbox is
  registered for push whatever is open (`PushRegistrar`, from `AppState`), while App
  settings → Notifications' **Private messages** is on, as it is by default
  (`AppState.privatePush`); off, its registration ends. idejo pushes them under its own
  `private` choice (Push notifications, on by default). On screen, the live bubbles stand
  for the open forum's pushes only, so a private message shows in any other forum. Pushes
  and new endpoints work for any forum held, open or not (`IdejoPushService`,
  `PushRegistrar.onEndpoint`, with the forum's own token).
- 2026-11-22 — On a forum's home page the top bar says "pull down for idejo menu" over a v,
  centred across the bar near its foot (`TopBar`'s `PullHint`, `ic_pull_down` from Material
  Symbols Rounded), small labels' size + 30 % at 50 % opacity; where it does not fit between
  the logo and the member's picture (72 dp each side), "idejo menu" only (2026-11-23).
  TalkBack skips it (the forum menu has its button). The tour's
  screenshots are taken with reduced motion, so the glow and the hand are still.
- 2026-11-23 — A pull down takes the home page's pull hint down with the finger, and it fades
  out within 40 dp (`PullHint.pulled`, the menus' `Sliding.shown`).
- 2026-11-23 — The top bar is pinned (SH-01): it no longer hides on scroll.
- 2026-11-23 — The forum menu can be pulled down wherever the member is, the group's inbox too
  (steward request): `InboxMenuFrame` (feature/shell) wraps `InboxApp`'s pages and gives every
  `TopBar` under it the pull (`LocalTopBarMenu`, core/designsystem; `pullDown` in `MenuPanel`),
  and the page under the bar the forum's blur (`glassBehind`) as the menu comes in.
  Over the inbox the menu (`InboxMenu`) has no **This forum**, only **Support** (greyed) on its
  line, then **Your forums**, **All my topics**, **Private Conversations** and **Add a forum**.
  Its shortcuts reach the tabs already shown through `InboxShortcuts.waiting`.
- 2026-11-23 — The sidebar's × (SH-33) is 40 % smaller (14.4 dp) in the same 44 dp target. A tap
  puts "Topic unfollowed. Tap to cancel" (70 % opacity) in place of the title for 3 s; it then
  fades out, the topic is unfollowed and the rows below slide up into its place (`Unfollowing`;
  no slide with reduced motion). A tap on the notice cancels, and nothing is sent; a sidebar
  closed while it shows unfollows at once.
- 2026-11-23 — Messages (TV-02) as the mobile web's: a hairline between them (1 px of the text's
  colour at 9 %, the web's `--hairline`; 18 % in the dark theme), 14 dp of padding above each
  and 48 dp below (was 10 and 10), the height of the message controls (MA-01), which show in
  that space when the message is tapped, so it does not grow.
- 2026-11-23 — Half that space below messages (TV-02): 24 dp. A tapped message's controls (48 dp)
  take it and 24 dp more; the message grows smoothly and the ones below slide down
  (`animateContentSize`; at once with reduced motion).
- 2026-11-23 — The `iframe` plugin's page (TV-26) shows at once, as on the web (steward request):
  no card or **Show page**, the frame as tall as its code asked (the web's 416 by default, 40 to
  800), a hairline round it, **Open {host}** under it (`FrameBlock`'s `PageFrame`). Videos keep
  their tap-to-load card. The WebView now has DOM storage on (embedded pages need it), a
  transparent background, and passes drags it cannot scroll on to the topic (nested scroll).
- 2026-11-23 — The frame's WebView fills its place (`MATCH_PARENT` layout params). Left to wrap
  its content, as an `AndroidView` is by default, it took the page's height for its viewport,
  so pages laid out against the frame (itch.io's widget) collapsed to nothing over their dark
  body: the black box the steward saw. Checked on a Pixel 6a.

## Milestones (spec §26)

- [x] M0 — Foundations
- [x] M1 — Read a forum
- [x] M2 — Write
- [x] M3 — Notifications and settings
- [x] M4 — idejo ID mode
- [ ] **M5 — 1.0, web parity**
- [ ] M6 — Moderation
- [ ] M7 — Right sidebar

Current milestone: **M5 — 1.0, web parity**: offline (OF), links (DL), the accessibility
audit (AX), the app's NF targets and the store listings; release on Play, F-Droid and GitHub.
Done so far: drafts (OF-03, NF-07: `Drafts` in core/data, one file per forum and place,
written on its own thread at each change and renamed into place; the composer keeps the
text after what the server holds and puts it back after the open message without sending
`typing`, the new-topic form its title and body; a forum the app lets go of,
`SessionManager.forgotten`, loses its drafts); the saved pages (OF-01: `PageCache`, Room in
core/data, one row per forum and request read through `ForumSession.cached`: server, me,
categories, the home page's topics, the sidebar's lists, Unread, the last 20 topics with
their windows, user cards; least recently shown first out within App settings' storage
limit; forgotten with the forum, emptied by **Clear cache**) and the offline bar (OF-02:
`OfflineBar` over the page while `ForumSession.savedCopy` says a saved copy is shown, the
forum not answering or answering 5xx); nothing else queued offline (OF-05: `ForumSession.offline`, set by any call the forum
does not answer and cleared by any it does, disables the topic's and inline cards' **Follow**,
reactions, **Vote**, and the sidebar's follows, Unfollow & mute and Mark unread; Android's
network, `NetworkWatch`, makes the open forum offline when lost and asks it again when back); OF-04:
an Enter or commit the forum does not answer waits as **Will send when you're back online**
(`ComposerState.waiting`), and when `offline` clears the composer is read again and the text goes
once: into the open message if still open, else its unsent part as a new message); DL-02: `linkTarget` reads `/`, `/c/{slug}`,
`/c/{slug}/t/{id}-{slug}`, `/t/{id}`, `/m/{id}`, `/u/{id}`, `/p…`, `/unread`, and `/chat…` on the
inbox, anything else `LinkTarget.External`; DL-03: `rememberLinkOpener` opens a link on the forum
here, on another forum held or the inbox there (`AppState.originOf`, a `PendingLink`), the rest in
a Custom Tab with the forum's primary colour); DL-01: App Links hosts are build-time only, `my.idejo.org`,
`*.idejo.org` and the `idejo.appLinkHosts` Gradle property, written into a generated manifest by
`AppLinksManifest` in `app/build.gradle.kts`; MainActivity opens a link with `openAt`, else in a
Custom Tab); DL-04: text shared to the app (`text/plain`) waits in `SharedImages.text`
as pictures do, the share card shows the forums held as chips when there are several (picking one
enters it and the card opens again there), and the topic's composer puts the text after its own).
M4 closed on 2026-11-19 (steward): the welcome screen's **Sign in with idejo ID**
and **Connect your forums** (SI-06: `IdejoIdRoute` in feature/onboarding, `GroupForums` in
core/data, `ConnectForumsRoute` in feature/inbox, which stands for the inbox until IB);
the joined forums read again at launch and hourly (SI-07: `GroupForums.start`, with
`AppState.seenForums`), a Connect card for one joined since and the logo's dot;
**Find more forums** (SI-08: the
group's directory at discovery's `community.directory_url`, `DirectoryClient` in core/api,
`FindForumsRoute`), whose **Join** is the forum's own app sign-in, not the web's
`/users/oidc`, and once joined opens the forum in the app (`GroupForums.join` answers its origin); the inbox (IB-01 to IB-03: `InboxRoute` with **Forums**, **Topics** and
**Chats** tabs; `InboxTopics` in core/data reads every connected forum with its own token,
`SessionManager.signedIn`, as IB-04 says; a topic tapped opens in its forum through
`PendingLinks`); live (IB-05: `InboxSockets`, one socket per connected forum while the
inbox is on screen); **Chats** (IB-06: the inbox's own conversations, `InboxApp` in app, the
inbox's back stack, its session `SessionManager.active` while shown); **Private Message**
through the inbox (IB-07: `InboxChats`, with idejo 0.3.18's IX-04); Back from a topic
opened from Topics returns to the inbox (IB-08: `PendingLink.fromInbox`); single-forum
mode's user card offers idejo ID mode under a chat service's **Private Message** (PC-02:
`Place.IdejoId`); the logo menu's **Your forums** with their dots (SH-12: `YourForums.kt`,
`InboxTopicsState.levels`), **All my topics** and **Private Conversations** (SH-13),
**Add a forum** by address or **Find more forums** (SH-14: `Place.AddForum`), and a
forum's long-press with **Remove from app** and **Open in browser** (SH-15); the inbox
opens where the menu asked through `InboxShortcuts`; App settings' **Mode** (MD-04:
`SessionManager.leaveIdejoId`); the last place opened restored at launch (MD-05:
`LastPlaces`, `routeOf`/`placeOf` in feature/shell); linked members' note in a forum's
Profile and account with **Settings for all forums**, the inbox's own Profile and account
(ST-07: `InboxPageRoute`) and its **Save for all forums** (IX-17: `SaveForAllForums`); the
logo's dot for news in the other forums (SH-03: `logoNews`, read again every five minutes
while a forum is open in idejo ID mode). M4's rest waits for idejo's API: SH-15's **Mark all
read** (Could).
M3 closed on 2026-11-19 (steward): Profile and account with Profile
(ST-01: name, bio with Preview, pictures cropped to square on the device), Writing (ST-02), Message controls and Email notifications (ST-03), Private messages, Ignored users
(ST-08), Push notifications (NT-08: this device is "on" once NT-02 registers it), Apps and
devices (ST-05), Change password (ST-04) and Delete account (ST-06); App settings' About
with the source code and the privacy note (ST-23); the live bubbles (NT-01: `LiveBubbles`
reads user:me's `notification`, the shell's `LiveBubbleStack` shows them); push (NT-02 to
NT-06, NT-09: `PushRegistrar` in core/data, the connector's `IdejoPushService` and
`PushNotifier` in feature/notifications, a tapped notification through `PendingLinks` and
`linkTarget`); the notifications bar (NT-07: the shell's `PushBar`); App settings' push
service, display and storage (ST-20 to ST-22, `AppSettingsViewModel`). M3's rest waits for
later milestones (ST-20's "open links in the app" came with M5's App Links: App settings →
**Links**, `LinkHandling.kt`, reads Android's per-host link state and opens its Open by default
page, since Android keeps the choice). M2
closed on 2026-11-15 (steward): the composer (CO), message actions (MA), private
conversations (PC-01, PC-03, PC-04). M1 closed on 2026-11-15: single-forum mode, sign-in, the shell,
browsing, the topic page, large screens.
The app needs idejo with callisto-rpg/Idejo#357 (the socket), #358 (inline links), #359
(the home page's Expanded view), #360 (Getting started), #361 (Help's commands), #362 (the
window's counts), #363 (tags' categories), #364 (banners), #368 (roles' slugs), #369 (the
reader's last message), #370 (category icons, `mark_colour`), #371 (followed topics),
#372 (the composer and controls choices; the app falls back without it), #373 (the push
VAPID key; without it the embedded FCM distributor cannot register), #374 (the chat service,
IX-04; without it Private Message stays the forum's own) and #375 (Save for all forums,
IX-17, idejo 0.3.19; without it the button does not show) (Steward decisions,
2026-11-14 to 2026-11-18).
