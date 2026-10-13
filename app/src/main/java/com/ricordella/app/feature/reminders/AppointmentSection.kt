package com.ricordella.app.feature.reminders

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf
import com.ricordella.app.core.notifications.ReminderNotifier
import com.ricordella.app.core.ui.DropdownField
import com.ricordella.app.core.ui.LocalAppSettings
import com.ricordella.app.core.ui.PushButton
import com.ricordella.app.core.ui.SectionHeader
import com.ricordella.app.core.ui.notifyOffsetLabel
import com.ricordella.app.domain.model.AppSettings
import com.ricordella.app.domain.model.AppointmentContact
import com.ricordella.app.domain.model.AppointmentInfo
import com.ricordella.app.domain.model.AppointmentMessage
import com.ricordella.app.domain.model.MessageChannel
import com.ricordella.app.domain.model.Reminder
import java.util.Locale

/** Appuntamento il cui messaggio va mandato appena si apre il dettaglio (dal pulsante della notifica). */
object AppointmentSend {
    var pending by mutableStateOf<String?>(null)
}

val MessageChannel.label: String
    get() = when (this) {
        MessageChannel.WHATSAPP -> "WhatsApp"
        MessageChannel.SMS -> "SMS"
        MessageChannel.EMAIL -> tr("Email")
    }

private val RemindBeforeOptions = listOf(0, 60, 120, 180, 1440, 2 * 1440)

/** Nel modulo: clienti (dalla rubrica o scritti a mano), come avvisarli e quando arriva la notifica per farlo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppointmentFields(info: AppointmentInfo?, onChange: (AppointmentInfo?) -> Unit) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Int?>(null) }
    val current = info ?: AppointmentInfo()
    fun merge(picked: AppointmentContact) {
        // Stesso nome già presente: si completa quel contatto (es. prima il numero, poi l'email).
        val i = current.contacts.indexOfFirst { it.name.isNotBlank() && it.name.equals(picked.name, ignoreCase = true) }
        val contacts = if (i < 0) current.contacts + picked else current.contacts.toMutableList().also {
            it[i] = it[i].copy(phone = picked.phone ?: it[i].phone, email = picked.email ?: it[i].email)
        }
        // Aggiungendo un'email si accende anche il canale Email.
        val channels = if (picked.email != null) current.channels + MessageChannel.EMAIL else current.channels
        onChange(current.copy(contacts = contacts, channels = channels))
    }
    val pickPhone = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.data?.takeIf { result.resultCode == Activity.RESULT_OK }?.let { readPicked(context, it, ContactsContract.CommonDataKinds.Phone.NUMBER) }
            ?.let { (name, number) -> merge(AppointmentContact(name, phone = number)) }
    }
    val pickEmail = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.data?.takeIf { result.resultCode == Activity.RESULT_OK }?.let { readPicked(context, it, ContactsContract.CommonDataKinds.Email.ADDRESS) }
            ?.let { (name, address) -> merge(AppointmentContact(name, email = address)) }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(tr("Appuntamento con clienti"), style = MaterialTheme.typography.bodyLarge)
            Text(tr("Ricordaglielo con un messaggio su WhatsApp, SMS o email"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = info != null, onCheckedChange = { on -> onChange(if (on) AppointmentInfo() else null) })
    }
    AnimatedVisibility(info != null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            current.contacts.forEachIndexed { index, contact ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(contact.name.ifBlank { tr("Senza nome") }, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            listOfNotNull(contact.phone, contact.email).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { editing = index }) { Icon(Icons.Rounded.Edit, contentDescription = tr("Modifica")) }
                    IconButton(onClick = { onChange(current.copy(contacts = current.contacts - contact)) }) {
                        Icon(Icons.Rounded.Close, contentDescription = tr("Rimuovi"))
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(
                    onClick = { launchPicker(context) { pickPhone.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) } },
                    label = { Text(tr("Numero dalla rubrica")) },
                    leadingIcon = { Icon(Icons.Rounded.Contacts, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                AssistChip(
                    onClick = { launchPicker(context) { pickEmail.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Email.CONTENT_URI)) } },
                    label = { Text(tr("Email dalla rubrica")) },
                    leadingIcon = { Icon(Icons.Rounded.Contacts, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                AssistChip(onClick = { editing = -1 }, label = { Text(tr("Scrivi a mano")) }, leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) })
            }
            Text(tr("Come avvisarli"), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MessageChannel.entries.forEach { channel ->
                    val on = channel in current.channels
                    FilterChip(
                        selected = on,
                        onClick = { onChange(current.copy(channels = if (on) current.channels - channel else current.channels + channel)) },
                        label = { Text(channel.label) },
                    )
                }
            }
            DropdownField(
                label = tr("Notifica per mandare il messaggio"),
                options = (RemindBeforeOptions + current.remindMinutesBefore).distinct().sorted(),
                selected = current.remindMinutesBefore,
                optionLabel = { if (it == 0) tr("All'orario dell'appuntamento") else notifyOffsetLabel(it) },
                onSelected = { minutes -> onChange(current.copy(remindMinutesBefore = minutes)) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (current.targets.isEmpty()) {
                Text(
                    tr("Aggiungi un cliente con il numero (per WhatsApp e SMS) o l'email."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    editing?.let { index ->
        ContactDialog(
            initial = current.contacts.getOrNull(index) ?: AppointmentContact(),
            onDismiss = { editing = null },
            onSave = { contact ->
                val contacts = if (index < 0) current.contacts + contact else current.contacts.toMutableList().also { it[index] = contact }
                val channels = if (contact.email != null) current.channels + MessageChannel.EMAIL else current.channels
                onChange(current.copy(contacts = contacts, channels = channels))
                editing = null
            },
        )
    }
}

private fun launchPicker(context: Context, launch: () -> Unit) {
    try {
        launch()
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, tr("Non c'è un'app della rubrica su questo telefono."), Toast.LENGTH_SHORT).show()
    }
}

/** Nome e valore (numero o email) del contatto scelto: il permesso di lettura vale solo per quella riga. */
private fun readPicked(context: Context, uri: Uri, column: String): Pair<String, String>? = try {
    context.contentResolver.query(uri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME, column), null, null, null)?.use { c ->
        if (!c.moveToFirst()) null else (c.getString(0).orEmpty()) to (c.getString(1) ?: return@use null)
    }
} catch (_: SecurityException) {
    null
}

