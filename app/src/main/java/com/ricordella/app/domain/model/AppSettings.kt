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

/** Come gestire le operazioni annuali (feste, pulizia): da sole, chiedendo prima, o mai. */
@Serializable
enum class AutoMode { AUTOMATIC, ASK, OFF }

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
    /** Suoni brevi quando si sceglie una categoria o una cosa. */
    val soundsEnabled: Boolean = true,
    /** Configurazione iniziale (tutorial ed eventuale import dal calendario) completata. */
    val onboardingDone: Boolean = false,
    /** Il tutorial di esporta/importa è già stato mostrato. */
    val backupTutorialSeen: Boolean = false,
    /** Giorno (epochDay) da cui contare l'intervallo per chiedere un nuovo backup. */
    val backupCheckEpochDay: Long? = null,
    /** Ogni quanti giorni proporre di aggiornare il backup (1, 7, 30, 90, 365). */
    val backupIntervalDays: Int = 90,
    /** File del backup scelto dall'utente: ogni esportazione lo sovrascrive. */
    val backupTargetUri: String? = null,
    /** Spostamento annuale delle feste importate all'anno nuovo. */
    val holidayMode: AutoMode = AutoMode.ASK,
    /** Pulizia annuale dei promemoria vecchi e poco utili. */
    val cleanupMode: AutoMode = AutoMode.ASK,
    /** Sezione Sviluppatore sbloccata (7 tocchi sulla versione). */
    val developerMode: Boolean = false,
    /** Ultimo anno in cui è stata proposta la pulizia dei promemoria vecchi. */
    val lastCleanupYear: Int? = null,
)
