package com.ricordella.app.core.notifications

import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.ricordella.app.MainActivity
import com.ricordella.app.R
import com.ricordella.app.RicordellaApplication
import com.ricordella.app.domain.date.TimeSource
import com.ricordella.app.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * Promemoria delle pozioni (bere acqua): un allarme tutto suo, separato da quello dei promemoria,
 * che suona nella fascia scelta ogni tot minuti. Arrivati all'obiettivo del giorno smette di chiedere.
 */
class PotionReminders(
    private val context: Context,
    private val settings: SettingsRepository,
    private val time: TimeSource,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val manager = NotificationManagerCompat.from(context)

    fun createChannel() {
        val channel = NotificationChannel(CHANNEL, tr("Pozioni"), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = tr("Ti ricorda di bere un po' d'acqua.")
            setSound(
                "android.resource://${context.packageName}/${R.raw.potion}".toUri(),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
            )
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Riprogramma da solo quando cambiano le pozioni o l'interruttore delle notifiche (non a ogni sorso). */
    fun watch(scope: CoroutineScope) {
        scope.launch {
            settings.settings
                .map { it.notificationsEnabled to it.potions.copy(day = null, drankMl = 0) }
                .distinctUntilChanged()
                .collect { refresh() }
        }
    }

    /** Programma il prossimo avviso (o lo cancella se le pozioni o le notifiche sono spente). */
    suspend fun refresh() {
        val app = settings.current()
        if (!app.notificationsEnabled || !app.potions.remindersOn) {
            alarmManager.cancel(alarmIntent())
            return
        }
        val next = app.potions.nextReminder(LocalDateTime.now(time.zone))
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atZone(time.zone).toInstant().toEpochMilli(), alarmIntent())
    }

    /** È l'ora di una pozione: avvisa se l'obiettivo di oggi non è ancora raggiunto, poi programma la prossima. */
    suspend fun ring() {
        val potions = settings.current().potions
        val drank = potions.drankOn(time.today().toEpochDay())
        if (drank < potions.goalMl && manager.areNotificationsEnabled()) {
            val left = potions.goalMl - drank
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_reminder)
                .setContentTitle(tr("È l'ora di una pozione 🧪"))
                .setContentText(trf("Ancora %1\$s ml per l'obiettivo di oggi", left))
                .setColor(0xFF3FA9E0.toInt())
                .setAutoCancel(true)
                .setContentIntent(openIntent())
                .addAction(0, trf("Bevuta! +%1\$s ml", potions.potionMl), drinkIntent())
                .build()
            try {
                manager.notify(NOTIFICATION_ID, notification)
            } catch (_: SecurityException) {
                // Permesso revocato: le pozioni restano da segnare nell'app.
            }
        }
        refresh()
    }

    suspend fun drink(ml: Int) {
        val today = time.today().toEpochDay()
        settings.update { it.copy(potions = it.potions.drink(today, ml)) }
        manager.cancel(NOTIFICATION_ID)
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_ALARM,
        Intent(context, PotionReceiver::class.java).setAction(PotionReceiver.ACTION_ALARM),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun drinkIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_DRINK,
        Intent(context, PotionReceiver::class.java).setAction(PotionReceiver.ACTION_DRINK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_POTIONS, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val CHANNEL = "potions"
        const val NOTIFICATION_ID = 7_100
        const val REQUEST_ALARM = 7_100
        const val REQUEST_DRINK = 7_101
        const val REQUEST_OPEN = 7_102
    }
}

/** Allarme delle pozioni e tasto "Bevuta!" della notifica. */
class PotionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as RicordellaApplication).container
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                when (intent.action) {
                    ACTION_ALARM -> container.potionReminders.ring()
                    ACTION_DRINK -> container.potionReminders.drink(container.settingsRepository.current().potions.potionMl)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ALARM = "com.ricordella.app.action.POTION_ALARM"
        const val ACTION_DRINK = "com.ricordella.app.action.POTION_DRINK"
    }
}
