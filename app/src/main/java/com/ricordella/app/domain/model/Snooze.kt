package com.ricordella.app.domain.model

import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Opzioni di "Rimanda". Il calcolo è centralizzato qui e non nella UI. */
sealed interface SnoozeOption {
    data class Minutes(val minutes: Long) : SnoozeOption
    data object TomorrowMorning : SnoozeOption
    data class At(val dateTime: LocalDateTime) : SnoozeOption

    companion object {
        val TEN_MINUTES = Minutes(10)
        val ONE_HOUR = Minutes(60)
    }
}

/** Istante fino al quale la notifica viene rimandata. */
fun SnoozeOption.snoozeUntil(now: Instant, zone: ZoneId, morningTime: LocalTime): Instant = when (this) {
    is SnoozeOption.Minutes -> now.plusSeconds(minutes * 60)
    SnoozeOption.TomorrowMorning -> now.atZone(zone).toLocalDate().plusDays(1).atTime(morningTime).atZone(zone).toInstant()
    is SnoozeOption.At -> dateTime.atZone(zone).toInstant()
}
