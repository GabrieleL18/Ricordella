package com.ricordella.app.domain.date

import com.ricordella.app.domain.model.SchedulingCandidate
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Decide quando notificare i promemoria.
 *
 * Android limita il numero di allarmi per app: per questo si programma un solo allarme,
 * quello della prossima notifica. Quando scatta, si notificano i promemoria dovuti e si
 * programma il successivo. I calcoli usano l'ora locale "da muro" nel fuso corrente,
 * quindi cambi di fuso e ora legale vengono rispettati ricalcolando il piano.
 */
class ReminderAlarmPlanner(
    /** Una notifica persa (es. dispositivo spento) viene recuperata solo entro questo margine. */
    private val missedGrace: Duration = Duration.ofHours(12),
) {

    data class Plan(
        /** Promemoria da notificare subito. */
        val dueNow: List<String>,
        /** Istante del prossimo allarme, se ce n'è uno. */
        val nextAlarmAt: Instant?,
        /** True se la prossima notifica ha un orario preciso (serve un exact alarm). */
        val nextAlarmIsTimed: Boolean,
    )

    fun triggerAt(
        dueDate: LocalDate,
        dueTime: LocalTime?,
        notifyOffsetMinutes: Int,
        snoozedUntil: Instant?,
        allDayTime: LocalTime,
        zone: ZoneId,
    ): Instant {
        if (snoozedUntil != null) return snoozedUntil
        val dueLocal = dueDate.atTime(dueTime ?: allDayTime)
        // Sottrazione sull'ora locale: "1 giorno prima alle 9" resta alle 9 anche col cambio d'ora.
        return dueLocal.minusMinutes(notifyOffsetMinutes.toLong()).atZone(zone).toInstant()
    }

    fun triggerAt(candidate: SchedulingCandidate, allDayTime: LocalTime, zone: ZoneId): Instant =
        triggerAt(
            candidate.dueDate,
            candidate.dueTime,
            candidate.notifyOffsetMinutes,
            candidate.snoozedUntil,
            allDayTime,
            zone,
        )

    /** True se la notifica per l'occorrenza attuale è già stata mostrata. */
    fun isAlreadyNotified(candidate: SchedulingCandidate, trigger: Instant): Boolean {
        val notifiedAt = candidate.lastNotifiedAt ?: return false
        return !notifiedAt.isBefore(trigger)
    }

    fun plan(
        candidates: List<SchedulingCandidate>,
        now: Instant,
        allDayTime: LocalTime,
        zone: ZoneId,
    ): Plan {
        val dueNow = mutableListOf<String>()
        var next: Instant? = null
        var nextIsTimed = false
        for (candidate in candidates) {
            val trigger = triggerAt(candidate, allDayTime, zone)
            if (isAlreadyNotified(candidate, trigger)) continue
            if (!trigger.isAfter(now)) {
                if (Duration.between(trigger, now) <= missedGrace) dueNow += candidate.id
            } else if (next == null || trigger.isBefore(next)) {
                next = trigger
                nextIsTimed = candidate.dueTime != null || candidate.snoozedUntil != null
            }
        }
        return Plan(dueNow, next, nextIsTimed)
    }
}
