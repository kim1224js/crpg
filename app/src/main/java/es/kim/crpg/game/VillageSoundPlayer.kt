package es.kim.crpg.game

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

class VillageSoundPlayer(context: Context) {
    private var doorSoundLoaded = false
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
        .apply {
            setOnLoadCompleteListener { _, _, status ->
                if (status == 0) doorSoundLoaded = true
            }
        }
    private val doorSoundId = context.assets.openFd("audio/village/wooden_door_open.wav").use {
        soundPool.load(it, 1)
    }
    fun playDoorOpen() {
        if (!doorSoundLoaded || !GameAudioSettings.effectsEnabled) return
        val volume = 0.78f * GameAudioSettings.effectsVolume
        if (volume > 0f) soundPool.play(doorSoundId, volume, volume, 1, 0, 1f)
    }

    fun release() {
        soundPool.release()
    }
}
