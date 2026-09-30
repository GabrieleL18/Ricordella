package com.ricordella.app.core.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Suoni brevi delle scelte, sintetizzati al primo avvio: nessun file audio nell'app. */
enum class UiSound(internal val notes: List<Pair<Float, Float>>, internal val noteMillis: Int) {
    /** Scelta di una categoria: "pop" che sale. */
    POP(listOf(440f to 880f), 110),
    /** Scelta di una cosa o completamento: due note brillanti. */
    DING(listOf(988f to 988f, 1319f to 1319f), 110),
    /** Torna indietro: nota che scende. */
    BACK(listOf(660f to 392f), 110),
}

/**
 * Riproduce i suoni con SoundPool sul volume multimediale (quello dei tasti laterali):
 * il volume "di sistema" usato prima è spesso a zero e i suoni non si sentivano.
 * I suoni vengono generati come WAV nella cache e caricati una volta sola.
 */
object UiSoundPlayer {
    private const val RATE = 44_100
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
        val dir = File(context.cacheDir, "sounds").apply { mkdirs() }
        UiSound.entries.forEach { sound ->
            val file = File(dir, "${sound.name.lowercase()}.wav")
            runCatching {
                if (!file.exists()) file.writeBytes(wav(synthesize(sound)))
                ids[sound] = soundPool.load(file.path, 1)
            }
        }
        pool = soundPool
    }

    fun play(sound: UiSound) {
        val id = ids[sound] ?: return
        pool?.play(id, 0.9f, 0.9f, 1, 0, 1f)
    }

    /** Onda sinusoidale con glissando e decadimento esponenziale, per ogni nota. */
    private fun synthesize(sound: UiSound): ShortArray {
        val perNote = RATE * sound.noteMillis / 1000
        val out = ShortArray(perNote * sound.notes.size)
        var phase = 0.0
        sound.notes.forEachIndexed { n, (from, to) ->
            for (i in 0 until perNote) {
                val progress = i / perNote.toDouble()
                phase += 2 * PI * (from + (to - from) * progress) / RATE
                val attack = (i / (RATE * 0.004)).coerceAtMost(1.0)
                val envelope = attack * exp(-4.0 * progress)
                out[n * perNote + i] = (sin(phase) * envelope * 0.6 * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out
    }

    /** Intestazione WAV PCM 16 bit mono. */
    private fun wav(pcm: ShortArray): ByteArray {
        val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply { pcm.forEach { putShort(it) } }.array()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.size)
        }.array()
        return ByteArrayOutputStream().apply { write(header); write(data) }.toByteArray()
    }
}

/** Restituisce una funzione che suona [UiSound] solo se i suoni sono attivi nelle impostazioni. */
@Composable
fun rememberUiSounds(): (UiSound) -> Unit {
    val enabled = LocalAppSettings.current.soundsEnabled
    return remember(enabled) { { sound: UiSound -> if (enabled) UiSoundPlayer.play(sound) } }
}
