// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.browse

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.idejo.app.core.api.getOrNull
import org.idejo.app.core.data.ForumSession
import org.idejo.app.core.model.Category

/** The forum's categories, for the lists that name each topic's (BR-04 to BR-06). */
internal class Categories {
    private val mutable = MutableStateFlow<List<Category>>(emptyList())
    val all: StateFlow<List<Category>> = mutable

    suspend fun load(session: ForumSession?) {
        session?.call { categories() }?.getOrNull()?.let { mutable.value = it }
    }
}

/** The category of id [id] among [all]. */
internal fun List<Category>.find(id: String?): Category? = firstOrNull { it.id == id }
