package com.ricordella.app.domain

/**
 * Programmazione delle notifiche dei promemoria.
 * Unico punto che programma, annulla e ricostruisce gli allarmi.
 */
interface ReminderScheduler {
    /** Ricalcola il piano delle notifiche e programma il prossimo allarme (o lo annulla). */
    suspend fun refresh()

    /** Rimuove l'eventuale notifica già visibile per il promemoria. */
    fun dismissNotification(reminderId: String)
}
