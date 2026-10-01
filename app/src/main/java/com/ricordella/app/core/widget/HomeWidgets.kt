package com.ricordella.app.core.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.SpannableString
import android.text.style.StrikethroughSpan
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.ricordella.app.MainActivity
import com.ricordella.app.R
import com.ricordella.app.RicordellaApplication
import com.ricordella.app.core.AppContainer
import com.ricordella.app.core.date.DateTexts
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf
import com.ricordella.app.core.ui.emoji
import com.ricordella.app.domain.model.Reminder
import com.ricordella.app.domain.model.ReminderStatus
import com.ricordella.app.domain.model.isMultiDay
import kotlinx.coroutines.CoroutineScope
import com.ricordella.app.domain.date.RecurrenceCalculator
import com.ricordella.app.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalTime

/*
 * Widget della schermata Home oltre al calendario: Pozioni (acqua), Buoni propositi e Giornata.
 * Stessa grafica del widget calendario: sfondo crema/scuro arrotondato, logo, pulsanti tondi azzurri.
 */

private val Context.container: AppContainer get() = (applicationContext as RicordellaApplication).container

/** Ridisegna i widget quando cambiano acqua o propositi (i promemoria li segue già lo scheduler). */
object HomeWidgets {
    fun watch(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.container.settingsRepository.settings
                .map { it.potions to it.resolutions }
                .distinctUntilChanged()
                .collect {
                    WaterWidgetProvider.requestUpdate(context)
                    ResolutionsWidgetProvider.requestUpdate(context)
                }
        }
    }

    fun updateAll(context: Context) {
        CalendarWidgetProvider.requestUpdate(context)
        WaterWidgetProvider.requestUpdate(context)
        ResolutionsWidgetProvider.requestUpdate(context)
        AgendaWidgetProvider.requestUpdate(context)
    }
}

private fun requestUpdate(context: Context, provider: Class<out AppWidgetProvider>) {
    val manager = AppWidgetManager.getInstance(context)
    val ids = manager.getAppWidgetIds(ComponentName(context, provider))
    if (ids.isEmpty()) return
    context.sendBroadcast(
        Intent(context, provider).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
    )
}

private fun broadcast(context: Context, provider: Class<*>, action: String): PendingIntent = PendingIntent.getBroadcast(
    context,
    action.hashCode(),
    Intent(context, provider).setAction(action),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

private fun openApp(context: Context, requestCode: Int, extras: Intent.() -> Unit = {}): PendingIntent = PendingIntent.getActivity(
    context,
    requestCode,
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).apply(extras),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

/** Template per i tocchi sulle righe delle liste: ogni riga aggiunge i suoi extra. Deve essere modificabile. */
private fun listTemplate(context: Context, provider: Class<*>, action: String): PendingIntent = PendingIntent.getBroadcast(
    context,
    action.hashCode(),
    Intent(context, provider).setAction(action),
    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

/** Collega una ListView al servizio che ne fornisce le righe; l'URI rende unico l'adapter di ogni widget. */
@Suppress("DEPRECATION")
private fun RemoteViews.bindList(context: Context, listId: Int, kind: String, widgetId: Int) {
    val intent = Intent(context, WidgetListService::class.java)
        .putExtra(WidgetListService.EXTRA_KIND, kind)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
    intent.data = Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME))
    setRemoteAdapter(listId, intent)
}

