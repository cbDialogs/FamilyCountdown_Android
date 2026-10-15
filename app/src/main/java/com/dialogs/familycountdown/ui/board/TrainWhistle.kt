package com.dialogs.familycountdown.ui.board

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.dialogs.familycountdown.R

/**
 * The steam whistle: one short clip preloaded into a [SoundPool] so each play
 * starts instantly. Plays on the media stream, so the device's media volume
 * sets how loud it is. [play] is a no-op until the clip has finished loading.
 */
class TrainWhistle(context: Context) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    @Volatile private var loaded = false
    private val soundId: Int

    init {
        // Only one sample is ever loaded, so any successful load is ours.
        pool.setOnLoadCompleteListener { _, _, status -> if (status == 0) loaded = true }
        soundId = pool.load(context, R.raw.steam_whistle, 1)
    }

    fun play() {
        if (loaded) pool.play(soundId, 1f, 1f, 1, 0, 1f)
    }

    fun release() = pool.release()
}
