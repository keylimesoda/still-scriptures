package dev.still.scriptures

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class StudyLink(val label: String, val url: String)
data class StudyNote(val verse: Int, val marker: String, val word: String, val text: String, val links: List<StudyLink>)
data class ChapterStudy(val summary: String, val notes: List<StudyNote>, val source: String, val notices: String)

/** Official study helps are downloaded only on an explicit overview/footnotes request.
 * They remain in private app storage, never in the APK or public source assets. */
class StudyRepository(context: Context) {
    private val directory = File(context.filesDir, "chapter-study").apply { mkdirs() }
    internal val memory = androidx.compose.runtime.mutableStateMapOf<String, ChapterStudy>()
    private val mutex = Mutex()
    suspend fun cached(book: Book, chapter: Int): ChapterStudy? = withContext(Dispatchers.IO) {
        memory["${book.id}-$chapter"] ?: runCatching { decode(File(directory, "${book.id}-$chapter.json").readText()) }.getOrNull()?.also { memory["${book.id}-$chapter"] = it }
    }
    suspend fun get(book: Book, chapter: Int): ChapterStudy = withContext(Dispatchers.IO) {
        mutex.withLock {
            cached(book, chapter)?.let { return@withLock it }
            val source = scriptureLink(book, ReadingPosition(book.id, chapter, 1)).substringBefore("&id=")
            val document = Jsoup.parse(download(source), source)
            val article = document.selectFirst("article[data-content-type=chapter]") ?: error("Chapter format changed")
            require(article.select(".body-block .verse").size == book.verseCounts[chapter - 1]) { "Incomplete chapter response" }
            val summary = article.select("header .study-summary, header .section-summary, header .section-intro, header .study-intro")
                .joinToString("\n\n") { it.text() }
            val notes = article.select(".study-notes li[data-full-marker]").mapNotNull { element ->
                val id = element.id()
                val match = Regex("note(\\d+)_([a-z]+)").matchEntire(id) ?: return@mapNotNull null
                val verse = match.groupValues[1].toInt()
                if (verse !in 1..book.verseCounts[chapter - 1]) return@mapNotNull null
                val word = article.selectFirst(".body-block a[data-scroll-id=$id]")?.clone()?.also { it.select("sup").remove() }?.text().orEmpty()
                val text = element.select("p").joinToString("\n") { it.text() }
                val links = element.select("a[href]").mapNotNull { a ->
                    val url = a.absUrl("href")
                    if (url.startsWith("https://www.churchofjesuschrist.org/study/")) StudyLink(a.text(), url) else null
                }
                StudyNote(verse, match.groupValues[2], word, text, links)
            }
            // Preserve any rights notices present in the retrieved chapter as well as attribution.
            val notices = document.select("[class*=copyright], [id*=copyright]").map { it.text() }
                .filter { it.isNotBlank() }.distinct().joinToString("\n")
            require(summary.isNotBlank() || notes.isNotEmpty()) { "Study helps were not found" }
            val result = ChapterStudy(summary, notes, source, notices)
            val atomic = AtomicFile(File(directory, "${book.id}-$chapter.json"))
            val stream = atomic.startWrite()
            try { stream.write(encode(result).toByteArray(Charsets.UTF_8)); atomic.finishWrite(stream) }
            catch (error: Exception) { atomic.failWrite(stream); throw error }
            memory["${book.id}-$chapter"] = result
            result
        }
    }
    private fun download(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000; connection.readTimeout = 8_000
        connection.setRequestProperty("User-Agent", "StillScriptures/0.6 (personal scripture reading)")
        try {
            require(connection.responseCode in 200..299) { "Download failed" }
            val deadline = android.os.SystemClock.elapsedRealtime() + 20_000
            return connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    require(android.os.SystemClock.elapsedRealtime() < deadline) { "Download timed out" }
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 2_000_000) { "Chapter too large" }
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            }
        } finally { connection.disconnect() }
    }
    private fun encode(study: ChapterStudy): String = JSONObject().apply {
        put("summary", study.summary); put("source", study.source); put("notices", study.notices)
        put("notes", JSONArray().apply { study.notes.forEach { note -> put(JSONObject().apply {
            put("verse", note.verse); put("marker", note.marker); put("word", note.word); put("text", note.text)
            put("links", JSONArray().apply { note.links.forEach { put(JSONObject().put("label", it.label).put("url", it.url)) } })
        }) } })
    }.toString()
    private fun decode(text: String): ChapterStudy {
        val obj = JSONObject(text); val notes = obj.getJSONArray("notes")
        return ChapterStudy(obj.getString("summary"), List(notes.length()) { i ->
            val n = notes.getJSONObject(i); val links = n.getJSONArray("links")
            StudyNote(n.getInt("verse"), n.getString("marker"), n.getString("word"), n.getString("text"),
                List(links.length()) { links.getJSONObject(it).let { l -> StudyLink(l.getString("label"), l.getString("url")) } })
        }, obj.getString("source"), obj.optString("notices"))
    }
}

/** Only numbered, bundled scriptures open offline. Ranges open at their first verse.
 * Topical Guide, Bible Dictionary, JST and other links remain official phone links. */
fun studyPosition(url: String, books: List<Book>): ReadingPosition? = runCatching {
    val uri = URI(url)
    if (uri.host != "www.churchofjesuschrist.org" || uri.fragment == "study-helps") return null
    val parts = uri.path.trim('/').split('/')
    if (parts.size != 5 || parts.take(2) != listOf("study", "scriptures")) return null
    val book = books.firstOrNull { it.id == parts[3] } ?: return null
    val expected = URI(scriptureLink(book, ReadingPosition(book.id, 1, 1))).path.split('/')[3]
    if (parts[2] != expected) return null
    val chapter = parts[4].toIntOrNull() ?: return null
    val verse = Regex("(?:^|&)id=p(\\d+)").find(uri.rawQuery.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
        ?: Regex("p(\\d+)").find(uri.fragment.orEmpty())?.groupValues?.get(1)?.toIntOrNull() ?: 1
    ReadingPosition(book.id, chapter, verse).takeIf { validVerse(it, listOf(book)) }
}.getOrNull()
