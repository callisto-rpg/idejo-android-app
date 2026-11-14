// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.topic

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.idejo.app.core.data.InlineCard
import org.idejo.app.core.data.TopicState
import org.idejo.app.core.data.namesMember
import org.idejo.app.core.designsystem.theme.ForumTheme
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.markdown.MentionPulse
import org.idejo.app.core.model.Category
import org.idejo.app.core.model.Continuation
import org.idejo.app.core.model.Forwarded
import org.idejo.app.core.model.Me
import org.idejo.app.core.model.Mention
import org.idejo.app.core.model.Message
import org.idejo.app.core.model.MessageState
import org.idejo.app.core.model.MovedAway
import org.idejo.app.core.model.Person
import org.idejo.app.core.model.Poll
import org.idejo.app.core.model.PollOption
import org.idejo.app.core.model.Reaction
import org.idejo.app.core.model.ReplyTo
import org.idejo.app.core.model.Role
import org.idejo.app.core.model.RoleCard
import org.idejo.app.core.model.Spoiler
import org.idejo.app.core.model.Tag
import org.idejo.app.core.model.Topic
import org.idejo.app.core.model.UnreadMark
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/** The topic page in both themes; `./gradlew recordRoborazziDebug` records. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h1400dp-xhdpi")
class TopicScreenshotTest {
    @Test
    fun `TV-01 TV-02 TV-05 TV-08 TV-10 a topic from the first unread`() =
        snapshots("Topic") {
            TopicScreen(
                TopicUi(
                    TopicState(
                        topic = TOPIC,
                        opening = OPENING,
                        messages = MESSAGES,
                        earlier = 12,
                        later = 3,
                        newBelow = 0,
                        unread = UnreadMark("m3", 2),
                        loading = false,
                    ),
                ),
                TopicActions(),
            )
        }

    @Test
    fun `TV-05 new messages while reading higher up`() =
        snapshots("TopicNew") {
            TopicScreen(
                TopicUi(
                    TopicState(
                        topic = TOPIC.copy(followed = true, locked = true),
                        messages = MESSAGES.take(2),
                        later = 4,
                        newBelow = 2,
                        loading = false,
                    ),
                ),
                TopicActions(),
            )
        }

    @Test
    fun `TV-02 TV-27 TP-09 TP-15 replies, forwards, banners and polls`() =
        snapshots("TopicExtras") {
            TopicScreen(
                TopicUi(
                    TopicState(topic = TOPIC, messages = EXTRAS, loading = false),
                    signedIn = true,
                ),
                TopicActions(),
            )
        }

    @Test
    fun `TV-07 mentions of the member glow, red for them, blue for their role`() =
        snapshots("TopicGlow") {
            val ada =
                Me(
                    "u_ada",
                    "Ada Lovelace",
                    roles = listOf(Role("Gardener", slug = "gardener")),
                )
            val context = MessageContext("https://garden.example.org", me = ada)
            Column {
                GLOWS.forEachIndexed { index, (message, kind) ->
                    val colour =
                        if (index ==
                            0
                        ) {
                            IdejoTheme.colors.dotMention
                        } else {
                            IdejoTheme.colors.dotRoleMention
                        }
                    MessageItem(
                        message,
                        context,
                        Modifier.glow(colour) { 1f },
                        pulse = MentionPulse({ it.namesMember(ada) }, colour) { 1f },
                    )
                    if (kind) HorizontalDivider()
                }
            }
        }

    @Test
    fun `TV-01 a topic the member may not view`() =
        snapshots("TopicGone") {
            TopicScreen(TopicUi(TopicState(loading = false, gone = true)), TopicActions())
        }

    @Test
    fun `TV-22 a topic link's card, followed`() =
        snapshots("CardTopic") {
            val topic =
                TOPIC.copy(
                    author = LEO,
                    messagesCount = 12,
                    lastActivityAt = NOW,
                    followed = true,
                )
            val opening = "Planning this year's **beds**: three of tomatoes, one of beans."
            InlineCardScreen(
                InlineCardUi(
                    InlineCard.Topic(topic, VEG, opening),
                    loading = false,
                    signedIn = true,
                ),
                InlineCardActions(),
            )
        }

    @Test
    fun `TV-22 a category link's card`() =
        snapshots("CardCategory") {
            InlineCardScreen(
                InlineCardUi(InlineCard.Category(VEG, listOf(TOPIC)), loading = false),
                InlineCardActions(),
            )
        }

    @Test
    fun `TV-22 a tag link's card`() =
        snapshots("CardTag") {
            val card = InlineCard.Tag(Tag("g_seeds", "Seeds", "seeds"), listOf(VEG), emptyList())
            InlineCardScreen(InlineCardUi(card, loading = false), InlineCardActions())
        }

    @Test
    fun `TV-22 a role link's card, without Open`() =
        snapshots("CardRole") {
            val role =
                RoleCard("r_gardener", "Gardener", "gardener", "#3f7d3a", listOf(LEO, MINA), 2)
            InlineCardScreen(
                InlineCardUi(InlineCard.Role(role), loading = false),
                InlineCardActions(),
            )
        }

    @Test
    fun `TV-22 a forum link's card, Join and Open waiting for M4`() =
        snapshots("CardForum") {
            val card = InlineCard.Forum("Bees", "bees.example.org")
            InlineCardScreen(InlineCardUi(card, loading = false), InlineCardActions())
        }

    @Test
    fun `TV-25 the image viewer, with zoom, Share and Save`() =
        snapshots(
            "ImageViewer",
        ) { ImageViewerContent(ViewedImage("https://garden.example/u/a.png")) {} }

    private fun snapshots(
        name: String,
        content: @Composable () -> Unit,
    ) {
        for (dark in listOf(false, true)) {
            val mode = if (dark) "dark" else "light"
            captureRoboImage("src/test/screenshots/${name}_$mode.png", OPTIONS) {
                IdejoTheme(GARDEN, dark) {
                    Surface(color = MaterialTheme.colorScheme.background) { content() }
                }
            }
        }
    }

    private companion object {
        val GARDEN = ForumTheme.fromHex("#3f7d3a", "#8a6a1f", "#2c6a8e", "#f7faf2", "#0b1410")!!
        val OPTIONS =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
            )
        val NOW: Instant = Instant.now()
        val LEO = Person("u_leo", "Leo Moreau", colour = "#2c6a8e")
        val MINA =
            Person(
                "u_mina",
                "Mina Kader",
                userTag = "she/her",
                role = Role("Moderator", colour = "#6a3e8e"),
                colour = "#8a6a1f",
            )
        val ADA = Person("u_ada", "Ada Lovelace", ignoredByMe = true)
        val VEG =
            Category(
                "c_veg",
                "Vegetables",
                "vegetables",
                colour = "#8a6a1f",
                description = "Beds, seeds and what grows in them.",
                topicsCount = 4,
            )
        val TOPIC =
            Topic(
                "t_1",
                "Tomato plans for spring",
                categoryId = "c_veg",
                tags = listOf(Tag("g_seeds", "Seeds", "seeds")),
            )

        fun message(
            id: String,
            author: Person,
            body: String?,
            minutes: Long,
        ) = Message(
            id,
            "t_1",
            author,
            state = MessageState.Closed,
            bodyMd = body,
            openedAt = NOW.minusSeconds(minutes * 60),
        )

        val EXTRAS =
            listOf(
                message("e1", LEO, "Which twine do you use?", 40).copy(
                    repliesTo =
                        listOf(
                            ReplyTo(
                                "m1",
                                "Mina Kader",
                                "Sungold every year. Pinch the suckers early.",
                            ),
                        ),
                    continuedIn = listOf(Continuation("t_9", "Garden twine")),
                    movedAfter = listOf(MovedAway("t_8", "Off topic: bees", 3, newTopic = true)),
                ),
                message("e2", MINA, "Look at this one.", 30).copy(
                    forwardedFrom =
                        Forwarded(
                            "m9",
                            visible = true,
                            authorName = "Ada Lovelace",
                            bodyMd = "Jute is **soft** on the stems.",
                            topicId = "t_7",
                            topicTitle = "Tools we love",
                        ),
                    movedAfter = listOf(MovedAway("t_6", "Weather talk", 1)),
                ),
                message("e3", LEO, "Which first?", 20).copy(
                    poll =
                        Poll(
                            "p_1",
                            "Which first?",
                            closesAt = NOW.plusSeconds(3_600),
                            resultsShown = true,
                            total = 5,
                            myVotes = listOf("o_1"),
                            options =
                                listOf(
                                    PollOption("o_1", "Sungold", colour = "#c9a227", count = 3),
                                    PollOption("o_2", "Brandywine", count = 2),
                                    PollOption("o_3", "Cherokee", count = 0),
                                ),
                        ),
                    scheduled = true,
                ),
                message("e4", MINA, "Pick any.", 10).copy(
                    poll =
                        Poll(
                            "p_2",
                            "Which beds this year?",
                            multiple = true,
                            hiddenUntilClosed = true,
                            closesAt = NOW.plusSeconds(7_200),
                            options = (1..6).map { PollOption("b_$it", "Bed number $it") },
                        ),
                ),
            )
        val OPENING =
            message(
                "m0",
                LEO,
                "Planning this year's beds. **Cherokee Purple** and Sungold first.",
                600,
            )
        val GLOWS =
            listOf(
                message("g1", MINA, "@ada can you check the seed tray?", 6).copy(
                    mentions = listOf(Mention("user", "@Ada Lovelace", "@ada", id = "u_ada")),
                ) to true,
                message("g2", LEO, "+gardener the frost is coming tonight.", 3).copy(
                    mentions =
                        listOf(
                            Mention(
                                "role",
                                "+Gardener",
                                "+gardener",
                                id = "gardener",
                                slug = "gardener",
                            ),
                        ),
                ) to false,
            )

        val MESSAGES =
            listOf(
                message("m1", MINA, "Sungold every year. Pinch the suckers early.", 30).copy(
                    reactions = listOf(Reaction("tomato", "tomato", 3, char = "🍅", mine = true)),
                ),
                message("m2", ADA, "Which twine do you use?", 20),
                message(
                    "m3",
                    LEO,
                    null,
                    12,
                ).copy(state = MessageState.Deleted, deletedBy = "author"),
                message("m4", MINA, "The ending of the book: ||they plant it all||.", 8).copy(
                    spoiler = Spoiler("Book 3"),
                ),
                message("m5", LEO, "Jute, soft on the stems.", 2).copy(moderatorEdited = true),
            )
    }
}
