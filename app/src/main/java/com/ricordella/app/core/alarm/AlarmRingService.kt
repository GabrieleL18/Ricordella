package com.ricordella.app.core.alarm

import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.ricordella.app.R
import com.ricordella.app.RicordellaApplication
import com.ricordella.app.domain.model.AlarmSound
import com.ricordella.app.domain.model.AppSettings
import com.ricordella.app.domain.model.SnoozeOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Fa suonare una sveglia: suono in loop sul volume della sveglia, vibrazione e notifica
 * a tutto schermo che apre [AlarmActivity]. È un servizio in primo piano così la sveglia
 * continua a suonare anche con l'app chiusa. "Ferma" completa il promemoria (se si ripete
 * passa al giorno dopo), "Posticipa" lo rimanda dei minuti scelti nelle impostazioni.
 */
class AlarmRingService : Service() {

    /** La sveglia che suona. [night] sceglie la scena: notte (maghetto) o giorno (maghetto e orso). */
    data class Ringing(val reminderId: String, val title: String, val night: Boolean, val snoozeMinutes: Int)

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
                if (id == null) stopSelf() else ring(intent, id)
            }
            ACTION_STOP -> finish(snooze = false)
            ACTION_SNOOZE -> finish(snooze = true)
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun ring(intent: Intent, id: String) {
        // Se ne suonava già un'altra, la nuova prende il suo posto (la vecchia resta attiva nell'app).
        silence()
        val hour = LocalTime.now().hour
        val alarm = Ringing(
            reminderId = id,
            title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
            night = when (intent.getIntExtra(EXTRA_SCENE, SCENE_AUTO)) {
                SCENE_NIGHT -> true
                SCENE_DAY -> false
                else -> hour < DAY_STARTS || hour >= NIGHT_STARTS
            },
            snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE, 10),
        )
        _ringing.value = alarm
        createChannel(this)
        startForeground(NOTIFICATION_ID, notification(alarm))

        val crescendo = intent.getBooleanExtra(EXTRA_CRESCENDO, false)
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
                val sound = if (intent.getStringExtra(EXTRA_SOUND) == AlarmSound.SYSTEM.name) {
                    RingtoneManager.getActualDefaultRingtoneUri(this@AlarmRingService, RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                } else null
                setDataSource(this@AlarmRingService, sound ?: "android.resource://$packageName/${R.raw.alarm_magic}".toUri())
                isLooping = true
                prepare()
                if (crescendo) setVolume(CRESCENDO_START, CRESCENDO_START)
                start()
            }
        }.getOrNull()
        if (crescendo) raiseVolume(step = 1)

        if (intent.getBooleanExtra(EXTRA_VIBRATE, true)) {
            vibrator = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) getSystemService(VibratorManager::class.java).defaultVibrator else getSystemService(Vibrator::class.java))
                .also { vibrate(it) }
        }
        handler.postDelayed(timeout, AUTO_SNOOZE_MS)
    }

    /** Volume crescente: da quasi muto al massimo in [CRESCENDO_STEPS] passi da un secondo e mezzo. */
    private fun raiseVolume(step: Int) {
        if (step > CRESCENDO_STEPS) return
        handler.postDelayed({
            val volume = CRESCENDO_START + (1f - CRESCENDO_START) * step / CRESCENDO_STEPS
            player?.setVolume(volume, volume)
            raiseVolume(step + 1)
        }, 1_500)
    }

    private fun vibrate(vibrator: Vibrator) {
        val pattern = VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0)
        // Con l'uso "sveglia" la vibrazione passa anche in modalità silenziosa o Non disturbare (se le sveglie sono ammesse).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(pattern, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }

    private fun finish(snooze: Boolean) {
        val alarm = _ringing.value
        silence()
        endedBySnooze = snooze
        _ringing.value = null
        if (alarm != null) {
            val container = (application as RicordellaApplication).container
            container.applicationScope.launch {
                if (snooze) container.snoozeReminder(alarm.reminderId, SnoozeOption.Minutes(alarm.snoozeMinutes.toLong()))
                else container.completeReminder(alarm.reminderId)
            }
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun silence() {
        handler.removeCallbacksAndMessages(null)
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
        .addAction(0, trf("Posticipa %1\$s min", alarm.snoozeMinutes), serviceIntent(ACTION_SNOOZE))
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

    /**
     * Riceve la sveglia di prova programmata con un allarme esatto: solo così Android permette
     * di avviare il servizio con l'app in background (un semplice timer verrebbe bloccato).
     */
    class TestReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            runCatching { ContextCompat.startForegroundService(context, Intent(intent).setClass(context, AlarmRingService::class.java).setAction(ACTION_RING)) }
        }
    }

    companion object {
        /** Di giorno (dalle 7 alle 20) la scena ha il sole e l'orso che dorme; altrimenti la notte. */
        const val DAY_STARTS = 7
        const val NIGHT_STARTS = 20
        const val SCENE_AUTO = 0
        const val SCENE_DAY = 1
        const val SCENE_NIGHT = 2
        private const val AUTO_SNOOZE_MS = 5 * 60_000L
        private const val CRESCENDO_START = 0.08f
        private const val CRESCENDO_STEPS = 20
        private const val NOTIFICATION_ID = 7_001
        private const val CHANNEL_ALARMS = "alarms"
        private const val ACTION_RING = "com.ricordella.app.alarm.RING"
        private const val ACTION_STOP = "com.ricordella.app.alarm.STOP"
        private const val ACTION_SNOOZE = "com.ricordella.app.alarm.SNOOZE"
        private const val EXTRA_REMINDER_ID = "com.ricordella.app.alarm.REMINDER_ID"
        private const val EXTRA_TITLE = "com.ricordella.app.alarm.TITLE"
        private const val EXTRA_SCENE = "com.ricordella.app.alarm.SCENE"
        private const val EXTRA_SNOOZE = "com.ricordella.app.alarm.SNOOZE_MINUTES"
        private const val EXTRA_VIBRATE = "com.ricordella.app.alarm.VIBRATE"
        private const val EXTRA_CRESCENDO = "com.ricordella.app.alarm.CRESCENDO"
        private const val EXTRA_SOUND = "com.ricordella.app.alarm.SOUND"

        private val _ringing = MutableStateFlow<Ringing?>(null)
        /** La sveglia che sta suonando, osservata dalla schermata a tutto schermo. */
        val ringing: StateFlow<Ringing?> = _ringing.asStateFlow()

        /** Come è finita l'ultima sveglia: la schermata sceglie l'animazione di posticipo o di stop. */
        @Volatile
        var endedBySnooze: Boolean = false
            private set

        private fun ringIntent(context: Context, reminderId: String, title: String, settings: AppSettings, scene: Int) =
            Intent(context, AlarmRingService::class.java)
                .setAction(ACTION_RING)
                .putExtra(EXTRA_REMINDER_ID, reminderId)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_SCENE, scene)
                .putExtra(EXTRA_SNOOZE, settings.alarmSnoozeMinutes)
                .putExtra(EXTRA_VIBRATE, settings.alarmVibration)
                .putExtra(EXTRA_CRESCENDO, settings.alarmCrescendo)
                .putExtra(EXTRA_SOUND, settings.alarmSound.name)

        /** Avvia la sveglia. False se Android non lo permette: si ripiega sulla notifica normale. */
        fun start(context: Context, reminderId: String, title: String, settings: AppSettings): Boolean = runCatching {
            ContextCompat.startForegroundService(context, ringIntent(context, reminderId, title, settings, SCENE_AUTO))
        }.isSuccess

        /** Sveglia finta tra [delaySeconds], con la scena scelta: passa da un allarme esatto come quelle vere. */
        fun scheduleTest(context: Context, settings: AppSettings, scene: Int, delaySeconds: Long = 10) {
            val intent = ringIntent(context, "developer-alarm", tr("Sveglia di prova"), settings, scene).setClass(context, TestReceiver::class.java)
            val pending = PendingIntent.getBroadcast(context, 7_002, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val manager = context.getSystemService(AlarmManager::class.java)
            val at = System.currentTimeMillis() + delaySeconds * 1000
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        }

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
