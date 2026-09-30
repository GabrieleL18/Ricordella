package com.ricordella.app

import android.app.Application
import com.ricordella.app.core.AppContainer
import kotlinx.coroutines.launch

class RicordellaApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.createChannel()
        // Gli allarmi vengono persi se l'app è stata forzata a chiudersi: all'avvio si ricostruiscono.
        container.applicationScope.launch { container.reminderScheduler.refresh() }
    }
}
