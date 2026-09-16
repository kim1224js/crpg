package es.kim.crpg.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

class VillageSoundPlayer(context: Context) {
    private val loadedSoundIds = mutableSetOf<Int>()
    private val pendingSounds = mutableMapOf<Int, Float>()
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
            setOnLoadCompleteListener { _, soundId, status ->
                if (status == 0) {
                    loadedSoundIds += soundId
                    pendingSounds.remove(soundId)?.let { baseVolume -> playNow(soundId, baseVolume) }
                } else {
                    pendingSounds.remove(soundId)
                }
            }
        }
    private val doorSoundId = context.assets.openFd("audio/village/door_creak_loud.ogg").use {
        soundPool.load(it, 1)
    }
    private val purchaseSoundId = context.assets.openFd("audio/village/purchase_coins.wav").use {
        soundPool.load(it, 1)
    }

    fun playDoorOpen() {
        play(doorSoundId, 1f)
    }

    fun playPurchase() {
        play(purchaseSoundId, 1f)
    }

    private fun play(soundId: Int, baseVolume: Float) {
        if (!GameAudioSettings.effectsEnabled) return
        if (soundId !in loadedSoundIds) {
            pendingSounds[soundId] = baseVolume
            return
        }
        playNow(soundId, baseVolume)
    }

    private fun playNow(soundId: Int, baseVolume: Float) {
        if (!GameAudioSettings.effectsEnabled) return
        val volume = baseVolume * GameAudioSettings.effectsVolume
        if (volume > 0f) soundPool.play(soundId, volume, volume, 1, 0, 1f)
    }

    fun release() {
        pendingSounds.clear()
        loadedSoundIds.clear()
        soundPool.release()
    }
}
