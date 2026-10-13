package com.ricordella.app.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.ricordella.app.domain.model.CalendarGroup
import com.ricordella.app.core.ui.theme.Tone
import com.ricordella.app.core.ui.theme.ricordellaColors
import com.ricordella.app.domain.model.ItemGroup
import com.ricordella.app.domain.model.ReminderType
import kotlin.math.absoluteValue

/*
 * Ogni accento ha un significato stabile, sempre accompagnato da un'icona o da un testo
 * (il colore non è mai l'unico segnale):
 * azzurro = attività, rinnovi e visite mediche · lavanda = eventi e feste · corallo = compleanni e scadenze vere ·
 * giallo = manutenzioni · menta = garanzie e pagamenti.
 */

val ReminderType.tone: Tone
    @Composable
    get() = with(MaterialTheme.ricordellaColors) {
        when (this@tone) {
            ReminderType.TASK, ReminderType.RENEWAL, ReminderType.MEDICAL_VISIT -> cyan
            ReminderType.EVENT, ReminderType.OTHER, ReminderType.HOLIDAY -> lavender
            ReminderType.BIRTHDAY, ReminderType.DEADLINE, ReminderType.ALARM -> coral
            ReminderType.MAINTENANCE -> pear
            ReminderType.WARRANTY, ReminderType.PAYMENT, ReminderType.VACATION -> mint
        }
    }

val ItemGroup.tone: Tone
    @Composable
    get() = with(MaterialTheme.ricordellaColors) {
        when (this@tone) {
            ItemGroup.VEHICLES -> cyan
            ItemGroup.HOME -> pear
            ItemGroup.ELECTRONICS -> lavender
            ItemGroup.DOCUMENTS -> coral
            ItemGroup.GENERIC -> mint
        }
    }

/** Tono stabile derivato da un testo (es. il nome di una persona), per avatar colorati. */
@Composable
fun toneFor(seed: String): Tone {
    val colors = MaterialTheme.ricordellaColors
    val tones = listOf(colors.cyan, colors.lavender, colors.coral, colors.pear, colors.mint)
    return tones[seed.lowercase().hashCode().absoluteValue % tones.size]
}

/** Colore di base di un gruppo del calendario (quello della legenda). */
val CalendarGroup.defaultTone: Tone
    @Composable
    get() = with(MaterialTheme.ricordellaColors) {
        when (this@defaultTone) {
            CalendarGroup.TASKS -> cyan
            CalendarGroup.EVENTS -> lavender
            CalendarGroup.DEADLINES -> coral
            CalendarGroup.PAYMENTS -> mint
        }
    }

/** Colore di un gruppo nel calendario: quello scelto nelle Impostazioni o quello dell'app. */
@Composable
fun calendarColor(group: CalendarGroup): Color =
    LocalAppSettings.current.calendarColors[group]?.let { Color(it) } ?: group.defaultTone.solid

/** Tono di un promemoria nel calendario: col colore scelto nelle Impostazioni, altrimenti quello del tipo. */
val ReminderType.calendarTone: Tone
    @Composable
    get() {
        val custom = LocalAppSettings.current.calendarColors[CalendarGroup.of(this)]?.let { Color(it) } ?: return tone
        return Tone(
            container = custom.copy(alpha = 0.22f).compositeOver(MaterialTheme.colorScheme.surface),
            content = MaterialTheme.colorScheme.onSurface,
            solid = custom,
        )
    }
