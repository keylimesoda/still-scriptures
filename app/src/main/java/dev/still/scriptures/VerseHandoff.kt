package dev.still.scriptures

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.math.floor

fun scriptureLink(book: Book, position: ReadingPosition): String {
    val volume = when (book.volume) {
        "Old Testament" -> "ot"
        "New Testament" -> "nt"
        "Book of Mormon" -> "bofm"
        "Doctrine and Covenants" -> "dc-testament"
        "Pearl of Great Price" -> "pgp"
        else -> error("Unknown scripture volume")
    }
    require(book.id == position.bookId && validVerse(position, listOf(book)))
    return "https://www.churchofjesuschrist.org/study/scriptures/$volume/${book.id}/${position.chapter}?lang=eng&id=p${position.verse}#p${position.verse}"
}

@Composable
fun VerseHandoff(book: Book, position: ReadingPosition, onHelp: () -> Unit) {
    val url = remember(book.id, position.chapter, position.verse) { scriptureLink(book, position) }
    LinkHandoff(book.reference(position.chapter, position.verse), url, onHelp)
}

@Composable
fun LinkHandoff(title: String, url: String, onHelp: () -> Unit) {
    val matrix = remember(url) {
        runCatching { QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.MARGIN to 4, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)) }.getOrNull()
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 22.dp)) {
        Text(title, color = Accent, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2)
        Spacer(Modifier.height(4.dp))
        if (matrix != null) {
            Canvas(Modifier.size(116.dp).semantics { contentDescription = "QR link to $title. Scan with your phone camera." }) {
                val cell = floor(size.width / matrix.width)
                val edge = cell * matrix.width
                val inset = (size.width - edge) / 2
                drawRect(Color(0xFFE0D8CA))
                for (y in 0 until matrix.height) for (x in 0 until matrix.width) if (matrix[x, y]) {
                    drawRect(Color.Black, Offset(inset + x * cell, inset + y * cell), Size(cell, cell))
                }
            }
            Text("Scan with your phone", color = Muted, fontSize = 11.sp)
        } else Text("Unable to create the link code.", color = Muted, fontSize = 12.sp)
        CompactChip(onClick = onHelp, label = { Text("Save / share in Gospel Library", fontSize = 10.sp, maxLines = 1) })
    }
}
