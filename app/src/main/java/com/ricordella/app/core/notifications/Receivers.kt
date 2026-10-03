package com.ricordella.app.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf
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
                ACTION_COMPLETE -> {
                    container.completeReminder(reminderId)
                    toast(context, tr("Fatto ✓"))
                }
                ACTION_SNOOZE_10_MINUTES -> snooze(context, container, reminderId, SnoozeOption.TEN_MINUTES)
                ACTION_SNOOZE_TOMORROW -> snooze(context, container, reminderId, SnoozeOption.TomorrowMorning)
            }
        }
    }

    /** Rimanda e dice quando tornerà: dalla notifica non c'è altro modo di vedere che ha funzionato. */
    private suspend fun snooze(context: Context, container: AppContainer, reminderId: String, option: SnoozeOption) {
        val until = container.snoozeReminder(reminderId, option) ?: return
        val at = until.atZone(container.time.zone)
        toast(context, trf("Te lo ricordo: %1\$s", DateTexts.relativeWithTime(at.toLocalDate(), at.toLocalTime(), container.time.today()).lowercase()))
    }

    private fun toast(context: Context, text: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show() }
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
            it.cycleReminders.refresh()
            it.autoBackup.refresh()
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
