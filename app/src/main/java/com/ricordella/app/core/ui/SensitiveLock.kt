package com.ricordella.app.core.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ricordella.app.core.i18n.tr

/** True se il telefono ha un blocco schermo (PIN, sequenza, password o impronta): senza, non c'è nulla da chiedere. */
fun Context.hasScreenLock(): Boolean = getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * Per le sezioni sensibili (es. il ciclo): con [enabled] chiede il blocco schermo del telefono prima di mostrare
 * [content], e finché è aperta nasconde la schermata dagli screenshot e dalle app recenti.
 * Si richiede a ogni apertura della sezione; senza blocco schermo sul telefono si passa direttamente.
 */
@Composable
fun SensitiveLock(enabled: Boolean, onCancel: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val locked = enabled && context.hasScreenLock()
    var unlocked by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(locked) {
        val window = context.activity()?.window
        if (locked) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (locked) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    if (!locked || unlocked) {
        content()
        return
    }
    @Suppress("DEPRECATION")
    val prompt = context.getSystemService(KeyguardManager::class.java)
        .createConfirmDeviceCredentialIntent(tr("Remindella"), tr("Sblocca per vedere questa sezione"))
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) unlocked = true else onCancel()
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!asked && prompt != null) { asked = true; launcher.launch(prompt) }
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(tr("Sezione protetta"), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            tr("Sblocca per vedere questa sezione"),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (prompt != null) TextButton(onClick = { launcher.launch(prompt) }, modifier = Modifier.padding(top = 16.dp)) { Text(tr("Sblocca")) }
        TextButton(onClick = onCancel) { Text(tr("Indietro")) }
    }
}
