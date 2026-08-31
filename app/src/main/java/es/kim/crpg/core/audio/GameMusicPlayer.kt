package es.kim.crpg.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

class GameMusicPlayer(context: Context) {
    private val appContext = context.applicationContext
    private var player: MediaPlayer? = null
    private var currentAssetPath: String? = null

    fun playVillage() = playTrack("audio/music/village_dark_ambient.ogg", .34f)

    fun playDungeonFloor(floor: Int) {
        val path = when (floor) {
            in 1..5 -> "audio/music/dungeon_ruins_01_05.ogg"
            in 6..10 -> "audio/music/dungeon_catacombs_06_10.ogg"
            else -> "audio/music/dungeon_furnace_11_15.ogg"
        }
        playTrack(path, .29f)
    }

    private fun playTrack(assetPath: String, volume: Float) {
        if (currentAssetPath == assetPath && player != null) {
            player?.setVolume(volume, volume)
            player?.takeIf { !it.isPlaying }?.start()
            return
        }
        player?.release()
        player = runCatching {
            MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            appContext.assets.openFd(assetPath).use {
                setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            isLooping = true
            setVolume(volume, volume)
            prepare()
            start()
        }
        }.getOrNull()
        currentAssetPath = if (player != null) assetPath else null
    }

    fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
    }

    fun release() {
        player?.release()
        player = null
        currentAssetPath = null
    }
}
