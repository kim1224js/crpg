package es.kim.crpg.ui.dungeon

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import es.kim.crpg.core.audio.GameAudioSettings

internal class DungeonSoundPlayer(private val appContext: Context) {
    private data class SoundSet(val attack: Int, val hit: Int, val death: Int)

    private val loadedSounds = mutableSetOf<Int>()
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
        .apply {
            setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) loadedSounds += sampleId
            }
        }

    private fun load(path: String): Int = runCatching {
        appContext.assets.openFd(path).use { soundPool.load(it, 1) }
    }.getOrDefault(0)

    private fun set(name: String) = SoundSet(
        attack = load("audio/monsters/${name}_attack.wav"),
        hit = load("audio/monsters/${name}_hit.wav"),
        death = load("audio/monsters/${name}_death.wav")
    )

    private val spiderSounds = set("spider")
    private val banditSounds = set("bandit")
    private val wildDogSounds = set("wild_dog")
    private val slimeSounds = set("slime")
    private val sounds = mapOf(
        "spider" to spiderSounds,
        "bandit" to banditSounds,
        "wild_dog" to wildDogSounds,
        "slime" to slimeSounds,
        "plague_rat" to wildDogSounds,
        "drowned_dead" to banditSounds,
        "spore_body" to slimeSounds,
        "hook_jailer" to banditSounds,
        "plague_bell_keeper" to banditSounds,
        "ash_arbalist" to banditSounds,
        "cinder_gargoyle" to wildDogSounds,
        "ember_deacon" to banditSounds,
        "molten_bombardier" to slimeSounds,
        "furnace_saint" to banditSounds
    )
    private val weaponAttacks = mapOf(
        "crude_sword" to load("audio/weapons/sword_swing.wav"),
        "crude_spear" to load("audio/weapons/spear_swing.wav"),
        "crude_bow" to load("audio/weapons/bow_shot.wav"),
        "crude_gun" to load("audio/weapons/gun_shot.ogg")
    )
    private val weaponImpacts = mapOf(
        "crude_sword" to load("audio/combat/weapon_hit_metal_light_01.ogg"),
        "crude_spear" to load("audio/combat/weapon_hit_metal_medium_01.ogg"),
        "crude_bow" to load("audio/combat/monster_hit_light_01.ogg"),
        "crude_gun" to load("audio/combat/monster_hit_heavy_01.ogg")
    )
    private val chestLatch = load("audio/combat/armor_hit_heavy_01.ogg")
    private val chestOpen = load("audio/village/wooden_door_open.wav")
    private val doorOpen = load("audio/village/wooden_door_open.wav")
    private val chestReveal = load("audio/combat/weapon_hit_metal_light_02.ogg")

    fun playAttack(monsterCode: String?) = play(sounds[monsterCode]?.attack)
    fun playHit(monsterCode: String?) = play(sounds[monsterCode]?.hit)
    fun playDeath(monsterCode: String?) = play(sounds[monsterCode]?.death)
    fun playWeaponAttack(weaponCode: String?) = play(weaponAttacks[weaponCode])
    fun playWeaponImpact(weaponCode: String?) = play(weaponImpacts[weaponCode], 0.72f)
    fun playChestLatch() = play(chestLatch, 0.68f)
    fun playChestOpen() = play(chestOpen, 0.42f)
    fun playDoorOpen() = play(doorOpen, 0.5f)
    fun playChestReveal() = play(chestReveal, 0.56f)

    private fun play(soundId: Int?, volume: Float = 0.9f) {
        if (GameAudioSettings.effectsEnabled && soundId != null && soundId != 0 && soundId in loadedSounds) {
            val adjustedVolume = volume * GameAudioSettings.effectsVolume
            if (adjustedVolume > 0f) {
                soundPool.play(soundId, adjustedVolume, adjustedVolume, 1, 0, 1f)
            }
        }
    }

    fun release() {
        loadedSounds.clear()
        soundPool.release()
    }
}
