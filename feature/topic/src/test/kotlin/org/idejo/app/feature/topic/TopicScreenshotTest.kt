// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.topic

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.idejo.app.core.data.TopicState
import org.idejo.app.core.designsystem.theme.ForumTheme
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.model.Message
import org.idejo.app.core.model.MessageState
import org.idejo.app.core.model.Person
import org.idejo.app.core.model.Reaction
import org.idejo.app.core.model.Role
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
    fun `TV-01 a topic the member may not view`() =
        snapshots("TopicGone") {
            TopicScreen(TopicUi(TopicState(loading = false, gone = true)), TopicActions())
        }

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

        val OPENING =
            message(
                "m0",
                LEO,
                "Planning this year's beds. **Cherokee Purple** and Sungold first.",
                600,
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
