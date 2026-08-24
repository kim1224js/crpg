package es.kim.crpg.game

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

class GameMusicPlayer(context: Context) {
    private val player: MediaPlayer? = runCatching {
        MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            context.assets.openFd("audio/music/village_dark_ambient.ogg").use {
                setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            isLooping = true
            setVolume(0.34f, 0.34f)
            prepare()
        }
    }.getOrNull()

    fun play() {
        player?.takeIf { !it.isPlaying }?.start()
    }

    fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
    }

    fun release() {
        player?.release()
    }
}
