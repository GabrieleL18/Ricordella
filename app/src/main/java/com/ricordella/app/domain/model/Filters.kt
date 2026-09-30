package com.ricordella.app.domain.model

enum class ReminderListScope { ALL, TODAY, UPCOMING, OVERDUE, COMPLETED }

enum class ReminderSortOrder { DATE_ASC, DATE_DESC, PRIORITY, TITLE }

/** Filtri della schermata Promemoria, tradotti in query dal data layer. */
data class ReminderFilter(
    val query: String = "",
    val scope: ReminderListScope = ReminderListScope.ALL,
    val personId: String? = null,
    val itemId: String? = null,
    val category: String? = null,
    val type: ReminderType? = null,
    val priority: Priority? = null,
    val sortOrder: ReminderSortOrder = ReminderSortOrder.DATE_ASC,
    val limit: Int = 500,
) {
    val hasSecondaryFilters: Boolean
        get() = personId != null || itemId != null || category != null || type != null || priority != null
}
