package dev.still.scriptures

data class Book(
    val id: String,
    val name: String,
    val volume: String,
    val verseCounts: List<Int>,
) {
    val isSections get() = id == "dc"
    fun reference(chapter: Int, verse: Int? = null): String {
        val label = if (isSections) "D&C" else name
        return "$label $chapter" + (verse?.let { ":$it" } ?: "")
    }
}

data class ReadingPosition(
    val bookId: String,
    val chapter: Int,
    val verse: Int,
    // Lazy reader item 0 is the heading; item n is verse n.
    val itemIndex: Int = verse,
    val offset: Int = 0,
)

data class Chapter(val book: Book, val number: Int, val verses: List<String>)

fun normalizePosition(position: ReadingPosition, books: List<Book>): ReadingPosition {
    val book = books.firstOrNull { it.id == position.bookId } ?: books.first()
    val chapter = position.chapter.coerceIn(1, book.verseCounts.size)
    val count = book.verseCounts[chapter - 1]
    return position.copy(
        bookId = book.id,
        chapter = chapter,
        verse = position.verse.coerceIn(1, count),
        itemIndex = position.itemIndex.coerceIn(0, count + 1),
        offset = position.offset.coerceAtLeast(0),
    )
}

fun nextChapter(books: List<Book>, current: ReadingPosition): ReadingPosition? {
    val bookIndex = books.indexOfFirst { it.id == current.bookId }
    if (bookIndex < 0) return null
    val book = books[bookIndex]
    return if (current.chapter < book.verseCounts.size) {
        ReadingPosition(book.id, current.chapter + 1, 1, itemIndex = 0)
    } else {
        books.getOrNull(bookIndex + 1)?.let { ReadingPosition(it.id, 1, 1, itemIndex = 0) }
    }
}
