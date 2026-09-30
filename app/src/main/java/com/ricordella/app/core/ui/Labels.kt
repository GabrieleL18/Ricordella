package com.ricordella.app.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Badge
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
        ReminderType.TASK -> "Attività"
        ReminderType.EVENT -> "Evento"
        ReminderType.MEDICAL_VISIT -> "Visita medica"
        ReminderType.HOLIDAY -> "Festa"
        ReminderType.DEADLINE -> "Scadenza"
        ReminderType.BIRTHDAY -> "Compleanno"
        ReminderType.WARRANTY -> "Garanzia"
        ReminderType.MAINTENANCE -> "Manutenzione"
        ReminderType.PAYMENT -> "Pagamento"
        ReminderType.RENEWAL -> "Rinnovo"
        ReminderType.OTHER -> "Altro"
    }

val ReminderType.icon: ImageVector
    get() = when (this) {
        ReminderType.TASK -> Icons.Rounded.TaskAlt
        ReminderType.EVENT -> Icons.Rounded.Event
        ReminderType.MEDICAL_VISIT -> Icons.Rounded.MedicalServices
        ReminderType.HOLIDAY -> Icons.Rounded.Celebration
        ReminderType.DEADLINE -> Icons.Rounded.HourglassBottom
        ReminderType.BIRTHDAY -> Icons.Rounded.Cake
        ReminderType.WARRANTY -> Icons.Rounded.VerifiedUser
        ReminderType.MAINTENANCE -> Icons.Rounded.Build
        ReminderType.PAYMENT -> Icons.Rounded.Payments
        ReminderType.RENEWAL -> Icons.Rounded.Autorenew
        ReminderType.OTHER -> Icons.Rounded.Notifications
    }

val Priority.label: String
    get() = when (this) {
        Priority.NORMAL -> "Normale"
        Priority.IMPORTANT -> "Importante"
        Priority.URGENT -> "Urgente"
    }

val Priority.icon: ImageVector?
    get() = when (this) {
        Priority.NORMAL -> null
        Priority.IMPORTANT -> Icons.Rounded.Flag
        Priority.URGENT -> Icons.Rounded.PriorityHigh
    }

val ItemGroup.label: String
    get() = when (this) {
        ItemGroup.VEHICLES -> "Veicoli"
        ItemGroup.HOME -> "Casa"
        ItemGroup.ELECTRONICS -> "Elettronica"
        ItemGroup.DOCUMENTS -> "Documenti"
        ItemGroup.GENERIC -> "Generico"
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

val PersonItemRole.label: String
    get() = when (this) {
        PersonItemRole.OWNER -> "Proprietario"
        PersonItemRole.USER -> "Utilizzatore"
        PersonItemRole.OTHER -> "Altro"
    }

val OdometerStatus.label: String
    get() = when (this) {
        OdometerStatus.FAR -> "Lontana"
        OdometerStatus.NEAR -> "Vicina"
        OdometerStatus.DUE -> "Da fare"
        OdometerStatus.OVERDUE -> "Superata"
    }

private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", Locale.ITALIAN)

/** Es. "Ogni 6 mesi", "Ogni 2 settimane (lun, gio)", "Ogni anno il 18 novembre". */
fun RecurrenceRule.describe(): String {
    val n = interval.coerceAtLeast(1)
    val base = when (frequency) {
        RecurrenceFrequency.DAILY -> if (n == 1) "Ogni giorno" else "Ogni $n giorni"
        RecurrenceFrequency.WEEKLY -> if (n == 1) "Ogni settimana" else "Ogni $n settimane"
        RecurrenceFrequency.MONTHLY -> if (n == 1) "Ogni mese" else "Ogni $n mesi"
        RecurrenceFrequency.YEARLY -> (if (n == 1) "Ogni anno" else "Ogni $n anni") + " il " + dayMonth.format(startDate)
    }
    val days = if (frequency == RecurrenceFrequency.WEEKLY && daysOfWeek.isNotEmpty()) {
        daysOfWeek.sortedBy { it.value }.joinToString(prefix = " (", postfix = ")") {
            DateTexts.weekdayFull(it).take(3).lowercase()
        }
    } else {
        ""
    }
    val end = endDate?.let { " fino al " + DateTexts.date(it, com.ricordella.app.domain.model.DateFormatStyle.NUMERIC) } ?: ""
    return base + days + end
}

/** Etichetta dell'anticipo di notifica. */
fun notifyOffsetLabel(minutes: Int): String = when (minutes) {
    0 -> "All'orario del promemoria"
    in 1..59 -> "$minutes minuti prima"
    60 -> "1 ora prima"
    in 61..1439 -> "${minutes / 60} ore prima"
    1440 -> "1 giorno prima"
    10080 -> "1 settimana prima"
    else -> "${minutes / 1440} giorni prima"
}

val NotifyOffsetPresets = listOf(0, 10, 60, 1440, 2 * 1440, 10080, 30 * 1440)
