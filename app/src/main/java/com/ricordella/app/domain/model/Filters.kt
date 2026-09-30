package com.ricordella.app.domain.model

import java.time.LocalDate
import java.time.Year
import java.time.YearMonth

enum class ReminderListScope { ALL, TODAY, UPCOMING, OVERDUE, COMPLETED }

enum class ReminderSortOrder { DATE_ASC, DATE_DESC, PRIORITY, TITLE }

enum class PeriodKind { ALL, MONTH, YEAR }

/** Periodo mostrato nella lista: sempre, un mese intero o un anno intero (quello che contiene [anchor]). */
data class ReminderPeriod(val kind: PeriodKind = PeriodKind.ALL, val anchor: LocalDate = LocalDate.now()) {
    val range: ClosedRange<LocalDate>?
        get() = when (kind) {
            PeriodKind.ALL -> null
            PeriodKind.MONTH -> YearMonth.from(anchor).let { it.atDay(1)..it.atEndOfMonth() }
            PeriodKind.YEAR -> Year.from(anchor).let { it.atDay(1)..it.atDay(it.length()) }
        }

    fun shift(steps: Long): ReminderPeriod = when (kind) {
        PeriodKind.ALL -> this
        PeriodKind.MONTH -> copy(anchor = anchor.plusMonths(steps))
        PeriodKind.YEAR -> copy(anchor = anchor.plusYears(steps))
    }
}

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
    val period: ReminderPeriod = ReminderPeriod(),
    val limit: Int = 500,
) {
    val hasSecondaryFilters: Boolean
        get() = personId != null || itemId != null || category != null || type != null || priority != null
}
