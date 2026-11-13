// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// Per-forum API clients, discovery, OAuth (PKCE), error mapping (pure JVM).
plugins {
    id("idejo.jvm.library")
}

dependencies {
    api(project(":core:model"))
}
