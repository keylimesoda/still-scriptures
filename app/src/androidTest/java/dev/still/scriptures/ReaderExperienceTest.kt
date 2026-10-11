package dev.still.scriptures

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before

class ReaderExperienceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun resetLocalReadingState() {
        compose.activity.getSharedPreferences("reading", 0).edit().clear().commit()
        compose.activityRule.scenario.recreate()
    }

    private fun waitForHome() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Go to scripture").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun rotary(amount: Float) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_UNKNOWN }
        val coordinates = MotionEvent.PointerCoords().apply { setAxisValue(MotionEvent.AXIS_SCROLL, amount) }
        val time = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(time, time, MotionEvent.ACTION_SCROLL, 1, arrayOf(properties),
            arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_ROTARY_ENCODER, 0)
        try { assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(event, true)) }
        finally { event.recycle() }
        SystemClock.sleep(220)
    }

    @Test fun rotaryScrollIsSavedAndSurvivesActivityRecreation() {
        waitForHome()
        val prefs = compose.activity.getSharedPreferences("reading", 0)
        val label = if (prefs.contains("book")) "Continue reading" else "Start reading"
        compose.onNodeWithText(label).performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("chapter-reader").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(5_000) { ReadingStore(compose.activity).position() != null }
        val before = ReadingStore(compose.activity).position()!!
        repeat(3) { rotary(-1f) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        SystemClock.sleep(300)
        val after = ReadingStore(compose.activity).position()!!
        assertTrue("Rotary input must scroll a chapter", before.itemIndex != after.itemIndex || before.offset != after.offset)
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("chapter-reader").fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals("Reading position must survive recreation", after, ReadingStore(compose.activity).position())
    }

    @Test fun pickerOpensARealOfflineChapter() {
        waitForHome()
        compose.onNodeWithText("Go to scripture").performScrollTo().performClick()
        compose.onNodeWithText("Choose book").performClick()
        compose.onNodeWithText("Choose chapter").performClick()
        compose.onNodeWithText("Choose verse").performClick()
        compose.onNodeWithText("Read").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("chapter-reader").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(5_000) { ReadingStore(compose.activity).position() != null }
        val saved = ReadingStore(compose.activity).position()!!
        assertTrue(saved.chapter >= 1)
        assertTrue(saved.verse >= 1)
        compose.onNodeWithTag("chapter-reader").assertExists()
    }

    @Test fun pickerRespondsToRotaryInput() {
        waitForHome()
        compose.onNodeWithText("Go to scripture").performScrollTo().performClick()
        compose.onNodeWithContentDescription("SCRIPTURES: Book of Mormon").assertExists()
        repeat(4) { rotary(-1f) }
        SystemClock.sleep(600)
        compose.onNodeWithContentDescription("SCRIPTURES: Book of Mormon").assertDoesNotExist()
        compose.onNodeWithText("Choose book").performClick()
        compose.onNodeWithText("Choose chapter").assertExists()
    }

    @Test fun screenAwakeAppliesOnlyWhileReading() {
        waitForHome()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Reading settings"))
        compose.onNodeWithText("Reading settings").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Keep screen awake"))
        compose.onNodeWithText("Keep screen awake").performClick()
        compose.onNodeWithText("On while reading").assertExists()
        assertEquals(0, compose.activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Return to the home menu, then enter the reader.
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitForHome()
        val label = if (ReadingStore(compose.activity).position() == null) "Start reading" else "Continue reading"
        compose.onNodeWithText(label).performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("chapter-reader").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle {
            assertTrue(compose.activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
        }
    }
}
