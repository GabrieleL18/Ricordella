package com.ricordella.app.core.ui

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ricordella.app.core.i18n.tr
import com.ricordella.app.core.notifications.ReminderNotifier
import com.ricordella.app.core.ui.theme.RicordellaDimensions
import com.ricordella.app.core.ui.theme.ricordellaColors

/** Una cosa che può far saltare o ritardare un promemoria, con la schermata di Android che la risolve. */
class NotificationCheck(val title: String, val okText: String, val problemText: String, val ok: Boolean, val fix: Intent, val critical: Boolean)

/**
 * Controlla quello che Android può togliere di nascosto: canale spento, allarmi esatti, schermo intero (sveglie)
 * e risparmio energetico. Il permesso di notifica ha già la sua scheda e la sua riga.
 */
fun Context.notificationChecks(): List<NotificationCheck> {
    val pkg = packageName
    val manager = getSystemService(NotificationManager::class.java)
    val channel = manager.getNotificationChannel(ReminderNotifier.CHANNEL_REMINDERS)
    val list = mutableListOf<NotificationCheck>()
    list += NotificationCheck(
        tr("Canale dei promemoria"),
        tr("Acceso."),
        tr("È spento: i promemoria non suonano né compaiono. Tocca per riaccenderlo."),
        ok = channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE,
        fix = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg).putExtra(Settings.EXTRA_CHANNEL_ID, ReminderNotifier.CHANNEL_REMINDERS),
        critical = true,
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        list += NotificationCheck(
            tr("Promemoria puntuali"),
            tr("Le notifiche con orario arrivano al minuto esatto."),
            tr("Non consentito: le notifiche possono arrivare con qualche minuto di ritardo. Tocca per consentire."),
            ok = getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
            fix = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:$pkg".toUri()),
            critical = true,
        )
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        list += NotificationCheck(
            tr("Sveglia a schermo intero"),
            tr("La sveglia compare anche a telefono bloccato."),
            tr("Non consentito: a telefono bloccato la sveglia suona ma non compare lo schermo. Tocca per consentire."),
            ok = manager.canUseFullScreenIntent(),
            fix = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:$pkg".toUri()),
            critical = false,
        )
    }
    list += NotificationCheck(
        tr("Risparmio energetico"),
        tr("Senza limiti: Android non ferma l'app in background."),
        tr("Con alcuni telefoni (Xiaomi, Samsung, Huawei...) il risparmio energetico può ritardare i promemoria. Tocca e scegli «Non ottimizzare» per Remindella."),
        ok = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(pkg),
        fix = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        critical = false,
    )
    return list
}

private fun Context.open(fix: Intent) {
    runCatching { startActivity(Intent(fix).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { runCatching { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

/** Il controllo, riletto ogni volta che si torna all'app dalle impostazioni di Android. */
@Composable
fun rememberNotificationChecks(): List<NotificationCheck> {
    val context = LocalContext.current
    var checks by remember { mutableStateOf(context.notificationChecks()) }
    LifecycleResumeEffect(Unit) {
        checks = context.notificationChecks()
        onPauseOrDispose { }
    }
    return checks
}

/** Righe delle Impostazioni: ogni controllo con la sua spunta o il suo avviso. */
@Composable
fun NotificationHealthRows() {
    val context = LocalContext.current
    val colors = MaterialTheme.ricordellaColors
    rememberNotificationChecks().forEach { check ->
        ListItem(
            headlineContent = { Text(check.title) },
            supportingContent = { Text(if (check.ok) check.okText else check.problemText) },
            leadingContent = {
                Icon(
                    if (check.ok) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                    contentDescription = if (check.ok) tr("A posto") else tr("Da sistemare"),
                    tint = if (check.ok) colors.mint.content else colors.coral.content,
                )
            },
            modifier = if (check.ok) Modifier else Modifier.clickable { context.open(check.fix) },
        )
    }
}

/** Scheda in Home: compare solo se Android ha tolto qualcosa che fa saltare o ritardare i promemoria. */
@Composable
fun NotificationHealthCard() {
    val context = LocalContext.current
    val tone = MaterialTheme.ricordellaColors.coral
    var hidden by rememberSaveable { mutableStateOf(false) }
    val broken = rememberNotificationChecks().filter { it.critical && !it.ok }
    AnimatedVisibility(visible = broken.isNotEmpty() && !hidden) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = RicordellaDimensions.spaceS)
                .background(tone.container, MaterialTheme.shapes.large)
                .padding(RicordellaDimensions.spaceL),
            verticalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceS),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RicordellaDimensions.spaceM)) {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = tone.content)
                Text(tr("Qualche promemoria potrebbe non arrivare"), style = MaterialTheme.typography.titleMedium, color = tone.content)
            }
            broken.forEach { check ->
                Text(check.title + ": " + check.problemText, style = MaterialTheme.typography.bodyMedium, color = tone.content)
            }
            Row {
                TextButton(onClick = { context.open(broken.first().fix) }) { Text(tr("Risolvi")) }
                TextButton(onClick = { hidden = true }) { Text(tr("Più tardi")) }
            }
        }
    }
}