private fun AppWidgetProvider.runAsync(context: Context, block: suspend () -> Unit) {
    val pending = goAsync()
    context.container.applicationScope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Pozioni

/** Widget piccolo dell'acqua: − e + tolgono o aggiungono una pozione, il centro apre le Pozioni. */
class WaterWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        val sign = when (intent.action) {
            ACTION_PLUS -> 1
            ACTION_MINUS -> -1
            else -> return super.onReceive(context, intent)
        }
        runAsync(context) {
            val container = context.container
            container.potionReminders.drink(sign * container.settingsRepository.current().potions.potionMl)
            requestUpdate(context)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        runAsync(context) {
            val container = context.container
            val potions = container.settingsRepository.current().potions
            val drank = potions.drankOn(container.time.today().toEpochDay())
            val views = RemoteViews(context.packageName, R.layout.widget_water)
            val reached = drank >= potions.goalMl
            views.setTextViewText(R.id.water_amount, (if (reached) "🎉 " else "💧 ") + trf("%1\$s ml", drank))
            views.setProgressBar(R.id.water_progress, 100, (drank * 100 / potions.goalMl.coerceAtLeast(1)).coerceAtMost(100), false)
            views.setTextViewText(
                R.id.water_goal,
                if (reached) tr("Obiettivo raggiunto!") else trf("su %1\$s · ±%2\$s ml", potions.goalMl, potions.potionMl),
            )
            views.setOnClickPendingIntent(R.id.water_plus, broadcast(context, WaterWidgetProvider::class.java, ACTION_PLUS))
            views.setOnClickPendingIntent(R.id.water_minus, broadcast(context, WaterWidgetProvider::class.java, ACTION_MINUS))
            views.setOnClickPendingIntent(R.id.water_open, openApp(context, REQUEST_OPEN) { putExtra(MainActivity.EXTRA_OPEN_POTIONS, true) })
            manager.updateAppWidget(ids, views)
        }
    }

    companion object {
        private const val ACTION_PLUS = "com.ricordella.app.widget.WATER_PLUS"
        private const val ACTION_MINUS = "com.ricordella.app.widget.WATER_MINUS"
        private const val REQUEST_OPEN = 2_100

        fun requestUpdate(context: Context) = requestUpdate(context, WaterWidgetProvider::class.java)
    }
}

// ---------------------------------------------------------------------------------------------
// Buoni propositi

/** Lista scorrevole dei propositi dell'anno: un tocco su una riga la spunta (o la toglie). */
class ResolutionsWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return super.onReceive(context, intent)
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        runAsync(context) {
            context.container.settingsRepository.update { app ->
                app.copy(resolutions = app.resolutions.map { if (it.id == id) it.copy(kept = !it.kept) else it })
            }
            requestUpdate(context)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        runAsync(context) {
            val container = context.container
            val year = container.time.today().year
            val list = container.settingsRepository.current().resolutions.filter { it.year == year }
            val kept = list.count { it.kept }
            ids.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_resolutions)
                views.setTextViewText(R.id.res_title, trf("Buoni propositi %1\$s", year))
                views.setTextViewText(R.id.res_count, "$kept/${list.size}")
                views.setViewVisibility(R.id.res_count, if (list.isEmpty()) View.GONE else View.VISIBLE)
                views.setProgressBar(R.id.res_progress, 100, if (list.isEmpty()) 0 else kept * 100 / list.size, false)
                views.setTextViewText(R.id.res_empty, tr("Tocca per scrivere i propositi di quest'anno ✨"))
                views.bindList(context, R.id.res_list, WidgetListService.KIND_RESOLUTIONS, widgetId)
                views.setEmptyView(R.id.res_list, R.id.res_empty)
                views.setPendingIntentTemplate(R.id.res_list, listTemplate(context, ResolutionsWidgetProvider::class.java, ACTION_TOGGLE))
                val open = openApp(context, REQUEST_OPEN) { putExtra(MainActivity.EXTRA_OPEN_RESOLUTIONS, true) }
                views.setOnClickPendingIntent(R.id.res_header, open)
                views.setOnClickPendingIntent(R.id.res_empty, open)
                manager.updateAppWidget(widgetId, views)
            }
            manager.notifyAppWidgetViewDataChanged(ids, R.id.res_list)
        }
    }

    companion object {
        const val EXTRA_ID = "com.ricordella.app.widget.RESOLUTION_ID"
        private const val ACTION_TOGGLE = "com.ricordella.app.widget.RESOLUTION_TOGGLE"
        private const val REQUEST_OPEN = 2_200

        fun requestUpdate(context: Context) = requestUpdate(context, ResolutionsWidgetProvider::class.java)
    }
}

// ---------------------------------------------------------------------------------------------
// Giornata

