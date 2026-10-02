package com.ricordella.app.data.share

import com.ricordella.app.data.backup.BackupDatabaseContent
import com.ricordella.app.domain.model.Item
import com.ricordella.app.domain.model.MaintenanceRecord
import com.ricordella.app.domain.model.Person
import com.ricordella.app.domain.model.Reminder
import com.ricordella.app.domain.model.ReminderCompletion
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.time.Instant

/**
 * Unione a tre vie tra i dati di questo telefono ([local]), quelli del file condiviso ([remote])
 * e lo stato dell'ultima sincronizzazione ([base]). Pensata per non perdere mai una modifica,
 * anche quando entrambi hanno lavorato offline:
 *
 * - Elemento cambiato da una parte sola (rispetto alla base): vince quella modifica.
 * - Cambiato da entrambe le parti: si uniscono i campi; se lo stesso campo è cambiato da tutte e due,
 *   vince la modifica più recente.
 * - Cancellazioni: valgono solo se c'è una segnalazione esplicita ([deleted], id → versione cancellata).
 *   Un elemento che manca nel file senza segnalazione (file sovrascritto da Drive, copia vecchia) non
 *   viene cancellato: si tiene e si riscrive. Se dopo la cancellazione qualcuno lo ha modificato, torna.
 * - Elemento nuovo: si tiene.
 *
 * Si uniscono persone, cose, promemoria (con ricorrenza e collegamenti), storico dei completamenti
 * e spese. Foto e allegati non viaggiano nel file: restano sul telefono che li ha.
 */
object ShareMerge {

    private enum class Side { LOCAL, REMOTE }

    private val json = Json { encodeDefaults = true }

    /** Unione campo per campo di un elemento modificato da entrambe le parti. */
    private fun <T> mergeFields(serializer: KSerializer<T>, base: T, local: T, remote: T, remoteNewer: Boolean): T {
        val b = json.encodeToJsonElement(serializer, base).jsonObject
        val l = json.encodeToJsonElement(serializer, local).jsonObject
        val r = json.encodeToJsonElement(serializer, remote).jsonObject
        val merged = (l.keys + r.keys).associateWith { key ->
            val lv = l[key]
            val rv = r[key]
            when {
                lv == rv -> lv
                lv == b[key] -> rv
                rv == b[key] -> lv
                remoteNewer -> rv
                else -> lv
            }
        }.filterValues { it != null }.mapValues { it.value!! }
        return json.decodeFromJsonElement(serializer, JsonObject(merged))
    }

    /** Sceglie, per ogni id, la versione da tenere e da che parte arriva (o nessuna se è stato cancellato). */
    private fun <T> pick(
        base: List<T>?,
        local: List<T>,
        remote: List<T>,
        deleted: Map<String, Instant>,
        serializer: KSerializer<T>,
        id: (T) -> String,
        stamp: (T) -> Instant,
    ): Map<String, Pair<T, Side>> {
        val b = base?.associateBy(id).orEmpty()
        val l = local.associateBy(id)
        val r = remote.associateBy(id)
        val result = LinkedHashMap<String, Pair<T, Side>>()
        (l.keys + r.keys).forEach { key ->
            val lv = l[key]
            val rv = r[key]
            val bv = b[key]
            val chosen: Pair<T, Side> = when {
                lv != null && rv != null -> when {
                    lv == rv -> lv to Side.LOCAL
                    bv != null && lv == bv -> rv to Side.REMOTE
                    bv != null && rv == bv -> lv to Side.LOCAL
                    bv != null -> {
                        val remoteNewer = stamp(rv) > stamp(lv)
                        mergeFields(serializer, bv, lv, rv, remoteNewer) to (if (remoteNewer) Side.REMOTE else Side.LOCAL)
                    }
                    else -> if (stamp(rv) > stamp(lv)) rv to Side.REMOTE else lv to Side.LOCAL
                }
                lv != null -> lv to Side.LOCAL
                else -> rv!! to Side.REMOTE
            }
            // Cancellato da qualcuno: resta cancellato, a meno che non sia stato modificato dopo.
            val deletedVersion = deleted[key]
            if (deletedVersion != null && stamp(chosen.first) <= deletedVersion) return@forEach
            result[key] = chosen
        }
        return result
    }

