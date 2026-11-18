// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The app's own choices, the same in every forum. */
class AppChoicesTest {
    @get:Rule val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val choices by lazy {
        AppChoices(ForumStore.create(folder.root.resolve("app.json"), scope))
    }

    @After
    fun stop() = scope.cancel()

    @Test
    fun `NT-07 Dismiss keeps the notifications bar away for 14 days, then it may come back`() =
        runBlocking {
            val day = 24L * 60 * 60 * 1000
            assertFalse(choices.pushBarQuiet { 0 }.first())

            choices.dismissPushBar(now = 0)

            assertTrue(choices.pushBarQuiet { 13 * day }.first())
            assertFalse(choices.pushBarQuiet { 14 * day }.first())
        }
}
