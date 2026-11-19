// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert

/** One answer of a forum, as last read (OF-01): its JSON, when read, when last shown. */
@Entity(tableName = "pages", primaryKeys = ["origin", "key"])
data class CachedPage(
    val origin: String,
    /** What was asked: `categories`, `topic:t_1`, `unread`… */
    val key: String,
    val json: String,
    val savedAt: Long,
    val usedAt: Long,
    val bytes: Long,
)

@Dao
interface PageDao {
    @Query("SELECT * FROM pages WHERE origin = :origin AND `key` = :key")
    suspend fun get(
        origin: String,
        key: String,
    ): CachedPage?

    @Upsert
    suspend fun put(page: CachedPage)

    @Query("UPDATE pages SET usedAt = :at WHERE origin = :origin AND `key` = :key")
    suspend fun touch(
        origin: String,
        key: String,
        at: Long,
    )

    @Query("DELETE FROM pages WHERE origin = :origin")
    suspend fun forget(origin: String)

    @Query("DELETE FROM pages")
    suspend fun clear()

    @Query("SELECT COALESCE(SUM(bytes), 0) FROM pages")
    suspend fun bytes(): Long

    @Query("SELECT origin, `key` FROM pages ORDER BY usedAt ASC LIMIT :count")
    suspend fun oldest(count: Int): List<PageKey>

    @Query(
        "SELECT `key` FROM pages WHERE origin = :origin AND `key` LIKE 'topic:%' " +
            "ORDER BY usedAt DESC LIMIT -1 OFFSET :keep",
    )
    suspend fun topicsBeyond(
        origin: String,
        keep: Int,
    ): List<String>

    @Query("DELETE FROM pages WHERE origin = :origin AND `key` = :key")
    suspend fun remove(
        origin: String,
        key: String,
    )
}

data class PageKey(
    val origin: String,
    val key: String,
)

@Database(entities = [CachedPage::class], version = 1, exportSchema = false)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun pages(): PageDao
}

/**
 * The forums' last answers, for the pages to show offline (OF-01): the server and its theme,
 * categories, the sidebar's lists, Unread, the last [TOPICS] topics opened with their window,
 * the users seen. Kept within [limitBytes] (App settings' storage, ST-21), the least
 * recently shown first out. Pictures are Coil's own disk cache.
 */
class PageCache(
    private val dao: PageDao,
    private val limitBytes: suspend () -> Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun get(
        origin: String,
        key: String,
    ): CachedPage? = dao.get(origin, key)?.also { dao.touch(origin, key, clock()) }

    suspend fun put(
        origin: String,
        key: String,
        json: String,
    ) {
        val now = clock()
        dao.put(CachedPage(origin, key, json, now, now, json.length.toLong() * 2))
        if (key.startsWith(TOPIC)) {
            dao.topicsBeyond(origin, TOPICS).forEach { topic ->
                dao.remove(origin, topic)
                dao.remove(origin, WINDOW + topic.removePrefix(TOPIC))
            }
        }
        trim()
    }

    /** Every answer of [origin]: the app no longer holds that forum. */
    suspend fun forget(origin: String) = dao.forget(origin)

    suspend fun clear() = dao.clear()

    suspend fun bytes(): Long = dao.bytes()

    private suspend fun trim() {
        val limit = limitBytes()
        while (dao.bytes() > limit) {
            val oldest = dao.oldest(1)
            if (oldest.isEmpty()) return
            oldest.forEach { dao.remove(it.origin, it.key) }
        }
    }

    companion object {
        /** Topics kept per forum, with their windows (OF-01). */
        const val TOPICS = 20
        const val TOPIC = "topic:"

        /** A topic's window of messages, kept and dropped with its topic. */
        const val WINDOW = "window:"

        fun database(context: Context): CacheDatabase =
            Room.databaseBuilder(context, CacheDatabase::class.java, "cache.db").build()
    }
}
