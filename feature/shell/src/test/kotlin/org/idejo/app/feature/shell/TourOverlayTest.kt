// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import org.idejo.app.core.data.OnboardingTour
import org.idejo.app.core.designsystem.component.LocalTourTargets
import org.idejo.app.core.designsystem.component.TourTargets
import org.idejo.app.core.designsystem.component.tourTarget
import org.idejo.app.core.model.Onboarding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The app's onboarding tour (IX-18; steward request, 2026-11-22). */
@RunWith(RobolectricTestRunner::class)
class TourOverlayTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val welcome = Onboarding("t_1", "Welcome", "Tell us about yourself")
    private val on = OnboardingTour.State(onboarding = welcome)
    private val done = mutableListOf<String>()
    private val actions =
        TourActions(
            onSwipe = { done += "swipe" },
            onOpenTopic = { done += "open" },
            onClose = { done += "close" },
        )

    private fun show(
        step: TourStep,
        tour: OnboardingTour.State = on,
    ): () -> Unit {
        var loneEnters by mutableStateOf(tour.loneEnters)
        rule.setContent {
            val targets = androidx.compose.runtime.remember { TourTargets() }
            CompositionLocalProvider(LocalTourTargets provides targets) {
                Box(Modifier.fillMaxSize()) {
                    Column {
                        Text("All categories", Modifier.tourTarget(TourTargets.ALL))
                        Text("Welcome", Modifier.tourTarget(TourTargets.topic("t_1")))
                        Box(Modifier.fillMaxWidth().height(80.dp).tourTarget(TourTargets.COMPOSER))
                    }
                    TourOverlay(
                        step,
                        tour.copy(loneEnters = loneEnters),
                        "Ada",
                        "All categories",
                        targets,
                        actions,
                    )
                }
            }
        }
        return { loneEnters += 1 }
    }

    @Test
    fun `IX-18 the steps follow where the member is`() {
        assertNull(tourStep(OnboardingTour.State(), onTopic = false, sidebarShown = false))
        assertEquals(TourStep.Swipe, tourStep(on, onTopic = false, sidebarShown = false))
        assertEquals(TourStep.OpenTopic, tourStep(on, onTopic = false, sidebarShown = true))
        assertEquals(TourStep.SayHi, tourStep(on, onTopic = true, sidebarShown = false))
        assertEquals(
            TourStep.Present,
            tourStep(on.copy(open = true), onTopic = true, sidebarShown = false),
        )
        assertEquals(
            TourStep.Finished,
            tourStep(OnboardingTour.State(finished = true), onTopic = true, sidebarShown = true),
        )
    }

    @Test
    fun `IX-18 a swipe to the right opens the sidebar`() {
        show(TourStep.Swipe)
        rule.onNodeWithText("Swipe to open the sidebar").assertExists()
        rule.onNodeWithTag(TOUR).performTouchInput { swipeRight() }
        assertEquals(listOf("swipe"), done)
    }

    @Test
    fun `IX-18 the onboarding topic is named, the page around it stopped`() {
        show(TourStep.OpenTopic)
        rule.onNodeWithText("Open the Welcome topic").assertExists()
    }

    @Test
    fun `IX-18 the composer asks for Hi then the question with Enter three times`() {
        show(TourStep.SayHi)
        rule.onNodeWithText("Type “Hi, I’m Ada” then press", substring = true).assertExists()
    }

    @Test
    fun `IX-18 a lone Enter shows the three times bubble, gone after 2 seconds`() {
        rule.mainClock.autoAdvance = false
        val enter = show(TourStep.Present, on.copy(open = true))
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText("Tell us about yourself", substring = true).assertExists()
        rule.onNodeWithTag(TOUR_HINT).assertDoesNotExist()
        enter()
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("three times in quick succession", substring = true).assertExists()
        rule.mainClock.advanceTimeBy(3_000)
        rule.onNodeWithTag(TOUR_HINT).assertDoesNotExist()
    }

    @Test
    fun `IX-18 the last card points at All categories and closes`() {
        show(TourStep.Finished, OnboardingTour.State(onboarding = welcome, finished = true))
        rule.onNodeWithText("You’ve completed your first message!", substring = true).assertExists()
        rule.onNodeWithTag(TOUR_CLOSE).performClick()
        assertEquals(listOf("close"), done)
    }
}
