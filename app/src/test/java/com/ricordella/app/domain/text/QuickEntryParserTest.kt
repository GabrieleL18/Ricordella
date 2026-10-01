package com.ricordella.app.domain.text

import com.ricordella.app.domain.model.RecurrenceFrequency
import com.ricordella.app.domain.model.ReminderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class QuickEntryParserTest {

    // Giovedì 1 ottobre 2026, ore 10:00.
    private val now = LocalDateTime.of(2026, 10, 1, 10, 0)
    private fun parse(text: String) = QuickEntryParser.parse(text, now)!!

    @Test
    fun relativeDayAndTime() {
        val entry = parse("dentista domani alle 9")
        assertEquals("Dentista", entry.title)
        assertEquals(LocalDate.of(2026, 10, 2), entry.date)
        assertEquals(LocalTime.of(9, 0), entry.time)
        assertEquals(ReminderType.MEDICAL_VISIT, entry.type)
    }

    @Test
    fun monthNameWithoutYearPicksNextOccurrence() {
        val entry = parse("bollo auto 31 gennaio")
        assertEquals("Bollo auto", entry.title)
        assertEquals(LocalDate.of(2027, 1, 31), entry.date)
        assertNull(entry.time)
    }

    @Test
    fun weeklyOnAWeekday() {
        val entry = parse("ogni lunedì palestra alle 18:30")
        assertEquals("Palestra", entry.title)
        assertEquals(LocalDate.of(2026, 10, 5), entry.date)
        assertEquals(LocalTime.of(18, 30), entry.time)
        assertEquals(RecurrenceFrequency.WEEKLY, entry.recurrence!!.frequency)
        assertEquals(setOf(DayOfWeek.MONDAY), entry.recurrence!!.daysOfWeek)
    }

    @Test
    fun birthdayRepeatsEveryYear() {
        val entry = parse("compleanno di Marco il 12 marzo")
        assertEquals("Compleanno di Marco", entry.title)
        assertEquals(LocalDate.of(2027, 3, 12), entry.date)
        assertEquals(ReminderType.BIRTHDAY, entry.type)
        assertEquals(RecurrenceFrequency.YEARLY, entry.recurrence!!.frequency)
    }

    @Test
    fun monthlyOnADay() {
        val entry = parse("pagare affitto ogni mese il 5")
        assertEquals("Pagare affitto", entry.title)
        assertEquals(LocalDate.of(2026, 10, 5), entry.date)
        assertEquals(5, entry.recurrence!!.dayOfMonth)
        assertEquals(ReminderType.PAYMENT, entry.type)
    }

    @Test
    fun numericDateAndRelativeWeeks() {
        assertEquals(LocalDate.of(2026, 12, 24), parse("regali 24/12").date)
        assertEquals(LocalDate.of(2026, 10, 15), parse("controllo tra 2 settimane").date)
    }

    @Test
    fun englishSentence() {
        val entry = parse("call with Anna tomorrow at 3pm")
        assertEquals("Call with Anna", entry.title)
        assertEquals(LocalDate.of(2026, 10, 2), entry.date)
        assertEquals(LocalTime.of(15, 0), entry.time)
    }

    @Test
    fun onlyTimeAlreadyPassedMeansTomorrow() {
        val entry = parse("chiamare mamma alle 8")
        assertEquals(LocalDate.of(2026, 10, 2), entry.date)
        assertEquals("Chiamare mamma", entry.title)
    }

    @Test
    fun relativeHoursAndHalfPast() {
        val inTwoHours = parse("chiamare Luca tra 2 ore")
        assertEquals("Chiamare Luca", inTwoHours.title)
        assertEquals(LocalTime.of(12, 0), inTwoHours.time)
        assertEquals(LocalTime.of(10, 30), parse("caffè tra mezz’ora").time)
        assertEquals(LocalTime.of(9, 30), parse("dentista domani alle 9 e mezza").time)
        assertEquals(LocalTime.of(17, 45), parse("treno alle 18 meno un quarto").time)
    }

    @Test
    fun mealsOnlyWithPreposition() {
        val dinner = parse("pizza con Luca a cena")
        assertEquals("Pizza con Luca", dinner.title)
        assertEquals(LocalTime.of(20, 0), dinner.time)
        // "cena" senza "a" resta nel titolo.
        assertEquals("Cena con Marco", parse("cena con Marco sabato").title)
    }

    @Test
    fun periodsWeekendEndOfMonthNextWeek() {
        assertEquals(LocalDate.of(2026, 10, 31), parse("pagare F24 fine mese").date)
        assertEquals(LocalDate.of(2026, 10, 3), parse("gita nel weekend").date)
        assertEquals(LocalDate.of(2026, 10, 5), parse("chiamare idraulico prossima settimana").date)
        assertEquals(LocalDate.of(2026, 11, 1), parse("rinnovo next month").date)
    }

    @Test
    fun severalWeekdaysWeekdaysAndEveryEvening() {
        val gym = parse("palestra ogni lunedì e giovedì alle 18")
        assertEquals("Palestra", gym.title)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), gym.recurrence!!.daysOfWeek)
        // Oggi (1 ottobre) è giovedì: si parte oggi.
        assertEquals(LocalDate.of(2026, 10, 1), gym.date)
        assertEquals(5, parse("stand-up giorni feriali alle 9").recurrence!!.daysOfWeek.size)
        val pill = parse("pillola ogni sera")
        assertEquals(RecurrenceFrequency.DAILY, pill.recurrence!!.frequency)
        assertEquals(LocalTime.of(20, 0), pill.time)
    }

    @Test
    fun multiDayAndPriority() {
        val trip = parse("vacanza dal 10 al 15 agosto")
        assertEquals("Vacanza", trip.title)
        assertEquals(LocalDate.of(2027, 8, 10), trip.date)
        assertEquals(LocalDate.of(2027, 8, 15), trip.endDate)
        assertEquals(LocalDate.of(2026, 10, 4), parse("fiera per 3 giorni domani").endDate)
        assertEquals(com.ricordella.app.domain.model.Priority.URGENT, parse("pagare multa urgente").priority)
        assertEquals("Pagare multa", parse("pagare multa !!").title)
        assertEquals(com.ricordella.app.domain.model.Priority.IMPORTANT, parse("pagare multa !").priority)
    }

    @Test
    fun suggestionsCompleteTheWordOrAddWhatIsMissing() {
        assertEquals(listOf("domani", "domenica"), QuickEntryParser.suggestions("dentista dom", now, english = false).take(2))
        assertEquals(true, "ogni settimana" in QuickEntryParser.suggestions("palestra ogni se", now, english = false))
        assertEquals(listOf("domani", "lunedì", "alle 9", "ogni settimana"), QuickEntryParser.suggestions("dentista ", now, english = false))
        assertEquals("dentista domani ", QuickEntryParser.applySuggestion("dentista dom", "domani"))
        assertEquals("palestra ogni settimana ", QuickEntryParser.applySuggestion("palestra ogni se", "ogni settimana"))
        assertEquals("dentista alle 9 ", QuickEntryParser.applySuggestion("dentista ", "alle 9"))
    }

    @Test
    fun germanFrenchSpanish() {
        parse("Zahnarzt morgen um 9").let {
            assertEquals("Zahnarzt", it.title)
            assertEquals(LocalDate.of(2026, 10, 2), it.date)
            assertEquals(LocalTime.of(9, 0), it.time)
        }
        parse("dentiste demain à 9h30").let {
            assertEquals("Dentiste", it.title)
            assertEquals(LocalDate.of(2026, 10, 2), it.date)
            assertEquals(LocalTime.of(9, 30), it.time)
        }
        parse("gimnasio cada lunes a las 18").let {
            assertEquals("Gimnasio", it.title)
            assertEquals(RecurrenceFrequency.WEEKLY, it.recurrence?.frequency)
            assertEquals(setOf(DayOfWeek.MONDAY), it.recurrence?.daysOfWeek)
            assertEquals(LocalTime.of(18, 0), it.time)
        }
        parse("Urlaub vom 10. bis 15. August").let {
            assertEquals("Urlaub", it.title)
            assertEquals(LocalDate.of(2027, 8, 10), it.date)
            assertEquals(LocalDate.of(2027, 8, 15), it.endDate)
        }
        parse("cena por la mañana").let { assertEquals(LocalTime.of(9, 0), it.time) }
        assertEquals(listOf("morgen", "montag"), QuickEntryParser.suggestions("Zahnarzt ", now, "de").take(2))
    }
}
