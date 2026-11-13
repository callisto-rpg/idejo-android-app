// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.api

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

/** A payload from src/test/resources/payloads. */
internal fun payload(name: String): String =
    requireNotNull(object {}.javaClass.getResource("/payloads/$name")) { name }.readText()

internal fun json(
    body: String,
    code: Int = 200,
    vararg headers: Pair<String, String>,
): MockResponse =
    MockResponse
        .Builder()
        .code(code)
        .addHeader("Content-Type", "application/json")
        .apply { headers.forEach { (name, value) -> addHeader(name, value) } }
        .body(body)
        .build()

internal fun error(
    code: Int,
    errorCode: String,
    message: String = "",
    details: String = "{}",
    vararg headers: Pair<String, String>,
): MockResponse =
    json(
        """{"error": {"code": "$errorCode", "message": "$message", "details": $details}}""",
        code,
        *headers,
    )

internal fun MockWebServer.address(): ForumAddress = ForumAddress.forTesting(url("/"))
