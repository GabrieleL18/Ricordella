package com.ricordella.app.core.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Suoni brevi delle scelte, sintetizzati al volo: nessun file audio nell'app. */
enum class UiSound(internal val notes: List<Pair<Float, Float>>, internal val noteMillis: Int) {
    /** Scelta di una categoria: "pop" che sale. */
    POP(listOf(440f to 880f), 90),
    /** Scelta di una cosa: due note brillanti. */
    DING(listOf(988f to 988f, 1319f to 1319f), 80),
    /** Torna indietro: nota che scende. */
    BACK(listOf(660f to 392f), 90),
}

private object UiSoundPlayer {
    private const val RATE = 22_050
    private val tracks = mutableMapOf<UiSound, AudioTrack>()

    @Synchronized
    fun play(sound: UiSound) {
        runCatching {
            val track = tracks.getOrPut(sound) { build(sound) }
            track.stop()
            track.reloadStaticData()
            track.play()
        }
    }

    private fun build(sound: UiSound): AudioTrack {
        val pcm = synthesize(sound)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
            .apply { write(pcm, 0, pcm.size) }
    }

    /** Onda sinusoidale con glissando e decadimento esponenziale, per ogni nota. */
    private fun synthesize(sound: UiSound): ShortArray {
        val perNote = RATE * sound.noteMillis / 1000
        val out = ShortArray(perNote * sound.notes.size)
        var phase = 0.0
        sound.notes.forEachIndexed { n, (from, to) ->
            for (i in 0 until perNote) {
                val progress = i / perNote.toDouble()
                val frequency = from + (to - from) * progress
                phase += 2 * PI * frequency / RATE
                val attack = (i / (RATE * 0.004)).coerceAtMost(1.0)
                val envelope = attack * exp(-5.0 * progress)
                out[n * perNote + i] = (sin(phase) * envelope * 0.28 * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out
    }
}

/** Restituisce una funzione che suona [UiSound] solo se i suoni sono attivi nelle impostazioni. */
@Composable
fun rememberUiSounds(): (UiSound) -> Unit {
    val enabled = LocalAppSettings.current.soundsEnabled
    return remember(enabled) { { sound: UiSound -> if (enabled) UiSoundPlayer.play(sound) } }
}
