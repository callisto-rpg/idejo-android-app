// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.markdown

import org.idejo.app.core.markdown.MdBlock.Paragraph
import org.idejo.app.core.markdown.MdInline.Text
import org.idejo.app.core.model.Mention
import org.junit.Assert.assertEquals
import org.junit.Test

class IdejoMarkdownTest {
    private fun inlines(
        markdown: String,
        mentions: List<Mention> = emptyList(),
    ) = (IdejoMarkdown.parse(markdown, mentions).blocks.single() as Paragraph).inlines

    @Test
    fun `TV-20 idejo's inline signs`() {
        assertEquals(
            listOf(MdInline.Highlight(listOf(Text("bright")))),
            inlines("==bright=="),
        )
        assertEquals(
            listOf(Text("x"), MdInline.Superscript(listOf(Text("2")))),
            inlines("x^2^"),
        )
        assertEquals(
            listOf(Text("a "), MdInline.Strike(listOf(Text("gone")))),
            inlines("a ~~gone~~"),
        )
        assertEquals(
            listOf(
                MdInline.Spoiler(0, listOf(Text("one"))),
                Text(" and "),
                MdInline.Spoiler(1, listOf(MdInline.Strong(listOf(Text("two"))))),
            ),
            inlines("||one|| and ||**two**||"),
        )
        assertEquals(2, IdejoMarkdown.parse("||one|| and ||two||").spoilerCount)
    }

    @Test
    fun `TV-20 a single sign is text`() {
        assertEquals(listOf(Text("a = b | c")), inlines("a = b | c"))
    }

    @Test
    fun `TV-20 small text at the start of a line`() {
        val blocks = IdejoMarkdown.parse("Hello\n\n-# said **quietly**").blocks
        assertEquals(
            MdBlock.Small(listOf(Text("said "), MdInline.Strong(listOf(Text("quietly"))))),
            blocks[1],
        )
    }

    @Test
    fun `TV-20 GitHub's blocks`() {
        val blocks =
            IdejoMarkdown
                .parse(
                    """
                    # Title

                    - [x] seeds
                    - [ ] water

                    3. three
                    4. four

                    > quoted

                    ---

                    | a | b |
                    |:-:|--:|
                    | 1 | 2 |
                    """.trimIndent(),
                ).blocks
        assertEquals(MdBlock.Heading(1, listOf(Text("Title"))), blocks[0])
        val tasks = blocks[1] as MdBlock.ListBlock
        assertEquals(listOf(true, false), tasks.items.map { it.checked })
        val numbered = blocks[2] as MdBlock.ListBlock
        assertEquals(true to 3, numbered.ordered to numbered.start)
        assertEquals(MdBlock.Quote(listOf(Paragraph(listOf(Text("quoted"))))), blocks[3])
        assertEquals(MdBlock.Rule, blocks[4])
        val table = blocks[5] as MdBlock.Table
        assertEquals(
            listOf(MdBlock.Alignment.Center, MdBlock.Alignment.End),
            table.header.map { it.alignment },
        )
        assertEquals(listOf(Text("2")), table.rows[0][1].inlines)
    }

    @Test
    fun `TV-20 bare links`() {
        assertEquals(
            listOf(
                Text("see "),
                MdInline.Link("https://idejo.org", listOf(Text("https://idejo.org"))),
            ),
            inlines("see https://idejo.org"),
        )
    }

    @Test
    fun `TV-20 raw HTML shows as typed`() {
        assertEquals(listOf(Text("<b>x</b>")), inlines("<b>x</b>"))
        assertEquals(
            Paragraph(listOf(Text("<div>hi</div>"))),
            IdejoMarkdown.parse("<div>hi</div>").blocks.single(),
        )
    }

    @Test
    fun `TV-20 emoji by name, not in code`() {
        assertEquals(
            listOf(Text("well "), MdInline.Emoji("tada"), Text(" "), MdInline.Code(":tada:")),
            inlines("well :tada: `:tada:`"),
        )
        assertEquals(setOf("tada", "agree"), IdejoMarkdown.parse(":tada: :agree:").emojiNames)
    }

    @Test
    fun `TV-21 inline links are placed from the message's mentions`() {
        val robin = Mention("user", "@robin", id = "u1", name = "robin")
        val robinB = Mention("user", "@robin_b", id = "u2", name = "robin_b")
        assertEquals(
            listOf(
                MdInline.InlineLink(robinB, "@robin_b"),
                Text(" and "),
                MdInline.InlineLink(robin, "@robin"),
                Text(", not @robinson or "),
                MdInline.Code("@robin"),
            ),
            inlines("@robin_b and @robin, not @robinson or `@robin`", listOf(robin, robinB)),
        )
    }

    @Test
    fun `TV-21 inline links stand where their source is written, with the words readers see`() {
        val topic =
            Mention(
                "topic",
                "#Garden plans",
                source = "#garden-plans",
                id = "t1",
                name = "Garden plans",
            )
        val role = Mention("role", "+Moderator", source = "+moderator", id = "moderator")
        val forum = Mention("forum", "&Garden club", source = "&garden", id = "garden.idejo.org")
        assertEquals(
            listOf(
                Text("See "),
                MdInline.InlineLink(topic, "#Garden plans"),
                Text(", "),
                MdInline.InlineLink(role, "+Moderator"),
                Text(" and "),
                MdInline.InlineLink(forum, "&Garden club"),
                Text(", not #garden-plans-2"),
            ),
            inlines(
                "See #Garden-Plans, +moderator and &garden, not #garden-plans-2",
                listOf(topic, role, forum),
            ),
        )
    }

    @Test
    fun `TV-21 no inline link inside a link`() {
        val robin = Mention("user", "@robin", id = "u1")
        assertEquals(
            listOf(MdInline.Link("/u/u1", listOf(Text("@robin")))),
            inlines("[@robin](/u/u1)", listOf(robin)),
        )
    }

    @Test
    fun `TV-21 images keep their address and words`() {
        assertEquals(
            listOf(MdInline.Image("/uploads/a.webp", "a rose")),
            inlines("![a rose](/uploads/a.webp)"),
        )
    }

    @Test
    fun `TV-24 code blocks keep their text`() {
        assertEquals(
            MdBlock.Code("val x = 1\n  indented"),
            IdejoMarkdown.parse("```kotlin\nval x = 1\n  indented\n```").blocks.single(),
        )
    }
}
