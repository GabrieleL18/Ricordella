package com.ricordella.app

import android.app.Application
import com.ricordella.app.core.AppContainer
import kotlinx.coroutines.launch

class RicordellaApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        com.ricordella.app.core.i18n.Lang.init(this)
        container = AppContainer(this)
        container.notifier.createChannel()
        com.ricordella.app.core.ui.UiSoundPlayer.init(this)
        // Gli allarmi vengono persi se l'app è stata forzata a chiudersi: all'avvio si ricostruiscono.
        container.applicationScope.launch { container.reminderScheduler.refresh() }
    }
}
