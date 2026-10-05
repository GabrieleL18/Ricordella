package com.ricordella.app.core

import kotlinx.coroutines.flow.map
import com.ricordella.app.domain.usecase.UndoCompletionUseCase
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.ricordella.app.core.notifications.AlarmManagerReminderScheduler
import com.ricordella.app.core.notifications.ReminderNotifier
import com.ricordella.app.data.backup.BackupRepository
import com.ricordella.app.data.local.database.RicordellaDatabase
import com.ricordella.app.data.repository.DataStoreSettingsRepository
import com.ricordella.app.data.repository.RoomAttachmentRepository
import com.ricordella.app.data.repository.RoomItemRepository
import com.ricordella.app.data.repository.RoomMaintenanceRepository
import com.ricordella.app.data.repository.RoomPersonRepository
import com.ricordella.app.data.repository.RoomReminderRepository
import com.ricordella.app.domain.date.RecurrenceCalculator
import com.ricordella.app.domain.date.ReminderAlarmPlanner
import com.ricordella.app.domain.date.TimeSource
import com.ricordella.app.domain.usecase.AddMaintenanceRecordUseCase
import com.ricordella.app.domain.usecase.CompleteReminderUseCase
import com.ricordella.app.domain.usecase.DeleteAllDataUseCase
import com.ricordella.app.domain.usecase.DeleteItemUseCase
import com.ricordella.app.domain.usecase.DeleteReminderUseCase
import com.ricordella.app.domain.usecase.GlobalSearchUseCase
import com.ricordella.app.domain.usecase.Housekeeping
import com.ricordella.app.data.calendar.CalendarImporter
import com.ricordella.app.domain.usecase.ReopenReminderUseCase
import com.ricordella.app.domain.usecase.RestoreBackupUseCase
import com.ricordella.app.domain.usecase.SaveItemUseCase
import com.ricordella.app.domain.usecase.SaveReminderUseCase
import com.ricordella.app.domain.usecase.SnoozeReminderUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock

private val Context.settingsDataStore by preferencesDataStore(name = "settings")
private val Context.demoSettingsDataStore by preferencesDataStore(name = "settings-demo")

/**
 * Composizione delle dipendenze dell'app (dependency injection manuale).
 * Esiste un'unica istanza, posseduta da [com.ricordella.app.RicordellaApplication].
 */
class AppContainer(context: Context) {

    val appContext = context.applicationContext

    /** Scope per lavoro che deve sopravvivere alle singole schermate (es. receiver). */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val time = TimeSource(Clock.systemUTC())
    val recurrenceCalculator = RecurrenceCalculator()
    private val alarmPlanner = ReminderAlarmPlanner()

    /** Modalità demo (screenshot): database e impostazioni separati, i dati veri restano intatti. */
    val isDemo = DemoMode.isOn(appContext)
    val databaseFileName = if (isDemo) "ricordella-demo.db" else "ricordella.db"

    private val database = RicordellaDatabase.create(appContext, databaseFileName)

    /** Svuota il database (solo per rifare i dati demo). */
    suspend fun clearAllTables() = kotlinx.coroutines.withContext(Dispatchers.IO) {
        database.clearAllTables()
        // Le categorie predefinite si creano solo alla nascita del database: senza, cose e promemoria non si salvano.
        database.backupDao().insertCategories(com.ricordella.app.data.local.database.BuiltInCategories.all)
    }

    val reminderRepository = RoomReminderRepository(database.reminderDao())
    val trash = com.ricordella.app.data.trash.Trash(appContext, database.backupDao())
    val personRepository = RoomPersonRepository(database.personDao(), trash)
    val itemRepository = RoomItemRepository(database.itemDao(), database.personDao())
    val maintenanceRepository = RoomMaintenanceRepository(database.maintenanceDao(), trash)
    val attachmentRepository = RoomAttachmentRepository(database.attachmentDao())
    val settingsRepository = DataStoreSettingsRepository(if (isDemo) appContext.demoSettingsDataStore else appContext.settingsDataStore)

    val notifier = ReminderNotifier(appContext)
    val reminderScheduler = AlarmManagerReminderScheduler(
        appContext,
        reminderRepository,
        settingsRepository,
        notifier,
        alarmPlanner,
        time,
    )

