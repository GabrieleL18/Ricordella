package com.ricordella.app.core.i18n

import android.content.Context
import java.util.Locale

/** Lingua scelta nelle impostazioni (con bandiera e nome nella lingua stessa): di default segue quella del sistema. */
enum class AppLanguage(val code: String?, val flag: String, val nativeName: String) {
    SYSTEM(null, "🌐", ""),
    ITALIAN("it", "🇮🇹", "Italiano"),
    ENGLISH("en", "🇬🇧", "English"),
    GERMAN("de", "🇩🇪", "Deutsch"),
    FRENCH("fr", "🇫🇷", "Français"),
    SPANISH("es", "🇪🇸", "Español"),
}

/**
 * Lingua corrente dell'app. I testi sono scritti in italiano nel codice e tradotti al volo
 * con [tr]; la scelta è salvata sul dispositivo (non nel backup) e letta all'avvio.
 * Cambiando lingua l'activity viene ricreata, così tutte le schermate si ridisegnano.
 */
object Lang {
    private const val PREFS = "language"
    private const val KEY = "app_language"
    private val supported = AppLanguage.entries.mapNotNull { it.code }

    /** Codice della lingua in uso: "it", "en", "de", "fr" o "es". */
    @Volatile
    var code: String = "it"
        private set

    val english: Boolean get() = code == "en"
    val italian: Boolean get() = code == "it"

    val locale: Locale get() = Locale.forLanguageTag(code)

    fun current(context: Context): AppLanguage =
        runCatching { AppLanguage.valueOf(prefs(context).getString(KEY, null) ?: "") }.getOrDefault(AppLanguage.SYSTEM)

    /** Da chiamare all'avvio dell'app e dopo ogni cambio. */
    fun init(context: Context) {
        // Lingua di sistema non tradotta: inglese, la più comprensibile.
        code = current(context).code ?: systemLanguage().takeIf { it in supported } ?: "en"
    }

    fun set(context: Context, language: AppLanguage) {
        prefs(context).edit().putString(KEY, language.name).apply()
        init(context)
    }

    private fun systemLanguage(): String =
        android.content.res.Resources.getSystem().configuration.locales[0]?.language ?: Locale.getDefault().language

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Solo per i test: forza la lingua senza Android. */
    internal fun forceEnglish(value: Boolean) {
        code = if (value) "en" else "it"
    }

    /** Solo per i test: forza una lingua qualsiasi. */
    internal fun force(languageCode: String) {
        code = languageCode
    }
}

private fun strings(code: String): Map<String, String>? = when (code) {
    "en" -> EnglishStrings
    "de" -> GermanStrings
    "fr" -> FrenchStrings
    "es" -> SpanishStrings
    else -> null
}

/** Traduce un testo italiano nella lingua corrente; se manca la traduzione usa l'inglese, poi l'italiano. */
fun tr(text: String): String {
    val code = Lang.code
    if (code == "it") return text
    return strings(code)?.get(text) ?: EnglishStrings[text] ?: text
}

/** Come [tr], per testi con segnaposto (%1$s, %2$s...). */
fun trf(text: String, vararg args: Any?): String = String.format(Lang.locale, tr(text), *args)
