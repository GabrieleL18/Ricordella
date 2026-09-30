package com.ricordella.app.core.i18n

import android.content.Context
import java.util.Locale

/** Lingua scelta nelle impostazioni: di default segue quella del sistema. */
enum class AppLanguage { SYSTEM, ITALIAN, ENGLISH }

/**
 * Lingua corrente dell'app. I testi sono scritti in italiano nel codice e tradotti al volo
 * con [tr]; la scelta è salvata sul dispositivo (non nel backup) e letta all'avvio.
 * Cambiando lingua l'activity viene ricreata, così tutte le schermate si ridisegnano.
 */
object Lang {
    private const val PREFS = "language"
    private const val KEY = "app_language"

    @Volatile
    var english: Boolean = false
        private set

    val locale: Locale get() = if (english) Locale.ENGLISH else Locale.ITALIAN

    fun current(context: Context): AppLanguage =
        runCatching { AppLanguage.valueOf(prefs(context).getString(KEY, null) ?: "") }.getOrDefault(AppLanguage.SYSTEM)

    /** Da chiamare all'avvio dell'app e dopo ogni cambio. */
    fun init(context: Context) {
        english = when (current(context)) {
            AppLanguage.SYSTEM -> systemLanguage() != "it"
            AppLanguage.ITALIAN -> false
            AppLanguage.ENGLISH -> true
        }
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
        english = value
    }
}

/** Traduce un testo italiano nella lingua corrente (resta in italiano se manca la traduzione). */
fun tr(text: String): String = if (Lang.english) EnglishStrings[text] ?: text else text

/** Come [tr], per testi con segnaposto (%1$s, %2$s...). */
fun trf(text: String, vararg args: Any?): String = String.format(Lang.locale, tr(text), *args)
