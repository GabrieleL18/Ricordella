@file:UseSerializers(LocalTimeSerializer::class, DayOfWeekSerializer::class)

package com.ricordella.app.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.DayOfWeek
import java.time.LocalTime

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
enum class DateFormatStyle { NUMERIC, EXTENDED }

@Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val dateFormat: DateFormatStyle = DateFormatStyle.EXTENDED,
    val notificationsEnabled: Boolean = true,
    /** Anticipo predefinito proposto per i nuovi promemoria, in minuti. */
    val defaultNotifyOffsetMinutes: Int = 0,
    /** Orario della notifica per i promemoria "tutto il giorno". */
    val allDayNotificationTime: LocalTime = LocalTime.of(9, 0),
)
