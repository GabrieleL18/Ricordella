package com.ricordella.app.feature.items

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf
import com.ricordella.app.domain.model.MoneyKind
import com.ricordella.app.domain.model.PersonRecap
import java.io.OutputStream

/** PDF semplice delle spese di un anno: il nome «Remindella», poi per ogni persona l'elenco dei pagamenti. Niente effetti. */
object MoneyPdf {
    private const val WIDTH = 595
    private const val HEIGHT = 842
    private const val MARGIN = 48f
    private const val LINE = 18f

    fun write(out: OutputStream, year: Int, scope: String, recap: List<PersonRecap>, nameOf: (String) -> String) {
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF111111.toInt(); textSize = 11f }
        val bold = Paint(ink).apply { typeface = Typeface.DEFAULT_BOLD }
        val title = Paint(bold).apply { textSize = 26f }
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

        canvas.drawText("Remindella", MARGIN, y + 22f, title)
        y += 44f
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
}
