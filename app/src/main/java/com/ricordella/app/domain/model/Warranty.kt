package com.ricordella.app.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Giorni prima della scadenza in cui la garanzia è considerata "in scadenza". */
const val WARRANTY_EXPIRING_DAYS = 30L

enum class WarrantyStatus { ACTIVE, EXPIRING, EXPIRED }

/** Stato della garanzia di una cosa, o null se la cosa non ha una garanzia. */
fun Item.warrantyStatus(today: LocalDate): WarrantyStatus? {
    val end = warrantyEndDate ?: return null
    val days = ChronoUnit.DAYS.between(today, end)
    return when {
        days < 0 -> WarrantyStatus.EXPIRED
        days <= WARRANTY_EXPIRING_DAYS -> WarrantyStatus.EXPIRING
        else -> WarrantyStatus.ACTIVE
    }
}
