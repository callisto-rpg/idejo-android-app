// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.browse

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.idejo.app.core.data.HomeState
import org.idejo.app.core.data.MemberCard
import org.idejo.app.core.data.PagesState
import org.idejo.app.core.data.TopicView
import org.idejo.app.core.data.UnreadState
import org.idejo.app.core.data.WelcomeState
import org.idejo.app.core.data.sections
import org.idejo.app.core.designsystem.theme.ForumTheme
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.model.Category
import org.idejo.app.core.model.CategoryGroup
import org.idejo.app.core.model.LatestMessage
import org.idejo.app.core.model.Me
import org.idejo.app.core.model.MentionState
import org.idejo.app.core.model.Person
import org.idejo.app.core.model.PluginInfo
import org.idejo.app.core.model.Role
import org.idejo.app.core.model.SearchHit
import org.idejo.app.core.model.Server
import org.idejo.app.core.model.ServerCommand
import org.idejo.app.core.model.Tag
import org.idejo.app.core.model.Topic
import org.idejo.app.core.model.TopicPreview
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/** The home and category pages in both themes; `./gradlew recordRoborazziDebug` records. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class BrowseScreenshotTest {
    @Test
    fun `BR-01 home, compact`() =
        snapshots("Home") { HomeScreen(home(TopicView.Compact), {}, {}, {}, {}, {}) }

    @Test
    fun `BR-01 BR-03 home, expanded`() =
        snapshots("HomeExpanded") { HomeScreen(home(TopicView.Expanded), {}, {}, {}, {}, {}) }

    @Test
    fun `BR-02 category page`() =
        snapshots("Category") {
            CategoryScreen(
                CategoryUi(
                    category = VEGETABLES,
                    topics = PagesState(TOPICS, loading = false),
                    view = TopicView.Compact,
                ),
                {},
                {},
                {},
                {},
                {},
            )
        }

    @Test
    fun `BR-04 Filter by tag`() =
        snapshots("Tags") {
            TagsScreen(
                TagsUi(
                    tags =
                        listOf(
                            SEEDS,
                            Tag("g_indoor", "Indoor", "indoor"),
                            Tag("g_trees", "Trees", "trees"),
                        ),
                    tagsLoading = false,
                    choice = TagChoice(setOf("seeds", "indoor"), any = true),
                    shown = TagChoice(setOf("seeds", "indoor"), any = true),
                    topics = PagesState(TOPICS.take(2), loading = false, end = true),
                    categories = listOf(VEGETABLES, FRUIT),
                ),
                TagsActions({}, {}, {}, {}, {}, {}),
            )
        }

    @Test
    fun `BR-05 search`() =
        snapshots("Search") {
            SearchScreen(
                SearchUi(
                    query = SearchQuery("sungold"),
                    searched = SearchQuery("sungold"),
                    hits = PagesState(HITS, loading = false, end = true),
                    categories = listOf(VEGETABLES, FRUIT),
                ),
                SearchActions({}, {}, {}, { _, _ -> }, {}, {}),
            )
        }

    @Test
    fun `BR-06 unread`() =
        snapshots("Unread") {
            UnreadScreen(
                UnreadUi(
                    UnreadState(
                        following = TOPICS.take(1),
                        others = PagesState(TOPICS.drop(1).take(1), loading = false, end = true),
                    ),
                    categories = listOf(VEGETABLES, FRUIT),
                ),
                {},
                {},
                {},
                {},
            )
        }

    @Test
    fun `BR-07 Rules & Roles`() =
        snapshots("Rules") {
            RulesScreen(WELCOME.copy(saved = true), RulesActions({}, {}, {}), {})
        }

    @Test
    fun `BR-07 the welcome card`() =
        snapshots("WelcomeCard") {
            MemberCardContent(
                MemberCard.Welcome,
                WELCOME,
                "Garden club",
                RulesActions({}, {}, {}),
                {},
            )
        }

    @Test
    fun `BR-11 Getting started`() =
        snapshots("GettingStarted") {
            MemberCardContent(
                MemberCard.GettingStarted,
                WELCOME,
                "Garden club",
                RulesActions({}, {}, {}),
                {},
            )
        }

    @Test
    fun `BR-08 Help`() = snapshots("Help", tall = true) { HelpScreen(SERVER) }

    private fun home(view: TopicView) =
        HomeUi(
            home =
                HomeState(
                    sections = sections(listOf(VEGETABLES, FRUIT, CHAT), emptyList()),
                    active = TOPICS,
                    expanded = view == TopicView.Expanded,
                    loading = false,
                ),
            view = view,
        )

    private fun snapshots(
        name: String,
        tall: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        if (tall) RuntimeEnvironment.setQualifiers("w390dp-h3200dp-xhdpi")
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
        val SERVER =
            Server(
                name = "Garden club",
                url = "https://garden.example.org/",
                aboutMd = "A club for **everyone** who grows things, from balconies to allotments.",
                rulesMd = "1. Be kind.\n2. No selling.\n3. Photos of *your own* garden only.",
                roles =
                    listOf(
                        Role("Gardener", id = "r_gardener", colour = "#3f7d3a"),
                        Role("Beekeeper", id = "r_bees", colour = "#b07a00"),
                    ),
                commands =
                    listOf(
                        ServerCommand(
                            "image",
                            "Add an image",
                            "Opens your files. The picture you choose goes at the end of your message, not sent yet.",
                        ),
                        ServerCommand(
                            "poll",
                            "Add a poll",
                            "Sends what you wrote, then asks for a question and its answers.",
                        ),
                    ),
                plugins = listOf(PluginInfo("dice", help = "Rolls dice: !dice 2d6.")),
            )
        val WELCOME =
            WelcomeState(
                server = SERVER,
                me = Me(id = "u_ada", displayName = "Ada Lovelace"),
                chosen = setOf("r_bees"),
                loading = false,
            )
        val SEEDS = Tag("g_seeds", "Seeds", "seeds")
        val HITS =
            listOf(
                SearchHit(
                    "m1",
                    "t_1",
                    "Tomato plans for spring",
                    categoryId = "c_veg",
                    authorName = "Mina Kader",
                    openedAt = NOW.minusSeconds(600),
                    excerpt =
                        listOf(
                            SearchHit.Segment("Sungold", match = true),
                            SearchHit.Segment(" every year. Pinch the suckers early."),
                        ),
                ),
                SearchHit(
                    "m2",
                    "t_4",
                    "Cherry tomatoes on a balcony",
                    categoryId = "c_veg",
                    authorName = "Leo Moreau",
                    openedAt = NOW.minusSeconds(86_400 * 2),
                    excerpt =
                        listOf(
                            SearchHit.Segment("Two pots: one "),
                            SearchHit.Segment("Sungold", match = true),
                            SearchHit.Segment(", one Black Cherry, both on the south rail."),
                        ),
                ),
            )
        val GARDENING = CategoryGroup("g1", "Gardening", 1)
        val VEGETABLES =
            Category(
                "c_veg",
                "Vegetables",
                "vegetables",
                description = "Beds, seeds and everything that ends up in a salad.",
                colour = "#3f7d3a",
                group = GARDENING,
                topicsCount = 42,
                lastActivityAt = NOW.minusSeconds(300),
            )
        val FRUIT =
            Category(
                "c_fruit",
                "Fruit trees",
                "fruit-trees",
                colour = "#c2410c",
                group = GARDENING,
                position = 2,
                topicsCount = 7,
                lastActivityAt = NOW.minusSeconds(86_400 * 3),
            )
        val CHAT = Category("c_chat", "Chat", "chat", topicsCount = 1)
        val LEO = Person("u_leo", "Leo Moreau", colour = "#2c6a8e")
        val MINA = Person("u_mina", "Mina Kader", colour = "#8a6a1f")
        val TOPICS =
            listOf(
                Topic(
                    "t_1",
                    "Tomato plans for spring",
                    categoryId = "c_veg",
                    author = LEO,
                    tags = listOf(SEEDS),
                    lastActivityAt = NOW.minusSeconds(120),
                    messagesCount = 24,
                    unreadCount = 3,
                    mentionState = MentionState.Reply,
                    pinned = true,
                    preview =
                        TopicPreview(
                            firstExcerpt =
                                "Planning this year's beds. Cherokee Purple and Sungold first.",
                            latest =
                                listOf(
                                    LatestMessage(
                                        "m1",
                                        "Mina",
                                        "Sungold every year. Pinch the suckers early.",
                                    ),
                                    LatestMessage(
                                        "m2",
                                        "Ada",
                                        "Which twine do you use for staking?",
                                    ),
                                ),
                            writers = listOf(LEO, MINA),
                            writersCount = 2,
                        ),
                ),
                Topic(
                    "t_2",
                    "Apple tree pruning in winter",
                    categoryId = "c_fruit",
                    author = MINA,
                    lastActivityAt = NOW.minusSeconds(7_200),
                    messagesCount = 5,
                    preview = TopicPreview(firstExcerpt = "When do you prune yours?"),
                ),
                Topic(
                    "t_3",
                    "Old seed swap",
                    categoryId = "c_veg",
                    author = LEO,
                    lastActivityAt = NOW.minusSeconds(86_400 * 5),
                    messagesCount = 2,
                    inactive = true,
                ),
            )
    }
}
