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

// Robolectric's Android 14 (sdk=34 in each module's robolectric.properties), resolved by
// Gradle rather than fetched by Robolectric itself at test time, and only while it is not in
// build/robolectric already: Maven Central answers CI's runners 429 for it (2026-11-22), so
// CI keeps that folder in a cache of its own (ci.yml). Every test runs Robolectric offline
// against it.
val robolectricAndroid =
    configurations.create("robolectricAndroid") {
        isTransitive = false
    }

dependencies {
    robolectricAndroid(libs.robolectric.android.all)
}

val robolectricDir =
    layout.buildDirectory
        .dir("robolectric")
        .get()
        .asFile

val robolectricJar =
    File(
        robolectricDir,
        "android-all-instrumented-${libs.robolectric.android.all.get().version}.jar",
    )

val robolectricJars =
    tasks.register<Sync>("robolectricJars") {
        from(robolectricAndroid)
        into(robolectricDir)
    }

subprojects {
    tasks.withType<Test>().configureEach {
        if (!robolectricJar.exists()) dependsOn(robolectricJars)
        systemProperty("robolectric.offline", "true")
        systemProperty("robolectric.dependency.dir", robolectricDir.absolutePath)
    }
}
