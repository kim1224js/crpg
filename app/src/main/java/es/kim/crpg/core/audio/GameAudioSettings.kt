package es.kim.crpg.core.audio

object GameAudioSettings {
    @Volatile var musicEnabled: Boolean = true
        private set
    @Volatile var effectsEnabled: Boolean = true
        private set
    @Volatile
    var effectsVolume: Float = 0.9f
        private set

    fun setEffectsVolumePercent(percent: Int) {
        effectsVolume = percent.coerceIn(0, 100) / 100f
    }

    fun setMusicEnabled(enabled: Boolean) { musicEnabled = enabled }
    fun setEffectsEnabled(enabled: Boolean) { effectsEnabled = enabled }
}
