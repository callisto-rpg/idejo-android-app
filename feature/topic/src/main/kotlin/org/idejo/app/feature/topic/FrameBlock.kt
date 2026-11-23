// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.topic

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.model.PluginBlock

/**
 * `video` and `iframe` (TV-26). A page (`iframe`) shows at once, as on the web (steward
 * request, 2026-11-23): its frame as tall as its code asked (the web's 416 by default), a
 * hairline round it, and under it **Open {host}**, which opens it outside the app. A video
 * stays a card naming where it comes from, which loads only on a tap. Both in a sandboxed
 * WebView ([SandboxedFrame]).
 */
@Composable
internal fun FrameBlock(
    block: PluginBlock,
    context: MessageContext,
) {
    val src = block.text("src")?.takeIf { it.startsWith("https://") } ?: return
    if (block.block == "video") VideoCard(block, src, context) else PageFrame(block, src, context)
}

/** `iframe`: the page itself, then its caption, as the web's. */
@Composable
private fun PageFrame(
    block: PluginBlock,
    src: String,
    context: MessageContext,
) {
    val host = hostOf(src)
    val height = (block.params["height"] as? JsonPrimitive)?.intOrNull ?: FRAME_HEIGHT
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth()) {
        SandboxedFrame(
            src,
            stringResource(R.string.frame_page_from, host),
            context.origin,
            context.onLink,
            Modifier
                .fillMaxWidth()
                .height(height.coerceIn(MIN_HEIGHT, MAX_HEIGHT).dp)
                .clip(shape)
                // The web's frame: a hairline and the text's colour at 2.5 % behind the page.
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = FRAME_TINT))
                .border(Dp.Hairline, MaterialTheme.colorScheme.onSurface.copy(alpha = LINE), shape),
        )
        TextButton(
            onClick = { context.onLink(src) },
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            Text(
                stringResource(R.string.frame_open, host),
                style = IdejoTheme.typography.labelMedium,
            )
        }
    }
}

/** `video`: a card naming where it comes from; only a tap loads it. */
@Composable
private fun VideoCard(
    block: PluginBlock,
    src: String,
    context: MessageContext,
) {
    val link = block.text("link") ?: src
    val host = hostOf(link)
    var loaded by rememberSaveable(src) { mutableStateOf(false) }
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val title = stringResource(R.string.frame_video_from, host)
            if (loaded) {
                SandboxedFrame(
                    src,
                    title,
                    context.origin,
                    context.onLink,
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(VIDEO_RATIO)
                        .clip(RoundedCornerShape(8.dp)),
                )
            } else {
                Text(title, style = IdejoTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.frame_loads_from, host),
                    style = IdejoTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { loaded = true }) { Text(stringResource(R.string.frame_play)) }
            }
            TextButton(onClick = { context.onLink(link) }) {
                Text(stringResource(R.string.frame_watch_on, host))
            }
        }
    }
}

/**
 * The page in a WebView, locked down as the web's sandboxed frame (TV-26; JavaScript on for
 * players, steward decision, 2026-11-14): no JavaScript bridge, no file or content access, no
 * new windows, third-party cookies off; DOM storage on, as a frame's; the forum's address as
 * its Referer. Whatever the member follows from it opens
 * outside the app, never in it.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SandboxedFrame(
    src: String,
    title: String,
    origin: String,
    onLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val open by rememberUpdatedState(onLink)
    AndroidView(
        factory = { android ->
            WebView(android).apply {
                // As big as its place: a WebView left to wrap its content takes the page's
                // height for its viewport, so a page laid out against the frame's height
                // (itch.io's widget, `position: absolute; top: 0; bottom: 0`) draws nothing.
                layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                contentDescription = title
                settings.apply {
                    javaScriptEnabled = true
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    allowFileAccess = false
                    allowContentAccess = false
                    setGeolocationEnabled(false)
                    mediaPlaybackRequiresUserGesture = true
                    // Embedded pages keep their state as in a browser's frame: many show
                    // nothing without it.
                    domStorageEnabled = true
                }
                // What the page leaves bare shows the frame behind it, as on the web.
                setBackgroundColor(Color.Transparent.toArgb())
                // A drag the page cannot scroll goes on to the topic (Compose's interop).
                isNestedScrollingEnabled = true
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                webViewClient = OutsideLinks { open(it) }
                // Players need to know who embeds them: YouTube refuses an embed without a
                // Referer (error 153). The forum's address, where the web shows it too.
                val referer = origin.trimEnd('/').takeIf { it.startsWith("https://") }
                loadUrl(src, referer?.let { mapOf("Referer" to "$it/") }.orEmpty())
            }
        },
        modifier = modifier.nestedScroll(rememberNestedScrollInteropConnection()),
        onRelease = { it.destroy() },
    )
}

/**
 * Keeps the frame on its own page: what the member taps opens outside the app (as any
 * link); what is not https never loads; the page's own redirects stay in the frame.
 */
private class OutsideLinks(
    private val open: (String) -> Unit,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        val url = request.url
        val web = url.scheme == "https" || url.scheme == "http"
        if (request.hasGesture() && web) open(url.toString())
        return request.hasGesture() || url.scheme != "https"
    }
}

private const val VIDEO_RATIO = 16f / 9f

/** The web's frame when its code asks for no height (26rem). */
private const val FRAME_HEIGHT = 416

/** The least a frame's code may ask for: a compact player's bar. */
private const val MIN_HEIGHT = 40
private const val MAX_HEIGHT = 800
private const val FRAME_TINT = 0.025f

/** The web's `--hairline`. */
private const val LINE = 0.09f
