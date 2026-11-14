// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.api.getOrNull
import org.idejo.app.core.api.map
import org.idejo.app.core.model.ApiError
import org.idejo.app.core.model.Mention
import org.idejo.app.core.model.RoleCard
import org.idejo.app.core.model.Category as ForumCategory
import org.idejo.app.core.model.Tag as ForumTag
import org.idejo.app.core.model.Topic as ForumTopic

/** The small card an inline link opens (TV-22), as the web's. */
sealed interface InlineCard {
    /** `+role`: its colour, member count and first members; no Open (steward decision). */
    data class Role(
        val role: RoleCard,
    ) : InlineCard

    /** `#topic`: its category, starter, count, tags and opening words; Follow / Unfollow. */
    data class Topic(
        val topic: ForumTopic,
        val category: ForumCategory?,
        /** The opening message's Markdown, for its first words. */
        val opening: String?,
    ) : InlineCard

    /** `/category`: its description, topic count and latest topics. */
    data class Category(
        val category: ForumCategory,
        val topics: List<ForumTopic>,
    ) : InlineCard

    /** `$tag`: where it belongs (none: every category) and the latest topics carrying it. */
    data class Tag(
        val tag: ForumTag,
        val categories: List<ForumCategory>,
        val topics: List<ForumTopic>,
    ) : InlineCard

    /** `&forum`: its name and address; Join and Open wait for idejo ID mode (M4). */
    data class Forum(
        val name: String,
        val host: String,
    ) : InlineCard
}

/**
 * Builds the card for an inline link in [session]'s forum (TV-22), as the web's
 * `InlineCards`: what the member may not view has no card, as the server's `not_found`
 * says. `@user` links open the user card (BR-09) instead; other kinds give null.
 */
class InlineCards(
    private val session: ForumSession,
) {
    suspend fun load(mention: Mention): ApiResult<InlineCard>? {
        val ref = mention.id ?: mention.slug ?: return null
        return when (mention.type) {
            "role" -> {
                session.call { roleCard(mention.slug ?: ref) }.map(InlineCard::Role)
            }

            "topic" -> {
                topic(ref)
            }

            "category" -> {
                category(ref)
            }

            "tag" -> {
                tag(mention.slug ?: ref)
            }

            "forum" -> {
                ApiResult.Success(
                    InlineCard.Forum(mention.name ?: mention.text.removePrefix("&"), ref),
                )
            }

            else -> {
                null
            }
        }
    }

    /** **Follow** / **Unfollow** on a topic's card: the topic as the server answers. */
    suspend fun follow(
        topicId: String,
        followed: Boolean,
    ): ApiResult<ForumTopic> = session.call { follow(topicId, followed) }

    private suspend fun topic(id: String): ApiResult<InlineCard> =
        coroutineScope {
            val found = session.call { topic(id) }
            val topic = found.getOrNull() ?: return@coroutineScope found as ApiResult.Failure
            val category =
                async { topic.categoryId?.let { session.call { category(it) }.getOrNull() } }
            val opening =
                async {
                    topic.firstMessageId?.let { session.call { message(it) }.getOrNull()?.bodyMd }
                }
            ApiResult.Success(InlineCard.Topic(topic, category.await(), opening.await()))
        }

    private suspend fun category(id: String): ApiResult<InlineCard> =
        coroutineScope {
            val topics = async { session.call { categoryTopics(id, limit = SHOWN) }.getOrNull() }
            val category = session.call { category(id) }
            val latest =
                topics
                    .await()
                    ?.data
                    .orEmpty()
                    .take(SHOWN)
            category.map { InlineCard.Category(it, latest) }
        }

    /** A tag the member may not view is missing from `GET /tags`: no card, as `not_found`. */
    private suspend fun tag(slug: String): ApiResult<InlineCard> =
        coroutineScope {
            val topics = async { session.call { tagged(listOf(slug), limit = SHOWN) }.getOrNull() }
            val categories = async { session.call { categories() }.getOrNull().orEmpty() }
            val tags = session.call { tags() }
            val tag: ForumTag =
                tags.getOrNull()?.firstOrNull { it.slug == slug }
                    ?: return@coroutineScope (tags as? ApiResult.Failure ?: NOT_FOUND)
            val where = categories.await().filter { it.id in tag.categoryIds.orEmpty() }
            ApiResult.Success(
                InlineCard.Tag(
                    tag,
                    where,
                    topics
                        .await()
                        ?.data
                        .orEmpty()
                        .take(SHOWN),
                ),
            )
        }

    private companion object {
        /** As the web's card: three. */
        const val SHOWN = 3
        val NOT_FOUND =
            ApiResult.Failure(
                ApiProblem.Server(HTTP_NOT_FOUND, ApiError(ApiError.NOT_FOUND)),
            )
        const val HTTP_NOT_FOUND = 404
    }
}
