// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.model.Me
import org.idejo.app.core.model.MePatch
import javax.inject.Inject

/**
 * **Profile** (ST-01) on the forum open, from its one copy of `GET /me`. The name and the
 * bio are forms: what the member types stays here until **Save** sends it (`PATCH /me`, only
 * that form's fields), and stays on a refusal, with the server's words. Pictures go with
 * `PUT` / `DELETE /me/avatar(-dark)`. Every answer is the forum's new `GET /me`, so the
 * shell and open topics show the new name and picture at once.
 */
@HiltViewModel
class ProfileViewModel
    @Inject
    constructor(
        sessions: SessionManager,
    ) : ViewModel() {
        private val session = sessions.active
        private val mutableState =
            MutableStateFlow(ProfileUiState(origin = session?.origin.orEmpty()))
        val state: StateFlow<ProfileUiState> = mutableState.asStateFlow()

        init {
            session?.let { forum ->
                viewModelScope.launch {
                    forum.me.state.filterNotNull().collect { me ->
                        mutableState.update {
                            it
                                .with(
                                    me,
                                ).copy(loading = false, loadProblem = null)
                        }
                    }
                }
            }
            load()
        }

        fun onEvent(event: ProfileEvent) {
            when (event) {
                is ProfileEvent.EditName -> {
                    mutableState.update {
                        it.copy(
                            name = event.text,
                            saved = null,
                        )
                    }
                }

                is ProfileEvent.EditTag -> {
                    mutableState.update {
                        it.copy(
                            tag = event.text,
                            saved = null,
                        )
                    }
                }

                is ProfileEvent.EditBio -> {
                    mutableState.update {
                        it.copy(
                            bio = event.text,
                            saved = null,
                        )
                    }
                }

                ProfileEvent.SaveName -> {
                    saveName()
                }

                ProfileEvent.SaveBio -> {
                    save(ProfilePart.Bio, MePatch(bioMd = state.value.bio))
                }

                is ProfileEvent.SetPicture -> {
                    picture(event.dark) {
                        setPicture(event.dark, event.bytes, event.type)
                    }
                }

                is ProfileEvent.RemovePicture -> {
                    picture(event.dark) { removePicture(event.dark) }
                }

                ProfileEvent.Unreadable -> {
                    mutableState.update { it.copy(unreadable = true) }
                }

                ProfileEvent.Retry -> {
                    load()
                }
            }
        }

        private fun load() {
            val forum = session
            if (forum == null) {
                mutableState.update { it.copy(loading = false) }
                return
            }
            mutableState.update {
                it.copy(
                    loading = forum.me.state.value == null,
                    loadProblem = null,
                )
            }
            viewModelScope.launch {
                val result = forum.me.refresh()
                mutableState.update {
                    when {
                        result is ApiResult.Success -> {
                            it.with(result.value).copy(loading = false)
                        }

                        result is ApiResult.Failure && forum.me.state.value == null -> {
                            it.copy(loading = false, loadProblem = result.problem)
                        }

                        else -> {
                            it.copy(loading = false)
                        }
                    }
                }
            }
        }

        private fun saveName() {
            val current = state.value
            save(
                ProfilePart.Name,
                MePatch(displayName = current.name.orEmpty(), userTag = current.tag.orEmpty()),
            )
        }

        /** Sends [patch]; the form keeps what was typed until the server agrees. */
        private fun save(
            part: ProfilePart,
            patch: MePatch,
        ) {
            val forum = session ?: return
            if (state.value.saving != null) return
            mutableState.update { it.copy(saving = part, problem = null, saved = null) }
            viewModelScope.launch {
                val result = forum.me.update(patch)
                mutableState.update {
                    when (result) {
                        is ApiResult.Success -> {
                            it.copy(saving = null, saved = part).withForms(result.value, part)
                        }

                        is ApiResult.Failure -> {
                            it.copy(saving = null, problem = result.problem, problemPart = part)
                        }
                    }
                }
            }
        }

        private fun picture(
            dark: Boolean,
            request: suspend org.idejo.app.core.data.MeRepository.() -> ApiResult<Me>,
        ) {
            val forum = session ?: return
            if (state.value.saving != null) return
            val part = if (dark) ProfilePart.DarkPicture else ProfilePart.Picture
            mutableState.update {
                it.copy(
                    saving = part,
                    problem = null,
                    saved = null,
                    unreadable = false,
                )
            }
            viewModelScope.launch {
                val result = forum.me.request()
                mutableState.update {
                    when (result) {
                        is ApiResult.Success -> {
                            it.copy(saving = null, saved = part).with(result.value)
                        }

                        is ApiResult.Failure -> {
                            it.copy(saving = null, problem = result.problem, problemPart = part)
                        }
                    }
                }
            }
        }

        /** The server's member; the forms are filled only while they are not being edited. */
        private fun ProfileUiState.with(me: Me): ProfileUiState =
            copy(
                me = me,
                name = if (name == null || name == server?.displayName) me.displayName else name,
                tag =
                    if (tag == null ||
                        tag == server?.userTag.orEmpty()
                    ) {
                        me.userTag.orEmpty()
                    } else {
                        tag
                    },
                bio =
                    if (bio == null ||
                        bio == server?.bioMd.orEmpty()
                    ) {
                        me.bioMd.orEmpty()
                    } else {
                        bio
                    },
            )

        /** After [part] was saved: that form shows what the server kept. */
        private fun ProfileUiState.withForms(
            me: Me,
            part: ProfilePart,
        ): ProfileUiState =
            when (part) {
                ProfilePart.Name -> copy(me = me, name = me.displayName, tag = me.userTag.orEmpty())
                ProfilePart.Bio -> copy(me = me, bio = me.bioMd.orEmpty())
                else -> with(me)
            }
    }
