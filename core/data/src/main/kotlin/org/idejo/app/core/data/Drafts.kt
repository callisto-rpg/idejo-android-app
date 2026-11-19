// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The text typed and not sent yet (OF-03), kept on the device per forum and per place: a
 * topic's composer, or a category's new-topic form. Every change is written at once on the
 * store's own thread, before the next frame (NF-07), to a file renamed into place, so a
 * killed app keeps the last whole draft. Reads come from memory after the first.
 *
 * Files sit under `<dir>/<forum>/<place>`, named by SHA-256 so no address reaches a path; a
 * forum the app lets go of (`SessionManager.forgotten`) loses its folder.
 */
class Drafts(
    private val dir: File,
    private val writer: ExecutorService =
        Executors.newSingleThreadExecutor {
            Thread(
                it,
                "drafts",
            )
        },
) {
    private val known = ConcurrentHashMap<String, String>()

    /** The draft at [place] in [origin], or null. */
    fun get(
        origin: String,
        place: String,
    ): String? {
        val key = key(origin, place)
        known[key]?.let { return it.ifEmpty { null } }
        val file = file(origin, place)
        val text = if (file.isFile) file.readText() else ""
        known.putIfAbsent(key, text)
        return known.getValue(key).ifEmpty { null }
    }

    /** Keeps [text] as the draft at [place] in [origin]; blank removes it. */
    fun put(
        origin: String,
        place: String,
        text: String,
    ) {
        val kept = if (text.isBlank()) "" else text
        if (known.put(key(origin, place), kept) == kept) return
        val file = file(origin, place)
        writer.execute {
            if (kept.isEmpty()) {
                file.delete()
            } else {
                file.parentFile?.mkdirs()
                val next = File(file.parentFile, file.name + ".new")
                next.writeText(kept)
                if (!next.renameTo(file)) {
                    file.delete()
                    next.renameTo(file)
                }
            }
        }
    }

    /** Removes the draft at [place] in [origin]: sent, or given up. */
    fun clear(
        origin: String,
        place: String,
    ) = put(origin, place, "")

    /** Every draft of [origin] (OF-03): the app no longer holds that forum. */
    fun forget(origin: String) {
        val prefix = hash(origin) + "/"
        known.keys.removeAll { it.startsWith(prefix) }
        val folder = File(dir, hash(origin))
        writer.execute { folder.deleteRecursively() }
    }

    /** Waits for the writes asked so far: for tests. */
    fun settle() = writer.submit {}.get()

    private fun key(
        origin: String,
        place: String,
    ) = hash(origin) + "/" + hash(place)

    private fun file(
        origin: String,
        place: String,
    ) = File(File(dir, hash(origin)), hash(place))

    private fun hash(text: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }

    companion object {
        /** A topic's composer. */
        fun topic(id: String) = "topic:$id"

        /** The new-topic form, in a category or none chosen yet. */
        fun newTopic(categoryId: String?) = "new:" + categoryId.orEmpty()
    }
}
