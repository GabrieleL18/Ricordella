package com.ricordella.app.domain.usecase

import com.ricordella.app.domain.model.Person

/** Cosa si sta facendo su un promemoria o una cosa che potrebbe essere di un altro. */
enum class GuardedAction { COMPLETE, REOPEN, EDIT }

/**
 * Con il file condiviso e "chi sono io" impostato: prima di toccare una cosa di un'altra persona
 * si chiede conferma ([allow]); dopo, si annota chi ha fatto cosa e si sincronizza subito ([done]).
 * Le cose proprie (collegate a me o a nessuno) non chiedono nulla.
 */
interface OwnershipGuard {
    /** False se l'utente ha detto di no. */
    suspend fun allow(people: List<Person>, title: String, action: GuardedAction): Boolean

    suspend fun done(people: List<Person>, title: String, action: GuardedAction)
}