/** Una riga del widget Giornata: l'intestazione di un giorno o un impegno. */
sealed interface AgendaRow {
    data class Day(val date: LocalDate) : AgendaRow
    data class Empty(val date: LocalDate) : AgendaRow
    data class Item(
        val reminder: Reminder,
        val occurrence: LocalDate,
        val done: Boolean,
        /** Si può spuntare (occorrenza corrente) o riaprire (già fatta); le occorrenze future no. */
        val toggleable: Boolean,
    ) : AgendaRow
}

/** Altezza indicativa (dp) di un'intestazione di giorno e di una riga del widget Giornata. */
private const val AGENDA_DAY_DP = 26
private const val AGENDA_ROW_DP = 44

/**
 * Impegni di [start] e del giorno dopo, fatti compresi: per i ricorrenti già avanzati
 * le occorrenze fatte si ricavano dallo storico dei completamenti.
 * Se in [heightDp] (lo spazio della lista) ci stanno, si aggiungono altri giorni, fino a una settimana.
 */
suspend fun agendaRows(repository: ReminderRepository, calculator: RecurrenceCalculator, start: LocalDate, heightDp: Int = 0): List<AgendaRow> {
    val end = start.plusDays(6)
    val entries = repository.observeForRange(start, end).first().filter { it.reminder.status != ReminderStatus.CANCELLED }
    val completions = repository.getCompletionsBetween(start, end)
    val doneKeys = completions.mapTo(HashSet()) { it.reminderId to it.occurrenceDate }
    val items = LinkedHashMap<Pair<String, LocalDate>, AgendaRow.Item>()
    entries.forEach { entry ->
        val reminder = entry.reminder
        calculator.daysCoveredInRange(reminder, entry.recurrenceRule, start, end).forEach { (date, occurrence) ->
            val done = (reminder.id to occurrence) in doneKeys || (entry.recurrenceRule == null && reminder.status == ReminderStatus.COMPLETED)
            val current = reminder.status == ReminderStatus.ACTIVE && occurrence == reminder.dueDate
            items[reminder.id to date] = AgendaRow.Item(reminder, occurrence, done, reminder.type.isCompletable && (done || current))
        }
    }
    completions.forEach { completion ->
        val key = completion.reminderId to completion.occurrenceDate
        if (key in items) return@forEach
        val reminder = entries.firstOrNull { it.reminder.id == completion.reminderId }?.reminder
            ?: repository.getReminder(completion.reminderId)?.reminder ?: return@forEach
        items[key] = AgendaRow.Item(reminder, completion.occurrenceDate, done = true, toggleable = true)
    }
    val order = compareBy<AgendaRow.Item>({ it.reminder.dueTime ?: LocalTime.MIN }, { it.reminder.title.lowercase() })
    val rows = mutableListOf<AgendaRow>()
    var used = 0
    for (n in 0L..6L) {
        val day = start.plusDays(n)
        val ofDay = items.filterKeys { it.second == day }.values.sortedWith(order)
        val block = listOf(AgendaRow.Day(day)) + ofDay.ifEmpty { listOf(AgendaRow.Empty(day)) }
        val cost = AGENDA_DAY_DP + (block.size - 1) * AGENDA_ROW_DP
        // Oggi e domani ci sono sempre (la lista scorre); gli altri giorni solo se entrano interi.
        if (n >= 2 && used + cost > heightDp) break
        rows += block
        used += cost
    }
    return rows
}

