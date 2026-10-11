package dev.still.scriptures

import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

fun lessonDateLabel(lesson: Lesson): String {
    val format = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    return if (lesson.start.month == lesson.end.month) "${lesson.start.format(format)}–${lesson.end.dayOfMonth}"
    else "${lesson.start.format(format)}–${lesson.end.format(format)}"
}

@Composable
fun LessonOverview(lesson: Lesson, books: List<Book>, today: LocalDate, refreshing: Boolean,
                   refreshError: Boolean, onGroup: (Int) -> Unit, onLesson: () -> Unit,
                   onWeeks: () -> Unit, onRefresh: () -> Unit) {
    WatchList {
        item { Heading("COME, FOLLOW ME", lesson.title) }
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(lessonDateLabel(lesson) + " · ${lesson.year}", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                if (today >= lesson.start && today <= lesson.end) Text("This week", color = Accent, fontSize = 11.sp)
                if (lesson.supporting) Text("Supporting readings", color = Muted, fontSize = 11.sp)
            }
        }
        lesson.groups.forEachIndexed { index, group ->
            item { MenuChip(if (lesson.groups.size == 1) "Read scriptures" else groupLabel(group, books), "Choose a chapter") { onGroup(index) } }
        }
        if (lesson.groups.isEmpty()) item { Text("No scripture links saved for this lesson.", color = Muted, fontSize = 13.sp) }
        item { MenuChip("Read lesson", "Official lesson · saved after opening", onLesson) }
        item { MenuChip("Other weeks", "Browse saved calendars", onWeeks) }
        item { MenuChip(if (refreshing) "Checking calendar…" else "Refresh calendar", "Internet required", onRefresh) }
        if (refreshError) item { Text("Unable to refresh. Saved readings are still available.", color = Muted, fontSize = 13.sp) }
        item { Text(if (lesson.supporting) "Daily verses are selected for this app from supporting readings."
                    else "Daily verses are selected for this app from the week's scripture readings.",
            color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center) }
    }
}

@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun OfficialLessonReader(lesson: Lesson, repository: LessonRepository, store: ReadingStore,
                         textSize: Int, keepAwake: Boolean, activity: MainActivity, onSettings: () -> Unit) {
    var blocks by remember(lesson.id) { mutableStateOf<List<LessonBlock>?>(null) }
    var loading by remember(lesson.id) { mutableStateOf(true) }
    var failed by remember(lesson.id) { mutableStateOf(false) }
    var request by remember(lesson.id) { mutableIntStateOf(0) }
    LaunchedEffect(lesson.id, request) {
        loading = true; failed = false
        try { blocks = repository.lessonText(lesson, force = request > 0) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    val loaded = blocks
    if (loaded == null) {
        WatchList {
            item { Heading("COME, FOLLOW ME", lesson.title) }
            item { Text(if (loading) "Opening lesson…" else "Connect to download this lesson. Once opened, it stays available offline until eight newer lessons have been visited.",
                color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center) }
            if (failed) item { MenuChip("Try again") { request++ } }
        }
        return
    }
    val initial = remember(lesson.id) { store.lessonPosition(lesson.id) }
    val state = rememberLazyListState(initial.first.coerceIn(0, loaded.size + 1), initial.second)
    val focus = remember { FocusRequester() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = LocalView.current
    fun save() = store.saveLesson(lesson.id, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
    DisposableEffect(keepAwake) {
        if (keepAwake) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    DisposableEffect(state, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) save() }
        lifecycle.addObserver(observer)
        onDispose { save(); lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state) {
        save(); focus.requestFocus()
        snapshotFlow { Triple(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset, state.isScrollInProgress) }
            .distinctUntilChanged().collectLatest { (_, _, scrolling) -> if (!scrolling) { delay(250); save() } }
    }
    DisposableEffect(view, focus) {
        val listener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { if (it) focus.requestFocus() }
        view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        onDispose { if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnWindowFocusChangeListener(listener) }
    }
    Scaffold(positionIndicator = { PositionIndicator(lazyListState = state) }) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Text("Come, Follow Me", color = Accent, fontSize = 11.sp, modifier = Modifier.padding(top = 13.dp))
        }
        LazyColumn(state = state, contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 32.dp).clipToBounds()
                .testTag("lesson-reader").rotaryScrollable(RotaryScrollableDefaults.behavior(state, hapticFeedbackEnabled = false), focus)) {
            item(key = "title") {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Heading(lessonDateLabel(lesson), lesson.title)
                    CompactChip(onClick = onSettings, label = { Text("Aa", color = Muted) },
                        colors = ChipDefaults.chipColors(backgroundColor = Color.Black, contentColor = Muted))
                }
            }
            itemsIndexed(loaded, key = { index, _ -> "block-$index" }) { _, block ->
                Text(block.text, color = if (block.heading) Accent else Ink,
                    fontFamily = if (block.heading) FontFamily.SansSerif else FontFamily.Serif,
                    fontSize = textSize.sp, lineHeight = (textSize * 1.5f).sp, modifier = Modifier.fillMaxWidth())
            }
            item(key = "end") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Official lesson text\nChurchofJesusChrist.org\nLast eight visited lessons saved offline.", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                    if (failed) Text("Refresh failed. Showing the saved lesson.", color = Muted, fontSize = 12.sp)
                    MenuChip(if (loading) "Refreshing…" else "Refresh lesson", "Internet required") { if (!loading) request++ }
                }
            }
        }
    }
}
