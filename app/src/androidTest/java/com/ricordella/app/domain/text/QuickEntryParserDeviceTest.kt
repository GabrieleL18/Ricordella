package com.ricordella.app.domain.text

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ricordella.app.domain.model.RecurrenceFrequency
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Le regex di Android (ICU) non sono quelle del PC: un flag accettato dai test unitari
 * (es. "(?U)") qui faceva crashare la Home. Questo test le esegue sul telefono vero.
 */
@RunWith(AndroidJUnit4::class)
class QuickEntryParserDeviceTest {

    private val now = LocalDateTime.of(2026, 10, 1, 10, 0)

    @Test
    fun ogniRegolaSiCompilaEFunzionaSuAndroid() {
        // Ogni lettera digitata rianalizza il testo: si provano anche i frammenti a metà.
        "ogni lunedì palestra alle 18:30".let { text -> (1..text.length).forEach { QuickEntryParser.parse(text.take(it), now) } }

        val weekly = QuickEntryParser.parse("ogni lunedì palestra alle 18:30", now)!!
        assertEquals("Palestra", weekly.title)
        assertEquals(LocalDate.of(2026, 10, 5), weekly.date)
        assertEquals(LocalTime.of(18, 30), weekly.time)
        assertEquals(RecurrenceFrequency.WEEKLY, weekly.recurrence?.frequency)

        val dentist = QuickEntryParser.parse("dentista domani alle 9", now)!!
        assertEquals(LocalDate.of(2026, 10, 2), dentist.date)
        assertEquals(LocalTime.of(9, 0), dentist.time)

        assertEquals(LocalDate.of(2027, 1, 31), QuickEntryParser.parse("bollo auto 31 gennaio", now)!!.date)
        assertEquals(RecurrenceFrequency.DAILY, QuickEntryParser.parse("pillola ogni 2 giorni", now)!!.recurrence?.frequency)
    }
}
