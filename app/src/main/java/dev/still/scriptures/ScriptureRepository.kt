package dev.still.scriptures

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

class ScriptureRepository(context: Context) {
    private val assets = context.applicationContext.assets
    private var cachedId: String? = null
    private var cachedChapters: JSONArray? = null

    suspend fun catalog(): List<Book> = withContext(Dispatchers.IO) {
        val array = JSONArray(assets.open("scriptures/catalog.json").bufferedReader().use { it.readText() })
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val counts = item.getJSONArray("verseCounts")
            Book(item.getString("id"), item.getString("name"), item.getString("volume"),
                List(counts.length()) { counts.getInt(it) })
        }
    }

    suspend fun chapter(book: Book, number: Int): Chapter = withContext(Dispatchers.IO) {
        val chapters = if (cachedId == book.id) cachedChapters!! else {
            JSONArray(assets.open("scriptures/${book.id}.json").bufferedReader().use { it.readText() })
                .also { cachedId = book.id; cachedChapters = it }
        }
        val verses = chapters.getJSONArray(number - 1)
        Chapter(book, number, List(verses.length()) { verses.getString(it) })
    }
}
