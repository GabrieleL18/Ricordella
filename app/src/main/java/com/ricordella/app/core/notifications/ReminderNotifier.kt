package com.ricordella.app.core.notifications

import android.os.Build
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.ricordella.app.MainActivity
import com.ricordella.app.R
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.domain.date.RelativeDateDescriber
import com.ricordella.app.domain.model.ReminderWithLinks
import com.ricordella.app.domain.model.displayName
import android.widget.RemoteViews
import com.ricordella.app.domain.model.ReminderType
import com.ricordella.app.core.ui.emoji
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Crea il canale e mostra/rimuove le notifiche dei promemoria. */
class ReminderNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.notification_channel_reminders),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_reminders_description)
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(entry: ReminderWithLinks, today: LocalDate) {
        if (!canPostNotifications()) return
        val reminder = entry.reminder
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(reminder.title)
            .setContentText(contentText(entry, today))
            // Aspetto personalizzato: badge del tipo, conto alla rovescia e firma di Ricordella.
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(customView(entry, today, R.layout.notification_reminder))
            .setCustomBigContentView(customView(entry, today, R.layout.notification_reminder_big))
            .setColor(0xFFC9A400.toInt())
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent(reminder.id))
            .apply {
                if (reminder.type.isCompletable) {
                    addAction(0, "Completa", actionIntent(reminder.id, NotificationActionReceiver.ACTION_COMPLETE))
                    addAction(0, "Tra 10 min", actionIntent(reminder.id, NotificationActionReceiver.ACTION_SNOOZE_10_MINUTES))
                    addAction(0, "Domani", actionIntent(reminder.id, NotificationActionReceiver.ACTION_SNOOZE_TOMORROW))
                }
            }
            .build()
        try {
            manager.notify(reminder.id, NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permesso revocato nel frattempo: il promemoria resta visibile nell'app.
        }
    }

    fun dismiss(reminderId: String) = manager.cancel(reminderId, NOTIFICATION_ID)

    fun dismissAll() = manager.cancelAll()

    private fun contentText(entry: ReminderWithLinks, today: LocalDate): String {
        val reminder = entry.reminder
        val whenText = if (reminder.type.isDeadlineLike) {
            RelativeDateDescriber.describeDeadline(reminder.dueDate, today)
        } else {
            DateTexts.relativeWithTime(reminder.dueDate, reminder.dueTime, today)
        }
        val subject = entry.items.firstOrNull()?.name ?: entry.people.firstOrNull()?.displayName
        return listOfNotNull(whenText, subject).joinToString(" · ")
    }

    private fun customView(entry: ReminderWithLinks, today: LocalDate, layout: Int): RemoteViews {
        val reminder = entry.reminder
        val (emoji, tone) = look(reminder.type)
        val days = ChronoUnit.DAYS.between(today, reminder.dueDate)
        return RemoteViews(context.packageName, layout).apply {
            setTextViewText(R.id.notif_badge, emoji)
            setInt(R.id.notif_badge, "setBackgroundResource", tone.badge)
            setTextViewText(R.id.notif_title, reminder.title)
            setTextViewText(R.id.notif_subtitle, contentText(entry, today))
            setTextViewText(R.id.notif_countdown, when {
                days == 0L -> "Oggi"
                days == 1L -> "Domani"
                days > 1 -> "tra $days gg"
                else -> "${-days} gg fa"
            })
            setInt(R.id.notif_countdown, "setBackgroundResource", if (days < 0) NotifTone.CORAL.pill else tone.pill)
            if (layout == R.layout.notification_reminder_big) {
                setTextViewText(R.id.notif_description, bigText(entry, today))
            }
        }
    }

    private enum class NotifTone(val badge: Int, val pill: Int) {
        CYAN(R.drawable.notif_badge_cyan, R.drawable.notif_pill_cyan),
        LAVENDER(R.drawable.notif_badge_lavender, R.drawable.notif_pill_lavender),
        CORAL(R.drawable.notif_badge_coral, R.drawable.notif_pill_coral),
        PEAR(R.drawable.notif_badge_pear, R.drawable.notif_pill_pear),
        MINT(R.drawable.notif_badge_mint, R.drawable.notif_pill_mint),
    }

    /** Emoji e colore per tipo: gli stessi colori dell'app. */
    private fun look(type: ReminderType): Pair<String, NotifTone> = type.emoji to when (type) {
        ReminderType.TASK, ReminderType.MEDICAL_VISIT, ReminderType.RENEWAL -> NotifTone.CYAN
        ReminderType.EVENT, ReminderType.HOLIDAY, ReminderType.OTHER -> NotifTone.LAVENDER
        ReminderType.DEADLINE, ReminderType.BIRTHDAY -> NotifTone.CORAL
        ReminderType.MAINTENANCE -> NotifTone.PEAR
        ReminderType.WARRANTY, ReminderType.PAYMENT, ReminderType.VACATION -> NotifTone.MINT
    }

    private fun bigText(entry: ReminderWithLinks, today: LocalDate): String =
        listOfNotNull(contentText(entry, today), entry.reminder.description?.takeIf { it.isNotBlank() })
            .joinToString("\n")

    private fun openIntent(reminderId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData("ricordella://reminder/$reminderId".toUri())
            .putExtra(MainActivity.EXTRA_REMINDER_ID, reminderId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun actionIntent(reminderId: String, action: String): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(action)
            .setData("ricordella://reminder/$reminderId/$action".toUri())
            .putExtra(NotificationActionReceiver.EXTRA_REMINDER_ID, reminderId)
        return PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
        private const val NOTIFICATION_ID = 1
    }
}
