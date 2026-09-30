package com.ricordella.app.domain.date

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Sorgente unica di "adesso". Il fuso viene letto a ogni chiamata, così un cambio
 * di fuso orario del dispositivo è recepito subito. Nei test si usa un Clock fisso.
 */
class TimeSource(
    private val clock: Clock = Clock.systemUTC(),
    private val zoneProvider: () -> ZoneId = ZoneId::systemDefault,
) {
    val zone: ZoneId get() = zoneProvider()

    fun now(): Instant = Instant.now(clock)

    fun localNow(): LocalDateTime = LocalDateTime.ofInstant(now(), zone)

    fun today(): LocalDate = localNow().toLocalDate()

    /** Emette l'ora locale ogni minuto: le schermate si aggiornano al cambio di giorno. */
    fun minuteTicks(): Flow<LocalDateTime> = flow {
        while (true) {
            val now = localNow()
            emit(now.withSecond(0).withNano(0))
            delay((60 - now.second) * 1_000L)
        }
    }.distinctUntilChanged()
}
