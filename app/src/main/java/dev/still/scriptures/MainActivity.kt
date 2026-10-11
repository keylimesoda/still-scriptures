package dev.still.scriptures

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.*
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime

internal val Ink = Color(0xFFE0D8CA)
internal val Accent = Color(0xFFCEB995)
internal val Muted = Color(0xFFABA49A)
internal val Surface = Color(0xFF181715)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = ScriptureRepository(this)
        val store = ReadingStore(this)
        val lessonRepository = LessonRepository(this)
        setContent {
            MaterialTheme(colors = Colors(
                primary = Accent, onPrimary = Color.Black, background = Color.Black,
                onBackground = Ink, surface = Surface, onSurface = Ink,
            )) {
                var books by remember { mutableStateOf<List<Book>?>(null) }
                var error by remember { mutableStateOf(false) }
                var lessons by remember { mutableStateOf<List<Lesson>>(emptyList()) }
                LaunchedEffect(Unit) {
                    try {
                        val catalog = repository.catalog()
                        lessons = runCatching { lessonRepository.load() }.getOrDefault(emptyList())
                        books = catalog
                    } catch (_: Exception) { error = true }
                }
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    if (error) Message("Unable to open the library. Please reinstall the app.")
                    else if (books == null) Message("Opening scripturesÃ¢â‚¬Â¦")
                    else ScriptureApp(books!!, repository, lessonRepository, lessons, store, this@MainActivity)
                }
            }
        }
    }
}

