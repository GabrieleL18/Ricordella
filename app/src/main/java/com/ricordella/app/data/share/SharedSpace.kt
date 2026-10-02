package com.ricordella.app.data.share

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.ricordella.app.R
import com.ricordella.app.RicordellaApplication
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.i18n.trf
import com.ricordella.app.core.notifications.ReminderNotifier
import com.ricordella.app.data.backup.BackupDatabaseContent
import com.ricordella.app.data.local.dao.BackupDao
import com.ricordella.app.domain.ReminderScheduler
import com.ricordella.app.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Una cosa fatta da una persona su un promemoria di un'altra (spuntato, riaperto, modificato):
 * viaggia nel file condiviso e all'altra persona arriva come notifica.
 */
@Serializable
data class ShareActivity(
    val id: String = UUID.randomUUID().toString(),
    val at: String,
    /** Telefono che l'ha fatta: le proprie attività non si notificano. */
    val device: String,
    /** Nome di chi l'ha fatta. */
    val by: String,
    val action: ShareAction,
    val title: String,
    /** Nome della persona a cui appartiene il promemoria. */
    val owner: String,
)

@Serializable
enum class ShareAction { COMPLETED, REOPENED, EDITED }

/** Contenuto del file condiviso (JSON compresso). */
@Serializable
private data class SharedFile(
    val formatVersion: Int = FORMAT_VERSION,
    val savedAt: String = "",
    val content: BackupDatabaseContent = BackupDatabaseContent(),
    /** Cancellazioni: id → istante della versione cancellata. */
    val deleted: Map<String, String> = emptyMap(),
    /** Ultime attività fatte sulle cose degli altri. */
    val activity: List<ShareActivity> = emptyList(),
)

private const val FORMAT_VERSION = 2
private const val MAX_ACTIVITY = 100

/** Le cancellazioni più vecchie di così si dimenticano: nel frattempo entrambi i telefoni le hanno viste. */
private val DELETION_MEMORY: Duration = Duration.ofDays(365)

/**
 * Condivisione con un'altra persona tramite un file su Google Drive (o qualsiasi app di file):
 * entrambi i telefoni leggono e scrivono lo stesso file, scelto con il selettore di Android.
 * Remindella non va su Internet: è l'app di Drive a sincronizzare il file tra i telefoni.
 *
 * Si legge il file, si uniscono i dati con [ShareMerge] e si riscrive il file con il risultato.
 * La "base" (ultimo stato unito) resta sul telefono e serve a capire cosa è cambiato da una parte
 * o dall'altra. Prima di riscrivere il file se ne tiene una copia sul telefono (shared-previous.rmd).
 *
 * In automatico si sincronizza all'apertura e alla chiusura dell'app, qualche secondo dopo ogni
 * modifica, ogni pochi minuti mentre l'app è aperta e ogni mezz'ora in background.
 * In manuale solo con "Sincronizza ora" (e subito dopo aver toccato una cosa di un altro).
 */
