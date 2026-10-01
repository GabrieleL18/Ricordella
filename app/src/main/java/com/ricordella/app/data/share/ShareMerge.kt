package com.ricordella.app.data.share

import com.ricordella.app.data.backup.BackupDatabaseContent
import java.time.Instant

/**
 * Unione a tre vie tra i dati di questo telefono ([local]), quelli del file condiviso ([remote])
 * e lo stato dell'ultima sincronizzazione ([base]).
 *
 * - Elemento presente da entrambe le parti: vince la modifica più recente.
 * - Elemento che manca da una parte ma c'era all'ultima sincronizzazione: è stato cancellato lì,
 *   quindi si cancella anche qui, a meno che nel frattempo dall'altra parte non sia stato modificato.
 * - Elemento nuovo (non c'era all'ultima sincronizzazione): si tiene.
 *
 * Si uniscono persone, cose, promemoria (con ricorrenza e collegamenti), storico dei completamenti
 * e spese. Foto e allegati non viaggiano nel file: restano sul telefono che li ha.
 */
object ShareMerge {

    private enum class Side { LOCAL, REMOTE }

    /** Sceglie, per ogni id, quale versione tenere (o nessuna se è stato cancellato). */
    private fun <T> pick(
        base: List<T>?,
        local: List<T>,
        remote: List<T>,
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
            val chosen = when {
                lv != null && rv != null -> if (stamp(rv) > stamp(lv)) rv to Side.REMOTE else lv to Side.LOCAL
                lv != null -> if (bv == null || stamp(lv) > stamp(bv)) lv to Side.LOCAL else null
                rv != null -> if (bv == null || stamp(rv) > stamp(bv)) rv to Side.REMOTE else null
                else -> null
            }
            if (chosen != null) result[key] = chosen
        }
        return result
    }

    /**
     * Restituisce i dati uniti. Foto e allegati di questo telefono vengono mantenuti; quelli che
     * arrivano dal file sono sempre vuoti (vedi [forFile]).
     */
    fun merge(base: BackupDatabaseContent?, local: BackupDatabaseContent, remote: BackupDatabaseContent): BackupDatabaseContent {
        val people = pick(base?.people, local.people, remote.people, { it.id }, { it.updatedAt })
        val items = pick(base?.items, local.items, remote.items, { it.id }, { it.updatedAt })
        val reminders = pick(base?.reminders, local.reminders, remote.reminders, { it.id }, { it.updatedAt })
        val completions = pick(base?.completions, local.completions, remote.completions, { it.id }, { it.completedAt })
        val maintenance = pick(base?.maintenance, local.maintenance, remote.maintenance, { it.id }, { it.createdAt })

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

    /** Versione da scrivere nel file condiviso: senza foto, documenti e allegati (sono file di questo telefono). */
    fun forFile(content: BackupDatabaseContent): BackupDatabaseContent = content.copy(
        people = content.people.map { it.copy(photoUri = null) },
        items = content.items.map { it.copy(photoUri = null, warrantyDocumentUri = null) },
        attachments = emptyList(),
    )
}