@Composable
private fun ScriptureApp(books: List<Book>, repository: ScriptureRepository, lessonRepository: LessonRepository,
                         initialLessons: List<Lesson>, store: ReadingStore, activity: MainActivity) {
    val navigation = rememberSwipeDismissableNavController()
    val studyRepository = remember { StudyRepository(activity) }
    val volumes = remember(books) { books.map { it.volume }.distinct() }
    var textSize by remember { mutableIntStateOf(store.textSize) }
    var keepAwake by remember { mutableStateOf(store.keepAwake) }
    var savedVerses by remember { mutableStateOf(store.savedVerses().filter { validVerse(it, books) }) }
    var lessons by remember { mutableStateOf(initialLessons) }
    var refreshing by remember { mutableStateOf(false) }
    var refreshError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val today = rememberLocalDate()
    val current = lessonForDate(lessons, today)
    fun refreshCalendar(year: Int) {
        if (refreshing) return
        refreshing = true; refreshError = false
        scope.launch {
            try { lessons = lessonRepository.refresh(year, books) }
            catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (_: Exception) { refreshError = true }
            finally { refreshing = false }
        }
    }
    fun open(position: ReadingPosition, resume: Boolean = false, parent: String = "home") {
        navigation.navigate("reader/${position.bookId}/${position.chapter}/${position.verse}/$resume") {
            popUpTo(parent) { inclusive = parent.startsWith("reader/") }
        }
    }
    fun last() = normalizePosition(store.position() ?: ReadingPosition("alma", 32, 21), books)

    SwipeDismissableNavHost(navController = navigation, startDestination = "home") {
        composable("home") {
            val position = last()
            val lastLesson = lessons.firstOrNull { it.id == store.lastLessonId() }
            val hasPosition = store.position() != null || lastLesson != null
            val daily = current?.let { dailyVerse(it, today) }
            WatchList {
                item { Heading("STILL", "Scriptures") }
                item {
                    MenuChip(if (hasPosition) "Continue reading" else "Start reading",
                        lastLesson?.let { "Lesson Ã‚Â· ${it.title}" } ?: books.first { it.id == position.bookId }.reference(position.chapter, position.verse)) {
                        if (lastLesson != null) navigation.navigate("lesson-text/${lastLesson.id}") else open(position, hasPosition)
                    }
                }
                item { MenuChip("Go to scripture", "Book Ã‚Â· chapter Ã‚Â· verse") { navigation.navigate("volume") } }
                item { MenuChip("Come, Follow Me", current?.title ?: "Browse saved lessons") { navigation.navigate("cfm") } }
                item { MenuChip("Scripture of the day", daily?.let { books.first { b -> b.id == it.bookId }.reference(it.chapter, it.verse) } ?: "Selection unavailable") {
                    if (daily != null) open(daily.position()) else navigation.navigate("cfm")
                } }
                item { MenuChip("Flip open", "Find a random verse") { navigation.navigate("flip-scope") } }
                item { MenuChip("Saved verses", "${savedVerses.size} saved on this watch") { navigation.navigate("saved") } }
                item { MenuChip("Reading settings", "Text size Ã‚Â· screen") { navigation.navigate("settings") } }
            }
        }
        composable("volume") {
            Wheel("SCRIPTURES", volumes, volumes.indexOf(books.first { it.id == last().bookId }.volume), "Choose reference") {
                navigation.navigate("reference/$it")
            }
        }
        composable("reference/{volume}", arguments = listOf(navArgument("volume") { type = NavType.IntType })) { entry ->
            val volume = volumes[entry.arguments!!.getInt("volume")]
            val options = books.filter { it.volume == volume }
            ReferencePicker(volume, options, last()) { open(it) }
        }
        composable("reader/{book}/{chapter}/{verse}/{resume}", arguments = listOf(
            navArgument("chapter") { type = NavType.IntType }, navArgument("verse") { type = NavType.IntType },
            navArgument("resume") { type = NavType.BoolType },
        )) { entry ->
            val args = entry.arguments!!
            val requested = ReadingPosition(args.getString("book")!!, args.getInt("chapter"), args.getInt("verse"))
            val saved = store.position()?.takeIf { args.getBoolean("resume") && it.bookId == requested.bookId && it.chapter == requested.chapter }
            val initial = normalizePosition(saved ?: requested, books)
            val next = nextChapter(books, initial)
            Reader(books.first { it.id == initial.bookId }, initial, repository, studyRepository, store, textSize, keepAwake, activity,
                savedKeys = savedVerses.map(::verseKey).toSet(),
                onVerse = { navigation.navigate("verse-actions/${it.bookId}/${it.chapter}/${it.verse}") },
                onSettings = { navigation.navigate("settings") },
                nextLabel = next?.let { books.first { book -> book.id == it.bookId }.reference(it.chapter) },
                onNext = { next?.let { open(it, parent = "reader/{book}/{chapter}/{verse}/{resume}") } })
        }
        composable("flip-scope") {
            Wheel("CHOOSE SCRIPTURES", volumes, volumes.indexOf(books.first { it.id == last().bookId }.volume), "Flip pages") {
                navigation.navigate("flip/$it")
            }
        }
        composable("flip/{scope}", arguments = listOf(navArgument("scope") { type = NavType.IntType })) { entry ->
            val selectedScope = entry.arguments!!.getInt("scope")
            val pool = books.filter { it.volume == volumes[selectedScope] }
            PageFlipReader(volumes[selectedScope], pool) { open(it) }
        }
        composable("saved") {
            WatchList {
                item { Heading("SAVED VERSES", "Keep a verse close") }
                if (savedVerses.isEmpty()) item { Text("Tap a verse while reading, then choose Save verse. It stays on this watch.",
                    color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center) }
                savedVerses.forEach { position ->
                    item { MenuChip(books.first { it.id == position.bookId }.reference(position.chapter, position.verse), "Read in its chapter") {
                        open(position, parent = "saved")
                    } }
                }
            }
        }
        composable("verse-actions/{book}/{chapter}/{verse}", arguments = listOf(
            navArgument("chapter") { type = NavType.IntType }, navArgument("verse") { type = NavType.IntType })) { entry ->
            val args = entry.arguments!!
            val position = ReadingPosition(args.getString("book")!!, args.getInt("chapter"), args.getInt("verse"))
            val book = books.first { it.id == position.bookId }
            LaunchedEffect(book.id, position.chapter) { studyRepository.cached(book, position.chapter) }
            val study = studyRepository.memory["${book.id}-${position.chapter}"]
            val notes = study?.notes?.filter { it.verse == position.verse }
            val isSaved = savedVerses.any { verseKey(it) == verseKey(position) }
            WatchList {
                item { Heading("VERSE", book.reference(position.chapter, position.verse)) }
                item { MenuChip(if (isSaved) "Ã¢â„¢Â¥ Remove saved verse" else "Ã¢â„¢Â¡ Save verse", "On this watch") {
                    savedVerses = store.toggleSaved(position).filter { validVerse(it, books) }
                    navigation.popBackStack()
                } }
                item { MenuChip(notes?.let { "Footnotes (${it.size})" } ?: "Footnotes",
                    if (study == null) "Download chapter study helps" else "Read notes and cross-references") {
                    navigation.navigate("footnotes/${position.bookId}/${position.chapter}/${position.verse}")
                } }
                item { MenuChip("Share / open on phone", "Scan a verse link") {
                    navigation.navigate("share/${position.bookId}/${position.chapter}/${position.verse}")
                } }
                item { MenuChip("Return to reading") { navigation.popBackStack() } }
            }
        }
        composable("footnotes/{book}/{chapter}/{verse}", arguments = listOf(
            navArgument("chapter") { type = NavType.IntType }, navArgument("verse") { type = NavType.IntType })) { entry ->
            val args = entry.arguments!!
            val position = ReadingPosition(args.getString("book")!!, args.getInt("chapter"), args.getInt("verse"))
            val book = books.first { it.id == position.bookId }
            StudyNotesScreen(book, position, studyRepository, textSize, keepAwake, activity, onLink = { link ->
                val target = studyPosition(link.url, books)
                if (target != null) navigation.navigate("reader/${target.bookId}/${target.chapter}/${target.verse}/false")
                else {
                    entry.savedStateHandle["study-link-url"] = link.url
                    entry.savedStateHandle["study-link-label"] = link.label
                    navigation.navigate("study-link")
                }
            })
        }
        composable("study-link") {
            val previous = navigation.previousBackStackEntry
            val url = previous?.savedStateHandle?.get<String>("study-link-url")
            val label = previous?.savedStateHandle?.get<String>("study-link-label") ?: "Official study page"
            if (url != null) LinkHandoff(label, url) { navigation.navigate("share-help") }
            else Message("Return to the note and select the link again.")
        }
        composable("share/{book}/{chapter}/{verse}", arguments = listOf(
            navArgument("chapter") { type = NavType.IntType }, navArgument("verse") { type = NavType.IntType })) { entry ->
            val args = entry.arguments!!
            val position = ReadingPosition(args.getString("book")!!, args.getInt("chapter"), args.getInt("verse"))
            val book = books.first { it.id == position.bookId }
            VerseHandoff(book, position) { navigation.navigate("share-help") }
        }
        composable("share-help") {
            WatchList {
                item { Heading("ON YOUR PHONE", "Save or share") }
                item { Text("Scan the code with your phone camera. Open the verse in Gospel Library or your browser.", color = Ink, fontSize = 14.sp) }
                item { Text("Save a bookmark or highlight in Gospel Library while signed in to keep it with your Church Account. Use Gospel Library's Share action to send the verse to someone.", color = Ink, fontSize = 14.sp) }
                item { Text("Watch saves stay on this watch. Opening the link does not sync or save them automatically.", color = Muted, fontSize = 13.sp) }
            }
        }
        composable("cfm") {
            LaunchedEffect(current?.year ?: today.year) {
                val year = current?.year ?: today.year
                if (lessonRepository.automaticRefreshDue(year)) refreshCalendar(year)
            }
            if (current != null) LessonOverview(current, books, today, refreshing, refreshError,
                onGroup = { navigation.navigate("lesson-chapters/${current.id}/$it?parent=cfm") },
                onLesson = { navigation.navigate("lesson-text/${current.id}") },
                onWeeks = { navigation.navigate("lesson-years") }, onRefresh = { refreshCalendar(current.year) })
            else WatchList {
                item { Heading("COME, FOLLOW ME", "No saved lesson for today") }
                item { MenuChip("Browse saved weeks") { navigation.navigate("lesson-years") } }
                item { MenuChip(if (refreshing) "CheckingÃ¢â‚¬Â¦" else "Refresh calendar") { refreshCalendar(today.year) } }
                if (refreshError) item { Text("Unable to refresh. Try again when connected.", color = Muted, fontSize = 13.sp) }
            }
        }
        composable("lesson/{id}") { entry ->
            lessons.firstOrNull { it.id == entry.arguments!!.getString("id") }?.let { lesson ->
                LessonOverview(lesson, books, today, refreshing, refreshError,
                    onGroup = { navigation.navigate("lesson-chapters/${lesson.id}/$it?parent=lesson") },
                    onLesson = { navigation.navigate("lesson-text/${lesson.id}") },
                    onWeeks = { navigation.navigate("lesson-years") }, onRefresh = { refreshCalendar(lesson.year) })
            }
        }
        composable("lesson-years") {
            val years = lessons.map { it.year }.distinct().sorted()
            if (years.isEmpty()) Message("Connect to refresh the calendar.")
            else Wheel("YEAR", years.map { "$it" }, years.indexOf(current?.year ?: today.year).coerceAtLeast(0), "Choose week") {
                navigation.navigate("lesson-weeks/${years[it]}")
            }
        }
        composable("lesson-weeks/{year}", arguments = listOf(navArgument("year") { type = NavType.IntType })) { entry ->
            val year = entry.arguments!!.getInt("year")
            val weeks = lessons.filter { it.year == year }
            WatchList(initialCenterItem = weeks.indexOfFirst { it.id == current?.id }.coerceAtLeast(0) + 1) {
                item { Heading("COME, FOLLOW ME", "$year") }
                weeks.forEach { lesson -> item { MenuChip(lessonDateLabel(lesson), lesson.title) { navigation.navigate("lesson/${lesson.id}") } } }
            }
        }
        composable("lesson-chapters/{id}/{group}?parent={parent}", arguments = listOf(
            navArgument("group") { type = NavType.IntType }, navArgument("parent") { defaultValue = "cfm" })) { entry ->
            val lesson = lessons.first { it.id == entry.arguments!!.getString("id") }
            val group = lesson.groups[entry.arguments!!.getInt("group")]
            val book = books.first { it.id == group.bookId }
            val parent = if (entry.arguments!!.getString("parent") == "cfm") "cfm" else "lesson/{id}"
            Wheel("CHAPTER", group.chapters.map { "$it" }, 0, "Read", book.name) {
                open(ReadingPosition(book.id, group.chapters[it], 1, itemIndex = 0), parent = parent)
            }
        }
        composable("lesson-text/{id}") { entry ->
            lessons.firstOrNull { it.id == entry.arguments!!.getString("id") }?.let { lesson ->
                OfficialLessonReader(lesson, lessonRepository, store, textSize, keepAwake, activity,
                    onSettings = { navigation.navigate("settings") })
            }
        }
        composable("settings") {
            WatchList {
                item { Heading("READING", "Make it comfortable") }
                item { MenuChip("Text size", "$textSize Ã‚Â· preview and adjust") { navigation.navigate("text-size") } }
                item {
                    MenuChip("Keep screen awake", if (keepAwake) "On while reading" else "Off Ã‚Â· use watch timeout") {
                        keepAwake = !keepAwake
                        store.keepAwake = keepAwake
                    }
                }
                item {
                    Text("Black background. Warm text. Brightness follows your watch setting.",
                        color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                }
            }
        }
        composable("text-size") {
            Stepper(value = textSize.toFloat(), onValueChange = {
                textSize = it.toInt(); store.textSize = textSize
            }, valueRange = 16f..26f, steps = 9,
                increaseIcon = { Text("+", fontSize = 25.sp, color = Accent) },
                decreaseIcon = { Text("Ã¢Ë†â€™", fontSize = 25.sp, color = Accent) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 30.dp)) {
                    Text("TEXT SIZE Ã‚Â· $textSize", fontSize = 11.sp, color = Muted)
                    Spacer(Modifier.height(8.dp))
                    Text("Be still, and know that I am God.", fontFamily = FontFamily.Serif,
                        fontSize = textSize.sp, lineHeight = (textSize * 1.45f).sp, textAlign = TextAlign.Center, color = Ink)
                }
            }
        }
    }
}