    /**
     * Cancellazioni fatte su questo telefono dall'ultima sincronizzazione: elementi che erano nella base
     * e qui non ci sono più. Per ognuno si annota la versione cancellata (il suo istante di modifica).
     */
    fun localDeletions(base: BackupDatabaseContent?, local: BackupDatabaseContent): Map<String, Instant> {
        if (base == null) return emptyMap()
        fun <T> gone(baseList: List<T>, localList: List<T>, id: (T) -> String, stamp: (T) -> Instant): Map<String, Instant> {
            val ids = localList.mapTo(HashSet(), id)
            return baseList.filter { id(it) !in ids }.associate { id(it) to stamp(it) }
        }
        return gone(base.people, local.people, { it.id }, { it.updatedAt }) +
            gone(base.items, local.items, { it.id }, { it.updatedAt }) +
            gone(base.reminders, local.reminders, { it.id }, { it.updatedAt }) +
            gone(base.completions, local.completions, { it.id }, { it.completedAt }) +
            gone(base.maintenance, local.maintenance, { it.id }, { it.createdAt })
    }

    /**
     * Restituisce i dati uniti. Foto e allegati di questo telefono vengono mantenuti; quelli che
     * arrivano dal file sono sempre vuoti (vedi [forFile]). [deleted] sono tutte le cancellazioni
     * note: quelle scritte nel file più quelle di questo telefono ([localDeletions]).
     */
    fun merge(
        base: BackupDatabaseContent?,
        local: BackupDatabaseContent,
        remote: BackupDatabaseContent,
        deleted: Map<String, Instant> = emptyMap(),
    ): BackupDatabaseContent {
        val people = pick(base?.people, local.people, remote.people, deleted, Person.serializer(), { it.id }, { it.updatedAt })
        val items = pick(base?.items, local.items, remote.items, deleted, Item.serializer(), { it.id }, { it.updatedAt })
        val reminders = pick(base?.reminders, local.reminders, remote.reminders, deleted, Reminder.serializer(), { it.id }, { it.updatedAt })
        val completions = pick(base?.completions, local.completions, remote.completions, deleted, ReminderCompletion.serializer(), { it.id }, { it.completedAt })
        val maintenance = pick(base?.maintenance, local.maintenance, remote.maintenance, deleted, MaintenanceRecord.serializer(), { it.id }, { it.createdAt })

        val localPeople = local.people.associateBy { it.id }
        val localItems = local.items.associateBy { it.id }
        // Le foto restano quelle di questo telefono (i percorsi dell'altro qui non esistono).
        val mergedPeople = people.values.map { (person, _) -> person.copy(photoUri = localPeople[person.id]?.photoUri) }
        val mergedItems = items.values.map { (item, _) ->
            val mine = localItems[item.id]
            item.copy(photoUri = mine?.photoUri, warrantyDocumentUri = mine?.warrantyDocumentUri)
        }
        val personIds = mergedPeople.mapTo(HashSet()) { it.id }
        val itemIds = mergedItems.mapTo(HashSet()) { it.id }
        val reminderIds = reminders.keys

        // Ricorrenza e collegamenti di un promemoria arrivano dalla stessa parte della versione scelta.
        fun <T> fromWinner(winners: Map<String, Pair<*, Side>>, localList: List<T>, remoteList: List<T>, owner: (T) -> String) =
            localList.filter { winners[owner(it)]?.second == Side.LOCAL } + remoteList.filter { winners[owner(it)]?.second == Side.REMOTE }

        val mergedReminders = reminders.values.map { it.first }
        val ruleIds = mergedReminders.mapNotNullTo(HashSet()) { it.recurrenceRuleId }
        val rules = (local.recurrenceRules + remote.recurrenceRules).distinctBy { it.id }.filter { it.id in ruleIds }
            .let { all ->
                // A parità di id, la regola della parte che ha vinto il promemoria.
                val winnerSide = reminders.values.associate { (reminder, side) -> reminder.recurrenceRuleId to side }
                all.map { rule ->
                    val preferred = if (winnerSide[rule.id] == Side.REMOTE) remote.recurrenceRules else local.recurrenceRules
                    preferred.firstOrNull { it.id == rule.id } ?: rule
                }
            }

        return BackupDatabaseContent(
            people = mergedPeople,
            categories = (local.categories + remote.categories).distinctBy { it.id },
            items = mergedItems,
            recurrenceRules = rules,
            reminders = mergedReminders,
            completions = completions.values.map { it.first }.filter { it.reminderId in reminderIds },
            maintenance = maintenance.values.map { it.first }.filter { it.itemId in itemIds },
            attachments = local.attachments.filter { it.ownerId in itemIds || it.ownerId in reminderIds },
            reminderPeople = fromWinner(reminders, local.reminderPeople, remote.reminderPeople) { it.reminderId }
                .filter { it.personId in personIds }.distinct(),
            reminderItems = fromWinner(reminders, local.reminderItems, remote.reminderItems) { it.reminderId }
                .filter { it.itemId in itemIds }.distinct(),
            personItems = fromWinner(items, local.personItems, remote.personItems) { it.itemId }
                .filter { it.personId in personIds }.distinctBy { it.personId to it.itemId },
        )
    }

