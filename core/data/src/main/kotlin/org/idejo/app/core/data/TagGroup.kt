// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import org.idejo.app.core.model.Category
import org.idejo.app.core.model.Tag

/**
 * A group of **Filter by tag** (BR-04): the tags of every category ([category] null), or one
 * category's. An untitled group holds tags the app cannot place: all of them on a server that
 * does not say (IX-10), or those of categories not loaded yet.
 */
data class TagGroup(
    val category: Category?,
    val tags: List<Tag>,
    val titled: Boolean = true,
)

/**
 * The web's groups: the tags of every category first, then each category's in the home page's
 * order ([sections]); a tag of several categories is in each of them.
 */
fun tagGroups(
    tags: List<Tag>,
    categories: List<Category>,
): List<TagGroup> {
    if (tags.any { it.categoryIds == null }) return listOf(TagGroup(null, tags, titled = false))
    val ordered =
        sections(categories, emptyList()).flatMap { s ->
            s.categories.map { it.category }
        }
    val known = ordered.map { it.id }.toSet()
    val everywhere = tags.filter { it.categoryIds.orEmpty().isEmpty() }
    val byCategory =
        ordered.mapNotNull { category ->
            tags
                .filter { category.id in it.categoryIds.orEmpty() }
                .takeIf { it.isNotEmpty() }
                ?.let { TagGroup(category, it) }
        }
    val unplaced =
        tags.filter { tag ->
            val ids = tag.categoryIds.orEmpty()
            ids.isNotEmpty() && ids.none { it in known }
        }
    return listOfNotNull(everywhere.takeIf { it.isNotEmpty() }?.let { TagGroup(null, it) }) +
        byCategory +
        listOfNotNull(
            unplaced.takeIf { it.isNotEmpty() }?.let { TagGroup(null, it, titled = false) },
        )
}
