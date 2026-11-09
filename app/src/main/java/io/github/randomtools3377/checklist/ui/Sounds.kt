package io.github.randomtools3377.checklist.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import io.github.randomtools3377.checklist.R

/**
 * The two celebration sounds (made by tools/make-sounds.py), played when sound is on in
 * Settings → Appearance. They play like touch sounds, at the phone's sound volume.
 */
class Sounds(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val tick = pool.load(context, R.raw.celebrate_tick, 1)
    private val big = pool.load(context, R.raw.celebrate_big, 1)

    fun tick() {
        pool.play(tick, 0.7f, 0.7f, 1, 0, 1f)
    }

    fun big() {
        pool.play(big, 0.9f, 0.9f, 1, 0, 1f)
    }

    fun release() = pool.release()
}