    val backupRepository = BackupRepository(appContext, database.backupDao(), settingsRepository, Clock.systemUTC())

    val saveReminder = SaveReminderUseCase(reminderRepository, settingsRepository, reminderScheduler, alarmPlanner, time)
    val completeReminder = CompleteReminderUseCase(reminderRepository, reminderScheduler, recurrenceCalculator, time)
    val reopenReminder = ReopenReminderUseCase(reminderRepository, reminderScheduler, time)
    val undoCompletion = UndoCompletionUseCase(reminderRepository, reminderScheduler, time)
    val setAlarmEnabled = com.ricordella.app.domain.usecase.SetAlarmEnabledUseCase(reminderRepository, saveReminder, reminderScheduler, time)
    val snoozeReminder = SnoozeReminderUseCase(reminderRepository, settingsRepository, reminderScheduler, time)
    val deleteReminder = DeleteReminderUseCase(reminderRepository, reminderScheduler, trash)
    val saveItem = SaveItemUseCase(itemRepository, reminderRepository, settingsRepository, saveReminder, time)
    val deleteItem = DeleteItemUseCase(itemRepository, reminderScheduler, trash)
    val addMaintenanceRecord =
        AddMaintenanceRecordUseCase(maintenanceRepository, itemRepository, settingsRepository, saveReminder, time)
    val globalSearch = GlobalSearchUseCase(personRepository, itemRepository, reminderRepository, maintenanceRepository, settingsRepository)
    val restoreBackup = RestoreBackupUseCase(backupRepository, reminderScheduler)
    val deleteAllData = DeleteAllDataUseCase(backupRepository, reminderScheduler)
    val sharedSpace = com.ricordella.app.data.share.SharedSpace(
        appContext,
        database.backupDao(),
        settingsRepository,
        reminderScheduler,
        applicationScope,
        databaseChanges = database.invalidationTracker
            .createFlow("person", "item", "reminder", "recurrence_rule", "reminder_completion", "maintenance_record", "reminder_person", "reminder_item", "person_item", emitInitialState = false)
            .map { },
        enabled = !isDemo,
    )

    /** Conferma e annotazione quando si toccano le cose degli altri nel file condiviso. */
    val sharedOwnership = com.ricordella.app.data.share.SharedOwnership(settingsRepository, sharedSpace).also { guard ->
        saveReminder.guard = guard
        completeReminder.guard = guard
        reopenReminder.guard = guard
        undoCompletion.guard = guard
        saveItem.guard = guard
    }
    val housekeeping = Housekeeping(backupRepository, database.reminderDao(), settingsRepository, reminderScheduler, time, trash)
    val developerTools = DeveloperTools(
        appContext, applicationScope, notifier, reminderScheduler, settingsRepository, housekeeping, saveReminder, personRepository, itemRepository, time, databaseFileName,
    )
    val potionReminders = com.ricordella.app.core.notifications.PotionReminders(appContext, settingsRepository, time)
    val cycleReminders = com.ricordella.app.core.notifications.CycleReminders(appContext, settingsRepository, personRepository, time)
    val autoBackup = com.ricordella.app.core.notifications.AutoBackup(appContext, settingsRepository, housekeeping, backupRepository, time)
    /** Crea una sveglia da un'ora (importata dal telefono): suona alla prossima occasione, una volta. */
    suspend fun importPhoneAlarm(at: java.time.LocalTime, title: String) {
        val me = settingsRepository.current().sharedMeId?.takeIf { personRepository.getPerson(it) != null }
        val now = time.now()
        val date = com.ricordella.app.domain.date.nextAlarmDate(at, emptySet(), time.localNow())
        val alarm = com.ricordella.app.domain.model.Reminder(title = title, type = com.ricordella.app.domain.model.ReminderType.ALARM, dueDate = date, dueTime = at, createdAt = now, updatedAt = now)
        saveReminder(com.ricordella.app.domain.model.ReminderDraft(alarm, null, setOfNotNull(me), emptySet()))
    }
    val calendarImporter = CalendarImporter(appContext, saveReminder, database.reminderDao(), time, trash)
}
