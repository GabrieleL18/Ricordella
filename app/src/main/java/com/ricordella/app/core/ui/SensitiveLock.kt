package com.ricordella.app.core.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
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

private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

/** True se il telefono ha un'impronta/volto registrati o un PIN, sequenza o password: senza, non c'è nulla da chiedere. */
fun Context.hasScreenLock(): Boolean = BiometricManager.from(this).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

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
    val host = context.activity() as? FragmentActivity
    val title = tr("Remindella")
    val subtitle = tr("Sblocca per vedere questa sezione")
    // Impronta (o volto) se ci sono, altrimenti PIN, sequenza o password del telefono.
    fun ask() {
        host ?: return
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlocked = true }
        }
        val info = BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle(subtitle).setAllowedAuthenticators(AUTHENTICATORS).build()
        BiometricPrompt(host, ContextCompat.getMainExecutor(context), callback).authenticate(info)
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!asked) { asked = true; ask() }
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
        TextButton(onClick = ::ask, modifier = Modifier.padding(top = 16.dp)) { Text(tr("Sblocca")) }
        TextButton(onClick = onCancel) { Text(tr("Indietro")) }
    }
}
