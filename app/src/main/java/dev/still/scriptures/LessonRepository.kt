package dev.still.scriptures

import android.content.Context
import android.net.ConnectivityManager
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
import java.net.URL
import java.time.LocalDate

class LessonRepository(private val context: Context) {
    private val directory = File(context.filesDir, "lessons").apply { mkdirs() }
    private val mutex = Mutex()
    private val preferences = context.getSharedPreferences("lesson-updates", 0)
    private var highlights: List<LessonVerse> = emptyList()
    private var bundled: List<Lesson> = emptyList()

    suspend fun load(): List<Lesson> = withContext(Dispatchers.IO) {
        bundled = decode(context.assets.open("lessons/calendar.json").bufferedReader().use { it.readText() })
        highlights = decodeVerses(JSONArray(context.assets.open("lessons/highlights.json").bufferedReader().use { it.readText() }))
        merged()
    }
    private fun merged(): List<Lesson> {
        val cached = directory.listFiles()?.filter { it.name.matches(Regex("calendar-\\d{4}\\.json")) }
            ?.flatMap { runCatching { decode(it.readText()) }.getOrDefault(emptyList()) } ?: emptyList()
        return (bundled + cached).associateBy { it.id }.values.sortedBy { it.start }
    }
    fun automaticRefreshDue(year: Int): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        if (!capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) || connectivity.isActiveNetworkMetered) return false
        return System.currentTimeMillis() - preferences.getLong("checked-$year", 0L) > 7L * 24 * 60 * 60 * 1000
    }
    fun lastChecked(year: Int): Long = preferences.getLong("checked-$year", 0L)

    suspend fun refresh(year: Int, books: List<Book>): List<Lesson> = withContext(Dispatchers.IO) {
        mutex.withLock {
            // Limit retries even if a publisher/network request fails. Manual refresh remains available.
            preferences.edit().putLong("checked-$year", System.currentTimeMillis()).apply()
            val cycle = listOf("old-testament", "new-testament", "book-of-mormon", "doctrine-and-covenants")
            val volume = cycle[Math.floorMod(year - 2026, 4)]
            val url = "https://www.churchofjesuschrist.org/study/manual/come-follow-me-for-home-and-church-$volume-$year?lang=eng"
            val parsed = parseCalendar(download(url), year, books, bundled + merged(), highlights)
            require(parsed.size in 50..54) { "The published calendar could not be read." }
            val file = AtomicFile(File(directory, "calendar-$year.json"))
            val stream = file.startWrite()
            try { stream.write(encode(parsed).toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
            catch (error: Exception) { file.failWrite(stream); throw error }
            merged()
        }
    }

    suspend fun lessonText(lesson: Lesson, force: Boolean = false): List<LessonBlock> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = File(directory, "text-${lesson.id}.json")
            if (!force && file.exists()) {
                val cached = runCatching { decodeBlocks(file.readText()) }.getOrNull()
                if (!cached.isNullOrEmpty()) { file.setLastModified(System.currentTimeMillis()); return@withLock cached }
            }
            val blocks = parseLessonText(download(lesson.url))
            require(blocks.isNotEmpty()) { "The official lesson could not be read." }
            val atomic = AtomicFile(file)
            val stream = atomic.startWrite()
            try {
                stream.write(JSONArray().apply { blocks.forEach { put(JSONObject().put("heading", it.heading).put("text", it.text)) } }.toString().toByteArray(Charsets.UTF_8))
                atomic.finishWrite(stream)
            } catch (error: Exception) { atomic.failWrite(stream); throw error }
            // Cache eight visited lessons. Verse text and calendar assets are retained independently.
            directory.listFiles()?.filter { it.name.startsWith("text-") && it.extension == "json" }
                ?.sortedByDescending { it.lastModified() }?.drop(8)?.forEach { it.delete() }
            blocks
        }
    }

    private fun download(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("User-Agent", "StillScriptures/0.2 (personal Wear OS scripture study)")
        try {
            require(connection.responseCode in 200..299) { "Unable to reach the official lesson source." }
            require(connection.contentLengthLong <= 2_000_000) { "The lesson download was too large." }
            val deadline = android.os.SystemClock.elapsedRealtime() + 20_000
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    require(android.os.SystemClock.elapsedRealtime() < deadline) { "The lesson download timed out." }
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 2_000_000) { "The lesson download was too large." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            return bytes.toString(Charsets.UTF_8)
        } finally { connection.disconnect() }
    }
    private fun decodeBlocks(text: String): List<LessonBlock> {
        val array = JSONArray(text)
        return List(array.length()) { array.getJSONObject(it).let { b -> LessonBlock(b.getBoolean("heading"), b.getString("text")) } }
    }
    private fun decodeVerses(array: JSONArray): List<LessonVerse> = List(array.length()) {
        array.getJSONObject(it).let { v -> LessonVerse(v.getString("bookId"), v.getInt("chapter"), v.getInt("verse")) }
    }
    private fun decode(text: String): List<Lesson> {
        val array = JSONArray(text)
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val groups = item.getJSONArray("groups")
            Lesson(item.getString("id"), item.getInt("year"), LocalDate.parse(item.getString("start")),
                LocalDate.parse(item.getString("end")), item.getString("title"), item.getString("url"),
                item.getBoolean("supporting"), List(groups.length()) {
                    val g = groups.getJSONObject(it); val c = g.getJSONArray("chapters")
                    ReadingGroup(g.getString("bookId"), List(c.length()) { n -> c.getInt(n) })
                }, decodeVerses(item.getJSONArray("highlights")))
        }
    }
    private fun encode(lessons: List<Lesson>): String = JSONArray().apply {
        lessons.forEach { lesson -> put(JSONObject().apply {
            put("id", lesson.id); put("year", lesson.year); put("start", lesson.start); put("end", lesson.end)
            put("title", lesson.title); put("url", lesson.url); put("supporting", lesson.supporting)
            put("groups", JSONArray().apply { lesson.groups.forEach { put(JSONObject().put("bookId", it.bookId).put("chapters", JSONArray(it.chapters))) } })
            put("highlights", JSONArray().apply { lesson.highlights.forEach { put(JSONObject().put("bookId", it.bookId).put("chapter", it.chapter).put("verse", it.verse)) } })
        }) }
    }.toString()
}

