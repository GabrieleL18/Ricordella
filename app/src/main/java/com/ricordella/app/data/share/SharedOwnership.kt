package com.ricordella.app.data.share

import com.ricordella.app.domain.model.Person
import com.ricordella.app.domain.repository.SettingsRepository
import com.ricordella.app.domain.usecase.GuardedAction
import com.ricordella.app.domain.usecase.OwnershipGuard
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Le cose degli altri nel file condiviso: un promemoria (o una cosa) collegato solo ad altre persone
 * appartiene a loro. Toccandolo si chiede conferma con la finestra mostrata dall'app
 * ([question]); fatto, si annota chi ha fatto cosa e si sincronizza subito.
 *
 * Dal widget o da una notifica (app non aperta) non c'è nessuno a cui chiedere: l'azione è già
 * una scelta esplicita, quindi si fa e si annota comunque.
 */
class SharedOwnership(private val settings: SettingsRepository, private val space: SharedSpace) : OwnershipGuard {

    class Question(val owner: String, val title: String, val action: GuardedAction, val answer: CompletableDeferred<Boolean>)

    private val _question = MutableStateFlow<Question?>(null)
    val question: StateFlow<Question?> = _question.asStateFlow()

    /** Quante schermate dell'app sono pronte a mostrare la domanda (l'app è in primo piano). */
    @Volatile var hosts = 0

    /** Nome di chi possiede la cosa, o null se è mia, di nessuno o se non si condivide. */
    private suspend fun ownerOf(people: List<Person>): String? {
        val app = settings.current()
        if (app.sharedFileUri == null) return null
        val me = app.sharedMeId ?: return null
        if (people.isEmpty() || people.any { it.id == me }) return null
        return people.joinToString(", ") { it.name }
    }

    override suspend fun allow(people: List<Person>, title: String, action: GuardedAction): Boolean {
        val owner = ownerOf(people) ?: return true
        if (hosts == 0) return true
        val question = Question(owner, title, action, CompletableDeferred())
        _question.value = question
        return try {
            question.answer.await()
        } finally {
            _question.compareAndSet(question, null)
        }
    }

    override suspend fun done(people: List<Person>, title: String, action: GuardedAction) {
        val owner = ownerOf(people) ?: return
        val shareAction = when (action) {
            GuardedAction.COMPLETE -> ShareAction.COMPLETED
            GuardedAction.REOPEN -> ShareAction.REOPENED
            GuardedAction.EDIT -> ShareAction.EDITED
        }
        space.record(shareAction, title, owner)
    }
}
