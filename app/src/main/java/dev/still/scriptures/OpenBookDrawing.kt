package dev.still.scriptures

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// Draw the book directly so page geometry follows the dial without raster assets.
internal fun DrawScope.drawOpenScriptures(progress: Float?, direction: Int) {
    val spineX = size.width / 2
    val halfWidth = size.width * .45f
    val bookHeight = size.height * .64f
    val rearY = size.height * .48f
    val frontY = size.height * .83f
    fun leaf(span: Float, lift: Float = 0f, layer: Float = 0f, curl: Float = 0f): Path {
        val flat = (abs(span) / halfWidth).coerceIn(0f, 1f)
        return Path().apply {
            moveTo(spineX, rearY + layer)
            cubicTo(spineX + span * .22f + curl, rearY + layer - lift * .35f - bookHeight * .14f * flat,
                spineX + span * .7f + curl, rearY + layer - lift * .85f - bookHeight * .19f * flat,
                spineX + span, rearY + layer - lift - bookHeight * .08f * flat)
            cubicTo(spineX + span + curl * .3f, rearY + layer - lift + bookHeight * .12f,
                spineX + span + curl * .3f, frontY + layer - lift - bookHeight * .2f * flat,
                spineX + span, frontY + layer - lift - bookHeight * .09f * flat)
            cubicTo(spineX + span * .66f + curl, frontY + layer - lift * .9f - bookHeight * .15f * flat,
                spineX + span * .24f + curl, frontY + layer - lift * .4f - bookHeight * .1f * flat,
                spineX, frontY + layer)
            close()
        }
    }
    val leather = Color(0xFF493423)
    for (side in listOf(-1, 1)) {
        drawPath(leaf(side * halfWidth * 1.04f, layer = 7.dp.toPx()), leather)
        drawPath(leaf(side * halfWidth * 1.04f, layer = 7.dp.toPx()), Accent.copy(alpha = .55f), style = Stroke(1.dp.toPx()))
        for (layer in 5 downTo 0) {
            val sheet = leaf(side * halfWidth, layer = layer * 1.1.dp.toPx())
            drawPath(sheet, Color(0xFF70604B))
            drawPath(sheet, Accent.copy(alpha = .6f), style = Stroke(.6.dp.toPx()))
        }
        drawPath(leaf(side * halfWidth), Brush.horizontalGradient(listOf(Color(0xFF9C896B), Color(0xFF584935), Color(0xFF9C896B))))
        // Small curved lines make the spread read as pages without presenting fake scripture text.
        for (line in 0..6) {
            val y = rearY + (frontY - rearY) * (.18f + line * .10f)
            val inkLine = Path().apply {
                moveTo(spineX + side * halfWidth * .14f, y)
                cubicTo(spineX + side * halfWidth * .33f, y - bookHeight * .09f,
                    spineX + side * halfWidth * .63f, y - bookHeight * .14f,
                    spineX + side * halfWidth * .84f, y - bookHeight * .11f)
            }
            drawPath(inkLine, Color(0xFF332C23).copy(alpha = .55f), style = Stroke(.6.dp.toPx()))
        }
    }
    drawLine(Color(0xFF2A2118), Offset(spineX, rearY), Offset(spineX, frontY + 7.dp.toPx()), 2.dp.toPx())
    if (progress != null) {
        // A small fan follows the main leaf. Positive input turns right-to-left; reverse input mirrors it.
        for (trailing in 2 downTo 0) {
            val phase = (progress - trailing * .12f).coerceIn(0f, 1f)
            if (phase <= 0f || phase >= 1f) continue
            val angle = phase * PI.toFloat()
            val span = direction * halfWidth * cos(angle)
            val lift = bookHeight * .67f * sin(angle)
            val curl = direction * halfWidth * .1f * sin(angle)
            val sheet = leaf(span, lift, curl = curl)
            val tone = .8f + abs(cos(angle)) * .2f
            drawPath(sheet, Brush.horizontalGradient(listOf(
                Color(0xFF75634B).copy(alpha = tone), Color(0xFFB4A07E).copy(alpha = tone), Color(0xFF8A7659).copy(alpha = tone))))
            drawPath(sheet, Accent.copy(alpha = .85f), style = Stroke(.8.dp.toPx()))
        }
    }
}
