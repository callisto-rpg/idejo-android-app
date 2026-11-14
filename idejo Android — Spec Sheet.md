# idejo Android — Spec Sheet

Nov 13, 2026 · draft v0.1 for steward review · repository `org.idejo.app`

Key-screen mockups: the Design canvas "idejo Android — key screens"
(https://claude.ai/artifact/QMjJZHvPpYp3QMhyZP14sr).

This is the source of truth for the official idejo Android app. It builds on idejo's
*Spec Sheet v1*, `docs/member-guide.md`, `docs/api-guide.md` and `docs/api-plan.md`, and on
the *Idejo Hub — Spec Sheet* (members' inbox, directory, idejo ID). Requirement IDs here are
stable and name the tests that cover them. Where an ID could be confused with one of idejo's
or the Hub's, write "app GM-…" / "idejo GM-…" / "Hub ID-…".

Priority: **Must** = needed for the milestone it is in · **Should** = planned, may slip ·
**Could** = later.

## 1. Overview

The app is a native Android client for idejo forums, built only on idejo's public API v1
(idejo AP-07 to AP-20). It works in one of two modes:

- **idejo ID mode** — the member signs in once with their idejo ID. The app opens on the
  same three tabs as the members' inbox (my.idejo.org): **Forums**, **Topics**, **Chats**.
  Every forum they have joined is one tap away, each in its own colours.
- **Single-forum mode** — the member types a forum's address, and the app is that forum's
  app: it opens on the forum's home page and shows nothing else.

Inside a forum the app is idejo: the same top bar (logo, breadcrumbs, user menu), the same
sidebar of categories and followed topics, the same topic page with its live growing
messages, and the same composer. It looks and behaves like an Android app: Material 3
motion and components, system back, edge-to-edge, Android notifications, share sheet.

**Who it is for:** members of idejo forums on Android phones, tablets, foldables and
Chromebooks. Moderators get their tools in a later milestone (section 20). Administrators
use the web (the app links there).

## 2. Decisions

| Question | Decision |
| --- | --- |
| Platform level | Kotlin, minSdk **26** (Android 8.0), target and compile the latest stable SDK |
| UI toolkit | Jetpack Compose with Material 3 |
| Look | Material 3 behaviour (ripples, sheets, predictive back, edge-to-edge) with idejo's identity: Fraunces and Rubik, idejo's cards, dots, bubbles, glows and the forum's colours. No Material You dynamic colour |
| Modes | Chosen at first launch: **Sign in with idejo ID** or **Enter a forum address**. Switchable later in App settings |
| Home in idejo ID mode | The inbox tabs (Forums, Topics, Chats), themed with the inbox's colours; entering a forum switches to its own theme |
| "Topics" across forums | Put together in the app from each joined forum's API (no inbox API) |
| Sign-in to joined forums | All at once at setup ("Connect N forums"); forums joined later prompt when they appear |
| Theme colours | Read from `GET /server` (needs idejo change IX-01); idejo's default gold until signed in |
| Logo menu (full screen) | Joined forums with unread dots (switch), the inbox's shortcuts (All my topics, Private Conversations, Find more forums) and **Add a forum** |
| User menu (full screen) | The web's items plus native settings screens, app settings, an account switcher and, for admins, an Admin link (opens the web) |
| Complete message on touch | Enter three times (as the web), the graphical composer's send menu, and Ctrl+Enter on hardware keyboards |
| Editor | Follows the member's "Use the graphical composer" setting: on, WYSIWYG like the web's; off, plain text with inline styling and the Markdown signs kept |
| Large screens | Adaptive: left sidebar permanent from the *expanded* width class (≥ 840 dp); right sidebar too when there is room |
| Distribution | Google Play, F-Droid and signed APKs on GitHub releases |
| Push | UnifiedPush everywhere; the Play build embeds the FCM-based UnifiedPush distributor so push works without a distributor app |
| Moderation | After member parity (milestone M6); administration stays on the web |
| Offline | Room cache for reading recently opened topics, the sidebar and Unread, plus offline drafts sent on reconnect |
| Links | Verified Android App Links for forums added in the app (needs idejo change IX-03) |
| Embeds | Native cards for polls, buttons, cards and forum cards; YouTube and iframes behind tap-to-load; GIFs straight from the provider, as the web does |
| Languages | The same as idejo's web interface (English today); every string in resources |
| Right sidebar | Built after web parity (milestone M7): activity feed, recent places, presence |
| Licence | **Apache-2.0**, with idejo's other conventions (REUSE/SPDX, CLA, requirement IDs, one requirement per PR, AI trailer) |

## 3. Scope

**In version 1.0 (web parity for members)**

- Both modes; adding, switching and removing forums; idejo ID and per-forum sign-in
- The forum shell: top bar, breadcrumbs, logo menu, user menu, left sidebar
- Home, categories, topic lists (compact and expanded), tags filter, search, Unread
- Topic page with live growing messages, catch-up, unread line, jump card, pinned messages
- The composer with every key, the graphical composer, commands and plugins
- Replies, forwards, reactions, mentions, polls, scheduled messages, spoilers, images, GIFs
- New topic, fork to new topic, follow, mute, mark unread, rename own topic
- Private conversations (the forum's own, or the inbox's Chats)
- Notifications in the app and through UnifiedPush; notification settings
- Profile and account settings as native screens; Rules & Roles; Help; warnings
- Offline reading and drafts; App Links; tablets and foldables; accessibility

**After 1.0**

- Moderation (M6), right sidebar (M7)

**Out of scope**

- The admin panel (opens in a Custom Tab), account deletion (the web's `account.deletion_url`)
- Anything the API does not offer: the app never scrapes HTML or calls non-API routes,
  except `/.well-known/idejo`, `/app/authorize` and the forum's `/logo`
- iOS; a Hub operator app

## 4. Modes

| ID | Requirement | Priority |
| --- | --- | --- |
| MD-01 | First launch shows a welcome screen in idejo's default theme with two choices: **Sign in with idejo ID** and **Enter a forum address**, and a short line on each | Must |
| MD-02 | idejo ID mode keeps one inbox (default `https://my.idejo.org/`, editable under "Use another idejo group") and any number of forums from that group, plus forums added by address | Must |
| MD-03 | Single-forum mode keeps exactly one forum. The logo menu shows no forum list and no inbox shortcuts; **Add a forum** there offers to switch to idejo ID mode | Must |
| MD-04 | App settings → **Mode** switches between modes. Leaving a mode keeps the tokens of forums still used, revokes (`POST /oauth/revoke`) the others and clears their cache, after a confirmation naming them | Must |
| MD-05 | The last place opened (inbox tab, forum, topic and scroll position) is restored on the next launch | Should |

## 5. Adding forums and signing in

Sign-in follows idejo's API guide sections 1 to 3 exactly; the app never sees a password.

| ID | Requirement | Priority |
| --- | --- | --- |
| SI-01 | **Enter a forum address**: one field (scheme optional, https assumed and required), then `GET /.well-known/idejo`. While it loads, the forum's name and logo appear as soon as known | Must |
| SI-02 | Discovery answers: no `api` → "This is not an idejo forum, or its version is too old for the app"; `api: null` → "The administrators of {name} have turned off the app"; `api.versions` without `v1` → "This forum needs a newer version of the app" | Must |
| SI-03 | Sign-in opens `oauth.authorize` in a Custom Tab with PKCE S256, a random `state`, every scope and `device_name` (the device's model name). The redirect `org.idejo.app:/oauth/callback` is caught by one activity; `state` is checked; `error=access_denied` returns quietly to the previous screen | Must |
| SI-04 | The code is exchanged at `oauth.token` within its 60 seconds; the token (`idj_…`) is encrypted with an Android Keystore AES-GCM key before it is stored, never logged, never in a URL, and sent only as `Authorization: Bearer` to the forum that issued it | Must |
| SI-05 | Visitors: a forum can also be added without signing in, to read public categories (API calls without a token); **Sign in** stays in the top bar | Should |
| SI-06 | idejo ID mode setup: sign in to the inbox as in SI-03, then `GET /me/communities`; a **Connect your forums** screen lists them (logo, name) and **Connect N forums** runs one Custom Tab per forum in a row — each is one tap thanks to the idejo ID session. A forum skipped or failed stays listed as "Not connected" with **Connect** | Must |
| SI-07 | Joined forums are re-read from `GET /me/communities` on every launch and once an hour; a forum newly joined (on the web or through the directory) shows a "Connect" card in Forums and a dot on the logo | Must |
| SI-08 | **Find more forums**: the group's directory from discovery's `community.directory_url` (`…api/v1/forums?q=&page=`), as cards with logo, name, address and member count, searchable; **Join** opens that forum's sign-in in a Custom Tab (joining is the forum's own flow) and adds it | Must |
| SI-09 | `401 invalid_token` or the socket's `token.revoked`: the forum shows "You were signed out of {name}" with **Sign in again**; its cache stays readable | Must |
| SI-10 | `403 banned` / `403 suspended` show the server's message (and `details.until`); writing is disabled | Must |
| SI-11 | Sign out of one forum (revoke, clear its cache, drafts and push registration) or of all, from the account switcher | Must |
| SI-12 | A 21st device revokes the oldest token on the server (idejo AP-09); the app treats it as SI-09 | Must |

## 6. The forum shell

Everything inside a forum shares one frame, close to the web's mobile layout.

```
┌──────────────────────────────────────────┐
│ [logo]  ›  [▣ Garden]  ›  Tomato plans  (A)│  top bar: logo · breadcrumbs · avatar
├──────────────────────────────────────────┤
│                                          │
│           page (home, category,          │
│           topic, search, unread…)        │
│                                          │
└──────────────────────────────────────────┘
 swipe → : left sidebar (full screen)     swipe ← : right sidebar (M7)
```

### Top bar

| ID | Requirement | Priority |
| --- | --- | --- |
| SH-01 | A Material 3 top app bar in the forum's colours: the forum's logo at the left, the breadcrumbs, then **Unread** (with its count and colour, as the web's) and the member's avatar at the right. It collapses on scroll down and returns on scroll up | Must |
| SH-02 | Breadcrumbs as the web's on a phone: the forum's logo (home), the category's icon tile, the topic's title (one line, ellipsised). In a private conversation: **Private Conversations** › the other person's name. Each crumb is a link; long-pressing the title shows it whole | Must |
| SH-03 | A dot on the logo as the web's community dot (idejo 0.3.10): white for new messages in followed topics of the member's other forums, red when one mentions them; in the app it is computed from the forums the app holds (IB-04) | Should |
| SH-04 | The topic page adds the web's round **jump** button at the top right (First message, First unread, Last message, My last message, pinned messages) | Must |

### Logo menu (full screen)

| ID | Requirement | Priority |
| --- | --- | --- |
| SH-10 | Tapping the logo opens a full-screen page (slides down from the top bar; system back or swipe up closes it) | Must |
| SH-11 | **This forum** at the top: logo, name, address, **Home**, **Share link** | Must |
| SH-12 | idejo ID mode: **Your forums**, every forum held in the app with its logo, name and unread dot (grey/blue/orange/red, as the sidebar's); tapping one switches to it. Not connected ones show **Connect** | Must |
| SH-13 | idejo ID mode: the inbox's shortcuts — **All my topics** (inbox Topics tab), **Private Conversations** (Chats, with its count), **Find more forums** (SI-08) | Must |
| SH-14 | **Add a forum**: by address (SI-01) or from the directory (SI-08); in single-forum mode it explains MD-03 | Must |
| SH-15 | Long-press a forum: **Mark all read** (Could), **Remove from app**, **Open in browser** | Should |

### User menu (full screen)

| ID | Requirement | Priority |
| --- | --- | --- |
| SH-20 | Tapping the avatar opens a full-screen page: the member's picture, display name, user tag and roles in the forum's colours | Must |
| SH-21 | The web's items: **Profile and account** (native screens, section 15), **Theme** (follow system, light, dark), **Tutorial bubbles** (All, Simple, Off), **Sign out** | Must |
| SH-22 | **App settings** (section 15.2) | Must |
| SH-23 | **Accounts**: every forum and the inbox with the account signed in there; sign out of one or of all (SI-11) | Must |
| SH-24 | **Admin** for owners and administrators: opens the forum's admin panel in a Custom Tab | Should |
| SH-25 | Visitors (SI-05) see **Sign in** and **Sign up** (discovery's `account.sign_up_url`) instead | Should |

### Left sidebar

The web's sidebar, filling the whole screen when pulled.

| ID | Requirement | Priority |
| --- | --- | --- |
| SH-30 | Opened by a swipe to the right on the page (not from the screen edge, which belongs to system back), or by the sidebar button at the left of the breadcrumbs; it covers the full screen and closes with a swipe to the left, back, or choosing a place. On expanded widths it stays open beside the page (SH-60) | Must |
| SH-31 | The same order and groups as the home page: **All categories**, each category with its icon tile and, under it, the topics the member follows there in the order joined; past four, **N more topics** | Must |
| SH-32 | Dots before topics: grey new messages, blue a `+role` mentioned, orange a reply, red a mention; a topic's name glows briefly when a message arrives (live, from the category and user channels) | Must |
| SH-33 | The fold arrow beside a category keeps only its topics with news (and the one open); remembered per forum | Must |
| SH-34 | **×** unfollows; the topic open is highlighted and listed with **+** to follow when not followed | Must |
| SH-35 | Above the categories: **Filter by tag**, **Search**, **Rules & Roles**, **Help**, with the web's roll-up (^/V) into icons, remembered | Must |
| SH-36 | At the bottom: **Private Conversations** with its count (the forum's own, or the inbox's Chats when the forum uses a chat service: IX-04) and, on linked forums, **All my topics** | Must |
| SH-38 | Sized for the thumb: every row at least 54 dp tall (categories 56 dp), text 17–18 sp, category tiles 34 dp, and the fold arrow, × and + each a 44 dp target of their own, so a tap on them never opens the topic | Must |
| SH-37 | Long-press a topic: **Follow/Unfollow**, **Unfollow & mute**, **Mark unread**, **Copy link**, **Share** | Must |

### Right sidebar (M7)

Specified in section 21; until then the right swipe does nothing.

### Large screens

| ID | Requirement | Priority |
| --- | --- | --- |
| SH-60 | Window size classes decide the layout: compact and medium as a phone; expanded (≥ 840 dp) keeps the left sidebar open beside the page, as the web on a computer; ≥ 1200 dp also the right sidebar (M7) and the jump card beside the topic instead of the round button | Must |
| SH-61 | Foldables: the hinge is never under the composer or a message; book posture puts the sidebar on one side | Should |
| SH-62 | Chromebooks and hardware keyboards: every key of the web (CO-03), Tab focus order, visible focus, a mouse right-click opens the same menus as long-press | Must |

## 7. Theme

| ID | Requirement | Priority |
| --- | --- | --- |
| TH-01 | Each forum's colours come from `GET /server` → `theme` (IX-01): primary, secondary, tertiary, light background, dark background (`#rrggbb` or `#rrggbbaa`) | Must |
| TH-02 | They map to a Material 3 colour scheme as idejo maps them to daisyUI: primary, secondary and tertiary with on-colours white or `#111111` by contrast; background the page; surface a little lighter (cards); surface-container a little darker; on-background the other background. Components use scheme roles only, never fixed colours (the web's rule) | Must |
| TH-03 | Light and dark follow the member's **Theme** (system, light, dark), saved per account | Must |
| TH-04 | Fraunces for titles and Rubik for text, bundled (OFL-1.1, credited in NOTICE); respects the system font scale | Must |
| TH-05 | Category colours and icons (`colour`, `icon` from the category resource), role colours for names, poll bar colours and link hues per kind as the web | Must |
| TH-06 | The last theme of every forum is cached, so a forum opens in its colours at once; a change on the server applies on the next `/server` read (each launch and on `settings.changed`) | Must |
| TH-07 | Switching forum cross-fades the colours (300 ms; none with reduced motion) | Should |
| TH-08 | Before sign-in, and for forums whose idejo is older than IX-01, idejo's defaults: `#daa304ff`, `#f2c94c`, `#b8860b`, `#fdfcf6`, `#000000` | Must |
| TH-09 | System bars edge-to-edge, tinted from the scheme; the launcher icon stays idejo's (adaptive and monochrome icon) | Must |
| TH-10 | Dark-theme pictures: `avatar_dark_url` in the dark theme, as the web | Must |

## 8. Browsing

| ID | Requirement | Priority |
| --- | --- | --- |
| BR-01 | Home: categories as cards in their groups (number of topics, last activity), then **Active topics** (`GET /topics/active`) | Must |
| BR-02 | Category page: pinned topics first, then topics with title, starter, tags, message count, participants, last activity, unread marker (`GET /categories/{id}/topics`); **New topic** button (FAB) where allowed | Must |
| BR-03 | **View**: Compact or Expanded, as the web (excerpt, pictures, three latest messages, writers), remembered per forum | Must |
| BR-04 | **Filter by tag** with And/Or (`GET /topics?tags=`); tapping a tag anywhere opens it | Must |
| BR-05 | **Search** (`GET /search`) with highlighted matches, category filter, paged | Must |
| BR-06 | **Unread** (`GET /unread`): Following first, then other topics; live through `unread.changed` | Must |
| BR-07 | **Rules & Roles** (`about_md`, `rules_md`, self-select roles with `PUT/DELETE /me/roles/{id}`), shown once on joining as the web | Must |
| BR-08 | **Help**: the keys, the signs and every `!` command of this forum (`GET /topics/{id}/commands`, plugins from `/server`), and **Formatting text** | Must |
| BR-09 | User card (tap a name): picture, name, tag, roles, bio's first 200 characters, **Private Message**, **Ignore** | Must |
| BR-10 | Pull to refresh on every list; cursor paging with endless scroll | Must |
| BR-11 | The **Getting started** card after first sign-in, with **Close** and **Close and hide this in the future** (`POST /me/welcome`) | Should |

## 9. Topic page

| ID | Requirement | Priority |
| --- | --- | --- |
| TV-01 | The topic's first message always on top, then the window from `GET /topics/{id}/messages` (landing on the first unread, `first_unread=true`, under a red **N unread messages** line that fades) | Must |
| TV-02 | Each message: avatar, display name in its role colour, user tag, role, time (its permalink: long-press copies it), the rendered body (section 9.1), images, poll, plugin blocks, reactions, and "edited"/"message edited by a moderator"/"view earlier version" as the web | Must |
| TV-03 | Live: join `topic:{id}`; `message.opened/appended/edited/closed/deleted`, reactions, polls, topic changes and moves apply in place; new text glows briefly, as the web's | Must |
| TV-04 | Catch-up: keep the last `cursor`; after any reconnect or return from background, `GET /topics/{id}/changes?since=` until `more` is false; `410 cursor_expired` or `messages.moved` reloads the window | Must |
| TV-05 | Long topics load both ways as the member scrolls, with **N earlier / later messages** links; **N new messages** chip while reading higher up; **Jump to the end to write** | Must |
| TV-06 | Read position: `PUT /topics/{id}/read` as messages are seen (debounced), never for messages not on screen | Must |
| TV-07 | Unread messages that mention the member (red) or a role of theirs (blue) glow as they are reached; the mention pulses (none with reduced motion) | Should |
| TV-08 | Messages from ignored users are collapsed (`ignored_by_me`); deleted messages show "Message deleted by user/moderator" in place | Must |
| TV-09 | Bodies longer than a screen show collapsed with **Show all** | Must |
| TV-10 | Topic header: title (pencil to rename one's own: `PATCH /topics/{id}`), tags, **Follow/Unfollow**, state (locked, archived, solved), "continues from…" | Must |
| TV-11 | Warnings (`GET /me/warnings`) show as the web's card until **I understand** | Must |
| TV-12 | While the app shows a topic, its channel stays joined; the app gets no push for it (server rule) | Must |

### 9.1 Rendering messages

| ID | Requirement | Priority |
| --- | --- | --- |
| TV-20 | `body_md` is rendered natively from idejo's dialect: GitHub Markdown (tables, strikethrough, task lists, autolinks) plus `==highlight==`, `x^2^`, `-# small`, `\|\|spoiler\|\|`, `:emoji:`, `@user`, `+role`, `#topic`, `/category`, `$tag`, `&forum`. Raw HTML is never rendered | Must |
| TV-21 | Links, mentions and images come from the structured objects (`mentions`, `images`, `replies_to`, `forwarded_from`, `poll`, `plugins`, `reactions`, `spoiler`), never from parsing the extensions' meaning in the app | Must |
| TV-22 | Inline links have the web's hue per kind; tapping opens the web's small card (preview, **Open**, **Follow** / **New topic**) | Must |
| TV-23 | Spoilers: the whole-message blur with **Reveal spoiler** and overlay text; inline bars revealed on tap | Must |
| TV-24 | Code blocks in a monospace font, horizontally scrollable, no highlighting (as the web) | Must |
| TV-25 | Images: lazy, sized from `width`/`height`, tap for a full-screen zoomable viewer with **Share** and **Save**; alt text read by TalkBack | Must |
| TV-26 | Plugin blocks: `button` and `card` native; `forum` as a native forum card with **Join**/**Open** (SI-08); `youtube` and `iframe` as a placeholder card that loads a sandboxed WebView only on tap (no JavaScript bridge, no file access, third-party cookies off) | Must |
| TV-27 | Polls: the web's side-by-side bars (or the list below when they do not fit), emoji, colours, **Change vote**, hidden results, closing time | Must |

## 10. The composer

The composer is the author's open message, as on the web. The server owns every rule:
the app sends keystrokes and commits, and draws the countdown from the composer state it
is given (idejo API guide, section 6). The app never works the rules out itself.

```
  (A) Ada Lovelace · sent, appends unsent         ← status bubble
  ┌────────────────────────────────────────────┐
  │ Tomatoes need staking early. Which twine │ ← sent text (high contrast) + unsent (highlighted)
  │ do you use|                                │
  └────────────────────────────────────────────┘
  ━━━━━━━━━━━━━━━━━━━━━━━━━━━──────── slow mode   ← orange line from may_start_new_at
  ☺  ▣  GIF  ✎  +                            ➤      ← graphical composer (setting)
```

| ID | Requirement | Priority |
| --- | --- | --- |
| CO-01 | The composer sits in the topic at the place of the author's open message (idejo GM-08): others' replies appear below it and push it up; with no open message it sits at the end. While focused, the list keeps it above the keyboard (IME insets) | Must |
| CO-02 | First keystroke of a new message: `POST /topics/{id}/composer/typing` (or `typing` on the channel). Enter: `POST …/composer/commit` with the **whole** open message as shown and `break: false`; the answer's message replaces the local one and the state updates | Must |
| CO-03 | Keys: the soft keyboard's return = **Enter** (the newline is intercepted, never inserted); **Enter three times** quickly with no typing between = complete (`break: true`); on hardware keyboards **Shift+Enter** = line break, **Ctrl+Enter** (and Cmd+Enter on Chromebooks' Search+Enter) = complete, also outside the box on the topic page | Must |
| CO-04 | Sent text shows in higher contrast than unsent text; text just sent glows then settles; editing inside sent text marks the changed part unsent until the next Enter (idejo GM-10) | Must |
| CO-05 | The status bubble beside the picture: **unsent**, **sent**, **sent, appends unsent**, then **sent & committed** for 3 s; the picture and name faded until something is sent | Must |
| CO-06 | Tutorial bubbles as the web (All, Simple, Off): **Enter to send**, **Sent!** with "Message now visible to all!", "Continue typing to add to your message"; the timer line's texts | Must |
| CO-07 | Slow mode: from `may_start_new_at`, `held_until`, `typing_since`, `window_seconds`, `unanswered_seconds` and the server's `now` (clock offset kept), the thin orange line fills under the box with the **slow mode** bubble and the web's rotating hints; at its end it flashes and the bubble says **Ctrl+Enter to start new message** (on touch: "Enter 3× to start new message") | Must |
| CO-08 | Errors show the server's own text (`message`) under the box: `too_soon`, `opening`, `closed`, `cooldown`, `rate_limited` (with `Retry-After`); nothing typed is ever lost | Must |
| CO-09 | Automatic punctuation is the server's (from the member's setting); the app shows what the commit answer returns | Must |
| CO-10 | The graphical composer (when the member's setting is on): emoji, image, GIF (when on), pencil (New line, Bold, Italic, Strikethrough, Insert link, Code, Quote, Bulleted list, Numbered list), **+** (the commands), and the paper plane; long-press on it opens **Send** / **Send & new line** / **Send & start new message** (while editing: **Save** / **Save & new line** / **Save and end the edit**; within the wait: **Send & finish (the next message waits for the line)**) | Must |
| CO-11 | WYSIWYG with the graphical composer: Markdown signs disappear once finished, titles, lists and quotes as they will look, pictures as pictures, list continuation and Backspace rules as the web. Without it: plain text with bold, italics, strikethrough and spoilers styled, signs kept | Must |
| CO-12 | Suggestions after `@ + # / $ &` (`GET /suggest`), narrowing as typed; tap or hardware arrows + Enter/Tab; Escape/back closes | Must |
| CO-13 | `!` commands list (`GET /topics/{id}/commands`): `!image`, `!gif`, `!new-topic`, `!poll`, `!schedule`, `!spoiler` (and `!spoiler:text:`), `!forward`, `!collapse` and the active plugins, each with its native dialog as described in the member guide | Must |
| CO-14 | Images: from the photo picker, camera, share sheet, paste or drag-and-drop; JPEG, PNG, GIF, WebP within `upload_max_bytes`; uploaded with `POST /uploads`, then inserted as the web's "image:" pill; alt-text prompt | Must |
| CO-15 | GIFs: the server's provider configuration (`GET /gifs`); the app calls the provider itself as the web does and names it under the results | Must |
| CO-16 | Reply: **Replying to …** above the box (**Cancel reply** until the first Enter) and the web's three cases (reply to a message above, below, or while open; `new: true`, `reply_to`); **Append to my message** | Must |
| CO-17 | Forward: the target card (`GET /forward-targets`, search), then the forwarded box above the composer with **Cancel forward** | Must |
| CO-18 | Edit a closed message of one's own: **Edit** opens it in the composer, Enter saves (`PUT /messages/{id}`), complete = `finish: true`; emptying it asks "Are you sure you want to delete your own message?" | Must |
| CO-19 | New topic: title, category, tags (required where the category says), icon, **topical**, first message in the same editor with **Preview**, never live; pending approval shown as such | Must |
| CO-20 | Scheduled messages under the box with **Cancel** (`GET/DELETE …/scheduled`) | Must |
| CO-21 | The topic channel stays joined while writing; when the app goes to the background the open message closes after 30 s, as a closed tab on the web (idejo GM-12) — the app does not try to keep it open | Must |
| CO-22 | Haptic tick on send and on completing (off in App settings) | Could |

## 11. Around a message

| ID | Requirement | Priority |
| --- | --- | --- |
| MA-01 | Tap a message: its actions in a bubble under it (React, Reply, Forward, New Topic, Edit, Report, Copy text, Copy link, Share); tap elsewhere or scroll closes it. **Show message controls on mobile** shows them under every message | Must |
| MA-02 | When the actions do not fit, Reply, Forward and New Topic go under **Quote** (as the web) | Must |
| MA-03 | Reactions: **React** gives the ten quick choices (`default_reactions`), **+** every emoji with search, the server's emoji first; tap one to add or take back; long-press the reactions for who reacted (`GET /messages/{id}/reactions`, **Show more** past five) | Must |
| MA-04 | **Report** with a reason (`POST /messages/{id}/report`) | Must |
| MA-05 | **New Topic** from a message (`POST /messages/{id}/new-topic`), linked both ways, **Continue topic** button on the source | Must |
| MA-06 | **Ignore** from the user card | Must |
| MA-07 | Text selection inside a message (long-press on text, when controls are not open) | Should |

## 12. idejo ID mode: the inbox

The home of idejo ID mode, matching my.idejo.org's pages, in the inbox's own colours.

```
┌──────────────────────────────────────────┐
│ [idejo]  ›  Topics                    (A)│
│  Forums      Topics      Chats           │  tabs
├──────────────────────────────────────────┤
│ Compact ▾      ◉ Unread only      Filter │
│ [g] ┌ Garden · Tomato plans ─────────┐ 3 new
│     └ Ada: which twine do you use…   ┘ 1 mention
│ [t] ┌ Tennis · Saturday doubles ─────┐ 5 new
└──────────────────────────────────────────┘
```

| ID | Requirement | Priority |
| --- | --- | --- |
| IB-01 | Top bar: the inbox's logo, breadcrumb "› Forums" / "› Topics" / "› Chats › name", avatar; three tabs under it; swipe between tabs | Must |
| IB-02 | **Forums**: a card per joined forum (logo, name, "N new messages" when above 0), not-connected forums with **Connect**, then **Find more forums**. Tapping enters the forum (its home, its theme) | Must |
| IB-03 | **Topics**: the followed topics of every connected forum, merged by latest activity, as idejo's topic rows with the forum's logo leading the row and, at its right (under it on narrow screens), "N new messages", "N mentions", "N reactions" each only above 0. Toolbar: Compact/Expanded, **Unread only** (remembered), **Filter** by forum (remembered) | Must |
| IB-04 | Topics are built in the app: per forum, the followed topics with their unread, mention and reaction counts (IX-02; until then `GET /unread`'s `following`, unread topics only, without reaction counts), fetched in parallel, at most four seconds each; a forum that fails shows a small "Couldn't reach {name}" row and the rest still shows | Must |
| IB-05 | Live: the app keeps one socket per connected forum while in the foreground and listens to `user:me` (`unread.changed`, `notification`) to update counts; it refreshes a forum's rows on change | Must |
| IB-06 | **Chats**: the inbox's private conversations through the inbox's own API (`/conversations`, scope `private`), as cards with the latest message and how many are new; opening one is a topic page without slow mode | Must |
| IB-07 | **Private Message** from a user card in any linked forum opens or starts the conversation in the inbox's Chats (IX-04) | Must |
| IB-08 | A topic tapped in Topics opens inside its forum (theme switch, breadcrumbs of that forum); back returns to the inbox | Must |

## 13. Private conversations

| ID | Requirement | Priority |
| --- | --- | --- |
| PC-01 | Forums without a chat service keep their own conversations (`/conversations`): list at the sidebar's bottom with the count of conversations with news; a conversation works like a topic with no slow mode | Must |
| PC-02 | Forums with a chat service (IX-04) send the member to the inbox's Chats in idejo ID mode; in single-forum mode, to the chat page in a Custom Tab with a line offering idejo ID mode | Must |
| PC-03 | "Members can send me private messages" in settings; a user card shows **Private Message** only when allowed | Must |
| PC-04 | Conversations never appear in search, the sidebar's categories or Unread (as the web) | Must |

## 14. Notifications

| ID | Requirement | Priority |
| --- | --- | --- |
| NT-01 | In-app bubbles at the top as the web's: new message in a followed topic (grey), role mention (blue), reply (orange), mention (red), reaction (purple); 10 s, paused while touched; tap goes to the message, swipe dismisses; none about the topic on screen. From the `user:me` channel's `notification` | Must |
| NT-02 | UnifiedPush (idejo AP-19): with the `push` scope, register the distributor's endpoint and Web Push keys with every connected forum (`POST /me/push/unifiedpush`, device name); unregister on sign-out; re-register when the distributor changes the endpoint | Must |
| NT-03 | Payloads are decrypted (RFC 8291 `aes128gcm`) with the keys registered; the declarative JSON gives title, body, `navigate`, `tag` and `lang`; `tag` replaces the earlier notification with the same tag per forum; `app_badge` sets the launcher badge where supported | Must |
| NT-04 | Tapping a notification opens `navigate` in the app (DL-02), in that forum | Must |
| NT-05 | Notification channels per forum and per kind (replies, mentions, role mentions, followed, private, warnings), so Android's own settings can silence any of them; the forum's name and logo as the notification's group | Must |
| NT-06 | `play` build: the embedded FCM distributor is the default when no other distributor is installed; `foss` build: with no distributor, settings explain how to install one (e.g. ntfy) and the app works without push | Must |
| NT-07 | Android 13+: notification permission asked only after the member has posted or followed a topic, with the web banner's wording and **Not now** (remembered on the server as the banner's dismissal is, or locally) | Must |
| NT-08 | Push settings screen as the web's: state on this device, the five switches (`PATCH /me/notification-settings`), the devices with **Remove**, **Send test notification** (`POST /me/push/test`) | Must |
| NT-09 | No polling and no persistent background connection; outside the foreground everything arrives by push | Must |

## 15. Settings

### 15.1 Profile and account (native screens, per forum)

| ID | Requirement | Priority |
| --- | --- | --- |
| ST-01 | Profile: display name, user tag, bio in Markdown with **Preview**, picture and dark-mode picture (crop to square, upload `PUT /me/avatar`, `/me/avatar-dark`, remove) | Must |
| ST-02 | Writing: **Automatic punctuation**, **Use the graphical composer** (the open topic follows at once) | Must |
| ST-03 | Message controls (mobile, desktop); Private messages; Email notifications (the web's six choices and the weekly option); Push (NT-08) | Must |
| ST-04 | Password change (`PUT /me/password`), warning that every device signs out — this one included, which then signs in again | Must |
| ST-05 | Apps and devices: the list (`GET /me/tokens`), this device marked, **Remove** | Must |
| ST-06 | **Delete account** opens `account.deletion_url` in a Custom Tab | Must |
| ST-07 | Linked members (idejo ID): the note that settings apply only to this forum, with a link to the inbox's settings, which offer **Save for all forums** as the web (when the inbox's API offers it) | Should |
| ST-08 | Ignored users list (`GET /me/ignored`) with unignore | Must |

### 15.2 App settings (on this device)

| ID | Requirement | Priority |
| --- | --- | --- |
| ST-20 | Mode (MD-04); push distributor choice (UnifiedPush's picker); open links in the app (DL-01) | Must |
| ST-21 | Cache: size used, limit (default 200 MB), **Clear cache** | Must |
| ST-22 | Text size inside messages (follows system by default), haptics, reduced motion (follows system) | Should |
| ST-23 | About: version, licence, open-source licences of every dependency, source code link, privacy note | Must |

## 16. Offline

| ID | Requirement | Priority |
| --- | --- | --- |
| OF-01 | A Room cache per forum: server and theme, categories, sidebar, Unread, the last 20 topics opened with their loaded windows, users and images seen (Coil disk cache), within the size limit, least-recently-used first | Must |
| OF-02 | Offline (or a forum unreachable), cached pages show with an "Offline — showing saved copy from {time}" bar; actions that need the server are disabled with a reason | Must |
| OF-03 | Drafts: the composer's unsent text is saved locally per forum and topic (and per new-topic form) on every change and restored on any return, also after the app is killed | Must |
| OF-04 | Enter while offline keeps the text as unsent with "Will send when you're back online". On reconnect the app re-reads the composer state, then sends the draft once as one commit: into the open message when it is still open, else as a new message when allowed; refused (`too_soon` etc.), the text stays in the box with the server's message | Must |
| OF-05 | Nothing else is queued offline (reactions, follows, votes): those buttons are disabled offline | Must |

## 17. Links

| ID | Requirement | Priority |
| --- | --- | --- |
| DL-01 | Verified App Links: the app declares the hosts of the forums added (and the inbox) and asks the system to verify them; forums serve `/.well-known/assetlinks.json` (IX-03) with the fingerprints of the three release keys. Forum links whose host is not added open in the browser | Must |
| DL-02 | Paths understood: `/`, `/c/{slug}`, `/c/{slug}/t/{id}-{slug}`, `/m/{id}` (with `?unread`), `/u/…`, `/p/{id}`, `/chat…` on the inbox; anything else opens in a Custom Tab | Must |
| DL-03 | Links inside messages to an added forum open in the app; other links in a Custom Tab (with the forum's toolbar colour) | Must |
| DL-04 | **Share** to the app: text or images shared from another app open a picker of forum and topic, then the composer with it | Should |

## 18. Accessibility and languages

| ID | Requirement | Priority |
| --- | --- | --- |
| AX-01 | WCAG 2.2 AA as applied to native apps: TalkBack labels, headings, focus order, 48 dp targets, contrast in both themes with any forum colours (on-colours chosen by contrast, TH-02) | Must |
| AX-02 | TalkBack announces a growing message when it opens and when it closes, never at each append (idejo AX-02), as a polite live region | Must |
| AX-03 | The open state, the time left in the window and "view earlier version" have text for TalkBack (idejo AX-03) | Must |
| AX-04 | Every gesture has a button: sidebars, message actions, Enter×3 (the send menu) | Must |
| AX-05 | Font scale to 200 % without lost content; reduced motion turns off glows, pulses and cross-fades | Must |
| AX-06 | Every string in Android resources, with plurals; right-to-left layouts mirrored; languages follow idejo's web translations | Must |

## 19. Security and privacy

| ID | Requirement | Priority |
| --- | --- | --- |
| SY-01 | https and wss only; no cleartext traffic (network security config); system trust store only | Must |
| SY-02 | Tokens and push keys encrypted with a Keystore key (SI-04); excluded from Android backups and device transfer | Must |
| SY-03 | No analytics, crash reporting or third-party SDKs in any build; the `play` build's only Google component is the FCM distributor | Must |
| SY-04 | Each forum's data, tokens and cache are kept apart; a request is only ever sent with the token of its own host | Must |
| SY-05 | WebViews only for tap-to-load embeds (TV-26), never for sign-in (Custom Tabs only) | Must |
| SY-06 | Screenshots allowed (it is a forum); private conversations hidden in the recent-apps preview | Should |
| SY-07 | Discovery and every API answer parsed leniently: unknown fields and events ignored (idejo API guide section 10) | Must |

## 20. Moderation (milestone M6)

| ID | Requirement | Priority |
| --- | --- | --- |
| MO-01 | Moderators of a category get **Moderate** under messages and on topics, with every action of the API's moderation routes (idejo AP-13): lock, pin, archive, move, rename, icon, topical, followed by everyone, delete; move or delete messages; close an open message; pin to the navigator; topic permissions | Must |
| MO-02 | Queue: reports and pending topics, with approve/reject and report states | Must |
| MO-03 | Warn, warnings list, ban and unban | Must |
| MO-04 | Moderation log, searchable | Should |
| MO-05 | Versions of edited messages ("view earlier version") | Must |

## 21. Right sidebar (milestone M7)

Opened by a swipe to the left on the page or a button at the right of the top bar; full
screen on phones, beside the page from 1200 dp.

| ID | Requirement | Priority |
| --- | --- | --- |
| RS-01 | **Activity**: the history of in-app bubbles (NT-01) across every forum in the app, newest first, with the forum's logo; tap goes to the message; **Mark all seen** | Must |
| RS-02 | **Recent places**: the topics, categories and conversations visited in the app, across forums, with back/forward | Must |
| RS-03 | **Presence**: who is reading the topic now (when idejo exposes presence through the API: IX-05) | Should |

## 22. Changes needed in idejo

The app talks to forums only through the API. These additions to idejo (and its inbox
mode) are asked for; each is small and useful beyond the app. Until one ships, the app
falls back as stated.

| ID | Change in idejo | Needed by | Fallback |
| --- | --- | --- | --- |
| IX-01 | `GET /server` adds `theme: {primary, secondary, tertiary, light_bg, dark_bg}` | TH-01 | idejo's default colours |
| IX-02 | `GET /me/followed`: the topics the member follows, by category (for the sidebar) and with `unread`, `mentions`, `reactions` counts (as `/community/topics` gives the inbox) | SH-31, IB-04 | Sidebar from category topic lists filtered by `followed`; inbox Topics from `/unread` (unread only, no reactions) |
| IX-03 | `/.well-known/assetlinks.json` naming `org.idejo.app` and the release fingerprints | DL-01 | Links open in the browser |
| IX-04 | Discovery says whether the forum uses a chat service (`community.chat_url`, `community.inbox_url`), and the inbox's API can start a conversation with an idejo ID subject (the web's `/chat/new?with=`); `GET /users/{id}` gives a linked member's subject to that end | PC-02, IB-07 | **Private Message** opens the web's link in a Custom Tab |
| IX-05 | Presence on the topic channel (idejo TP-16) | RS-03 | Not shown |
| IX-06 | Inbox mode serves API v1 for its own pages (`/me/communities`, `/conversations`, settings) — to confirm on idejo 0.3.x | SI-06, IB-06 | — (blocking for M4) |
| IX-10 | A tag may belong to several categories; `GET /tags` gives each tag its `category_ids` (empty: every category), as the web groups Filter by tag (callisto-rpg/Idejo#363) | BR-04 | One list of tags, without categories |

## 23. Non-functional requirements

Cited as "app NF-xx".

| ID | Area | Target |
| --- | --- | --- |
| NF-01 | Live latency | An event received is on screen within 100 ms (95th percentile) on a mid-range phone |
| NF-02 | Start | Cold start to a cached forum's home under 1.5 s on a mid-range phone (baseline profiles) |
| NF-03 | Smoothness | Topic scrolling without dropped frames at 60 Hz with 500 loaded messages |
| NF-04 | Battery | No wake locks, no polling, sockets closed within 30 s of going to the background |
| NF-05 | Size | Under 15 MB download for each build |
| NF-06 | Compatibility | Android 8.0 (API 26) to the latest; phones, tablets, foldables, Chromebooks; tested on API 26, 30 and latest |
| NF-07 | Durability | No typed text is ever lost: drafts on disk before the next frame after a change |
| NF-08 | Quality | Every requirement ID has tests named after it; the composer's behaviour is tested against a fake server reproducing the API guide's examples, and against a real idejo in CI end-to-end |
| NF-09 | Reproducible | F-Droid reproducible build of the `foss` flavour |

## 24. Proposed tech stack

| Layer | Choice | Why |
| --- | --- | --- |
| Language | Kotlin, coroutines and Flow | Android standard |
| UI | Jetpack Compose, Material 3, Material 3 adaptive (window size classes, list-detail) | Native look with full control of idejo's identity |
| Navigation | Navigation Compose with typed routes, one graph per forum | Back stack per forum, deep links |
| DI | Hilt | Standard, Apache-2.0 |
| HTTP | OkHttp + Retrofit with kotlinx.serialization (lenient, unknown keys ignored) | Apache-2.0; per-forum clients with their own token |
| Events | Own Phoenix Channels v2 client over OkHttp WebSocket (heartbeat, rejoin, backoff) | Small, testable, no Gson; JavaPhoenixClient (MIT) as reference only |
| Storage | Room (cache), DataStore (settings), Keystore AES-GCM (tokens, push keys) | — |
| Background | WorkManager (draft sending, cache trimming) | — |
| Images | Coil 3 with disk cache | Compose-first, Apache-2.0 |
| Markdown | commonmark-java (BSD-2) with GFM extensions and idejo's extensions, rendered to Compose | No HTML, matches idejo's dialect |
| Sign-in | AndroidX Browser (Custom Tabs) | Required by the API guide |
| Push | UnifiedPush connector (Apache-2.0); embedded FCM distributor in `play` only | NT-02, NT-06 |
| Builds | Gradle with version catalog, flavours `foss` and `play`, R8, baseline profiles | F-Droid and Play |
| Tests | JUnit, Turbine, MockWebServer, Compose UI tests, Roborazzi screenshots, an end-to-end job against idejo's Docker image | NF-08 |
| Quality | ktlint, detekt, Android Lint, REUSE lint, licence check | — |

Every dependency must be under a licence compatible with Apache-2.0 and accepted by
F-Droid (no proprietary code in `foss`).

## 25. Licensing and contributions

| ID | Requirement |
| --- | --- |
| LI-01 | Apache-2.0; every source file has an SPDX header (`Apache-2.0`); REUSE-compliant |
| LI-02 | The app never copies idejo's AGPL source (stylesheets, scripts, templates); behaviour is reimplemented from the specs and guides. Fonts and icons are taken from their own sources under their licences |
| LI-03 | The same licence-grant CLA as idejo, checked on pull requests |
| LI-04 | AI-assisted commits carry "Assisted-by: Claude Code"; every change reviewed by a person |
| LI-05 | "idejo" name and logo under idejo's trademark policy (idejo LI-07) |

## 26. Milestones

1. **M0 — Foundations**: project, flavours, CI, licence and REUSE, design system (theme
   engine TH-01 to TH-10, fonts, components), API client, Phoenix client, fake server.
2. **M1 — Read a forum**: single-forum mode, SI-01 to SI-05, SI-09 to SI-12, the shell
   (SH-01 to SH-37), browsing (BR), topic page (TV), large screens (SH-60 to SH-62).
3. **M2 — Write**: the composer (CO), message actions (MA), private conversations (PC-01,
   PC-03, PC-04).
4. **M3 — Notifications and settings**: NT, ST.
5. **M4 — idejo ID mode**: MD, SI-06 to SI-08, the inbox (IB), PC-02, logo menu SH-12 to SH-15.
6. **M5 — 1.0, web parity**: offline (OF), links (DL), accessibility audit (AX), app NF
   targets, store listings. Release on Play, F-Droid and GitHub.
7. **M6 — Moderation** (MO).
8. **M7 — Right sidebar** (RS).

## 27. Items still to confirm

- [ ] idejo changes IX-01 to IX-06: who builds them and in which idejo version.
- [ ] Play and GitHub signing keys; F-Droid signing (its own key, or reproducible builds with ours) — decides IX-03's fingerprints.
- [ ] Default inbox address for idejo ID mode (my.idejo.org, or the hosting domain once LB-05 is settled).
- [ ] Store name and listing text ("idejo").
