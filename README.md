<!--
SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
SPDX-License-Identifier: Apache-2.0
-->

# idejo for Android

The official Android app for [idejo](https://idejo.org) forums.

The app talks to forums only through idejo's public API v1. Native Kotlin and Jetpack
Compose; Android 8.0 (API 26) and later.

- What the app does: [`idejo Android — Spec Sheet.md`](idejo%20Android%20%E2%80%94%20Spec%20Sheet.md)
- How it is built and the commands: [`CLAUDE.md`](CLAUDE.md)
- Contributing: [`CONTRIBUTING.md`](CONTRIBUTING.md)

## Build

JDK 17 and the Android SDK:

```sh
./gradlew assembleFossDebug   # F-Droid and GitHub flavour
./gradlew check               # every check CI runs
```

## Licence

Apache-2.0 ([`LICENSE`](LICENSE)), REUSE-compliant. Third-party material is listed in
[`NOTICE`](NOTICE).
