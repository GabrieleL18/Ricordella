package com.ricordella.app.core.ui

import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.ElectricScooter
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.Microwave
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.TabletAndroid
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.ui.graphics.vector.ImageVector
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.domain.model.ItemGroup
import com.ricordella.app.domain.model.ItemKind
import com.ricordella.app.domain.model.OdometerStatus
import com.ricordella.app.domain.model.PersonItemRole
import com.ricordella.app.domain.model.Priority
import com.ricordella.app.domain.model.RecurrenceFrequency
import com.ricordella.app.domain.model.RecurrenceRule
import com.ricordella.app.domain.model.ReminderType
import java.time.format.DateTimeFormatter
import java.util.Locale

/* Testi e icone con cui i modelli di dominio vengono mostrati nell'interfaccia. */

val ReminderType.label: String
    get() = when (this) {
        ReminderType.TASK -> tr("Attività")
        ReminderType.EVENT -> tr("Evento")
        ReminderType.VACATION -> tr("Vacanza")
        ReminderType.MEDICAL_VISIT -> tr("Visita medica")
        ReminderType.HOLIDAY -> tr("Festa")
        ReminderType.DEADLINE -> tr("Scadenza")
        ReminderType.BIRTHDAY -> tr("Compleanno")
        ReminderType.WARRANTY -> tr("Garanzia")
        ReminderType.MAINTENANCE -> tr("Manutenzione")
        ReminderType.PAYMENT -> tr("Pagamento")
        ReminderType.RENEWAL -> tr("Rinnovo")
        ReminderType.OTHER -> tr("Altro")
        ReminderType.ALARM -> tr("Allarme")
    }

val ReminderType.icon: ImageVector
    get() = when (this) {
        ReminderType.TASK -> Icons.Rounded.TaskAlt
        ReminderType.EVENT -> Icons.Rounded.Event
        ReminderType.VACATION -> Icons.Rounded.BeachAccess
        ReminderType.MEDICAL_VISIT -> Icons.Rounded.MedicalServices
        ReminderType.HOLIDAY -> Icons.Rounded.Celebration
        ReminderType.DEADLINE -> Icons.Rounded.HourglassBottom
        ReminderType.BIRTHDAY -> Icons.Rounded.Cake
        ReminderType.WARRANTY -> Icons.Rounded.VerifiedUser
        ReminderType.MAINTENANCE -> Icons.Rounded.Build
        ReminderType.PAYMENT -> Icons.Rounded.Payments
        ReminderType.RENEWAL -> Icons.Rounded.Autorenew
        ReminderType.OTHER -> Icons.Rounded.Notifications
        ReminderType.ALARM -> Icons.Rounded.Alarm
    }

/** Emoji del tipo: per widget e notifiche, dove le icone di Compose non si possono usare. */
val ReminderType.emoji: String
    get() = when (this) {
        ReminderType.TASK -> "✅"
        ReminderType.EVENT -> "📅"
        ReminderType.VACATION -> "🏖️"
        ReminderType.MEDICAL_VISIT -> "🩺"
        ReminderType.HOLIDAY -> "🎉"
        ReminderType.DEADLINE -> "⏳"
        ReminderType.BIRTHDAY -> "🎂"
        ReminderType.WARRANTY -> "🛡️"
        ReminderType.MAINTENANCE -> "🔧"
        ReminderType.PAYMENT -> "💳"
        ReminderType.RENEWAL -> "🔁"
        ReminderType.OTHER -> "🔔"
        ReminderType.ALARM -> "⏰"
    }

val Priority.label: String
    get() = when (this) {
        Priority.NORMAL -> tr("Normale")
        Priority.IMPORTANT -> tr("Importante")
        Priority.URGENT -> tr("Urgente")
    }

val Priority.icon: ImageVector?
    get() = when (this) {
        Priority.NORMAL -> null
        Priority.IMPORTANT -> Icons.Rounded.Flag
        Priority.URGENT -> Icons.Rounded.PriorityHigh
    }

val ItemGroup.label: String
    get() = when (this) {
        ItemGroup.VEHICLES -> tr("Veicoli")
        ItemGroup.HOME -> tr("Casa")
        ItemGroup.ELECTRONICS -> tr("Elettronica")
        ItemGroup.DOCUMENTS -> tr("Documenti")
        ItemGroup.GENERIC -> tr("Generico")
    }

val ItemGroup.icon: ImageVector
    get() = when (this) {
        ItemGroup.VEHICLES -> Icons.Rounded.DirectionsCar
        ItemGroup.HOME -> Icons.Rounded.Home
        ItemGroup.ELECTRONICS -> Icons.Rounded.Smartphone
        ItemGroup.DOCUMENTS -> Icons.Rounded.Description
        ItemGroup.GENERIC -> Icons.Rounded.Inventory2
    }

