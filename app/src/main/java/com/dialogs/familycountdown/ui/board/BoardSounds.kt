package com.dialogs.familycountdown.ui.board

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.staticCompositionLocalOf
import com.dialogs.familycountdown.R
import java.util.Collections
import kotlin.random.Random

/**
 * The board's sounds — the arrival-train whistle, the per-card flap tick and the
 * razzle-dazzle card shuffle — preloaded into one [SoundPool] so every play
 * starts instantly. Plays on the media stream, so the device's media volume sets
 * how loud they are. Each play is a no-op until its clip has finished loading.
 */
class BoardSounds(context: Context) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val loaded: MutableSet<Int> = Collections.synchronizedSet(HashSet())
    private val whistleId: Int
    private val tickId: Int
    private val shuffleId: Int

    /** Stream id of the looping shuffle while a dazzle run is playing it, else 0. */
    private var shuffleStream = 0

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) loaded += sampleId }
        whistleId = pool.load(context, R.raw.steam_whistle, 1)
        tickId = pool.load(context, R.raw.flap_tick, 1)
        shuffleId = pool.load(context, R.raw.card_shuffle, 1)
    }

    fun whistle() {
        if (whistleId in loaded) pool.play(whistleId, 1f, 1f, 1, 0, 1f)
    }

    /**
     * One card landing. Slightly randomised pitch so several rows flipping on the
     * same second read as separate cards rather than one loud click.
     */
    fun tick() {
        if (tickId in loaded) {
            val rate = TICK_RATE_MIN + Random.nextFloat() * (TICK_RATE_MAX - TICK_RATE_MIN)
            pool.play(tickId, TICK_VOLUME, TICK_VOLUME, 0, 0, rate)
        }
    }

    /** Starts the shuffle looping; it keeps going until [stopShuffle]. */
    fun startShuffle() {
        if (shuffleStream != 0 || shuffleId !in loaded) return
        shuffleStream = pool.play(shuffleId, 1f, 1f, 1, -1, 1f)
    }

    fun stopShuffle() {
        if (shuffleStream != 0) pool.stop(shuffleStream)
        shuffleStream = 0
    }

    fun release() = pool.release()

    companion object {
        const val TICK_VOLUME = 0.6f
        const val TICK_RATE_MIN = 0.94f
        const val TICK_RATE_MAX = 1.06f
    }
}

/** The board's sounds, or null when flap sounds are switched off (or outside the board). */
val LocalBoardSounds = staticCompositionLocalOf<BoardSounds?> { null }