fun parseCalendar(html: String, year: Int, books: List<Book>, known: List<Lesson>, curated: List<LessonVerse>): List<Lesson> {
    val rows = Jsoup.parse(html, "https://www.churchofjesuschrist.org").select("li[data-date-start][data-date-end]")
    val parsed = rows.mapIndexed { index, row ->
        val title = row.selectFirst(".title")?.text()?.replace('\uFFFD', '–') ?: error("Missing lesson title")
        val start = LocalDate.parse(row.attr("data-date-start")); val end = LocalDate.parse(row.attr("data-date-end"))
        require(end == start.plusDays(6) && start.year in (year - 1)..year && end.year in year..(year + 1))
        val id = "$year-${(index + 1).toString().padStart(2, '0')}"
        val original = known.firstOrNull { it.id == id }
        var groups = parseAssignments(title, books)
        val supporting = groups.isEmpty()
        if (supporting) groups = original?.groups ?: emptyList()
        val pool = (original?.highlights ?: curated).filter { v -> groups.any { it.bookId == v.bookId && v.chapter in it.chapters } }
        val url = row.selectFirst("a[href]")?.absUrl("href") ?: error("Missing lesson link")
        require(URL(url).protocol == "https" && URL(url).host == "www.churchofjesuschrist.org")
        Lesson(id, year, start, end, title, url, supporting, groups, pool)
    }
    parsed.zipWithNext().forEach { (a, b) -> require(b.start == a.end.plusDays(1)) }
    return parsed
}

fun parseLessonText(html: String): List<LessonBlock> {
    val document = Jsoup.parse(html)
    val body = document.selectFirst("article .body-block") ?: return emptyList()
    body.select("script, style, figure, aside, sup").remove()
    return body.select("h2,h3,h4,p,li").filter { element ->
        // List items often contain paragraph children; render those children once.
        element.tagName() != "li" || element.select("p").isEmpty()
    }.mapNotNull {
        val text = it.text().trim()
        if (text.isBlank()) null else LessonBlock(it.tagName().startsWith("h"), text)
    }
}
