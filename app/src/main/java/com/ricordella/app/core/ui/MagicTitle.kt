package com.ricordella.app.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.ricordella.app.core.ui.theme.BrandTitleStyle
import com.ricordella.app.core.ui.theme.ricordellaColors

private const val NAME = "Remindella"
private val Outline = Color(0xFF3B1F73)
private val Gold = listOf(Color(0xFFFFF6B0), Color(0xFFFFD23D), Color(0xFFFF8A00))

/**
 * Il nome dell'app come nella grafica dello store: lettere dorate che "ballano" col bordo viola,
 * una stella al posto del puntino della i e, sotto, la scia di una bacchetta.
 */
@Composable
fun MagicTitle(modifier: Modifier = Modifier, fontSize: TextUnit = BrandTitleStyle.fontSize, trail: Boolean = true) {
    val px = with(LocalDensity.current) { fontSize.toPx() }
    val bolt = MaterialTheme.ricordellaColors.bolt
    val base = BrandTitleStyle.copy(fontSize = fontSize)
    // copy(color) sostituisce la sfumatura di BrandTitleStyle (merge terrebbe il brush).
    val edge = base.copy(
        color = Outline,
        drawStyle = Stroke(width = px * 0.14f, join = StrokeJoin.Round),
        shadow = Shadow(Outline.copy(alpha = 0.5f), Offset(0f, px * 0.06f)),
    )
    val fill = TextStyle(brush = Brush.verticalGradient(Gold), shadow = Shadow(Color(0x66FFC400), blurRadius = px * 0.25f))
    Column(
        modifier
            .width(IntrinsicSize.Max)
            .clearAndSetSemantics { contentDescription = NAME; heading() },
    ) {
        Row {
            NAME.forEachIndexed { i, ch ->
                val even = i % 2 == 0
                val glyph = if (ch == 'i') "ı" else ch.toString()
                Box(Modifier.graphicsLayer { translationY = px * if (even) -0.05f else 0.05f; rotationZ = if (even) -5f else 5f }) {
                    Text(glyph, style = edge)
                    Text(glyph, style = base.merge(fill))
                    // Il puntino della i è una stella che brilla.
                    if (ch == 'i') Canvas(Modifier.matchParentSize()) {
                        val dot = Offset(size.width / 2, size.height * 0.26f)
                        drawCircle(Brush.radialGradient(listOf(bolt.copy(alpha = 0.8f), Color.Transparent), dot, px * 0.2f), px * 0.2f, dot)
                        drawFourPointStar(dot, px * 0.14f, Color.White, rotation = 15f)
                    }
                }
            }
        }
        if (trail) Canvas(Modifier.fillMaxWidth().height((fontSize.value * 0.3f).sp.let { with(LocalDensity.current) { it.toDp() } })) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(w * 0.03f, h * 0.45f)
                quadraticTo(w * 0.35f, h * 1.05f, w * 0.65f, h * 0.55f)
                quadraticTo(w * 0.82f, h * 0.25f, w * 0.96f, h * 0.2f)
            }
            drawPath(
                path,
                Brush.horizontalGradient(listOf(Color.Transparent, bolt)),
                style = Stroke(width = px * 0.06f, cap = StrokeCap.Round),
            )
            drawFourPointStar(Offset(w * 0.96f, h * 0.2f), px * 0.16f, bolt, rotation = 10f)
            drawFourPointStar(Offset(w * 0.72f, h * 0.9f), px * 0.06f, Color.White.copy(alpha = 0.8f))
            drawFourPointStar(Offset(w * 0.5f, h * 0.15f), px * 0.05f, Color.White.copy(alpha = 0.6f))
        }
    }
}
