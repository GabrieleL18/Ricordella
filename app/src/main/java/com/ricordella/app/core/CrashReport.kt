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
    internal const val MAX_CHARS = 8_000
    private const val APP_PACKAGE = "com.ricordella.app"
    private const val MAX_APP_FRAMES = 8

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { File(app.filesDir, FILE).writeText(format(header("Errore nel thread «${thread.name}»"), error)) }
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
    fun deviceInfo(): String = format(header("Segnalazione dell'utente"), null)

    /** Apre la posta con il rapporto già scritto. False se sul telefono non c'è un'app di posta. */
    fun send(context: Context, report: String): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_SENDTO, "mailto:".toUri())
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
            .putExtra(Intent.EXTRA_SUBJECT, "Remindella ${BuildConfig.VERSION_NAME}: segnalazione")
            .putExtra(Intent.EXTRA_TEXT, report.take(MAX_CHARS))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.isSuccess

    private fun header(title: String): String =
        "$title\nRemindella ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})\n" +
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}\n" +
            LocalDateTime.now()

    /** Un punto dello stack trace come «File.kt, riga 42 (classe.metodo)». */
    private fun StackTraceElement.place(): String =
        "${fileName ?: "?"}, riga ${if (lineNumber >= 0) lineNumber else "?"} ($className.$methodName)"

    /** L'ultima causa della catena: da dove è davvero partito l'errore (con un limite contro i cicli). */
    private fun Throwable.rootCause(): Throwable {
        var current = this
        repeat(20) { current = current.cause?.takeIf { it !== current } ?: return current }
        return current
    }

    /**
     * Il rapporto, con in cima la risposta a «dove?»: il primo punto del codice dell'app nello stack dell'errore
     * originale, poi le tappe nel codice dell'app e solo alla fine lo stack completo (tagliato se troppo lungo).
     * Con la release minificata i nomi sono abbreviati: il numero di build serve a leggerli con il file mapping.txt.
     */
    internal fun format(header: String, error: Throwable?): String = buildString {
        appendLine(header)
        if (error == null) return@buildString
        val root = error.rootCause()
        val appFrames = root.stackTrace.filter { it.className.startsWith(APP_PACKAGE) }
        appendLine()
        appendLine("DOVE: " + (appFrames.firstOrNull() ?: root.stackTrace.firstOrNull())?.place().orEmpty().ifEmpty { "sconosciuto (stack vuoto)" })
        appendLine("ERRORE: ${error::class.java.name}: ${error.message.orEmpty()}")
        if (root !== error) appendLine("CAUSA ORIGINALE: ${root::class.java.name}: ${root.message.orEmpty()}")
        if (appFrames.isNotEmpty()) {
            appendLine()
            appendLine("TAPPE NEL CODICE DELL'APP (dalla più vicina all'errore):")
            appFrames.take(MAX_APP_FRAMES).forEachIndexed { i, frame -> appendLine("${i + 1}. ${frame.place()}") }
        }
        appendLine()
        appendLine("STACK COMPLETO:")
        append(error.stackTraceToString())
    }.take(MAX_CHARS)
}
