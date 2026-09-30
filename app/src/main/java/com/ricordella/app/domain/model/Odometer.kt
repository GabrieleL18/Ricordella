package com.ricordella.app.domain.model

/** Soglia entro la quale una manutenzione a km è considerata "vicina". */
const val ODOMETER_NEAR_THRESHOLD_KM = 1_500

/** Oltre questa percorrenza dalla scadenza la manutenzione è considerata "superata". */
const val ODOMETER_OVERDUE_TOLERANCE_KM = 500

/**
 * Stato di una scadenza a chilometri:
 * - FAR: mancano più di [ODOMETER_NEAR_THRESHOLD_KM];
 * - NEAR: si sta avvicinando;
 * - DUE: raggiunta (entro la tolleranza);
 * - OVERDUE: superata di oltre [ODOMETER_OVERDUE_TOLERANCE_KM].
 */
fun odometerStatus(currentKm: Int, dueKm: Int): OdometerStatus {
    val remaining = dueKm - currentKm
    return when {
        remaining > ODOMETER_NEAR_THRESHOLD_KM -> OdometerStatus.FAR
        remaining > 0 -> OdometerStatus.NEAR
        remaining > -ODOMETER_OVERDUE_TOLERANCE_KM -> OdometerStatus.DUE
        else -> OdometerStatus.OVERDUE
    }
}

/** Stato a km del promemoria, se ha una scadenza a km e un veicolo collegato con km noti. */
val ReminderWithLinks.odometerStatus: OdometerStatus?
    get() {
        val dueKm = reminder.dueOdometerKm ?: return null
        val currentKm = odometerItem?.odometerKm ?: return null
        return odometerStatus(currentKm, dueKm)
    }
