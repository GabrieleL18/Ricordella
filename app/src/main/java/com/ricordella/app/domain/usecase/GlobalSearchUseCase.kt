package com.ricordella.app.domain.usecase

import com.ricordella.app.domain.model.SearchResults
import com.ricordella.app.domain.repository.ItemRepository
import com.ricordella.app.domain.repository.MaintenanceRepository
import com.ricordella.app.domain.repository.PersonRepository
import com.ricordella.app.domain.repository.ReminderRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Ricerca locale unica su persone, cose, promemoria/eventi e manutenzioni. */
class GlobalSearchUseCase(
    private val people: PersonRepository,
    private val items: ItemRepository,
    private val reminders: ReminderRepository,
    private val maintenance: MaintenanceRepository,
) {
    suspend operator fun invoke(query: String, limitPerSection: Int = 30): SearchResults {
        if (query.isBlank()) return SearchResults()
        return coroutineScope {
            val foundPeople = async { people.search(query, limitPerSection) }
            val foundItems = async { items.search(query, limitPerSection) }
            val foundReminders = async { reminders.search(query, limitPerSection) }
            val foundMaintenance = async { maintenance.search(query, limitPerSection) }
            SearchResults(foundPeople.await(), foundItems.await(), foundReminders.await(), foundMaintenance.await())
        }
    }
}
