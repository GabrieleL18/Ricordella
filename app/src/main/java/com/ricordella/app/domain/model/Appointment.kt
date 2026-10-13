package com.ricordella.app.domain.model

import kotlinx.serialization.Serializable

/** Come avvisare un cliente dell'appuntamento: si apre l'app scelta col testo già scritto. */
@Serializable
enum class MessageChannel { WHATSAPP, SMS, EMAIL }

@Serializable
data class AppointmentContact(
    val name: String = "",
    val phone: String? = null,
    val email: String? = null,
)

/** Appuntamento con uno o più clienti da avvisare con un messaggio preimpostato. */
@Serializable
data class AppointmentInfo(
    val contacts: List<AppointmentContact> = emptyList(),
    val channels: Set<MessageChannel> = setOf(MessageChannel.WHATSAPP),
    /** Quanto prima dell'appuntamento arriva la notifica per mandare il messaggio, in minuti. */
    val remindMinutesBefore: Int = 1440,
) {
    /**
     * Un messaggio per ogni cliente e canale utilizzabile (WhatsApp e SMS col telefono, email con l'indirizzo).
     * Senza numero né email resta WhatsApp, dove la chat si sceglie a mano. Un cliente che con i canali scelti
     * non si raggiungerebbe (es. solo l'email) riceve il messaggio nei modi che ha.
     */
    val targets: List<Pair<AppointmentContact, MessageChannel>>
        get() = contacts.flatMap { c ->
            val usable = MessageChannel.entries.filter { ch ->
                when (ch) {
                    MessageChannel.EMAIL -> !c.email.isNullOrBlank()
                    MessageChannel.SMS -> !c.phone.isNullOrBlank()
                    MessageChannel.WHATSAPP -> !c.phone.isNullOrBlank() || c.email.isNullOrBlank()
                }
            }
            usable.filter { it in channels }.ifEmpty { usable }.map { c to it }
        }
}

object AppointmentMessage {
    const val DEFAULT_TEMPLATE =
        "Ciao {nome}, ti ricordo il tuo appuntamento \"{titolo}\" di {data} alle {ora}. A presto!"

    /**
     * Riempie il testo preimpostato. I dati dell'appuntamento ci sono sempre: se il testo non contiene
     * {data} o {ora} vengono aggiunti in fondo.
     */
    fun fill(template: String, name: String, title: String, date: String, time: String?): String {
        val base = template.ifBlank { DEFAULT_TEMPLATE }
        val missing = listOfNotNull(
            "{data}".takeIf { it !in base },
            "{ora}".takeIf { time != null && it !in base },
        )
        var full = if (missing.isEmpty()) base else base.trimEnd() + "\n" + "{titolo}: " + missing.joinToString(" ")
        // Tutto il giorno: via anche la parola prima dell'ora ("alle", "at", "um"...).
        if (time == null) full = full.replace(Regex("\\s+\\S+\\s+\\{ora\\}"), "")
        return full
            .replace("{nome}", name.trim())
            .replace("{titolo}", title)
            .replace("{data}", date)
            .replace("{ora}", time.orEmpty())
            .replace(Regex(" {2,}"), " ")
            .replace(Regex(" +([,.!?])"), "$1")
            .trim()
    }
}
