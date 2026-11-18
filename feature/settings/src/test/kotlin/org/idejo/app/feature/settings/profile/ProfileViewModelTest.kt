// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.profile

import org.idejo.app.core.api.ApiProblem
import org.idejo.app.feature.settings.SignedInForum
import org.idejo.app.feature.settings.until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** **Profile** (ST-01) against the fake forum. */
class ProfileViewModelTest {
    @get:Rule val forum = SignedInForum()

    private fun loaded(): ProfileViewModel {
        forum.signIn()
        return ProfileViewModel(forum.sessions).also { vm -> vm.state.until { !it.loading } }
    }

    private val shared get() = requireNotNull(forum.sessions.active).me.state

    @Test
    fun `ST-01 the forms show the member's name, tag and bio`() {
        val state = loaded().state.value
        assertEquals("Ada Lovelace", state.name)
        assertEquals("ada", state.tag)
        assertEquals("", state.bio)
        assertNull(state.me?.avatarUrl)
    }

    @Test
    fun `ST-01 save sends the name and tag only, and the shell sees the new name`() {
        val vm = loaded()
        vm.onEvent(ProfileEvent.EditName("Ada King"))
        vm.onEvent(ProfileEvent.EditTag(""))
        vm.onEvent(ProfileEvent.SaveName)

        val saved = vm.state.until { it.saving == null && it.saved == ProfilePart.Name }
        assertEquals("Ada King", saved.name)
        assertEquals("", saved.tag)
        val patch = forum.fake.requests.last { it.method == "PATCH" }
        assertEquals("""{"display_name":"Ada King","user_tag":""}""", patch.body?.utf8())
        assertEquals("Ada King", shared.value?.displayName)
        assertNull(shared.value?.userTag)
    }

    @Test
    fun `ST-01 a refused name keeps what was typed and shows the server's words`() {
        val vm = loaded()
        vm.onEvent(ProfileEvent.EditName("  "))
        vm.onEvent(ProfileEvent.SaveName)

        val failed = vm.state.until { it.saving == null && it.problem != null }
        assertEquals("  ", failed.name)
        assertEquals(ProfilePart.Name, failed.problemPart)
        assertEquals(
            "Display name can't be blank.",
            (failed.problem as ApiProblem.Server).error.message,
        )
        assertEquals("Ada Lovelace", shared.value?.displayName)
    }

    @Test
    fun `ST-01 the bio is saved alone`() {
        val vm = loaded()
        vm.onEvent(ProfileEvent.EditBio("Analyst of **engines**."))
        vm.onEvent(ProfileEvent.SaveBio)

        val saved = vm.state.until { it.saving == null && it.saved == ProfilePart.Bio }
        assertEquals("Analyst of **engines**.", saved.bio)
        val patch = forum.fake.requests.last { it.method == "PATCH" }
        assertEquals("""{"bio_md":"Analyst of **engines**."}""", patch.body?.utf8())
        assertEquals("Analyst of **engines**.", shared.value?.bioMd)
    }

    @Test
    fun `ST-01 typing is kept while another form is saved`() {
        val vm = loaded()
        vm.onEvent(ProfileEvent.EditBio("Half-written"))
        vm.onEvent(ProfileEvent.EditName("Ada King"))
        vm.onEvent(ProfileEvent.SaveName)

        val saved = vm.state.until { it.saving == null && it.saved == ProfilePart.Name }
        assertEquals("Half-written", saved.bio)
    }

    @Test
    fun `ST-01 a picture is uploaded as multipart image and shown everywhere`() {
        val vm = loaded()
        vm.onEvent(ProfileEvent.SetPicture(dark = false, byteArrayOf(1, 2, 3), "image/jpeg"))

        val saved = vm.state.until { it.saving == null && it.saved == ProfilePart.Picture }
        val sent = forum.fake.requests.last { it.method == "PUT" }
        assertEquals("/api/v1/me/avatar", sent.url.encodedPath)
        assertTrue(
            sent.body
                ?.utf8()
                .orEmpty()
                .contains("name=\"image\"; filename=\"picture.jpg\""),
        )
        val url = requireNotNull(saved.me?.avatarUrl)
        assertEquals(url, shared.value?.avatarUrl)
        assertNull(saved.me?.avatarDarkUrl)
    }

    @Test
    fun `ST-01 the dark-mode picture is its own, and both can be removed`() {
        val vm = loaded()
        vm.onEvent(ProfileEvent.SetPicture(dark = true, byteArrayOf(1), "image/jpeg"))
        vm.state.until { it.saving == null && it.saved == ProfilePart.DarkPicture }
        assertEquals(
            "/api/v1/me/avatar-dark",
            forum.fake.requests
                .last { it.method == "PUT" }
                .url.encodedPath,
        )

        vm.onEvent(ProfileEvent.RemovePicture(dark = true))
        val removed = vm.state.until { it.saving == null && it.me?.avatarDarkUrl == null }
        assertEquals(ProfilePart.DarkPicture, removed.saved)
        assertEquals(
            "/api/v1/me/avatar-dark",
            forum.fake.requests
                .last { it.method == "DELETE" }
                .url.encodedPath,
        )
    }

    @Test
    fun `ST-01 offline, nothing is lost`() {
        val vm = loaded()
        forum.goOffline()
        vm.onEvent(ProfileEvent.EditBio("Kept"))
        vm.onEvent(ProfileEvent.SaveBio)

        val failed = vm.state.until { it.saving == null && it.problem != null }
        assertEquals("Kept", failed.bio)
        assertTrue(failed.problem is ApiProblem.Network)
    }
}
