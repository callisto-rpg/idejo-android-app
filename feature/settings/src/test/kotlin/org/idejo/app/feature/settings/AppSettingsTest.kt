// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** App settings: the private messages switch (PC-03), the version and the licences (ST-23). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class AppSettingsTest {
    @Test
    fun `ST-23 the licence check's report lists every library with its licences`() {
        val licences =
            parseLicences(
                """
                [
                  {"groupId": "com.squareup.okhttp3", "artifactId": "okhttp", "version": "5.3.2",
                   "name": "okhttp", "spdxLicenses": [{"identifier": "Apache-2.0",
                   "name": "Apache License 2.0", "url": "https://www.apache.org/licenses/LICENSE-2.0"}],
                   "scm": {"url": "https://github.com/square/okhttp"}},
                  {"groupId": "org.example", "artifactId": "odd", "version": "1.0",
                   "unknownLicenses": [{"name": "Some licence", "url": "https://example.org"}],
                   "somethingNew": true}
                ]
                """.trimIndent(),
            )
        assertEquals(
            listOf(
                Licence("okhttp", "5.3.2", listOf("Apache-2.0")),
                Licence("org.example:odd", "1.0", listOf("Some licence")),
            ),
            licences,
        )
    }

    @Test
    fun `ST-23 an unreadable report lists nothing`() {
        assertEquals(emptyList<Licence>(), parseLicences("not json"))
    }

    @Test
    fun `ST-23 PC-03 about, and the private messages switch`() {
        for (dark in listOf(false, true)) {
            val mode = if (dark) "dark" else "light"
            captureRoboImage("src/test/screenshots/AppSettings_$mode.png", OPTIONS) {
                IdejoTheme(darkTheme = dark) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.width(390.dp)) {
                            AppSettingsScreen(
                                "0.0.1",
                                listOf(
                                    Licence("Coil", "3.6.3", listOf("Apache-2.0")),
                                    Licence("commonmark-java", "0.32.0", listOf("BSD-2-Clause")),
                                ),
                                privateMessages = PrivateMessagesUi(accepts = true),
                            )
                        }
                    }
                }
            }
        }
    }

    private companion object {
        val OPTIONS =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
            )
    }
}
