package dev.still.scriptures

import android.content.Context
import org.json.JSONArray

class ReadingStore(context: Context) {
    private val preferences = context.getSharedPreferences("reading", Context.MODE_PRIVATE)
    fun savedVerses(): List<ReadingPosition> = runCatching {
        val array = JSONArray(preferences.getString("savedVerses", "[]"))
        List(array.length()) { array.optString(it) }.distinct().mapNotNull(::parseVerseKey)
    }.getOrDefault(emptyList())
    fun toggleSaved(position: ReadingPosition): List<ReadingPosition> {
        val key = verseKey(position)
        val old = savedVerses()
        val updated = if (old.any { verseKey(it) == key }) old.filterNot { verseKey(it) == key }
            else listOf(ReadingPosition(position.bookId, position.chapter, position.verse)) + old
        preferences.edit().putString("savedVerses", JSONArray(updated.map(::verseKey)).toString()).apply()
        return updated
    }
    fun position(): ReadingPosition? {
        val id = preferences.getString("book", null) ?: return null
        return ReadingPosition(id, preferences.getInt("chapter", 1), preferences.getInt("verse", 1),
            preferences.getInt("item", 1), preferences.getInt("offset", 0))
    }
    fun save(position: ReadingPosition) {
        preferences.edit().putString("lastKind", "scripture").putString("book", position.bookId).putInt("chapter", position.chapter)
            .putInt("verse", position.verse).putInt("item", position.itemIndex)
            .putInt("offset", position.offset).apply()
    }
    fun lastLessonId(): String? = if (preferences.getString("lastKind", "scripture") == "lesson") preferences.getString("lastLesson", null) else null
    fun lessonPosition(id: String): Pair<Int, Int> = if (preferences.getString("lastLesson", null) == id)
        preferences.getInt("lessonItem", 0).coerceAtLeast(0) to preferences.getInt("lessonOffset", 0).coerceAtLeast(0) else 0 to 0
    fun saveLesson(id: String, item: Int, offset: Int) {
        preferences.edit().putString("lastKind", "lesson").putString("lastLesson", id)
            .putInt("lessonItem", item).putInt("lessonOffset", offset).apply()
    }
    var textSize: Int
        get() = preferences.getInt("textSize", 18).coerceIn(16, 26)
        set(value) { preferences.edit().putInt("textSize", value.coerceIn(16, 26)).apply() }
    var keepAwake: Boolean
        get() = preferences.getBoolean("keepAwake", false)
        set(value) { preferences.edit().putBoolean("keepAwake", value).apply() }
}
