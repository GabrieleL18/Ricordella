package com.ricordella.app.feature.items

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.Shader
import com.ricordella.app.R
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf
import com.ricordella.app.domain.model.MoneyKind
import com.ricordella.app.domain.model.PersonRecap
import java.io.OutputStream

/** PDF delle spese di un anno: il nome «Remindella» scritto come nell'app, poi per ogni persona l'elenco dei pagamenti. */
object MoneyPdf {
    private const val WIDTH = 595
    private const val HEIGHT = 842
    private const val MARGIN = 48f
    private const val LINE = 18f

    fun write(context: Context, out: OutputStream, year: Int, scope: String, recap: List<PersonRecap>, nameOf: (String) -> String) {
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF111111.toInt(); textSize = 11f }
        val bold = Paint(ink).apply { typeface = Typeface.DEFAULT_BOLD }
        val muted = Paint(ink).apply { color = 0xFF666666.toInt(); textSize = 10f }
        val rule = Paint().apply { color = 0xFFCCCCCC.toInt(); strokeWidth = 0.8f }

        val doc = PdfDocument()
        var number = 0
        var page = doc.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, ++number).create())
        var canvas: Canvas = page.canvas
        var y = MARGIN

        fun ensure(height: Float) {
            if (y + height <= HEIGHT - MARGIN) return
            doc.finishPage(page)
            page = doc.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, ++number).create())
            canvas = page.canvas
            y = MARGIN
        }

        fun row(left: String, right: String, paint: Paint = ink, indent: Float = 0f) {
            ensure(LINE)
            val rightWidth = paint.measureText(right)
            val room = WIDTH - 2 * MARGIN - indent - rightWidth - 12f
            val text = if (paint.measureText(left) <= room) left
            else left.substring(0, paint.breakText(left, true, room - paint.measureText("…"), null)) + "…"
            canvas.drawText(text, MARGIN + indent, y, paint)
            canvas.drawText(right, WIDTH - MARGIN - rightWidth, y, paint)
            y += LINE
        }

        brandTitle(canvas, context, MARGIN, y + 32f, 36f)
        y += 54f
        canvas.drawText(trf("Spese %1\$s", year) + " · " + scope, MARGIN, y, muted)
        y += 26f

        recap.forEach { person ->
            ensure(LINE * 3)
            row(nameOf(person.personId), DateTexts.money(person.spentCents), bold)
            canvas.drawLine(MARGIN, y - LINE + 5f, WIDTH - MARGIN, y - LINE + 5f, rule)
            person.lines.forEach { line ->
                val label = line.title + if (line.count > 1) " ×${line.count}" else ""
                val amount = (if (line.kind == MoneyKind.INCOME) "+ " else "") + DateTexts.money(line.cents)
                row(label, amount, ink, indent = 8f)
            }
            if (person.receivedCents > 0) row(tr("Entrate"), "+ " + DateTexts.money(person.receivedCents), muted, indent = 8f)
            y += 12f
        }

        if (recap.size > 1) {
            ensure(LINE * 2)
            canvas.drawLine(MARGIN, y - 6f, WIDTH - MARGIN, y - 6f, rule)
            y += 6f
            row(tr("Totale spese"), DateTexts.money(recap.sumOf { it.spentCents }), bold)
            val received = recap.sumOf { it.receivedCents }
            if (received > 0) row(tr("Totale entrate"), "+ " + DateTexts.money(received), bold)
        }

        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }

    /** «Remindella» come l'intestazione dell'app: Fredoka dorata col bordo viola, lettere che ballano e una stella sulla i. */
    private fun brandTitle(canvas: Canvas, context: Context, x: Float, baseline: Float, size: Float) {
        val font = runCatching { Typeface.create(context.resources.getFont(R.font.fredoka), 600, false) }.getOrDefault(Typeface.DEFAULT_BOLD)
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font; textSize = size; color = 0xFF3B1F73.toInt()
            style = Paint.Style.STROKE; strokeWidth = size * 0.14f; strokeJoin = Paint.Join.ROUND
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font; textSize = size
            shader = LinearGradient(0f, baseline - size * 0.8f, 0f, baseline, intArrayOf(0xFFFFF6B0.toInt(), 0xFFFFD23D.toInt(), 0xFFFF8A00.toInt()), null, Shader.TileMode.CLAMP)
        }
        var cx = x + size * 0.1f
        "Remindella".forEachIndexed { index, ch ->
            val glyph = if (ch == 'i') "\u0131" else ch.toString()
            val width = fill.measureText(glyph)
            val even = index % 2 == 0
            canvas.save()
            canvas.rotate(if (even) -5f else 5f, cx + width / 2, baseline - size * 0.3f)
            canvas.translate(0f, size * if (even) -0.05f else 0.05f)
            canvas.drawText(glyph, cx, baseline, edge)
            canvas.drawText(glyph, cx, baseline, fill)
            if (ch == 'i') star(canvas, cx + width / 2, baseline - size * 0.72f, size * 0.13f)
            canvas.restore()
            cx += width
        }
    }

    private fun star(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val path = Path()
        for (k in 0 until 8) {
            val radius = if (k % 2 == 0) r else r * 0.35f
            val angle = Math.PI / 4 * k - Math.PI / 2
            val px = cx + (radius * Math.cos(angle)).toFloat()
            val py = cy + (radius * Math.sin(angle)).toFloat()
            if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = r * 0.5f; strokeJoin = Paint.Join.ROUND; color = 0xFF3B1F73.toInt() })
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFC400.toInt() })
    }
}
