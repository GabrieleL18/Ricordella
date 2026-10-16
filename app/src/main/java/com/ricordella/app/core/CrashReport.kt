package com.ricordella.app.core

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import com.ricordella.app.BuildConfig
import java.io.File
import java.time.LocalDateTime

/**
 * Se l'app si chiude per un errore, lo salva sul telefono (versione, modello e stack trace, nessun dato dell'utente)
 * e al prossimo avvio chiede se mandarlo allo sviluppatore. Non serve Internet né librerie: si apre la posta
 * dell'utente, che vede il testo e decide se spedirlo.
 */
object CrashReport {
    const val EMAIL = "lannilab.support@gmail.com"
    private const val FILE = "crash-report.txt"
    private const val MAX_CHARS = 8_000

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { File(app.filesDir, FILE).writeText(describe("Errore in «${thread.name}»", error)) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Il rapporto dell'ultimo arresto anomalo, se non è ancora stato gestito. */
    fun pending(context: Context): String? =
        File(context.filesDir, FILE).takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }

    /** Informazioni di base per una segnalazione scritta a mano (senza errore). */
    fun deviceInfo(): String = describe("Segnalazione dell'utente", null)

    /** Apre la posta con il rapporto già scritto. False se sul telefono non c'è un'app di posta. */
    fun send(context: Context, report: String): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_SENDTO, "mailto:".toUri())
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
            .putExtra(Intent.EXTRA_SUBJECT, "Remindella ${BuildConfig.VERSION_NAME}: segnalazione")
            .putExtra(Intent.EXTRA_TEXT, report.take(MAX_CHARS))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.isSuccess

    private fun describe(title: String, error: Throwable?): String = buildString {
        appendLine(title)
        appendLine("Remindella ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine(LocalDateTime.now().toString())
        if (error != null) {
            appendLine()
            append(error.stackTraceToString().take(MAX_CHARS))
        }
    }
}
