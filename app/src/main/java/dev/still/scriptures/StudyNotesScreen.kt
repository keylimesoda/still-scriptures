package dev.still.scriptures

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun StudyNotesScreen(book: Book, position: ReadingPosition, repository: StudyRepository,
                     textSize: Int, keepAwake: Boolean, activity: MainActivity, onLink: (StudyLink) -> Unit) {
    DisposableEffect(keepAwake) {
        if (keepAwake) activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    val key = "${book.id}-${position.chapter}"
    val study = repository.memory[key]
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun load() {
        if (loading) return
        loading = true; failed = false
        scope.launch {
            try { repository.get(book, position.chapter) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { failed = true }
            finally { loading = false }
        }
    }
    LaunchedEffect(key) { if (study == null) load() }
    val state = rememberLazyListState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Scaffold(positionIndicator = { PositionIndicator(lazyListState = state) }) {
        LazyColumn(state = state, contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 24.dp).clipToBounds()
                .rotaryScrollable(RotaryScrollableDefaults.behavior(state, hapticFeedbackEnabled = false), focus)) {
            item { Heading("FOOTNOTES", book.reference(position.chapter, position.verse)) }
            when {
                loading -> item { Text("Downloading chapter study helps…", color = Muted, fontSize = 14.sp) }
                failed -> {
                    item { Text("These notes are not downloaded yet. Connect to the internet, then try again. Your scripture text remains available offline.", color = Ink, fontSize = 14.sp) }
                    item { MenuChip("Try again") { load() } }
                }
                study != null -> {
                    val notes = study.notes.filter { it.verse == position.verse }
                    if (notes.isEmpty()) item { Text("No footnotes for this verse in the official chapter.", color = Muted, fontSize = 14.sp) }
                    notes.forEach { note ->
                        item { Text("${note.verse}${note.marker} · ${note.word}", color = Accent, fontSize = 14.sp) }
                        item { Text(note.text, color = Ink, fontFamily = FontFamily.Serif,
                            fontSize = textSize.sp, lineHeight = (textSize * 1.5f).sp) }
                        note.links.forEach { link -> item {
                            MenuChip(link.label, "Open reference") { onLink(link) }
                        } }
                    }
                    item { Text("Official study helps · downloaded for personal reading. Saved offline on this watch.", color = Muted, fontSize = 11.sp) }
                    if (study.notices.isNotBlank()) item { Text(study.notices, color = Muted, fontSize = 11.sp) }
                    item { MenuChip("Official chapter on phone", "Scan source link") { onLink(StudyLink(book.reference(position.chapter), study.source + "#study-helps")) } }
                }
            }
        }
    }
}
