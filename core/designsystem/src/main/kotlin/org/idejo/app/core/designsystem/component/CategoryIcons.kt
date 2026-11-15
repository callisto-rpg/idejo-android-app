// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.component

import android.content.res.AssetManager
import android.util.Xml
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.ConcurrentHashMap

/**
 * idejo's category icons (TH-05): the steward's drawings, CC-BY-4.0, bundled as idejo keeps
 * them (`assets/category-icons/<name>.svg`, from idejo's `graphics/category-icons`). Read as
 * idejo reads them: a 32 × 32 tile, then shapes in two marker colours, the tile's `#6567e7`
 * and the shapes' `#ffffff`, drawn here in the category's colour and white or near-black.
 * Only `rect`, `circle`, `ellipse`, `path` and `g` (with `rotate`) are drawn.
 */
@Immutable
class CategoryIcon internal constructor(
    private val name: String,
    private val shapes: List<IconNode>,
) {
    /** The drawing on [tile], its shapes in [ink]. */
    fun vector(
        tile: Color,
        ink: Color,
    ): ImageVector =
        ImageVector
            .Builder(name, GRID.dp, GRID.dp, GRID, GRID)
            .apply { shapes.forEach { add(it, tile, ink) } }
            .build()

    private fun ImageVector.Builder.add(
        node: IconNode,
        tile: Color,
        ink: Color,
    ) {
        when (node) {
            is IconNode.Group -> {
                addGroup(rotate = node.rotate, pivotX = node.pivotX, pivotY = node.pivotY)
                node.children.forEach { add(it, tile, ink) }
                clearGroup()
            }

            is IconNode.Shape -> {
                addPath(
                    node.nodes,
                    fill = node.style.fill?.let { SolidColor(it.color(tile, ink)) },
                    stroke = node.style.stroke?.let { SolidColor(it.color(tile, ink)) },
                    strokeLineWidth = node.style.strokeWidth,
                    strokeLineCap = node.style.cap,
                    strokeLineJoin = node.style.join,
                )
            }
        }
    }

    companion object {
        private val names = Regex("[a-z0-9-]{1,40}")
        private val cache = ConcurrentHashMap<String, Result<CategoryIcon>>()

        /** Icon [name] from [assets], or null when the app has no such drawing. */
        fun load(
            assets: AssetManager,
            name: String,
        ): CategoryIcon? {
            // The name comes from the server: only a plain name reaches the assets.
            if (!names.matches(name)) return null
            return cache
                .getOrPut(name) {
                    runCatching {
                        assets.open("category-icons/$name.svg").use {
                            CategoryIcon(name, IconReader.read(it.reader().readText()))
                        }
                    }
                }.getOrNull()
        }
    }
}

/** Icon [name]'s drawing, or null (no icon, or one this app does not have). */
@Composable
fun rememberCategoryIcon(name: String?): CategoryIcon? {
    val assets = LocalContext.current.assets
    return remember(name) { name?.let { CategoryIcon.load(assets, it) } }
}

/** One of the marker colours, or a colour of its own. */
internal sealed interface Paint {
    data object Tile : Paint

    data object Ink : Paint

    data class Own(
        val value: Color,
    ) : Paint

    fun color(
        tile: Color,
        ink: Color,
    ): Color =
        when (this) {
            Tile -> tile
            Ink -> ink
            is Own -> value
        }
}

/** What an element draws with, inherited from its groups as in SVG. */
internal data class Style(
    val fill: Paint? = Paint.Own(Color.Black),
    val stroke: Paint? = null,
    val strokeWidth: Float = 1f,
    val cap: StrokeCap = StrokeCap.Butt,
    val join: StrokeJoin = StrokeJoin.Miter,
)

internal sealed interface IconNode {
    data class Group(
        val rotate: Float,
        val pivotX: Float,
        val pivotY: Float,
        val children: List<IconNode>,
    ) : IconNode

    data class Shape(
        val nodes: List<PathNode>,
        val style: Style,
    ) : IconNode
}

