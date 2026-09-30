@file:UseSerializers(LocalTimeSerializer::class, DayOfWeekSerializer::class)

package com.ricordella.app.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
enum class DateFormatStyle { NUMERIC, EXTENDED }

/** Suono della sveglia: l'incantesimo di Remindella o la suoneria sveglia del telefono. */
@Serializable
enum class AlarmSound { MAGIC, SYSTEM }

/** Valori proposti quando si crea un promemoria di un certo tipo. */
@Serializable
data class TypeDefaults(
    val notificationsEnabled: Boolean = true,
    /** Null = tutto il giorno. */
    val time: LocalTime? = null,
    val notifyOffsetMinutes: Int = 0,
)

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
    /** Ogni quanti giorni aggiornare il backup (1, 7, 30, 90, 365): di default ogni giorno. */
    val backupIntervalDays: Int = 1,
    /** File del backup scelto dall'utente: ogni esportazione lo sovrascrive. */
    val backupTargetUri: String? = null,
    /** Orario del backup automatico (sul file scelto o in Download/Remindella); null = spento. */
    val autoBackupTime: LocalTime? = LocalTime.of(22, 0),
    /** Spostamento annuale delle feste importate all'anno nuovo. */
    val holidayMode: AutoMode = AutoMode.ASK,
    /** Pulizia annuale dei promemoria vecchi e poco utili. */
    val cleanupMode: AutoMode = AutoMode.ASK,
    /** Sezione Sviluppatore sbloccata (7 tocchi sulla versione). */
    val developerMode: Boolean = false,
    /** Ultimo anno in cui è stata proposta la pulizia dei promemoria vecchi. */
    val lastCleanupYear: Int? = null,
    /** Predefiniti per tipo; i tipi assenti usano notifiche accese, tutto il giorno e [defaultNotifyOffsetMinutes]. */
    val typeDefaults: Map<ReminderType, TypeDefaults> = emptyMap(),
    val alarmVibration: Boolean = true,
    val alarmSnoozeMinutes: Int = 10,
    /** La sveglia parte piano e alza il volume in mezzo minuto. */
    val alarmCrescendo: Boolean = false,
    val alarmSound: AlarmSound = AlarmSound.MAGIC,
    /** Si è tornati nell'app dopo aver aperto PayPal: il maghetto del riquadro "supporto" resta innamorato. */
    val supportLoved: Boolean = false,
    /** Pozioni: il promemoria per bere acqua, separato da tutto il resto. */
    val potions: PotionSettings = PotionSettings(),
) {
    fun defaultsFor(type: ReminderType): TypeDefaults =
        typeDefaults[type] ?: TypeDefaults(notifyOffsetMinutes = if (type == ReminderType.ALARM) 0 else defaultNotifyOffsetMinutes)
}

/**
 * Pozioni (acqua da bere): obiettivo giornaliero, dimensione di una pozione e fascia oraria in cui
 * ricordarlo. Non sono promemoria: non compaiono in Home, Calendario o liste.
 */
@Serializable
data class PotionSettings(
    val remindersOn: Boolean = true,
    val goalMl: Int = 2000,
    val potionMl: Int = 250,
    val everyMinutes: Int = 60,
    val from: LocalTime = LocalTime.of(9, 0),
    val until: LocalTime = LocalTime.of(21, 0),
    /** Giorno (epochDay) a cui si riferisce [drankMl]: il giorno dopo si riparte da zero. */
    val day: Long? = null,
    val drankMl: Int = 0,
) {
    fun drankOn(epochDay: Long): Int = if (day == epochDay) drankMl else 0

    /** Aggiunge (o toglie, se negativo) acqua bevuta nel giorno [epochDay]. */
    fun drink(epochDay: Long, ml: Int): PotionSettings = copy(day = epochDay, drankMl = (drankOn(epochDay) + ml).coerceAtLeast(0))

    /**
     * Prossimo avviso dopo [now]: dalle [from] ogni [everyMinutes] minuti fino alle [until] comprese;
     * finita la fascia di oggi, il primo di domani.
     */
    fun nextReminder(now: LocalDateTime): LocalDateTime {
        val step = everyMinutes.coerceAtLeast(15).toLong()
        var slot = now.toLocalDate().atTime(from)
        val end = now.toLocalDate().atTime(maxOf(until, from))
        while (slot <= end) {
            if (slot > now) return slot
            slot = slot.plusMinutes(step)
        }
        return now.toLocalDate().plusDays(1).atTime(from)
    }
}
