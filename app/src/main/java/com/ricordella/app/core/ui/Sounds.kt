package com.ricordella.app.core.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ricordella.app.R

/** Suoni brevi delle scelte (file in res/raw, generati da tools/generate_sounds.py). */
enum class UiSound(@RawRes internal val res: Int) {
    /** Scelta di una categoria: "pop" che sale. */
    POP(R.raw.ui_pop),
    /** Scelta di una cosa o completamento: due note brillanti. */
    DING(R.raw.ui_ding),
    /** Torna indietro: nota che scende. */
    BACK(R.raw.ui_back),
}

/** Riproduce i suoni con SoundPool sul volume multimediale (quello dei tasti laterali). */
object UiSoundPlayer {
    private var pool: SoundPool? = null
    private val ids = mutableMapOf<UiSound, Int>()

    @Synchronized
    fun init(context: Context) {
        if (pool != null) return
        val soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        UiSound.entries.forEach { ids[it] = soundPool.load(context, it.res, 1) }
        pool = soundPool
    }

    fun play(sound: UiSound) {
        val id = ids[sound] ?: return
        pool?.play(id, 1f, 1f, 1, 0, 1f)
    }
}

/** Restituisce una funzione che suona [UiSound] solo se i suoni sono attivi nelle impostazioni. */
@Composable
fun rememberUiSounds(): (UiSound) -> Unit {
    val enabled = LocalAppSettings.current.soundsEnabled
    return remember(enabled) { { sound: UiSound -> if (enabled) UiSoundPlayer.play(sound) } }
}
