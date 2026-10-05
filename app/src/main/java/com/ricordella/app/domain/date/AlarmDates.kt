package com.ricordella.app.domain.date

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import com.ricordella.app.domain.model.Reminder
import com.ricordella.app.domain.model.ReminderStatus
import com.ricordella.app.domain.model.ReminderType

/**
 * Prossimo giorno in cui suona una sveglia delle [time]: oggi se non è ancora passata, altrimenti domani;
 * mai prima di [notBefore] (sveglia in pausa) e sempre in uno dei [days] (vuoto = qualsiasi giorno).
 */
fun nextAlarmDate(time: LocalTime, days: Set<DayOfWeek>, now: LocalDateTime, notBefore: LocalDate? = null): LocalDate {
    var date = now.toLocalDate()
    if (!date.atTime(time).isAfter(now)) date = date.plusDays(1)
    if (notBefore != null && date < notBefore) date = notBefore
    if (days.isNotEmpty()) while (date.dayOfWeek !in days) date = date.plusDays(1)
    return date
}

/** Quando suona la prossima volta questa sveglia (null se non è una sveglia accesa): rimandata, oppure alla sua data e ora. */
fun Reminder.nextRing(now: LocalDateTime, zone: ZoneId): LocalDateTime? {
    if (type != ReminderType.ALARM || !notificationsEnabled || status != ReminderStatus.ACTIVE) return null
    snoozedUntil?.let { LocalDateTime.ofInstant(it, zone) }?.takeIf { it.isAfter(now) }?.let { return it }
    return dueTime?.let { dueDate.atTime(it) }?.takeIf { it.isAfter(now) }
}
