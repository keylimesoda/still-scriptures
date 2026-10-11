package dev.still.scriptures

import kotlin.random.Random

fun verseKey(position: ReadingPosition): String = "${position.bookId}|${position.chapter}|${position.verse}"

fun parseVerseKey(key: String): ReadingPosition? {
    val parts = key.split('|')
    if (parts.size != 3) return null
    val chapter = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
    val verse = parts[2].toIntOrNull()?.takeIf { it > 0 } ?: return null
    return ReadingPosition(parts[0], chapter, verse)
}

fun validVerse(position: ReadingPosition, books: List<Book>): Boolean {
    val book = books.firstOrNull { it.id == position.bookId } ?: return false
    return position.chapter in 1..book.verseCounts.size && position.verse in 1..book.verseCounts[position.chapter - 1]
}

// Each bundled verse has the same chance. A small shuffled deck keeps watch memory bounded.
fun randomFlips(books: List<Book>, count: Int = 128, random: Random = Random.Default): List<ReadingPosition> {
    val total = books.sumOf { it.verseCounts.sum() }
    if (total == 0 || count <= 0) return emptyList()
    val slots = linkedSetOf<Int>()
    while (slots.size < count.coerceAtMost(total)) slots += random.nextInt(total)
    return slots.map { slot ->
        var remaining = slot
        var result: ReadingPosition? = null
        loop@ for (book in books) {
            for ((chapter, size) in book.verseCounts.withIndex()) {
                if (remaining < size) { result = ReadingPosition(book.id, chapter + 1, remaining + 1); break@loop }
                remaining -= size
            }
        }
        requireNotNull(result)
    }
}