@Composable
private fun ContactDialog(initial: AppointmentContact, onDismiss: () -> Unit, onSave: (AppointmentContact) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var phone by remember { mutableStateOf(initial.phone.orEmpty()) }
    var email by remember { mutableStateOf(initial.email.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Cliente")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(tr("Nome")) }, singleLine = true)
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(tr("Telefono")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(tr("Email")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                if (phone.isBlank() && email.isBlank()) {
                    Text(
                        tr("Senza numero né email il messaggio si manda su WhatsApp: si apre con il testo pronto e scegli tu la chat."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() || phone.isNotBlank() || email.isNotBlank(),
                onClick = { onSave(AppointmentContact(name.trim(), phone.trim().ifEmpty { null }, email.trim().ifEmpty { null })) },
            ) { Text(tr("Salva")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Annulla")) } },
    )
}

/** Testo per un cliente, dal messaggio preimpostato nelle Impostazioni. */
fun appointmentText(settings: AppSettings, reminder: Reminder, contact: AppointmentContact): String =
    AppointmentMessage.fill(
        template = settings.appointmentTemplate ?: tr(AppointmentMessage.DEFAULT_TEMPLATE),
        name = contact.name.substringBefore(' '),
        title = reminder.title,
        date = DateTexts.fullDate(reminder.dueDate),
        time = reminder.dueTime?.let(DateTexts::time),
    )

/** Apre WhatsApp, l'app degli SMS o quella della posta col messaggio già scritto: l'invio lo conferma l'utente. */
fun appointmentIntent(context: Context, contact: AppointmentContact, channel: MessageChannel, text: String, subject: String): Intent = when (channel) {
    MessageChannel.WHATSAPP -> if (contact.phone.isNullOrBlank()) {
        // Senza numero: WhatsApp si apre col testo pronto e la chat si sceglie dall'elenco.
        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).setPackage("com.whatsapp")
    } else {
        Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${whatsappNumber(context, contact.phone)}?text=${Uri.encode(text)}"))
    }
    MessageChannel.SMS -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(contact.phone.orEmpty())}")).putExtra("sms_body", text)
    // Destinatario, oggetto e testo anche nell'indirizzo mailto: alcune app di posta ignorano gli extra.
    MessageChannel.EMAIL -> Intent(
        Intent.ACTION_SENDTO,
        Uri.parse("mailto:${Uri.encode(contact.email.orEmpty(), "@")}?subject=${Uri.encode(subject)}&body=${Uri.encode(text)}"),
    )
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(contact.email.orEmpty()))
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, text)
}

/** WhatsApp vuole il numero internazionale senza "+": senza prefisso si usa quello del paese del telefono. */
private fun whatsappNumber(context: Context, phone: String): String {
    val country = context.getSystemService(TelephonyManager::class.java)?.networkCountryIso?.takeIf { it.isNotBlank() }
        ?: Locale.getDefault().country
    val e164 = PhoneNumberUtils.formatNumberToE164(phone, country.uppercase())
    return (e164 ?: phone).filter(Char::isDigit)
}

/** Apre i messaggi uno dopo l'altro: tornando indietro da uno si passa al successivo. */
fun sendAppointmentMessages(context: Context, settings: AppSettings, reminder: Reminder, targets: List<Pair<AppointmentContact, MessageChannel>>) {
    if (targets.isEmpty()) return
    val intents = targets.map { (contact, channel) -> appointmentIntent(context, contact, channel, appointmentText(settings, reminder, contact), reminder.title) }
    try {
        context.startActivities(intents.reversed().toTypedArray())
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, tr("Manca l'app per inviare questo messaggio."), Toast.LENGTH_SHORT).show()
    }
    NotificationManagerCompat.from(context).cancel(reminder.id, ReminderNotifier.NOTIFICATION_ID_APPOINTMENT)
}

/** Scelta dei messaggi da mandare: uno per volta o tutti insieme. */
@Composable
fun AppointmentSendDialog(reminder: Reminder, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settings = LocalAppSettings.current
    val targets = reminder.appointment?.targets.orEmpty()
    var sent by remember { mutableStateOf(emptySet<Int>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Ricorda l'appuntamento")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (targets.isEmpty()) Text(tr("Aggiungi un cliente con il numero (per WhatsApp e SMS) o l'email."))
                targets.firstOrNull()?.let { (contact, _) ->
                    Text(
                        appointmentText(settings, reminder, contact),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                targets.forEachIndexed { index, target ->
                    val (contact, channel) = target
                    TextButton(onClick = {
                        sendAppointmentMessages(context, settings, reminder, listOf(target))
                        sent = sent + index
                    }) {
                        Icon(if (index in sent) Icons.Rounded.Check else Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            trf("%1\$s a %2\$s", channel.label, contact.name.ifBlank { contact.phone ?: contact.email.orEmpty() }),
                            modifier = Modifier.padding(start = 8.dp).weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (targets.size > 1) TextButton(onClick = {
                // Il dialogo resta aperto: se un'app non torna alla successiva, si manda il resto da qui.
                sendAppointmentMessages(context, settings, reminder, targets)
                sent = targets.indices.toSet()
            }) { Text(tr("Invia tutti")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Chiudi")) } },
    )
}

/** Nel dettaglio: i clienti e il pulsante per mandare loro il promemoria. Dalla notifica si apre già pronto. */
@Composable
fun AppointmentDetail(reminder: Reminder) {
    val info = reminder.appointment ?: return
    val context = LocalContext.current
    val settings = LocalAppSettings.current
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(reminder.id, AppointmentSend.pending) {
        if (AppointmentSend.pending == reminder.id) {
            AppointmentSend.pending = null
            // Un solo messaggio da mandare: si apre direttamente l'app giusta.
            if (info.targets.size == 1) sendAppointmentMessages(context, settings, reminder, info.targets) else open = true
        }
    }
    SectionHeader(tr("Appuntamento"))
    info.contacts.forEach { contact ->
        Text(
            listOfNotNull(contact.name.takeIf { it.isNotBlank() }, contact.phone, contact.email).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    PushButton(
        text = tr("Ricorda l'appuntamento"),
        onClick = { open = true },
        icon = Icons.Rounded.Send,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    if (open) AppointmentSendDialog(reminder, onDismiss = { open = false })
}
