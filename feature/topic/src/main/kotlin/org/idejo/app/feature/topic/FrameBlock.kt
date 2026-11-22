// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.topic

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.model.PluginBlock

/**
 * `video` and `iframe` (TV-26): a card naming where the page comes from; only a tap loads it,
 * in a sandboxed WebView ([SandboxedFrame]). Its caption opens the page outside the app.
 */
@Composable
internal fun FrameBlock(
    block: PluginBlock,
    context: MessageContext,
) {
    val src = block.text("src")?.takeIf { it.startsWith("https://") } ?: return
    val video = block.block == "video"
    val link = (if (video) block.text("link") else null) ?: src
    val host = hostOf(link)
    var loaded by rememberSaveable(src) { mutableStateOf(false) }
    val size =
        if (video) {
            Modifier.aspectRatio(VIDEO_RATIO)
        } else {
            val height = (block.params["height"] as? JsonPrimitive)?.intOrNull ?: FRAME_HEIGHT
            Modifier.height(height.coerceIn(MIN_HEIGHT, MAX_HEIGHT).dp)
        }
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val title =
                stringResource(
                    if (video) R.string.frame_video_from else R.string.frame_page_from,
                    host,
                )
            if (loaded) {
                SandboxedFrame(
                    src,
                    title,
                    context.origin,
                    context.onLink,
                    Modifier.fillMaxWidth().then(size),
                )
            } else {
                Text(title, style = IdejoTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.frame_loads_from, host),
                    style = IdejoTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { loaded = true }) {
                    Text(stringResource(if (video) R.string.frame_play else R.string.frame_show))
                }
            }
            TextButton(onClick = { context.onLink(link) }) {
                Text(
                    stringResource(
                        if (video) R.string.frame_watch_on else R.string.frame_open,
                        host,
                    ),
                )
            }
        }
    }
}

/**
 * The page in a WebView, locked down (TV-26; JavaScript on for players, steward decision,
 * 2026-11-14): no JavaScript bridge, no file or content access, no new windows, third-party
 * cookies off; the forum's address as its Referer. Whatever the member follows from it opens
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
                contentDescription = title
                settings.apply {
                    javaScriptEnabled = true
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    allowFileAccess = false
                    allowContentAccess = false
                    setGeolocationEnabled(false)
                    mediaPlaybackRequiresUserGesture = true
                }
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                webViewClient = OutsideLinks { open(it) }
                // Players need to know who embeds them: YouTube refuses an embed without a
                // Referer (error 153). The forum's address, where the web shows it too.
                val referer = origin.trimEnd('/').takeIf { it.startsWith("https://") }
                loadUrl(src, referer?.let { mapOf("Referer" to "$it/") }.orEmpty())
            }
        },
        modifier = modifier.clip(RoundedCornerShape(8.dp)),
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
private const val FRAME_HEIGHT = 360
private const val MIN_HEIGHT = 120
private const val MAX_HEIGHT = 800
