package com.ricordella.app

import android.app.Application
import com.ricordella.app.core.AppContainer
import com.ricordella.app.core.seedDemoDataIfEmpty
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class RicordellaApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        com.ricordella.app.core.i18n.Lang.init(this)
        container = AppContainer(this)
        container.notifier.createChannel()
        container.potionReminders.createChannel()
        container.cycleReminders.createChannel()
        com.ricordella.app.core.ui.UiSoundPlayer.init(this)
        // In demo i dati vanno pronti prima della prima schermata, altrimenti partirebbe la configurazione iniziale.
        if (container.isDemo) runBlocking { container.seedDemoDataIfEmpty() }
        // Gli allarmi vengono persi se l'app è stata forzata a chiudersi: all'avvio si ricostruiscono.
        container.applicationScope.launch { container.reminderScheduler.refresh() }
        container.potionReminders.watch(container.applicationScope)
        container.cycleReminders.watch(container.applicationScope)
        container.autoBackup.watch(container.applicationScope)
        container.sharedSpace.start()
        com.ricordella.app.core.widget.HomeWidgets.watch(this, container.applicationScope)
    }
}
