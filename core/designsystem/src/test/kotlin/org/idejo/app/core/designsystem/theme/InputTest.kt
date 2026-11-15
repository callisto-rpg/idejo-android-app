// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.theme

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A mouse and a hardware keyboard (SH-62). */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
@RunWith(RobolectricTestRunner::class)
class InputTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `SH-62 a right-click does what a long-press does, not a tap`() {
        val done = mutableListOf<String>()
        rule.setContent {
            Box(
                Modifier
                    .size(100.dp)
                    .testTag("target")
                    .onSecondaryClick { done += "menu" }
                    .combinedClickable(
                        onClick = { done += "tap" },
                        onLongClick = { done += "menu" },
                    ),
            )
        }
        rule.onNodeWithTag("target").performMouseInput { rightClick() }
        rule.waitForIdle()
        assertEquals(listOf("menu"), done)
    }

    @Test
    fun `SH-62 Tab moves the focus from one element to the next, in reading order`() {
        rule.setContent {
            Column {
                listOf("one", "two").forEach { name ->
                    Text(
                        name,
                        Modifier.testTag(name).size(80.dp).combinedClickable(onClick = {}),
                    )
                }
            }
        }
        rule.onRoot().performKeyInput { pressKey(Key.Tab) }
        rule.onNodeWithTag("one").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.Tab) }
        rule.onNodeWithTag("two").assertIsFocused()
    }
}
