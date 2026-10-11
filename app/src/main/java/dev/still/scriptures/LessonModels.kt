package dev.still.scriptures

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class LessonVerse(val bookId: String, val chapter: Int, val verse: Int) {
    fun position() = ReadingPosition(bookId, chapter, verse)
}
data class ReadingGroup(val bookId: String, val chapters: List<Int>)
data class Lesson(
    val id: String, val year: Int, val start: LocalDate, val end: LocalDate,
    val title: String, val url: String, val supporting: Boolean,
    val groups: List<ReadingGroup>, val highlights: List<LessonVerse>,
)
data class LessonBlock(val heading: Boolean, val text: String)

fun lessonForDate(lessons: List<Lesson>, date: LocalDate): Lesson? =
    lessons.firstOrNull { date >= it.start && date <= it.end }

fun dailyVerse(lesson: Lesson, date: LocalDate): LessonVerse? {
    if (date < lesson.start || date > lesson.end || lesson.highlights.isEmpty()) return null
    return lesson.highlights[(ChronoUnit.DAYS.between(lesson.start, date) % lesson.highlights.size).toInt()]
}

private fun normalizeTitle(value: String): String = value.replace('\u00a0', ' ')
    .replace('\uFFFD', '-').replace('–', '-').replace('—', '-').replace(Regex("\\s+"), " ").trim()

fun parseAssignments(title: String, books: List<Book>): List<ReadingGroup> {
    val names = books.associateBy { normalizeTitle(it.name) }
    val groups = linkedMapOf<String, MutableList<Int>>()
    var current: Book? = null
    for (raw in normalizeTitle(title).split(';')) {
        var segment = raw.trim()
        val shared = Regex("(\\d)\\s*(?:and|-)\\s*(\\d)\\s+(John|Peter|Timothy|Thessalonians)").matchEntire(segment)
        if (shared != null) {
            for (number in shared.groupValues[1].toInt()..shared.groupValues[2].toInt()) {
                val book = names["$number ${shared.groupValues[3]}"] ?: error("Unknown book")
                groups.getOrPut(book.id) { mutableListOf() }.addAll(1..book.verseCounts.size)
            }
            current = null
            continue
        }
        val name = names.keys.sortedByDescending { it.length }.firstOrNull { segment == it || segment.startsWith("$it ") }
        if (name != null) { current = names.getValue(name); segment = segment.removePrefix(name).trim() }
        val book = current ?: return emptyList()
        val chapters = if (segment.isEmpty()) (1..book.verseCounts.size).toList() else segment.split(',').flatMap { part ->
            val match = Regex("(\\d+)(?:-(\\d+))?").matchEntire(part.trim()) ?: error("Unsupported chapter range")
            val start = match.groupValues[1].toInt()
            val endText = match.groupValues[2]
            var end = endText.toIntOrNull() ?: start
            if (end < start && endText.isNotEmpty()) end = (start.toString().dropLast(endText.length) + endText).toInt()
            require(end >= start && end <= book.verseCounts.size && start >= 1)
            (start..end).toList()
        }
        groups.getOrPut(book.id) { mutableListOf() }.addAll(chapters)
    }
    return groups.map { ReadingGroup(it.key, it.value.distinct()) }
}

fun groupLabel(group: ReadingGroup, books: List<Book>): String {
    val book = books.first { it.id == group.bookId }
    val runs = mutableListOf<String>()
    val chapters = group.chapters
    var index = 0
    while (index < chapters.size) {
        val first = chapters[index]
        var last = first
        while (index + 1 < chapters.size && chapters[index + 1] == last + 1) { index++; last++ }
        runs += if (first == last) "$first" else "$first–$last"
        index++
    }
    return book.name + " " + runs.joinToString(", ")
}
