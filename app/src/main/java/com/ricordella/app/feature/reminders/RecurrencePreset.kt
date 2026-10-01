package com.ricordella.app.feature.reminders

import com.ricordella.app.core.i18n.tr

import com.ricordella.app.domain.model.RecurrenceFrequency
import com.ricordella.app.domain.model.RecurrenceRule

/** Scelte di ricorrenza proposte nel modulo, più l'intervallo personalizzato. */
enum class RecurrencePreset(private val italianLabel: String, val frequency: RecurrenceFrequency?, val interval: Int) {
    NONE("Nessuna", null, 1),
    DAILY("Ogni giorno", RecurrenceFrequency.DAILY, 1),
    WEEKLY("Ogni settimana", RecurrenceFrequency.WEEKLY, 1),
    BIWEEKLY("Ogni 2 settimane", RecurrenceFrequency.WEEKLY, 2),
    MONTHLY("Ogni mese", RecurrenceFrequency.MONTHLY, 1),
    QUARTERLY("Ogni 3 mesi", RecurrenceFrequency.MONTHLY, 3),
    SEMIANNUAL("Ogni 6 mesi", RecurrenceFrequency.MONTHLY, 6),
    YEARLY("Ogni anno", RecurrenceFrequency.YEARLY, 1),
    CUSTOM("Personalizzata", null, 1);

    val label: String get() = tr(italianLabel)

    companion object {
        fun from(rule: RecurrenceRule?): RecurrencePreset {
            if (rule == null) return NONE
            if (rule.daysOfWeek.isNotEmpty() || rule.endDate != null || rule.fromLastDone) return CUSTOM
            return entries.firstOrNull { it.frequency == rule.frequency && it.interval == rule.interval } ?: CUSTOM
        }
    }
}

val RecurrenceFrequency.unitLabel: String
    get() = when (this) {
        RecurrenceFrequency.DAILY -> tr("giorni")
        RecurrenceFrequency.WEEKLY -> tr("settimane")
        RecurrenceFrequency.MONTHLY -> tr("mesi")
        RecurrenceFrequency.YEARLY -> tr("anni")
    }
