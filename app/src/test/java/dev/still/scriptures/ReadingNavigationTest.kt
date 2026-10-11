package dev.still.scriptures

import org.junit.Assert.*
import org.junit.Test

class ReadingNavigationTest {
    private val books = listOf(
        Book("alma", "Alma", "Book of Mormon", listOf(31, 21)),
        Book("dc", "Doctrine and Covenants", "Doctrine and Covenants", listOf(39)),
    )
    @Test fun nextChapterResetsTheReadingOffset() {
        assertEquals(ReadingPosition("alma", 2, 1, 0), nextChapter(books, ReadingPosition("alma", 1, 20, 20, 120)))
    }
    @Test fun lastChapterOffersTheNextBook() {
        assertEquals(ReadingPosition("dc", 1, 1, 0), nextChapter(books, ReadingPosition("alma", 2, 21)))
    }
    @Test fun endOfLibraryDoesNotWrapToGenesis() {
        assertNull(nextChapter(books, ReadingPosition("dc", 1, 39)))
    }
    @Test fun damagedSavedPositionCannotOpenANonexistentChapter() {
        assertEquals(ReadingPosition("alma", 2, 21, 22, 0),
            normalizePosition(ReadingPosition("missing", 900, 999, 999, -5), books))
    }
    @Test fun savedOffsetWithinALongVerseIsPreserved() {
        val position = ReadingPosition("alma", 1, 3, 3, 178)
        assertEquals(position, normalizePosition(position, books))
    }
    @Test fun scriptureLabelsRespectDoctrineAndCovenantsReferences() {
        assertEquals("D&C 1:12", books[1].reference(1, 12))
        assertEquals("Alma 2", books[0].reference(2))
    }
}
