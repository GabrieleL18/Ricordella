package com.ricordella.app.core.alarm

import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.ricordella.app.R
import com.ricordella.app.RicordellaApplication
import com.ricordella.app.domain.model.SnoozeOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Fa suonare una sveglia: suono in loop sul volume della sveglia, vibrazione e notifica
 * a tutto schermo che apre [AlarmActivity]. È un servizio in primo piano così la sveglia
 * continua a suonare anche con l'app chiusa. "Ferma" completa il promemoria (se si ripete
 * passa al giorno dopo), "Posticipa" lo rimanda di [SNOOZE_MINUTES] minuti.
 */
class AlarmRingService : Service() {

    data class Ringing(val reminderId: String, val title: String)

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    // Nessuno la ferma: dopo qualche minuto si posticipa da sola, come le sveglie vere.
    private val timeout = Runnable { finish(snooze = true) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> {
                val id = intent.getStringExtra(EXTRA_REMINDER_ID)
                if (id == null) stopSelf() else ring(Ringing(id, intent.getStringExtra(EXTRA_TITLE).orEmpty()))
            }
            ACTION_STOP -> finish(snooze = false)
            ACTION_SNOOZE -> finish(snooze = true)
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun ring(alarm: Ringing) {
        // Se ne suonava già un'altra, la nuova prende il suo posto (la vecchia resta attiva nell'app).
        silence()
        _ringing.value = alarm
        createChannel(this)
        startForeground(NOTIFICATION_ID, notification(alarm))

        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
                setDataSource(this@AlarmRingService, "android.resource://$packageName/${R.raw.alarm_magic}".toUri())
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()

        vibrator = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) getSystemService(VibratorManager::class.java).defaultVibrator else getSystemService(Vibrator::class.java))
            .also {
                @Suppress("DEPRECATION")
                it.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
                )
            }
        handler.postDelayed(timeout, AUTO_SNOOZE_MS)
    }

    private fun finish(snooze: Boolean) {
        val alarm = _ringing.value
        silence()
        _ringing.value = null
        if (alarm != null) {
            val container = (application as RicordellaApplication).container
            container.applicationScope.launch {
                if (snooze) container.snoozeReminder(alarm.reminderId, SnoozeOption.Minutes(SNOOZE_MINUTES))
                else container.completeReminder(alarm.reminderId)
            }
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun silence() {
        handler.removeCallbacks(timeout)
        player?.run { runCatching { stop() }; release() }
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    override fun onDestroy() {
        silence()
        _ringing.value = null
        super.onDestroy()
    }

    private fun notification(alarm: Ringing) = NotificationCompat.Builder(this, CHANNEL_ALARMS)
        .setSmallIcon(R.drawable.ic_stat_reminder)
        .setContentTitle("⏰ " + alarm.title)
        .setContentText(tr("Sveglia"))
        .setColor(0xFFC9A400.toInt())
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setOngoing(true)
        .setContentIntent(screenIntent())
        .setFullScreenIntent(screenIntent(), true)
        .addAction(0, trf("Posticipa %1\$s min", SNOOZE_MINUTES), serviceIntent(ACTION_SNOOZE))
        .addAction(0, tr("Ferma"), serviceIntent(ACTION_STOP))
        .build()

    private fun screenIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun serviceIntent(action: String): PendingIntent =
        PendingIntent.getService(this, action.hashCode(), Intent(this, AlarmRingService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE)

    companion object {
        const val SNOOZE_MINUTES = 10L
        private const val AUTO_SNOOZE_MS = 5 * 60_000L
        private const val NOTIFICATION_ID = 7_001
        private const val CHANNEL_ALARMS = "alarms"
        private const val ACTION_RING = "com.ricordella.app.alarm.RING"
        private const val ACTION_STOP = "com.ricordella.app.alarm.STOP"
        private const val ACTION_SNOOZE = "com.ricordella.app.alarm.SNOOZE"
        private const val EXTRA_REMINDER_ID = "com.ricordella.app.alarm.REMINDER_ID"
        private const val EXTRA_TITLE = "com.ricordella.app.alarm.TITLE"

        private val _ringing = MutableStateFlow<Ringing?>(null)
        /** La sveglia che sta suonando, osservata dalla schermata a tutto schermo. */
        val ringing: StateFlow<Ringing?> = _ringing.asStateFlow()

        /** Avvia la sveglia. False se Android non lo permette: si ripiega sulla notifica normale. */
        fun start(context: Context, reminderId: String, title: String): Boolean = runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AlarmRingService::class.java).setAction(ACTION_RING).putExtra(EXTRA_REMINDER_ID, reminderId).putExtra(EXTRA_TITLE, title),
            )
        }.isSuccess

        fun stop(context: Context) = context.startService(Intent(context, AlarmRingService::class.java).setAction(ACTION_STOP))

        fun snooze(context: Context) = context.startService(Intent(context, AlarmRingService::class.java).setAction(ACTION_SNOOZE))

        /** Canale senza suono: suona il servizio, in loop, sul volume della sveglia. */
        private fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ALARMS, tr("Sveglie"), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