val ItemKind?.icon: ImageVector
    get() = when (this) {
        ItemKind.CAR, ItemKind.OTHER_VEHICLE -> Icons.Rounded.DirectionsCar
        ItemKind.MOTORBIKE -> Icons.Rounded.TwoWheeler
        ItemKind.SCOOTER -> Icons.Rounded.ElectricScooter
        ItemKind.WASHING_MACHINE -> Icons.Rounded.LocalLaundryService
        ItemKind.DISHWASHER, ItemKind.FRIDGE -> Icons.Rounded.Kitchen
        ItemKind.OVEN -> Icons.Rounded.Microwave
        ItemKind.AIR_CONDITIONER -> Icons.Rounded.AcUnit
        ItemKind.BOILER -> Icons.Rounded.Whatshot
        ItemKind.TV -> Icons.Rounded.Tv
        ItemKind.OTHER_HOME -> Icons.Rounded.Home
        ItemKind.SMARTPHONE -> Icons.Rounded.Smartphone
        ItemKind.TABLET -> Icons.Rounded.TabletAndroid
        ItemKind.COMPUTER, ItemKind.OTHER_ELECTRONICS -> Icons.Rounded.Computer
        ItemKind.CONSOLE -> Icons.Rounded.SportsEsports
        ItemKind.CAMERA -> Icons.Rounded.PhotoCamera
        ItemKind.PERSONAL_DOCUMENT -> Icons.Rounded.Badge
        ItemKind.CONTRACT, ItemKind.OTHER_DOCUMENT -> Icons.Rounded.Description
        ItemKind.WARRANTY_DOCUMENT -> Icons.Rounded.VerifiedUser
        ItemKind.EQUIPMENT -> Icons.Rounded.Handyman
        ItemKind.OBJECT -> Icons.Rounded.Inventory2
        ItemKind.OTHER, null -> Icons.Rounded.Category
    }

/** Nome della categoria: quelle predefinite si traducono, quelle create dall'utente restano come sono. */
val com.ricordella.app.domain.model.Category.label: String
    get() = if (isBuiltIn) tr(name) else name

val PersonItemRole.label: String
    get() = when (this) {
        PersonItemRole.OWNER -> tr("Proprietario")
        PersonItemRole.USER -> tr("Utilizzatore")
        PersonItemRole.OTHER -> tr("Altro")
    }

val OdometerStatus.label: String
    get() = when (this) {
        OdometerStatus.FAR -> tr("Lontana")
        OdometerStatus.NEAR -> tr("Vicina")
        OdometerStatus.DUE -> tr("Da fare")
        OdometerStatus.OVERDUE -> tr("Superata")
    }

private val dayMonth get() = DateTimeFormatter.ofPattern(if (com.ricordella.app.core.i18n.Lang.english) "MMMM d" else "d MMMM", com.ricordella.app.core.i18n.Lang.locale)

/** Es. "Ogni 6 mesi", "Ogni 2 settimane (lun, gio)", "Ogni anno il 18 novembre". */
fun RecurrenceRule.describe(): String {
    val n = interval.coerceAtLeast(1)
    val base = when (frequency) {
        RecurrenceFrequency.DAILY -> if (n == 1) tr("Ogni giorno") else trf("Ogni %1\$s giorni", n)
        RecurrenceFrequency.WEEKLY -> if (n == 1) tr("Ogni settimana") else trf("Ogni %1\$s settimane", n)
        RecurrenceFrequency.MONTHLY -> if (n == 1) tr("Ogni mese") else trf("Ogni %1\$s mesi", n)
        RecurrenceFrequency.YEARLY -> (if (n == 1) tr("Ogni anno") else trf("Ogni %1\$s anni", n)) + tr(" il ") + dayMonth.format(startDate)
    }
    val days = if (frequency == RecurrenceFrequency.WEEKLY && daysOfWeek.isNotEmpty()) {
        daysOfWeek.sortedBy { it.value }.joinToString(prefix = " (", postfix = ")") {
            DateTexts.weekdayFull(it).take(3).lowercase()
        }
    } else {
        ""
    }
    val end = endDate?.let { tr(" fino al ") + DateTexts.date(it, com.ricordella.app.domain.model.DateFormatStyle.NUMERIC) } ?: ""
    return base + days + end
}

/** Etichetta dell'anticipo di notifica. */
fun notifyOffsetLabel(minutes: Int): String = when (minutes) {
    0 -> tr("All'orario del promemoria")
    in 1..59 -> trf("%1\$s minuti prima", minutes)
    60 -> tr("1 ora prima")
    in 61..1439 -> trf("%1\$s ore prima", minutes / 60)
    1440 -> tr("1 giorno prima")
    10080 -> tr("1 settimana prima")
    else -> trf("%1\$s giorni prima", minutes / 1440)
}

val NotifyOffsetPresets = listOf(0, 10, 60, 1440, 2 * 1440, 10080, 30 * 1440)