class SharedSpace(
    private val context: Context,
    private val backupDao: BackupDao,
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val scope: CoroutineScope,
    /** Si emette a ogni modifica del database (per la sincronizzazione automatica). */
    private val databaseChanges: Flow<Unit>,
    /** Spento in modalità demo: i dati finti non devono arrivare all'altra persona. */
    private val enabled: Boolean = true,
) {
    sealed interface Status {
        data object Off : Status
        data object Syncing : Status
        data class Synced(val at: Instant, val changedHere: Boolean) : Status
        data class Failed(val reason: String) : Status
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }
    private val mutex = Mutex()
    private val baseFile get() = File(context.filesDir, "shared-base.json.gz")
    private val previousFile get() = File(context.filesDir, "shared-previous.rmd")
    private val pendingFile get() = File(context.filesDir, "shared-pending-activity.json")
    private val prefs get() = context.getSharedPreferences("shared_space", Context.MODE_PRIVATE)
    private val resolver get() = context.contentResolver

    /** Identificativo casuale di questo telefono, per non notificare a sé stessi le proprie attività. */
    val deviceId: String
        get() = prefs.getString("device", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("device", it).apply() }

    private val _status = MutableStateFlow<Status>(Status.Off)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _activity = MutableStateFlow<List<ShareActivity>>(emptyList())

    /** Ultime attività lette dal file (le più recenti prima). */
    val activity: StateFlow<List<ShareActivity>> = _activity.asStateFlow()

    /** Fino a quando ignorare le modifiche del database: sono quelle scritte dalla sincronizzazione stessa. */
    @Volatile private var ignoreChangesUntil = 0L
    private var foregroundJob: Job? = null

    /** Nuovo file appena creato con il selettore: ci si scrivono i dati di questo telefono. */
    suspend fun create(uri: Uri, automatic: Boolean) = connect(uri, automatic)

    /** File già esistente (creato dall'altra persona): al primo giro si uniscono i dati di entrambi. */
    suspend fun join(uri: Uri, automatic: Boolean) = connect(uri, automatic)

    private suspend fun connect(uri: Uri, automatic: Boolean): Boolean {
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        withContext(Dispatchers.IO) { baseFile.delete() }
        settings.update { it.copy(sharedFileUri = uri.toString(), sharedAutoSync = automatic) }
        return sync()
    }

    suspend fun leave() {
        val uri = settings.current().sharedFileUri?.toUri()
        settings.update { it.copy(sharedFileUri = null, sharedLastSync = null) }
        withContext(Dispatchers.IO) { baseFile.delete() }
        uri?.let { runCatching { resolver.releasePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } }
        _status.value = Status.Off
    }

    /**
     * Avvia la sincronizzazione automatica (all'avvio dell'app): dopo ogni modifica e ogni mezz'ora
     * in background. Si accende e spegne da sola seguendo le impostazioni.
     */
    @OptIn(FlowPreview::class)
    fun start() {
        if (!enabled) return
        scope.launch {
            settings.settings.map { it.sharedFileUri != null && it.sharedAutoSync }.distinctUntilChanged().collectLatest { auto ->
                scheduleBackground(auto)
                if (auto) {
                    databaseChanges
                        .filter { System.currentTimeMillis() > ignoreChangesUntil }
                        .debounce(4_000)
                        .collect { sync() }
                }
            }
        }
    }

    /** App in primo piano: in automatico controlla le novità dell'altra persona ogni due minuti. */
    fun onForeground(visible: Boolean) {
        foregroundJob?.cancel()
        if (!visible) {
            scope.launch { if (isAutomatic()) sync() }
            return
        }
        foregroundJob = scope.launch {
            while (isActive) {
                if (isAutomatic()) sync()
                delay(120_000)
            }
        }
    }

    private suspend fun isAutomatic(): Boolean = settings.current().let { it.sharedFileUri != null && it.sharedAutoSync }

    /** Sincronizza senza bloccare chi chiama; se una sincronizzazione è già in corso non ne parte un'altra. */
    fun syncInBackground() {
        if (mutex.isLocked) return
        scope.launch { sync() }
    }

    /**
     * Annota che questo telefono ha toccato una cosa di un altro e sincronizza subito, anche in manuale:
     * l'altra persona deve saperlo il prima possibile.
     */
    suspend fun record(action: ShareAction, title: String, owner: String) {
        val me = settings.current().let { app -> app.sharedMeName }
        val entry = ShareActivity(at = Instant.now().toString(), device = deviceId, by = me ?: tr("L'altra persona"), action = action, title = title, owner = owner)
        withContext(Dispatchers.IO) {
            val pending = readPending() + entry
            pendingFile.writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(ShareActivity.serializer()), pending))
        }
        scope.launch { sync() }
    }

    suspend fun sync(): Boolean = mutex.withLock {
        val uri = settings.current().sharedFileUri?.toUri()?.takeIf { enabled } ?: run { _status.value = Status.Off; return false }
        _status.value = Status.Syncing
        val result = runCatching { withContext(Dispatchers.IO) { syncWith(uri) } }
        _status.value = result.fold(
            onSuccess = { changed -> Status.Synced(Instant.now(), changed) },
            onFailure = { Status.Failed(it.message ?: "") },
        )
        if (result.isSuccess) settings.update { it.copy(sharedLastSync = Instant.now().toEpochMilli()) }
        result.isSuccess
    }

    /** Restituisce true se i dati di questo telefono sono cambiati. */
    private suspend fun syncWith(uri: Uri): Boolean {
        val bytes = readBytes(uri)
        val remote = parse(bytes)
        val fullLocal = backupDao.readAll()
        val scope = settings.current().shareScope
        // Solo ciò che si condivide entra nell'unione; il resto resta com'è, qui e nel file.
        val local = ShareMerge.strip(fullLocal, scope)
        val remoteShared = ShareMerge.strip(remote.content, scope)
        val base = readBase()?.let { ShareMerge.strip(it, scope) }
        val now = Instant.now()

        val deleted = HashMap<String, Instant>()
        remote.deleted.forEach { (id, at) -> runCatching { Instant.parse(at) }.getOrNull()?.let { deleted[id] = it } }
        ShareMerge.localDeletions(base, local).forEach { (id, at) -> deleted[id] = maxOf(at, deleted[id] ?: Instant.EPOCH) }
        deleted.entries.removeAll { it.value.isBefore(now.minus(DELETION_MEMORY)) }

        val mergedShared = ShareMerge.merge(base, local, remoteShared, deleted)
        val merged = ShareMerge.plus(mergedShared, ShareMerge.rest(fullLocal, scope))
        val forFile = ShareMerge.forFile(ShareMerge.plus(mergedShared, ShareMerge.rest(remote.content, scope)))
        val pending = readPending()
        val activity = (remote.activity + pending).distinctBy { it.id }.sortedByDescending { it.at }.take(MAX_ACTIVITY)
        val deletedText = deleted.mapValues { it.value.toString() }

        val fileChanged = !sameData(forFile, ShareMerge.forFile(remote.content)) || deletedText != remote.deleted || activity != remote.activity
        if (fileChanged) {
            // Copia di sicurezza del file com'era, prima di riscriverlo.
            if (bytes.isNotEmpty()) previousFile.writeBytes(bytes)
            write(uri, SharedFile(savedAt = now.toString(), content = forFile, deleted = deletedText, activity = activity))
        }
        pendingFile.delete()
        val changedHere = !sameData(merged, fullLocal)
        // ponytail: tra la lettura e la sostituzione passano pochi millisecondi; una modifica fatta
        // proprio in quell'istante verrebbe ripresa alla sincronizzazione successiva solo se ancora presente.
        if (changedHere) {
            ignoreChangesUntil = System.currentTimeMillis() + 3_000
            backupDao.replaceAll(merged)
            scheduler.refresh()
        }
        writeBase(forFile)
        _activity.value = activity
        notifyNewActivity(activity)
        return changedHere
    }

    /** Notifica le attività dell'altra persona non ancora viste. */
    private fun notifyNewActivity(activity: List<ShareActivity>) {
        val seen = prefs.getString("seen_activity", null)
        val fresh = activity.filter { it.device != deviceId && (seen == null || it.at > seen) }
        activity.maxOfOrNull { it.at }?.let { prefs.edit().putString("seen_activity", it).apply() }
        // Alla prima sincronizzazione non si notifica lo storico intero.
        if (seen == null) return
        fresh.take(5).forEach { entry ->
            val text = when (entry.action) {
                ShareAction.COMPLETED -> trf("%1\$s ha segnato come fatto «%2\$s» di %3\$s", entry.by, entry.title, entry.owner)
                ShareAction.REOPENED -> trf("%1\$s ha riaperto «%2\$s» di %3\$s", entry.by, entry.title, entry.owner)
                ShareAction.EDITED -> trf("%1\$s ha modificato «%2\$s» di %3\$s", entry.by, entry.title, entry.owner)
            }
            val notification = NotificationCompat.Builder(context, ReminderNotifier.CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_stat_reminder)
                .setColor(0xFFC9A400.toInt())
                .setContentTitle(tr("Novità dal file condiviso"))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(entry.id.hashCode(), notification) }
        }
    }

    private fun readBytes(uri: Uri): ByteArray =
        resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IOException("File condiviso non disponibile")

    private fun parse(bytes: ByteArray): SharedFile {
        if (bytes.isEmpty()) return SharedFile()
        val text = runCatching { GZIPInputStream(bytes.inputStream()).use { it.readBytes() }.decodeToString() }
            .getOrElse { throw IOException("Il file scelto non è un file condiviso di Remindella") }
        val file = runCatching { json.decodeFromString(SharedFile.serializer(), text) }
            .getOrElse { throw IOException("Il file condiviso è rovinato: lo ricreo con i dati di questo telefono alla prossima modifica") }
        if (file.formatVersion > FORMAT_VERSION) throw IOException("File creato con una versione più recente di Remindella")
        return file
    }

    private fun write(uri: Uri, file: SharedFile) {
        val output = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: resolver.openOutputStream(uri, "w")
            ?: throw IOException("File condiviso non scrivibile")
        output.use { out ->
            GZIPOutputStream(out).use { gz -> gz.write(json.encodeToString(SharedFile.serializer(), file).toByteArray()) }
        }
    }

    private fun readPending(): List<ShareActivity> = runCatching {
        if (!pendingFile.exists()) return emptyList()
        json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(ShareActivity.serializer()), pendingFile.readText())
    }.getOrDefault(emptyList())

    private fun readBase(): BackupDatabaseContent? = runCatching {
        if (!baseFile.exists()) return null
        json.decodeFromString(BackupDatabaseContent.serializer(), GZIPInputStream(baseFile.inputStream()).use { it.readBytes() }.decodeToString())
    }.getOrNull()

    private fun writeBase(content: BackupDatabaseContent) {
        GZIPOutputStream(baseFile.outputStream()).use { it.write(json.encodeToString(BackupDatabaseContent.serializer(), content).toByteArray()) }
    }

    private fun scheduleBackground(on: Boolean) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = PendingIntent.getBroadcast(
            context,
            7_300,
            Intent(context, SharedSyncReceiver::class.java).setAction(SharedSyncReceiver.ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (on) {
            alarms.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + AlarmManager.INTERVAL_HALF_HOUR, AlarmManager.INTERVAL_HALF_HOUR, intent)
        } else {
            alarms.cancel(intent)
        }
    }

    /** Stessi dati, indipendentemente dall'ordine. */
    private fun sameData(a: BackupDatabaseContent, b: BackupDatabaseContent): Boolean =
        a.people.toSet() == b.people.toSet() && a.items.toSet() == b.items.toSet() &&
            a.reminders.toSet() == b.reminders.toSet() && a.recurrenceRules.toSet() == b.recurrenceRules.toSet() &&
            a.completions.toSet() == b.completions.toSet() && a.maintenance.toSet() == b.maintenance.toSet() &&
            a.categories.toSet() == b.categories.toSet() && a.reminderPeople.toSet() == b.reminderPeople.toSet() &&
            a.reminderItems.toSet() == b.reminderItems.toSet() && a.personItems.toSet() == b.personItems.toSet() &&
            a.attachments.toSet() == b.attachments.toSet()
}

/** Sincronizzazione automatica in background, ogni mezz'ora circa (Android sceglie il momento migliore). */
class SharedSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val container = (context.applicationContext as RicordellaApplication).container
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                container.sharedSpace.sync()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.ricordella.app.action.SHARED_SYNC"
    }
}
