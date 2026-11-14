// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET /server`: the forum's name, texts, roles, settings, emoji and plugins. */
@Serializable
data class Server(
    val name: String,
    val url: String,
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("about_md") val aboutMd: String? = null,
    @SerialName("rules_md") val rulesMd: String? = null,
    @SerialName("privacy_md") val privacyMd: String? = null,
    val roles: List<Role> = emptyList(),
    val settings: ServerSettings = ServerSettings(),
    val emoji: List<Emoji> = emptyList(),
    @SerialName("default_reactions") val defaultReactions: List<Emoji> = emptyList(),
    val plugins: List<PluginInfo> = emptyList(),
    /** Every built-in `!` command, for Help (IX-08); empty from older servers. */
    val commands: List<ServerCommand> = emptyList(),
    /** The forum's colours (TH-01, idejo IX-01); null until the server sends them. */
    val theme: ServerTheme? = null,
)

@Serializable
data class ServerSettings(
    @SerialName("gm_window_seconds") val windowSeconds: Int = 0,
    @SerialName("gm_unanswered_seconds") val unansweredSeconds: Int = 0,
    @SerialName("gm_early_reply") val earlyReply: Boolean = false,
    val gifs: Boolean = false,
    val push: Boolean = false,
    @SerialName("upload_types") val uploadTypes: List<String> = emptyList(),
    @SerialName("upload_max_bytes") val uploadMaxBytes: Long? = null,
    val registration: String? = null,
)

/** The five colours, `#rrggbb` or `#rrggbbaa`, each possibly missing or malformed (TH-01). */
@Serializable
data class ServerTheme(
    val primary: String? = null,
    val secondary: String? = null,
    val tertiary: String? = null,
    @SerialName("light_bg") val lightBackground: String? = null,
    @SerialName("dark_bg") val darkBackground: String? = null,
)

@Serializable
data class Emoji(
    val name: String,
    val char: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
)

@Serializable
data class PluginInfo(
    val command: String,
    val name: String? = null,
    val description: String? = null,
    val argument: String? = null,
    val block: Boolean = false,
    /** What the Help page says of it (IX-08). */
    val help: String? = null,
)

/** A built-in `!` command as the Help page words it (BR-08, IX-08). */
@Serializable
data class ServerCommand(
    val name: String,
    val detail: String? = null,
    val help: String? = null,
)
