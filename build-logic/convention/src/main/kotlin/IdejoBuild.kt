// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/** The version catalog, for the convention plugins (no type-safe accessors in them). */
val Project.catalog: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun VersionCatalog.version(name: String): String = findVersion(name).get().requiredVersion

fun VersionCatalog.int(name: String): Int = version(name).toInt()

fun VersionCatalog.library(name: String) = findLibrary(name).get()

/**
 * Android Lint for every Android module: warnings fail the build. Off: the checks that only
 * say a newer version exists (versions are pinned and raised on purpose, CLAUDE.md → Stack).
 */
fun com.android.build.api.dsl.Lint.idejoDefaults() {
    abortOnError = true
    warningsAsErrors = true
    checkDependencies = false
    disable +=
        setOf(
            "GradleDependency",
            "NewerVersionAvailable",
            "AndroidGradlePluginVersion",
            "OldTargetApi",
        )
}