    /** Solo ciò che si condivide secondo [scope], con i collegamenti che restano validi. */
    fun strip(c: BackupDatabaseContent, scope: com.ricordella.app.domain.model.ShareScope): BackupDatabaseContent {
        val reminders = c.reminders.filter { if (it.type == com.ricordella.app.domain.model.ReminderType.ALARM) scope.alarms else scope.reminders }
        val rIds = reminders.mapTo(HashSet()) { it.id }
        val ruleIds = reminders.mapNotNullTo(HashSet()) { it.recurrenceRuleId }
        val people = if (scope.people) c.people else emptyList()
        val items = if (scope.items) c.items else emptyList()
        val pIds = people.mapTo(HashSet()) { it.id }
        val iIds = items.mapTo(HashSet()) { it.id }
        return c.copy(
            people = people,
            items = items,
            reminders = reminders,
            recurrenceRules = c.recurrenceRules.filter { it.id in ruleIds },
            completions = c.completions.filter { it.reminderId in rIds },
            maintenance = c.maintenance.filter { it.itemId in iIds },
            reminderPeople = c.reminderPeople.filter { it.reminderId in rIds && it.personId in pIds },
            reminderItems = c.reminderItems.filter { it.reminderId in rIds && it.itemId in iIds },
            personItems = c.personItems.filter { it.personId in pIds && it.itemId in iIds },
        )
    }

    /** Il resto: ciò che [strip] lascia fuori. Non viaggia nel file ma non va perso (né qui né nel file). */
    fun rest(c: BackupDatabaseContent, scope: com.ricordella.app.domain.model.ShareScope): BackupDatabaseContent {
        val kept = strip(c, scope)
        return BackupDatabaseContent(
            people = c.people - kept.people.toSet(),
            items = c.items - kept.items.toSet(),
            recurrenceRules = c.recurrenceRules - kept.recurrenceRules.toSet(),
            reminders = c.reminders - kept.reminders.toSet(),
            completions = c.completions - kept.completions.toSet(),
            maintenance = c.maintenance - kept.maintenance.toSet(),
            reminderPeople = c.reminderPeople - kept.reminderPeople.toSet(),
            reminderItems = c.reminderItems - kept.reminderItems.toSet(),
            personItems = c.personItems - kept.personItems.toSet(),
        )
    }

    /** Unisce due parti disgiunte (la condivisa e il resto). */
    fun plus(a: BackupDatabaseContent, b: BackupDatabaseContent) = BackupDatabaseContent(
        people = a.people + b.people,
        categories = a.categories,
        items = a.items + b.items,
        recurrenceRules = a.recurrenceRules + b.recurrenceRules,
        reminders = a.reminders + b.reminders,
        completions = a.completions + b.completions,
        maintenance = a.maintenance + b.maintenance,
        attachments = a.attachments,
        reminderPeople = a.reminderPeople + b.reminderPeople,
        reminderItems = a.reminderItems + b.reminderItems,
        personItems = a.personItems + b.personItems,
    )

    /** Versione da scrivere nel file condiviso: senza foto, documenti e allegati (sono file di questo telefono). */
    fun forFile(content: BackupDatabaseContent): BackupDatabaseContent = content.copy(
        people = content.people.map { it.copy(photoUri = null) },
        items = content.items.map { it.copy(photoUri = null, warrantyDocumentUri = null) },
        attachments = emptyList(),
    )
}
