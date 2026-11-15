// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.writing

import org.idejo.app.core.api.ApiProblem
import org.idejo.app.feature.settings.SignedInForum
import org.idejo.app.feature.settings.until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** **Writing** (ST-02) against the fake forum. */
class WritingViewModelTest {
    @get:Rule val forum = SignedInForum()

    private fun loaded(): WritingViewModel {
        forum.signIn()
        return WritingViewModel(forum.sessions).also { vm -> vm.state.until { !it.loading } }
    }

    @Test
    fun `ST-02 the section shows the member's choices`() {
        val state = loaded().state.value
        assertEquals(true, state.autoPunctuation)
        assertEquals(true, state.graphicalComposer)
        assertNull(state.loadProblem)
    }

    @Test
    fun `ST-02 turning off automatic punctuation patches me`() {
        val vm = loaded()
        vm.onEvent(WritingEvent.AutoPunctuation(false))

        assertEquals(false, vm.state.value.autoPunctuation)
        val saved = vm.state.until { !it.saving }
        assertEquals(false, saved.autoPunctuation)
        val patch = forum.fake.requests.last { it.method == "PATCH" }
        assertEquals("""{"auto_punctuation":false}""", patch.body?.utf8())
        assertEquals(false, forum.fake.choicesOf(forum.ada).autoPunctuation)
    }

    @Test
    fun `ST-02 a failed patch reverts the switch and shows the server message`() {
        val vm = loaded()
        forum.fake.failNext(422, "invalid_request", "That choice cannot be saved now.")
        vm.onEvent(WritingEvent.GraphicalComposer(false))

        val failed = vm.state.until { !it.saving }
        assertEquals(true, failed.graphicalComposer)
        val problem = failed.problem as ApiProblem.Server
        assertEquals("That choice cannot be saved now.", problem.error.message)
        assertEquals(true, forum.fake.choicesOf(forum.ada).graphicalComposer)
    }

    @Test
    fun `ST-02 offline, the switch goes back and nothing was changed`() {
        val vm = loaded()
        forum.goOffline()
        vm.onEvent(WritingEvent.AutoPunctuation(false))

        val failed = vm.state.until { !it.saving }
        assertEquals(true, failed.autoPunctuation)
        assertTrue(failed.problem is ApiProblem.Network)
    }

    @Test
    fun `ST-02 the graphical composer choice reaches the open composer at once`() {
        val vm = loaded()
        // What the open topic's composer and message controls read (ComposerViewModel.me).
        val open = requireNotNull(forum.sessions.active).me.state
        assertEquals(true, open.value?.graphicalComposer)

        vm.onEvent(WritingEvent.GraphicalComposer(false))

        assertEquals(false, open.until { it?.graphicalComposer == false }?.graphicalComposer)
    }

    @Test
    fun `ST-02 a server without graphical_composer hides the row`() {
        forum.fake.memberChoices = false
        val state = loaded().state.value
        assertNull(state.graphicalComposer)
        assertEquals(true, state.autoPunctuation)
    }

    @Test
    fun `ST-02 a section that cannot be read offers to try again`() {
        forum.signIn()
        forum.fake.failNext(500, "internal", "Something went wrong.")
        val vm = WritingViewModel(forum.sessions)
        val failed = vm.state.until { it.loadProblem != null }
        assertFalse(failed.loading)

        vm.onEvent(WritingEvent.Retry)

        val read = vm.state.until { it.loadProblem == null && !it.loading }
        assertEquals(true, read.autoPunctuation)
    }
}
