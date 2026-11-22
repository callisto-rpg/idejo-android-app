<!--
SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
SPDX-License-Identifier: Apache-2.0
-->

# Contributing

Thank you for helping. Read [`CLAUDE.md`](CLAUDE.md) first: its rules apply to every
change, by people and by AI assistants alike.

## The licence agreement

Contributions are accepted under idejo's
[Contributor Licence Agreement](https://github.com/callisto-rpg/Idejo/blob/main/docs/legal/CLA.md)
(LI-03). It grants licences; you keep your copyright. On your first pull request a check
asks you to sign it by posting a comment with the sentence it gives; you sign once.

## Pull requests

- **One requirement or fix per pull request.** Name the requirement IDs it covers
  (`SI-03`, `TH-02`…) in the title or description.
- **Tests named after the IDs**: ``fun `CO-03 enter three times completes the message`()``.
- **The full check passes** before you open it: `./gradlew check` and `reuse lint`
  (CLAUDE.md → Commands). Update the screenshots when the UI changed.
- **Every file has an SPDX header** (Apache-2.0), or an entry in `REUSE.toml` when it
  cannot carry one.
- **Never copy idejo's code.** idejo is AGPL-3.0, this app is Apache-2.0: read idejo to
  understand how something behaves, then write it anew (LI-02).
- **New dependencies** only under Apache-2.0, MIT, BSD or a similar licence accepted by
  F-Droid; nothing proprietary, and no analytics, crash reporting, ads or tracking. `./gradlew licensee` checks it.
- A person reviews every change before it is merged. Changes touching sign-in, tokens, the
  Keystore, push decryption, WebViews or link handling get an extra review.
- A feature that needs something idejo's API does not offer is not worked around: describe
  it as an IX item in the spec and ask the steward.

## AI-assisted commits

Commits made with an AI assistant end with the trailer:

```
Assisted-by: Claude Code
```

(naming the assistant used), and are reviewed by a person like any other (LI-04).
