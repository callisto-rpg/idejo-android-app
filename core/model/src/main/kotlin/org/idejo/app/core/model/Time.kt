// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** An API time: ISO 8601 in UTC (API guide §4). */
typealias Time =
    @Serializable(with = TimeSerializer::class)
    Instant

/** Reads ISO 8601 times, with or without an offset other than Z. */
object TimeSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("org.idejo.app.Time", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: Instant,
    ) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Instant {
        val text = decoder.decodeString()
        return try {
            Instant.parse(text)
        } catch (_: DateTimeParseException) {
            OffsetDateTime.parse(text).toInstant()
        }
    }
}
