// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import org.idejo.app.core.api.IdejoJson
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * The app's state on disk, one JSON file (excluded from backups, SY-02). Tokens in it are
 * sealed; nothing else in it is secret.
 */
class ForumStore(
    private val store: DataStore<AppState>,
) {
    val state: Flow<AppState> get() = store.data

    suspend fun current(): AppState = store.data.first()

    suspend fun update(transform: (AppState) -> AppState): AppState = store.updateData(transform)

    suspend fun updateForum(
        origin: String,
        transform: (ForumRecord) -> ForumRecord,
    ): AppState =
        update { state ->
            val forum = state.forum(origin)
            val inbox = state.inbox
            when {
                forum != null -> state.with(transform(forum))
                inbox?.origin == origin -> state.copy(inbox = transform(inbox))
                else -> state
            }
        }

    companion object {
        fun create(
            file: File,
            scope: CoroutineScope,
        ): ForumStore =
            ForumStore(DataStoreFactory.create(AppStateSerializer, scope = scope) { file })
    }
}

private object AppStateSerializer : Serializer<AppState> {
    override val defaultValue = AppState()

    override suspend fun readFrom(input: InputStream): AppState =
        try {
            IdejoJson.decodeFromString(AppState.serializer(), input.readBytes().decodeToString())
        } catch (e: SerializationException) {
            throw CorruptionException("Unreadable app state", e)
        }

    override suspend fun writeTo(
        t: AppState,
        output: OutputStream,
    ) = output.write(stateJson.encodeToString(AppState.serializer(), t).encodeToByteArray())
}

/** Defaults written out, so a file read by a later version keeps every choice. */
private val stateJson = kotlinx.serialization.json.Json(IdejoJson) { encodeDefaults = true }
