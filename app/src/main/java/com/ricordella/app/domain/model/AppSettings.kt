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

/** Quali promemoria, oltre alle sveglie, suonano a tutto schermo finché non si risponde. */
@Serializable
enum class InsistentLevel {
    NONE, URGENT, IMPORTANT;

    fun applies(priority: Priority): Boolean = when (this) {
        NONE -> false
        URGENT -> priority == Priority.URGENT
        IMPORTANT -> priority != Priority.NORMAL
    }
}

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
    /** Tutorial che compaiono da soli la prima volta (si possono sempre riaprire dal "?"). */
    val tutorialsEnabled: Boolean = true,
    /** Il tutorial di "Scrivi al volo" è già stato mostrato. */
    val quickEntryTutorialSeen: Boolean = false,
    /** Ultima versione dell'app di cui si sono viste le novità (0 = prima installazione, nessuna novità). */
    val newsSeenVersion: Int = 0,
    /** Giorno (epochDay) da cui contare l'intervallo per chiedere un nuovo backup. */
    val backupCheckEpochDay: Long? = null,
    /** Ogni quanti giorni aggiornare il backup (1, 7, 30, 90, 365): di default ogni giorno. */
    val backupIntervalDays: Int = 1,
    /** File del backup scelto dall'utente: ogni esportazione lo sovrascrive. */
    val backupTargetUri: String? = null,
    /** Orario del backup automatico (sul file scelto o in Download/Remindella); null = spento. */
    val autoBackupTime: LocalTime? = LocalTime.of(22, 0),
    /** Backup ogni 1, 3 o 6 ore invece che a giorni (0 = si usano [backupIntervalDays] e [autoBackupTime]). */
    val backupEveryHours: Int = 0,
    /** Ultimo backup riuscito (millisecondi): da qui si contano le ore. */
    val lastBackupMillis: Long? = null,
    /** Spostamento annuale delle feste importate all'anno nuovo. */
    val holidayMode: AutoMode = AutoMode.ASK,
    /** Pulizia annuale dei promemoria vecchi e poco utili. */
    val cleanupMode: AutoMode = AutoMode.ASK,
    /** File condiviso con un'altra persona (su Drive o altra app di file); null = non condiviso. */
    val sharedFileUri: String? = null,
    /** Ultima sincronizzazione riuscita del file condiviso (millisecondi). */
    val sharedLastSync: Long? = null,
    /** Sincronizzazione automatica del file condiviso (altrimenti solo con "Sincronizza ora"). */
    val sharedAutoSync: Boolean = true,
    /** Chi sono io tra le persone (su questo telefono): le cose degli altri chiedono conferma. */
    val sharedMeId: String? = null,
    /** Nome di [sharedMeId], scritto nelle attività che vede l'altra persona. */
    val sharedMeName: String? = null,
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
    /** I promemoria importanti/urgenti suonano come la sveglia, insistenti, finché non si risponde. */
    val insistentLevel: InsistentLevel = InsistentLevel.NONE,
    /** Si è tornati nell'app dopo aver aperto PayPal: il maghetto del riquadro "supporto" resta innamorato. */
    val supportLoved: Boolean = false,
    /** Pozioni: il promemoria per bere acqua, separato da tutto il resto. */
    val potions: PotionSettings = PotionSettings(),
    /** Buoni propositi di tutti gli anni. */
    val resolutions: List<Resolution> = emptyList(),
    /** Ultimo anno per cui si è risposto all'invito a scrivere i buoni propositi. */
    val resolutionsAskedYear: Int? = null,
    /** Ultimo anno di cui si è visto il recap dei buoni propositi. */
    val resolutionsRecapYear: Int? = null,
    /** Versione dei termini d'uso accettata (0 = mai): sotto [TERMS_VERSION] l'app chiede di accettarli. */
    val termsAcceptedVersion: Int = 0,
    /** Giorno (epochDay) in cui sono stati accettati i termini. */
    val termsAcceptedEpochDay: Long? = null,
    /** Minuti prima di una sveglia ripetuta in cui arriva l'avviso silenzioso "salti questa volta?" (0 = mai). */
    val alarmPreNoticeMinutes: Int = 30,
    /** Nel calendario si vedono solo le sveglie importanti o urgenti (le altre restano nelle liste). */
    val hideNormalAlarmsInCalendar: Boolean = false,
    /** Ciclo mestruale: una voce per persona, e la cronologia delle mestruazioni (solo su questo telefono). */
    val cycleProfiles: List<CycleProfile> = emptyList(),
    val cycleLog: List<CycleEntry> = emptyList(),
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
    /** Acqua bevuta nei giorni passati (epochDay → ml), per il recap dell'ultima settimana. */
    val history: Map<Long, Int> = emptyMap(),
) {
    fun drankOn(epochDay: Long): Int = if (day == epochDay) drankMl else history[epochDay] ?: 0

    /** Aggiunge (o toglie, se negativo) acqua bevuta nel giorno [epochDay]. */
    fun drink(epochDay: Long, ml: Int): PotionSettings {
        val past = if (day != null && day != epochDay) history + (day to drankMl) else history
        return copy(day = epochDay, drankMl = (drankOn(epochDay) + ml).coerceAtLeast(0), history = past.filterKeys { it > epochDay - 14 })
    }

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