/** Impegni del giorno e dei successivi (quanti ne entrano): le frecce cambiano giorno, il titolo torna a oggi, il cerchio spunta. */
class AgendaWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PREVIOUS, ACTION_NEXT, ACTION_TODAY -> {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val offset = when (intent.action) {
                    ACTION_PREVIOUS -> prefs.getInt(KEY_OFFSET, 0) - 1
                    ACTION_NEXT -> prefs.getInt(KEY_OFFSET, 0) + 1
                    else -> 0
                }
                prefs.edit().putInt(KEY_OFFSET, offset).apply()
                requestUpdate(context)
            }
            ACTION_TOGGLE -> {
                val id = intent.getStringExtra(EXTRA_ID) ?: return
                val occurrence = LocalDate.ofEpochDay(intent.getLongExtra(EXTRA_EPOCH_DAY, 0))
                val done = intent.getBooleanExtra(EXTRA_DONE, false)
                runAsync(context) {
                    val container = context.container
                    if (done) container.undoCompletion(id, occurrence) else container.completeReminder(id)
                    // Lo scheduler ridisegna già i widget; qui si aggiorna subito la lista.
                    requestUpdate(context)
                }
            }
            else -> super.onReceive(context, intent)
        }
    }

    // Ridimensionato: con più spazio entrano più giorni.
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, widgetId: Int, newOptions: android.os.Bundle) =
        onUpdate(context, manager, intArrayOf(widgetId))

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val offset = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_OFFSET, 0)
        val today = context.container.time.today()
        val start = today.plusDays(offset.toLong())
        ids.forEach { widgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_agenda)
            views.setTextViewText(
                R.id.agenda_title,
                if (offset == 0) tr("La tua giornata") else DateTexts.weekdayAndDay(start).replaceFirstChar { it.uppercase() },
            )
            views.bindList(context, R.id.agenda_list, WidgetListService.KIND_AGENDA, widgetId)
            views.setPendingIntentTemplate(R.id.agenda_list, listTemplate(context, AgendaWidgetProvider::class.java, ACTION_TOGGLE))
            views.setOnClickPendingIntent(R.id.agenda_prev, broadcast(context, AgendaWidgetProvider::class.java, ACTION_PREVIOUS))
            views.setOnClickPendingIntent(R.id.agenda_next, broadcast(context, AgendaWidgetProvider::class.java, ACTION_NEXT))
            views.setOnClickPendingIntent(R.id.agenda_title, broadcast(context, AgendaWidgetProvider::class.java, ACTION_TODAY))
            views.setOnClickPendingIntent(R.id.agenda_logo, openApp(context, REQUEST_OPEN))
            manager.updateAppWidget(widgetId, views)
        }
        manager.notifyAppWidgetViewDataChanged(ids, R.id.agenda_list)
    }

    companion object {
        const val EXTRA_ID = "com.ricordella.app.widget.AGENDA_ID"
        const val EXTRA_EPOCH_DAY = "com.ricordella.app.widget.AGENDA_DAY"
        const val EXTRA_DONE = "com.ricordella.app.widget.AGENDA_DONE"
        private const val ACTION_PREVIOUS = "com.ricordella.app.widget.AGENDA_PREVIOUS"
        private const val ACTION_NEXT = "com.ricordella.app.widget.AGENDA_NEXT"
        private const val ACTION_TODAY = "com.ricordella.app.widget.AGENDA_TODAY"
        private const val ACTION_TOGGLE = "com.ricordella.app.widget.AGENDA_TOGGLE"
        private const val REQUEST_OPEN = 2_300
        const val PREFS = "agenda_widget"
        const val KEY_OFFSET = "day_offset"

        fun requestUpdate(context: Context) = requestUpdate(context, AgendaWidgetProvider::class.java)
    }
}

// ---------------------------------------------------------------------------------------------
// Righe delle liste

/** Fornisce le righe delle liste scorrevoli (propositi e giornata). */
class WidgetListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        if (intent.getStringExtra(EXTRA_KIND) == KIND_AGENDA) {
            AgendaFactory(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))
        } else ResolutionsFactory(applicationContext)

    companion object {
        const val EXTRA_KIND = "com.ricordella.app.widget.LIST_KIND"
        const val KIND_RESOLUTIONS = "resolutions"
        const val KIND_AGENDA = "agenda"
    }
}

private fun struck(text: String, done: Boolean): CharSequence =
    if (!done) text else SpannableString(text).apply { setSpan(StrikethroughSpan(), 0, text.length, 0) }

private abstract class SimpleFactory(protected val context: Context) : RemoteViewsService.RemoteViewsFactory {
    override fun onCreate() = Unit
    override fun onDestroy() = Unit
    override fun getLoadingView(): RemoteViews? = null
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false
}

private class ResolutionsFactory(context: Context) : SimpleFactory(context) {
    private var rows = emptyList<com.ricordella.app.domain.model.Resolution>()

