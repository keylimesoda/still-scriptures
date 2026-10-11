package dev.still.scriptures

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*

private fun rollerBookLabel(book: Book): String = when (book.id) {
    "dc" -> "D&C"
    "js-m" -> "JS–Matthew"
    "js-h" -> "JS–History"
    "w-of-m" -> "Words of M."
    "1-thes" -> "1 Thess."
    "2-thes" -> "2 Thess."
    "1-cor" -> "1 Cor."
    "2-cor" -> "2 Cor."
    "1-chr" -> "1 Chron."
    "2-chr" -> "2 Chron."
    "eccl" -> "Eccles."
    "philip" -> "Philip."
    else -> book.name
}

@Composable
fun ReferencePicker(volume: String, books: List<Book>, last: ReadingPosition, onRead: (ReadingPosition) -> Unit) {
    val initial = remember { if (validVerse(last, books)) last else ReadingPosition(books.first().id, 1, 1) }
    val bookState = rememberPickerState(books.size, books.indexOfFirst { it.id == initial.bookId }, repeatItems = false)
    val book = books[bookState.selectedOption]
    val chapterState = key(book.id) {
        rememberPickerState(book.verseCounts.size, if (book.id == initial.bookId) initial.chapter - 1 else 0, repeatItems = false)
    }
    val chapter = chapterState.selectedOption + 1
    val verseState = key(book.id, chapter) {
        rememberPickerState(book.verseCounts[chapter - 1],
            if (book.id == initial.bookId && chapter == initial.chapter) initial.verse - 1 else 0, repeatItems = false)
    }
    val group = rememberPickerGroupState()
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 22.dp)) {
        Text(volume, color = Muted, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 1)
        Text(book.reference(chapter, verseState.selectedOption + 1), color = Accent, fontSize = 13.sp,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val bookWidth = maxWidth * .50f
            val numberWidth = maxWidth * .20f
            val separatorWidth = maxWidth * .05f
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf("BOOK" to bookWidth, (if (book.isSections) "SECT." else "CH.") to numberWidth, "VERSE" to numberWidth)
                        .forEachIndexed { index, (label, width) ->
                            if (index > 0) Spacer(Modifier.width(separatorWidth))
                            Text(label, color = if (group.selectedIndex == index) Accent else Muted,
                                fontSize = 9.sp, textAlign = TextAlign.Center,
                                modifier = Modifier.width(width).clickable { group.selectedIndex = index })
                        }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxWidth().height(38.dp)) {
                        drawLine(Accent.copy(alpha = .3f), Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
                        drawLine(Accent.copy(alpha = .3f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                    }
                    PickerGroup(
                        PickerGroupItem(bookState, Modifier.width(bookWidth).fillMaxHeight(),
                            contentDescription = "Book: ${book.name}", onSelected = { group.selectedIndex = 0 }) { index, selected ->
                            Box(Modifier.width(bookWidth).height(38.dp), contentAlignment = Alignment.Center) {
                                Text(rollerBookLabel(books[index]), color = if (selected) Ink else Muted,
                                    fontSize = 13.sp, lineHeight = 15.sp, textAlign = TextAlign.Center, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis)
                            }
                        },
                        PickerGroupItem(chapterState, Modifier.width(numberWidth).fillMaxHeight(),
                            contentDescription = "${if (book.isSections) "Section" else "Chapter"}: $chapter",
                            onSelected = { group.selectedIndex = 1 }) { index, selected ->
                            Box(Modifier.width(numberWidth).height(38.dp), contentAlignment = Alignment.Center) {
                                Text("${index + 1}", color = if (selected) Ink else Muted, fontSize = 18.sp)
                            }
                        },
                        PickerGroupItem(verseState, Modifier.width(numberWidth).fillMaxHeight(),
                            contentDescription = "Verse: ${verseState.selectedOption + 1}",
                            onSelected = { group.selectedIndex = 2 }) { index, selected ->
                            Box(Modifier.width(numberWidth).height(38.dp), contentAlignment = Alignment.Center) {
                                Text("${index + 1}", color = if (selected) Ink else Muted, fontSize = 18.sp)
                            }
                        },
                        modifier = Modifier.fillMaxSize(), pickerGroupState = group, autoCenter = false,
                        separator = { Text("·", color = Accent, fontSize = 17.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.width(separatorWidth)) })
                }
            }
        }
        Text("Tap a roller · turn the bezel", color = Muted, fontSize = 10.sp, textAlign = TextAlign.Center)
        CompactChip(onClick = { onRead(ReadingPosition(book.id, chapter, verseState.selectedOption + 1)) },
            label = { Text("Read", fontSize = 12.sp) }, colors = ChipDefaults.chipColors(backgroundColor = Accent, contentColor = Color.Black))
    }
}
