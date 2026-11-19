// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What `GET /.well-known/idejo` says about a forum (API guide §1). Whether `api` is
 * missing or null matters (SI-02); `DiscoveryResult` in core/api tells them apart.
 */
@Serializable
data class Discovery(
    val name: String,
    val url: String,
    @SerialName("idejo_version") val idejoVersion: String? = null,
    @SerialName("logo_url") val logoUrl: String? = null,
    val api: ApiInfo? = null,
    val oauth: OAuthInfo? = null,
    val community: CommunityInfo? = null,
    val account: AccountLinks? = null,
    val features: List<String> = emptyList(),
    val limits: Limits? = null,
)

@Serializable
data class ApiInfo(
    val versions: List<String> = emptyList(),
    val base: String = "/api/v1",
    val openapi: String? = null,
    val socket: String = "/api/v1/socket",
)

@Serializable
data class OAuthInfo(
    val authorize: String,
    val token: String,
    val revoke: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("redirect_uri") val redirectUri: String,
    val scopes: List<String> = emptyList(),
    @SerialName("code_challenge_methods") val codeChallengeMethods: List<String> = emptyList(),
)

@Serializable
data class CommunityInfo(
    @SerialName("directory_url") val directoryUrl: String? = null,
    /**
     * The group's chat service, where members linked to idejo ID keep their private
     * conversations (IX-04, idejo 0.3.18), or null; [inboxUrl] is the inbox it belongs to.
     */
    @SerialName("chat_url") val chatUrl: String? = null,
    @SerialName("inbox_url") val inboxUrl: String? = null,
)

@Serializable
data class AccountLinks(
    @SerialName("deletion_url") val deletionUrl: String? = null,
    @SerialName("sign_up_url") val signUpUrl: String? = null,
)

@Serializable
data class Limits(
    @SerialName("page_size") val pageSize: Int = 100,
    @SerialName("requests_per_minute") val requestsPerMinute: Int? = null,
    @SerialName("upload_max_mb") val uploadMaxMb: Int? = null,
    @SerialName("devices_per_user") val devicesPerUser: Int? = null,
)

/** A forum of the same group the member joined (`GET /me/communities`, API guide §9). */
@Serializable
data class Community(
    val name: String,
    val url: String,
    @SerialName("logo_url") val logoUrl: String? = null,
)