    // Gira su un thread del sistema: qui si può aspettare il caricamento.
    override fun onDataSetChanged() {
        rows = runBlocking {
            val year = context.container.time.today().year
            context.container.settingsRepository.current().resolutions.filter { it.year == year }
        }
    }

    override fun getCount() = rows.size
    override fun getViewTypeCount() = 1

    override fun getViewAt(position: Int): RemoteViews {
        val resolution = rows[position]
        return RemoteViews(context.packageName, R.layout.widget_resolution_row).apply {
            setImageViewResource(R.id.row_check, if (resolution.kept) R.drawable.ic_widget_check_on else R.drawable.ic_widget_check_off)
            setTextViewText(R.id.row_text, struck(resolution.text, resolution.kept))
            setTextColor(R.id.row_text, context.getColor(if (resolution.kept) R.color.widget_text_muted else R.color.widget_text))
            setOnClickFillInIntent(R.id.row, Intent().putExtra(ResolutionsWidgetProvider.EXTRA_ID, resolution.id))
        }
    }
}

private class AgendaFactory(context: Context, private val widgetId: Int) : SimpleFactory(context) {
    private var rows = emptyList<AgendaRow>()
    private var today: LocalDate = LocalDate.now()

    override fun onDataSetChanged() {
        val container = context.container
        today = container.time.today()
        val offset = context.getSharedPreferences(AgendaWidgetProvider.PREFS, Context.MODE_PRIVATE).getInt(AgendaWidgetProvider.KEY_OFFSET, 0)
        // Altezza in verticale (MAX_HEIGHT) meno intestazione e margini del widget.
        val height = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId).getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) - 62
        rows = runBlocking { agendaRows(container.reminderRepository, container.recurrenceCalculator, today.plusDays(offset.toLong()), height) }
    }

    override fun getCount() = rows.size
    override fun getViewTypeCount() = 2

    override fun getViewAt(position: Int): RemoteViews = when (val row = rows[position]) {
        is AgendaRow.Day -> RemoteViews(context.packageName, R.layout.widget_agenda_day).apply {
            setTextViewText(R.id.day_title, DateTexts.dayHeader(row.date, today))
        }
        is AgendaRow.Empty -> RemoteViews(context.packageName, R.layout.widget_agenda_row).apply {
            setTextViewText(R.id.row_emoji, "✨")
            setTextViewText(R.id.row_title, tr("Niente in programma"))
            setTextColor(R.id.row_title, context.getColor(R.color.widget_text_muted))
            setViewVisibility(R.id.row_time, View.GONE)
            setViewVisibility(R.id.row_check, View.INVISIBLE)
        }
        is AgendaRow.Item -> RemoteViews(context.packageName, R.layout.widget_agenda_row).apply {
            val reminder = row.reminder
            setTextViewText(R.id.row_emoji, reminder.type.emoji)
            setTextViewText(R.id.row_title, struck(reminder.title, row.done))
            setTextColor(R.id.row_title, context.getColor(if (row.done) R.color.widget_text_muted else R.color.widget_text))
            setTextViewText(
                R.id.row_time,
                when {
                    reminder.isMultiDay -> tr("più giorni")
                    else -> reminder.dueTime?.let(DateTexts::time) ?: tr("tutto il giorno")
                },
            )
            setImageViewResource(
                R.id.row_check,
                when {
                    row.done -> R.drawable.ic_widget_check_on
                    row.toggleable -> R.drawable.ic_widget_check_off
                    else -> R.drawable.ic_widget_check_disabled
                },
            )
            setViewVisibility(R.id.row_check, if (reminder.type.isCompletable) View.VISIBLE else View.INVISIBLE)
            if (row.toggleable) {
                setOnClickFillInIntent(
                    R.id.row,
                    Intent()
                        .putExtra(AgendaWidgetProvider.EXTRA_ID, reminder.id)
                        .putExtra(AgendaWidgetProvider.EXTRA_EPOCH_DAY, row.occurrence.toEpochDay())
                        .putExtra(AgendaWidgetProvider.EXTRA_DONE, row.done),
                )
            }
        }
    }
}
