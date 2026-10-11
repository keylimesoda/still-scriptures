package dev.still.scriptures

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay

@Composable
fun PageFlipReader(volume: String, books: List<Book>, onOpen: (ReadingPosition) -> Unit) {
    val keys = rememberSaveable { randomFlips(books).map(::verseKey) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var movement by remember { mutableIntStateOf(0) }
    var direction by remember { mutableIntStateOf(1) }
    var touching by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf(false) }
    var resumed by remember { mutableStateOf(false) }
    val turn = remember { Animatable(1f) }
    val focus = remember { FocusRequester() }
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOpen by rememberUpdatedState(onOpen)
    val pixelsPerPage = with(LocalDensity.current) { 18.dp.toPx() }
    var remainder by remember { mutableFloatStateOf(0f) }
    fun move(pixels: Float) {
        if (pixels == 0f || opened) return
        armed = true
        movement++
        direction = if (pixels > 0) 1 else -1
        remainder += pixels
        val steps = (remainder / pixelsPerPage).toInt()
        if (steps != 0) {
            index = Math.floorMod(index + steps, keys.size)
            remainder -= steps * pixelsPerPage
        }
    }
    fun open() {
        if (opened) return
        val position = parseVerseKey(keys[index]) ?: return
        opened = true
        currentOpen(position)
    }
    DisposableEffect(lifecycle) {
        resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumed = true
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                resumed = false; armed = false; touching = false
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    DisposableEffect(view, focus) {
        val listener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { if (it) focus.requestFocus() }
        view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        onDispose { if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnWindowFocusChangeListener(listener) }
    }
    LaunchedEffect(movement) {
        if (movement > 0) { turn.snapTo(0f); turn.animateTo(1f, tween(220, easing = LinearEasing)) }
    }
    // A fresh event cancels the pending opening. Holding a swipe never opens a page underneath a finger.
    LaunchedEffect(movement, armed, touching, resumed) {
        if (armed && !touching && resumed) { delay(900); open() }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 24.dp)
            .onRotaryScrollEvent { move(it.verticalScrollPixels); true }
            .focusRequester(focus).focusable()) {
        Text(volume, color = Accent, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2)
        Canvas(Modifier.weight(1f).fillMaxWidth()
            .semantics { contentDescription = "Scripture pages. Turn the bezel or swipe vertically; stop to open a verse." }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { touching = true },
                    onDragEnd = { touching = false },
                    onDragCancel = { touching = false; armed = false },
                    onVerticalDrag = { change, amount -> change.consume(); move(-amount) })
            }) {
            drawOpenScriptures(if (armed && turn.value < 1f) turn.value else null, direction)
        }
        Text(if (armed) "Stop to open a page" else "Turn the bezel to flip pages", color = Muted,
            fontSize = 12.sp, textAlign = TextAlign.Center)
        CompactChip(onClick = { open() }, label = { Text("Open this page", fontSize = 11.sp) })
    }
}
