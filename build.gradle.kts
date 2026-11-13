// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// Style and static checks over the whole repository. ktlint (with the Compose rules) and
// detekt run as their pinned command-line tools, so they follow every Kotlin source and
// build script whatever plugins a module applies; `./gradlew check` runs them.
plugins {
    base
}

val ktlint = configurations.create("ktlint")
val detektCli = configurations.create("detektCli")

dependencies {
    ktlint(libs.ktlint.cli) {
        attributes {
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        }
    }
    ktlint(libs.compose.rules.ktlint)
    detektCli(libs.detekt.cli)
}

val kotlinSources =
    fileTree(rootDir) {
        include("**/*.kt", "**/*.kts")
        exclude("**/build/**", "**/.gradle/**", "**/.kotlin/**")
    }

val ktlintPatterns = listOf("**/*.kt", "**/*.kts", "!**/build/**", "!**/.gradle/**")

val ktlintCheck =
    tasks.register<JavaExec>("ktlintCheck") {
        group = "verification"
        description = "Checks the Kotlin style (ktlint, Compose rules)."
        classpath = ktlint
        mainClass.set("com.pinterest.ktlint.Main")
        workingDir = rootDir
        args(ktlintPatterns)
        inputs.files(kotlinSources)
        inputs.file(".editorconfig")
        outputs.upToDateWhen { true }
    }

tasks.register<JavaExec>("ktlintFormat") {
    group = "formatting"
    description = "Fixes what ktlint can fix."
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    workingDir = rootDir
    args(listOf("-F") + ktlintPatterns)
}

val detekt =
    tasks.register<JavaExec>("detekt") {
        group = "verification"
        description = "Static analysis of the Kotlin sources (detekt)."
        classpath = detektCli
        mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
        workingDir = rootDir
        args(
            "--input",
            rootDir.absolutePath,
            "--config",
            file("config/detekt/detekt.yml").absolutePath,
            "--build-upon-default-config",
            "--excludes",
            "**/build/**,**/.gradle/**,**/.kotlin/**",
        )
        inputs.files(kotlinSources)
        inputs.file("config/detekt/detekt.yml")
        outputs.upToDateWhen { true }
    }

tasks.named("check") {
    dependsOn(ktlintCheck, detekt)
}
