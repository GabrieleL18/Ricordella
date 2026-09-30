package com.ricordella.app.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ricordella.app.RicordellaApplication
import com.ricordella.app.core.AppContainer
import com.ricordella.app.domain.model.SnoozeOption
import kotlinx.coroutines.launch

/** Esegue lavoro asincrono da un BroadcastReceiver mantenendolo vivo fino al termine. */
private fun BroadcastReceiver.runAsync(context: Context, block: suspend (AppContainer) -> Unit) {
    val container = (context.applicationContext as RicordellaApplication).container
    val pending = goAsync()
    container.applicationScope.launch {
        try {
            block(container)
        } finally {
            pending.finish()
        }
    }
}

/** Riceve l'allarme programmato: mostra i promemoria dovuti e programma il successivo. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALARM) return
        runAsync(context) { it.reminderScheduler.refresh() }
    }

    companion object {
        const val ACTION_ALARM = "com.ricordella.app.action.REMINDER_ALARM"
    }
}

/** Gestisce le azioni della notifica: Completa e Rimanda. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        runAsync(context) { container ->
            when (intent.action) {
                ACTION_COMPLETE -> container.completeReminder(reminderId)
                ACTION_SNOOZE_10_MINUTES -> container.snoozeReminder(reminderId, SnoozeOption.TEN_MINUTES)
                ACTION_SNOOZE_TOMORROW -> container.snoozeReminder(reminderId, SnoozeOption.TomorrowMorning)
            }
        }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "com.ricordella.app.extra.REMINDER_ID"
        const val ACTION_COMPLETE = "com.ricordella.app.action.COMPLETE"
        const val ACTION_SNOOZE_10_MINUTES = "com.ricordella.app.action.SNOOZE_10_MINUTES"
        const val ACTION_SNOOZE_TOMORROW = "com.ricordella.app.action.SNOOZE_TOMORROW"
    }
}

/**
 * Ricostruisce la pianificazione dopo riavvio, aggiornamento dell'app, cambio di ora,
 * data o fuso orario, e variazione del permesso per gli allarmi esatti.
 */
class AlarmRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        runAsync(context) {
            it.reminderScheduler.refresh()
            it.potionReminders.refresh()
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