/** The SVG's elements into [IconNode]s. */
internal object IconReader {
    fun read(svg: String): List<IconNode> {
        val parser = Xml.newPullParser()
        parser.setInput(svg.reader())
        val stack = ArrayDeque<Pair<MutableList<IconNode>, Style>>()
        val opened = ArrayDeque<IconNode.Group?>()
        stack.addLast(mutableListOf<IconNode>() to Style())
        val top = stack.first().first
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> start(parser, stack, opened)
                XmlPullParser.END_TAG -> end(parser.name, stack, opened)
            }
        }
        return top
    }

    private fun start(
        parser: XmlPullParser,
        stack: ArrayDeque<Pair<MutableList<IconNode>, Style>>,
        opened: ArrayDeque<IconNode.Group?>,
    ) {
        val attrs =
            (0 until parser.attributeCount).associate {
                parser.getAttributeName(it) to parser.getAttributeValue(it)
            }
        val (siblings, inherited) = stack.last()
        val style = style(attrs, inherited)
        if (parser.name == "g") {
            val (angle, x, y) = rotation(attrs["transform"])
            opened.addLast(IconNode.Group(angle, x, y, emptyList()))
            stack.addLast(mutableListOf<IconNode>() to style)
            return
        }
        opened.addLast(null)
        shape(parser.name, attrs)?.let { siblings += IconNode.Shape(it, style) }
    }

    private fun end(
        tag: String,
        stack: ArrayDeque<Pair<MutableList<IconNode>, Style>>,
        opened: ArrayDeque<IconNode.Group?>,
    ) {
        val group = opened.removeLastOrNull() ?: return
        if (tag != "g") return
        val (children, _) = stack.removeLast()
        stack.last().first += group.copy(children = children)
    }

    private fun style(
        attrs: Map<String, String>,
        inherited: Style,
    ) = Style(
        fill = attrs["fill"]?.let(::paint) ?: inherited.fill.takeIf { "fill" !in attrs },
        stroke = attrs["stroke"]?.let(::paint) ?: inherited.stroke.takeIf { "stroke" !in attrs },
        strokeWidth = attrs["stroke-width"]?.toFloatOrNull() ?: inherited.strokeWidth,
        cap =
            when (attrs["stroke-linecap"]) {
                "round" -> StrokeCap.Round
                "square" -> StrokeCap.Square
                "butt" -> StrokeCap.Butt
                else -> inherited.cap
            },
        join =
            when (attrs["stroke-linejoin"]) {
                "round" -> StrokeJoin.Round
                "bevel" -> StrokeJoin.Bevel
                "miter" -> StrokeJoin.Miter
                else -> inherited.join
            },
    )

    private fun paint(value: String): Paint? =
        when (value.trim().lowercase()) {
            "none" -> null
            TILE -> Paint.Tile
            INK -> Paint.Ink
            else -> hex(value)?.let { Paint.Own(it) }
        }

    private fun hex(value: String): Color? =
        Regex("#([0-9a-fA-F]{6})")
            .matchEntire(value.trim())
            ?.let { Color(OPAQUE or it.groupValues[1].toLong(HEX)) }

    private fun rotation(transform: String?): Triple<Float, Float, Float> {
        val numbers =
            transform
                ?.let { Regex("rotate\\(([^)]*)\\)").find(it)?.groupValues?.get(1) }
                ?.split(Regex("[ ,]+"))
                ?.mapNotNull { it.toFloatOrNull() }
                .orEmpty()
        return Triple(
            numbers.getOrElse(0) { 0f },
            numbers.getOrElse(1) { 0f },
            numbers.getOrElse(2) { 0f },
        )
    }

    private fun shape(
        tag: String,
        attrs: Map<String, String>,
    ): List<PathNode>? {
        fun number(name: String) = attrs[name]?.toFloatOrNull() ?: 0f
        return when (tag) {
            "path" -> {
                attrs["d"]?.let { addPathNodes(it) }
            }

            "rect" -> {
                val rx = attrs["rx"]?.toFloatOrNull() ?: attrs["ry"]?.toFloatOrNull() ?: 0f
                val ry = attrs["ry"]?.toFloatOrNull() ?: rx
                rect(number("x"), number("y"), number("width"), number("height"), rx, ry)
            }

            "circle" -> {
                ellipse(number("cx"), number("cy"), number("r"), number("r"))
            }

            "ellipse" -> {
                ellipse(number("cx"), number("cy"), number("rx"), number("ry"))
            }

            else -> {
                null
            }
        }
    }

    @Suppress("LongParameterList") // A rounded rectangle's six numbers, as in SVG.
    private fun rect(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        rx: Float,
        ry: Float,
    ): List<PathNode> {
        val a = rx.coerceIn(0f, w / 2)
        val b = ry.coerceIn(0f, h / 2)
        return PathBuilder()
            .apply {
                moveTo(x + a, y)
                horizontalLineTo(x + w - a)
                if (a > 0f) arcTo(a, b, 0f, false, true, x + w, y + b)
                verticalLineTo(y + h - b)
                if (a > 0f) arcTo(a, b, 0f, false, true, x + w - a, y + h)
                horizontalLineTo(x + a)
                if (a > 0f) arcTo(a, b, 0f, false, true, x, y + h - b)
                verticalLineTo(y + b)
                if (a > 0f) arcTo(a, b, 0f, false, true, x + a, y)
                close()
            }.nodes
    }

    private fun ellipse(
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
    ): List<PathNode> =
        PathBuilder()
            .apply {
                moveTo(cx - rx, cy)
                arcTo(rx, ry, 0f, false, true, cx + rx, cy)
                arcTo(rx, ry, 0f, false, true, cx - rx, cy)
                close()
            }.nodes
}

private const val GRID = 32f
private const val TILE = "#6567e7"
private const val INK = "#ffffff"
private const val HEX = 16
private const val OPAQUE = 0xFF000000

/** For tests: [IconReader] without the assets. */
internal fun readIcon(svg: String): List<IconNode> = IconReader.read(svg)
