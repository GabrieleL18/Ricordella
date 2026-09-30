package com.ricordella.app.core.widget

import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.StyleSpan
import android.widget.RemoteViews
import com.ricordella.app.MainActivity
import com.ricordella.app.R
import com.ricordella.app.RicordellaApplication
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.domain.date.ReminderTimeline
import com.ricordella.app.domain.model.ReminderStatus
import com.ricordella.app.domain.model.ReminderWithLinks
import com.ricordella.app.domain.model.isMultiDay
import com.ricordella.app.core.ui.emoji
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Widget "Calendario": mese corrente e successivo, sfogliabili con le frecce (con transizione
 * animata). I giorni con almeno un impegno hanno lo sfondo pieno (rosso se c'è qualcosa di
 * scaduto), oggi è giallo; in alto il prossimo promemoria. Toccando un giorno se ne vede l'anteprima.
 */
class CalendarWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SHOW_NEXT, ACTION_SHOW_PREVIOUS -> flip(context, forward = intent.action == ACTION_SHOW_NEXT)
            ACTION_SELECT_DAY -> {
                // Tocco su un giorno: mostra (o richiude) l'anteprima dei suoi impegni.
                val day = intent.getLongExtra(EXTRA_EPOCH_DAY, Long.MIN_VALUE)
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val current = prefs.getLong(KEY_SELECTED, Long.MIN_VALUE)
                prefs.edit().putLong(KEY_SELECTED, if (current == day) Long.MIN_VALUE else day).apply()
                requestUpdate(context)
            }
            else -> super.onReceive(context, intent)
        }
    }

    /** Cambia mese con l'animazione del ViewFlipper, senza ridisegnare tutto il widget. */
    private fun flip(context: Context, forward: Boolean) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, CalendarWidgetProvider::class.java))
        val views = RemoteViews(context.packageName, R.layout.widget_calendar)
        if (forward) views.showNext(R.id.widget_flipper) else views.showPrevious(R.id.widget_flipper)
        manager.partiallyUpdateAppWidget(ids, views)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val page = (prefs.getInt(KEY_PAGE, 0) + if (forward) 1 else MONTHS - 1) % MONTHS
        prefs.edit().putInt(KEY_PAGE, page).apply()
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val container = (context.applicationContext as RicordellaApplication).container
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                val today = container.time.today()
                val now = container.time.localNow()
                val firstDay = container.settingsRepository.current().firstDayOfWeek
                val months = List(MONTHS) { YearMonth.from(today).plusMonths(it.toLong()) }
                val rangeStart = gridStart(months.first(), firstDay)
                val rangeEnd = gridStart(months.last(), firstDay).plusDays(41)

                val busy = mutableMapOf<LocalDate, Boolean>() // data → true se c'è qualcosa di scaduto
                val byDay = mutableMapOf<LocalDate, MutableList<ReminderWithLinks>>()
                var next: Pair<String, LocalDate>? = null
                container.reminderRepository.observeForRange(rangeStart, rangeEnd).first().forEach { entry ->
                    val reminder = entry.reminder
                    if (reminder.status == ReminderStatus.CANCELLED) return@forEach
                    container.recurrenceCalculator.daysCoveredInRange(reminder, entry.recurrenceRule, rangeStart, rangeEnd).forEach { (date, start) ->
                        val overdue = start == reminder.dueDate && ReminderTimeline.isOverdue(reminder, now)
                        busy[date] = (busy[date] ?: false) || overdue
                        byDay.getOrPut(date) { mutableListOf() } += entry
                        val upcoming = reminder.status == ReminderStatus.ACTIVE && !date.isBefore(today)
                        if (upcoming && (next == null || date.isBefore(next!!.second))) next = reminder.title to date
                    }
                }

                val views = RemoteViews(context.packageName, R.layout.widget_calendar)
                views.setTextViewText(R.id.widget_next, describeNext(next, today))
                views.setOnClickPendingIntent(R.id.widget_logo, open(context, REQUEST_OPEN, null, null))
                views.setOnClickPendingIntent(R.id.widget_next, open(context, REQUEST_OPEN, null, null))
                views.setOnClickPendingIntent(R.id.widget_prev, broadcast(context, ACTION_SHOW_PREVIOUS))
                views.setOnClickPendingIntent(R.id.widget_next_month, broadcast(context, ACTION_SHOW_NEXT))
                views.removeAllViews(R.id.widget_flipper)
                months.forEach { views.addView(R.id.widget_flipper, monthPage(context, it, today, firstDay, busy)) }
                val page = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_PAGE, 0)
                views.setDisplayedChild(R.id.widget_flipper, page.coerceIn(0, MONTHS - 1))
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val selected = prefs.getLong(KEY_SELECTED, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay)
                    ?.takeIf { !it.isBefore(rangeStart) && !it.isAfter(rangeEnd) }
                preview(context, views, selected, selected?.let { byDay[it] }.orEmpty(), today)
                manager.updateAppWidget(ids, views)
            } finally {
                pending.finish()
            }
        }
    }

    private fun describeNext(next: Pair<String, LocalDate>?, today: LocalDate): String {
        val (title, date) = next ?: return tr("Niente in arrivo ✨")
        val when_ = when (val days = ChronoUnit.DAYS.between(today, date)) {
            0L -> tr("oggi")
            1L -> tr("domani")
            else -> trf("tra %1\$s giorni", days)
        }
        return trf("Prossimo: %1\$s · %2\$s", title, when_)
    }

    private fun monthPage(
        context: Context,
        month: YearMonth,
        today: LocalDate,
        firstDay: DayOfWeek,
        busy: Map<LocalDate, Boolean>,
    ): RemoteViews {
        val pkg = context.packageName
        val page = RemoteViews(pkg, R.layout.widget_month_page)
        page.setTextViewText(R.id.widget_month, DateTexts.monthTitle(month))
        repeat(7) { i ->
            val label = RemoteViews(pkg, R.layout.widget_weekday)
            label.setTextViewText(R.id.widget_weekday, DateTexts.weekdayShort(firstDay.plus(i.toLong())))
            page.addView(R.id.widget_weekdays, label)
        }
        val start = gridStart(month, firstDay)
        // Solo le settimane che contengono giorni del mese: 4, 5 o 6 righe.
        val weeks = ((month.atEndOfMonth().toEpochDay() - start.toEpochDay()) / 7 + 1).toInt()
        repeat(weeks) { week ->
            val row = RemoteViews(pkg, R.layout.widget_row)
            repeat(7) { d ->
                val date = start.plusDays((week * 7 + d).toLong())
                val cell = RemoteViews(pkg, R.layout.widget_day)
                if (YearMonth.from(date) == month) {
                    val text = SpannableString(date.dayOfMonth.toString())
                    if (date == today || date in busy) text.setSpan(StyleSpan(Typeface.BOLD), 0, text.length, 0)
                    cell.setTextViewText(R.id.widget_day, text)
                    val background = when {
                        date == today && date in busy -> R.drawable.widget_day_today_busy
                        date == today -> R.drawable.widget_day_today
                        busy[date] == true -> R.drawable.widget_day_overdue
                        date in busy -> R.drawable.widget_day_busy
                        else -> 0
                    }
                    cell.setInt(R.id.widget_day, "setBackgroundResource", background)
                    when {
                        date == today -> cell.setTextColor(R.id.widget_day, 0xFF1F1A00.toInt())
                        date in busy -> cell.setTextColor(R.id.widget_day, 0xFFFFFFFF.toInt())
                        date.dayOfWeek == DayOfWeek.SUNDAY -> cell.setTextColor(R.id.widget_day, context.getColor(R.color.widget_weekend))
                    }
                    cell.setOnClickPendingIntent(R.id.widget_day, selectDay(context, date))
                } else {
                    cell.setTextViewText(R.id.widget_day, "")
                }
                row.addView(R.id.widget_row, cell)
            }
            page.addView(R.id.widget_grid, row)
        }
        return page
    }

    /** Pannello con gli impegni del giorno toccato: tocco su una riga = apre il promemoria. */
    private fun preview(context: Context, views: RemoteViews, day: LocalDate?, entries: List<ReminderWithLinks>, today: LocalDate) {
        if (day == null) {
            views.setViewVisibility(R.id.widget_preview, android.view.View.GONE)
            return
        }
        views.setViewVisibility(R.id.widget_preview, android.view.View.VISIBLE)
        views.setTextViewText(R.id.widget_preview_title, DateTexts.dayHeader(day, today).lowercase().replaceFirstChar { it.uppercase() })
        views.setOnClickPendingIntent(R.id.widget_preview_close, selectDay(context, day))
        views.removeAllViews(R.id.widget_preview_list)
        val sorted = entries.distinctBy { it.reminder.id }.sortedWith(compareBy(ReminderTimeline.chronologicalOrder) { it.reminder })
        if (sorted.isEmpty()) {
            val row = RemoteViews(context.packageName, R.layout.widget_preview_row)
            row.setTextViewText(R.id.widget_preview_row, tr("Niente in programma ✨"))
            views.addView(R.id.widget_preview_list, row)
        }
        sorted.take(PREVIEW_ROWS).forEachIndexed { index, entry ->
            val reminder = entry.reminder
            val row = RemoteViews(context.packageName, R.layout.widget_preview_row)
            val time = if (reminder.isMultiDay) tr("più giorni") else reminder.dueTime?.let(DateTexts::time) ?: tr("tutto il giorno")
            row.setTextViewText(R.id.widget_preview_row, "${reminder.type.emoji}  $time · ${reminder.title}")
            val open = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_REMINDER_ID, reminder.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            row.setOnClickPendingIntent(
                R.id.widget_preview_row,
                PendingIntent.getActivity(context, REQUEST_PREVIEW_ROW + index, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
            views.addView(R.id.widget_preview_list, row)
        }
        if (sorted.size > PREVIEW_ROWS) {
            val more = RemoteViews(context.packageName, R.layout.widget_preview_row)
            more.setTextViewText(R.id.widget_preview_row, trf("+ altri %1\$s: apri l'app", sorted.size - PREVIEW_ROWS))
            more.setOnClickPendingIntent(R.id.widget_preview_row, open(context, REQUEST_OPEN, null, null))
            views.addView(R.id.widget_preview_list, more)
        }
    }

    private fun selectDay(context: Context, date: LocalDate): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_DAY + (date.toEpochDay() % 1000).toInt(),
        Intent(context, CalendarWidgetProvider::class.java).setAction(ACTION_SELECT_DAY).putExtra(EXTRA_EPOCH_DAY, date.toEpochDay()),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun gridStart(month: YearMonth, firstDay: DayOfWeek): LocalDate =
        month.atDay(1).with(TemporalAdjusters.previousOrSame(firstDay))

    private fun open(context: Context, requestCode: Int, action: String?, date: LocalDate?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_ACTION, action)
            .apply { date?.let { putExtra(EXTRA_EPOCH_DAY, it.toEpochDay()) } }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun broadcast(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        action.hashCode(),
        Intent(context, CalendarWidgetProvider::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val EXTRA_ACTION = "com.ricordella.app.extra.WIDGET_ACTION"
        const val EXTRA_EPOCH_DAY = "com.ricordella.app.extra.WIDGET_EPOCH_DAY"
        const val ACTION_REMINDER = "reminder"
        const val ACTION_EVENT = "event"
        const val ACTION_ITEM = "item"
        const val ACTION_PERSON = "person"

        private const val ACTION_SHOW_NEXT = "com.ricordella.app.widget.NEXT_MONTH"
        private const val ACTION_SHOW_PREVIOUS = "com.ricordella.app.widget.PREVIOUS_MONTH"
        private const val ACTION_SELECT_DAY = "com.ricordella.app.widget.SELECT_DAY"
        private const val KEY_SELECTED = "selected_day"
        private const val PREVIEW_ROWS = 4
        private const val REQUEST_PREVIEW_ROW = 30
        private const val PREFS = "calendar_widget"
        private const val KEY_PAGE = "page"
        /** Mesi sfogliabili: il corrente e il successivo. */
        private const val MONTHS = 2

        private const val REQUEST_OPEN = 1
        private const val REQUEST_ACTION = 10
        private const val REQUEST_DAY = 1000

        /** Ridisegna tutti i widget installati (chiamato quando cambiano i promemoria o la data). */
        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CalendarWidgetProvider::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, CalendarWidgetProvider::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
            )
        }
    }
}
