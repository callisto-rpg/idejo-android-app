// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.model.Me
import org.idejo.app.core.model.MePatch

/**
 * The signed-in member on one forum (`GET /me`), shared by every screen of its
 * [ForumSession]: the shell, the topic page, the composer and Profile and account. A choice
 * changed in Profile and account reaches the open composer and message controls at once
 * (ST-02), because they all read [state].
 *
 * The server owns the choices: [state] is always the server's latest answer.
 */
class MeRepository internal constructor(
    private val session: ForumSession,
) {
    private val mutableState = MutableStateFlow<Me?>(null)

    /** The member as the server last answered; null before the first answer, or a visitor. */
    val state: StateFlow<Me?> = mutableState.asStateFlow()

    /** Reads `GET /me` again and publishes it. A visitor has no `/me`: nothing is called. */
    suspend fun refresh(): ApiResult<Me>? {
        if (session.access.value != Access.SignedIn) return null
        return session.call { me() }.also(::publish)
    }

    /** [refresh] only when nothing was read yet: what pages that only show the member call. */
    suspend fun load() {
        if (mutableState.value == null) refresh()
    }

    /** `PATCH /me` with only [patch]'s fields; the server's answer is published. */
    suspend fun update(patch: MePatch): ApiResult<Me> =
        session.call { updateMe(patch) }.also(::publish)

    /** Sets the picture, or the dark theme's with [dark] (ST-01); the answer is published. */
    suspend fun setPicture(
        dark: Boolean,
        bytes: ByteArray,
        type: String,
    ): ApiResult<Me> = session.call { setPicture(dark, bytes, type) }.also(::publish)

    /** Removes the picture, or the dark theme's with [dark] (ST-01). */
    suspend fun removePicture(dark: Boolean): ApiResult<Me> =
        session.call { removePicture(dark) }.also(::publish)

    private fun publish(result: ApiResult<Me>) {
        if (result is ApiResult.Success) mutableState.value = result.value
    }
}
