// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// idejo's Markdown dialect: parser and Compose renderer (TV-20 to TV-24).
plugins {
    id("idejo.android.library")
    id("idejo.android.compose")
    id("idejo.android.screenshots")
}

android {
    namespace = "org.idejo.app.core.markdown"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(libs.commonmark)
    implementation(libs.commonmark.gfm.tables)
    implementation(libs.commonmark.gfm.strikethrough)
    implementation(libs.commonmark.task.list.items)
    implementation(libs.commonmark.autolink)
}