@OptIn(ExperimentalWearFoundationApi::class)
@Composable
internal fun WatchList(initialCenterItem: Int = 1, content: ScalingLazyListScope.() -> Unit) {
    val state = rememberScalingLazyListState(initialCenterItemIndex = initialCenterItem)
    Scaffold(positionIndicator = { PositionIndicator(scalingLazyListState = state) }) {
        ScalingLazyColumn(state = state,
            contentPadding = PaddingValues(top = 24.dp, bottom = 30.dp, start = 22.dp, end = 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().testTag("watch-menu"),
            rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(state, hapticFeedbackEnabled = false),
            content = content)
    }
}

@Composable
internal fun Heading(eyebrow: String, title: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Text(eyebrow, color = Accent, fontSize = 11.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(6.dp))
        Text(title, color = Ink, fontFamily = FontFamily.Serif, fontSize = 20.sp,
            lineHeight = 24.sp, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun MenuChip(label: String, secondary: String? = null, onClick: () -> Unit) {
    Chip(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        label = { Text(label, maxLines = 2, fontSize = 14.sp) },
        secondaryLabel = secondary?.let { { Text(it, color = Muted, fontSize = 11.sp, maxLines = 2) } },
        colors = ChipDefaults.chipColors(backgroundColor = Surface, contentColor = Ink))
}

@Composable
private fun Wheel(label: String, options: List<String>, initial: Int, action: String,
                  subtitle: String? = null, repeatItems: Boolean = false, onConfirm: (Int) -> Unit) {
    val state = rememberPickerState(options.size, initial.coerceIn(options.indices), repeatItems = repeatItems)
    // Native Wear Picker supplies rotary snapping, focus, touch scrolling and accessibility.
    Column(Modifier.fillMaxSize().padding(top = 24.dp, bottom = 22.dp, start = 22.dp, end = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Accent, fontSize = 11.sp, letterSpacing = 2.sp)
        subtitle?.let { Text(it, color = Muted, fontSize = 12.sp, maxLines = 2, textAlign = TextAlign.Center) }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().height(43.dp)) {
                drawLine(Accent.copy(alpha = 0.25f), androidx.compose.ui.geometry.Offset(0f, 0f),
                    androidx.compose.ui.geometry.Offset(size.width, 0f), 1.dp.toPx())
                drawLine(Accent.copy(alpha = 0.25f), androidx.compose.ui.geometry.Offset(0f, size.height),
                    androidx.compose.ui.geometry.Offset(size.width, size.height), 1.dp.toPx())
            }
            Picker(state, contentDescription = "$label: ${options[state.selectedOption]}",
                modifier = Modifier.fillMaxSize(), gradientColor = Color.Black) { index ->
                Box(Modifier.fillMaxWidth().height(43.dp), contentAlignment = Alignment.Center) {
                    Text(options[index], fontSize = if (options[index].length > 20) 15.sp else 19.sp,
                        color = if (index == state.selectedOption) Ink else Muted,
                        textAlign = TextAlign.Center, maxLines = 2, lineHeight = 20.sp)
                }
            }
        }
        CompactChip(onClick = { onConfirm(state.selectedOption) },
            label = { Text(action, fontSize = 12.sp) },
            colors = ChipDefaults.chipColors(backgroundColor = Accent, contentColor = Color.Black))
    }
}

@OptIn(ExperimentalWearFoundationApi::class)
@Composable
private fun Reader(book: Book, initial: ReadingPosition, repository: ScriptureRepository, studyRepository: StudyRepository,
                   store: ReadingStore, textSize: Int, keepAwake: Boolean, activity: MainActivity,
                   savedKeys: Set<String>, onVerse: (ReadingPosition) -> Unit,
                   onSettings: () -> Unit, nextLabel: String?, onNext: () -> Unit) {
    val study = studyRepository.memory["${book.id}-${initial.chapter}"]
    var expanded by rememberSaveable(book.id, initial.chapter) { mutableStateOf(false) }
    var studyLoading by remember { mutableStateOf(false) }
    var studyFailed by remember { mutableStateOf(false) }
    val studyScope = rememberCoroutineScope()
    LaunchedEffect(book.id, initial.chapter) { studyRepository.cached(book, initial.chapter) }
    fun overview() {
        expanded = !expanded
        if (!expanded || study != null || studyLoading) return
        studyLoading = true; studyFailed = false
        studyScope.launch {
            try { studyRepository.get(book, initial.chapter) }
            catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (_: Exception) { studyFailed = true }
            finally { studyLoading = false }
        }
    }
    var chapter by remember(book.id, initial.chapter) { mutableStateOf<Chapter?>(null) }
    var failed by remember(book.id, initial.chapter) { mutableStateOf(false) }
    LaunchedEffect(book.id, initial.chapter) {
        try { chapter = repository.chapter(book, initial.chapter) } catch (_: Exception) { failed = true }
    }
    DisposableEffect(keepAwake) {
        if (keepAwake) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    if (failed) { Message("Unable to open this chapter. Go back and try again."); return }
    val loaded = chapter ?: run { Message("Opening chapterÃ¢â‚¬Â¦"); return }
    val state = rememberLazyListState(initial.itemIndex, initial.offset)
    val focus = remember { FocusRequester() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = LocalView.current
    fun save() {
        val visibleVerse = state.firstVisibleItemIndex.coerceIn(1, loaded.verses.size)
        store.save(initial.copy(verse = visibleVerse, itemIndex = state.firstVisibleItemIndex,
            offset = state.firstVisibleItemScrollOffset))
    }
    // Save when scrolling settles and immediately when the reader leaves the foreground.
    DisposableEffect(state, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) save() }
        lifecycle.addObserver(observer)
        onDispose { save(); lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state) {
        save()
        focus.requestFocus()
        snapshotFlow { Triple(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset, state.isScrollInProgress) }
            .distinctUntilChanged().collectLatest { (_, _, scrolling) ->
                if (!scrolling) { delay(250); save() }
            }
    }
    // A watch sleep/wake cycle can change focus without leaving this composition.
    DisposableEffect(view, focus) {
        val listener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) focus.requestFocus()
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        onDispose { if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnWindowFocusChangeListener(listener) }
    }
    Scaffold(positionIndicator = { PositionIndicator(lazyListState = state) }) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Text(book.reference(initial.chapter), color = Accent, fontSize = 11.sp,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 13.dp).widthIn(max = 112.dp))
        }
        LazyColumn(state = state, contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 32.dp)
                .clipToBounds().testTag("chapter-reader").rotaryScrollable(
                RotaryScrollableDefaults.behavior(state, hapticFeedbackEnabled = false), focus)) {
            item(key = "heading") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                        .clickable(onClickLabel = "Toggle chapter overview") { overview() },
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(book.reference(initial.chapter), color = Accent, fontSize = 15.sp,
                            fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                        Text(if (expanded) "Chapter overview â–´" else "Chapter overview â–¾",
                            color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    if (expanded) {
                        when {
                            studyLoading -> Text("Downloading study helpsâ€¦", color = Muted, fontSize = 13.sp)
                            studyFailed -> {
                                Text("Not downloaded. Connect to the internet and try again. Scripture text is still available offline.", color = Muted, fontSize = 13.sp)
                                MenuChip("Try again") { expanded = false; overview() }
                            }
                            study != null -> {
                                Text(study.summary.ifBlank { "No overview was provided for this chapter." }, color = Ink,
                                    fontFamily = FontFamily.Serif, fontSize = textSize.sp, lineHeight = (textSize * 1.5f).sp,
                                    modifier = Modifier.padding(vertical = 12.dp))
                                Text("Official chapter overview Â· saved on this watch", color = Muted, fontSize = 11.sp)
                                if (study.notices.isNotBlank()) Text(study.notices, color = Muted, fontSize = 11.sp)
                            }
                        }
                    }
                    CompactChip(onClick = onSettings, label = { Text("Aa", color = Muted, fontSize = 13.sp) },
                        colors = ChipDefaults.chipColors(backgroundColor = Color.Black, contentColor = Muted))
                    Text("Tap a verse for notes, save or share", color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center)
                }
            }
            itemsIndexed(loaded.verses, key = { index, _ -> "verse-${index + 1}" }) { index, text ->
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(color = Accent, fontSize = (textSize - 4).sp,
                        fontFamily = FontFamily.SansSerif)) {
                        append("${index + 1} ")
                        val markers = study?.notes?.filter { it.verse == index + 1 }?.joinToString("Â·") { it.marker }.orEmpty()
                        if (markers.isNotBlank()) append("$markers ")
                        if (verseKey(ReadingPosition(book.id, initial.chapter, index + 1)) in savedKeys) append("Ã¢â„¢Â¥ ")
                        append(" ")
                    }
                    append(text)
                }, color = Ink, fontFamily = FontFamily.Serif,
                    fontSize = textSize.sp, lineHeight = (textSize * 1.5f).sp,
                    modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Verse ${index + 1} actions") {
                        onVerse(ReadingPosition(book.id, initial.chapter, index + 1))
                    })
            }
            item(key = "end") {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("End of ${if (book.isSections) "section" else "chapter"}", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    if (nextLabel != null) MenuChip("Next ${if (book.isSections) "section" else "chapter"}", nextLabel, onNext)
                    Spacer(Modifier.height(8.dp))
                    CompactChip(onClick = onSettings, label = { Text("Aa Ã‚Â· Reading settings", fontSize = 11.sp) })
                }
            }
        }
    }
}

@Composable
internal fun Message(message: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, color = Muted, textAlign = TextAlign.Center, fontSize = 14.sp)
    }
}

@Composable
private fun rememberLocalDate(): LocalDate {
    var date by remember { mutableStateOf(LocalDate.now()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) date = LocalDate.now() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(date) {
        val now = ZonedDateTime.now()
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        delay(java.time.Duration.between(now, midnight).toMillis().coerceAtLeast(1_000))
        date = LocalDate.now()
    }
    return date
}
