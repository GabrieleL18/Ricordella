package com.ricordella.app.core

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

    private val appContext = context.applicationContext

    /** Scope per lavoro che deve sopravvivere alle singole schermate (es. receiver). */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val time = TimeSource(Clock.systemUTC())
    val recurrenceCalculator = RecurrenceCalculator()
    private val alarmPlanner = ReminderAlarmPlanner()

    /** Modalità demo (screenshot): database e impostazioni separati, i dati veri restano intatti. */
    val isDemo = DemoMode.isOn(appContext)
    val databaseFileName = if (isDemo) "ricordella-demo.db" else "ricordella.db"

    private val database = RicordellaDatabase.create(appContext, databaseFileName)

    val reminderRepository = RoomReminderRepository(database.reminderDao())
    val personRepository = RoomPersonRepository(database.personDao())
    val itemRepository = RoomItemRepository(database.itemDao(), database.personDao())
    val maintenanceRepository = RoomMaintenanceRepository(database.maintenanceDao())
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
    val snoozeReminder = SnoozeReminderUseCase(reminderRepository, settingsRepository, reminderScheduler, time)
    val deleteReminder = DeleteReminderUseCase(reminderRepository, reminderScheduler)
    val saveItem = SaveItemUseCase(itemRepository, reminderRepository, settingsRepository, saveReminder, time)
    val deleteItem = DeleteItemUseCase(itemRepository, reminderScheduler)
    val addMaintenanceRecord =
        AddMaintenanceRecordUseCase(maintenanceRepository, itemRepository, settingsRepository, saveReminder, time)
    val globalSearch = GlobalSearchUseCase(personRepository, itemRepository, reminderRepository, maintenanceRepository)
    val restoreBackup = RestoreBackupUseCase(backupRepository, reminderScheduler)
    val deleteAllData = DeleteAllDataUseCase(backupRepository, reminderScheduler)
    val housekeeping = Housekeeping(backupRepository, database.reminderDao(), settingsRepository, reminderScheduler, time)
    val developerTools = DeveloperTools(
        appContext, applicationScope, notifier, reminderScheduler, settingsRepository, housekeeping, saveReminder, personRepository, itemRepository, time, databaseFileName,
    )
    val potionReminders = com.ricordella.app.core.notifications.PotionReminders(appContext, settingsRepository, time)
    val calendarImporter = CalendarImporter(appContext, saveReminder, database.reminderDao(), time)
}
