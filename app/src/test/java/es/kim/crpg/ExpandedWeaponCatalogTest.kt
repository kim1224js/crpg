package es.kim.crpg

import es.kim.crpg.game.catalog.ExpandedWeaponCatalog
import es.kim.crpg.game.catalog.ItemCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class ExpandedWeaponCatalogTest {
    @Test
    fun catalog_has_210_unique_balanced_weapons() {
        val weapons = ExpandedWeaponCatalog.all()
        assertEquals(210, weapons.size)
        assertEquals(210, weapons.map { it.code }.distinct().size)
        assertEquals(210, weapons.map { it.name }.distinct().size)
        assertEquals(setOf(30), weapons.groupingBy { it.grade }.eachCount().values.toSet())
        assertEquals(listOf(52, 52, 53, 53), weapons.groupingBy { it.specialEffect?.substringBefore('|') }.eachCount().values.sorted())
        assertTrue(weapons.all { it.category == "WEAPON" && it.attackPower > 0 && it.basePrice > 0 })
    }

    @Test
    fun higher_grades_gain_more_options_and_power() {
        val weapons = ExpandedWeaponCatalog.all()
        val normal = weapons.filter { it.grade == "NORMAL" }
        val mythic = weapons.filter { it.grade == "MYTHIC" }
        assertTrue(mythic.minOf { it.attackPower } > normal.minOf { it.attackPower })
        assertTrue(mythic.all { it.specialEffect.orEmpty().count { char -> char == '|' } >= 4 })
    }

    @Test
    fun every_sword_has_its_own_asset_path() {
        val swords = ExpandedWeaponCatalog.all().filter { it.specialEffect?.substringBefore('|') == "ADJACENT_SWEEP" }
        assertEquals(52, swords.size)
        assertEquals(52, swords.map { it.assetPath }.distinct().size)
        assertTrue(swords.all { it.assetPath.endsWith("/${it.code}.png") })
        val assetRoot = listOf(File("app/src/main/assets"), File("src/main/assets")).first { it.isDirectory }
        val files = swords.map { File(assetRoot, it.assetPath) }
        assertTrue(files.all { it.isFile && it.length() > 10_000L })
        assertTrue(files.all { it.inputStream().use { stream -> stream.readNBytes(8).contentEquals(PNG_SIGNATURE) } })
        val hashes = files.map { file ->
            MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        }
        assertEquals(52, hashes.distinct().size)
    }

    @Test
    fun every_sword_exposes_complete_guide_information() {
        val all = ExpandedWeaponCatalog.all()
        val swords = all.filter { it.specialEffect?.substringBefore('|') == "ADJACENT_SWEEP" }
        ItemCatalog.initialize(all)
        assertTrue(swords.all { sword ->
            val guide = ItemCatalog.equipmentOption(sword)
            guide != null &&
                guide.category == "무기" &&
                guide.lines.any { it.contains("공격력") } &&
                guide.lines.any { it.contains("공격 소모") } &&
                guide.lines.any { it.contains("사거리") } &&
                guide.specialEffect == sword.detail &&
                sword.detail.orEmpty().contains("공격")
        })
    }

    @Test
    fun every_bow_has_a_unique_valid_asset_and_guide_information() {
        val all = ExpandedWeaponCatalog.all()
        val bows = all.filter { it.specialEffect?.substringBefore('|') == "DOUBLE_SHOT_50" }
        assertEquals(53, bows.size)
        assertEquals(53, bows.map { it.assetPath }.distinct().size)
        assertTrue(bows.all { it.assetPath.endsWith("/${it.code}.png") })
        ItemCatalog.initialize(all)
        assertTrue(bows.all { bow ->
            ItemCatalog.equipmentOption(bow)?.let { guide ->
                guide.category == "무기" && guide.lines.size >= 3 && guide.specialEffect == bow.detail
            } == true
        })
        assertUniquePngAssets(bows.map { it.assetPath }, 53)
    }

    @Test
    fun every_spear_and_gun_has_a_unique_valid_asset_and_guide_information() {
        val all = ExpandedWeaponCatalog.all()
        ItemCatalog.initialize(all)
        val families = listOf("LINE_THRUST" to 53, "KILL_PIERCE" to 52)
        families.forEach { (style, expected) ->
            val weapons = all.filter { it.specialEffect?.substringBefore('|') == style }
            assertEquals(expected, weapons.size)
            assertEquals(expected, weapons.map { it.assetPath }.distinct().size)
            assertTrue(weapons.all { weapon ->
                weapon.assetPath.endsWith("/${weapon.code}.png") &&
                    ItemCatalog.equipmentOption(weapon)?.let { guide ->
                        guide.category == "무기" && guide.lines.size >= 3 && guide.specialEffect == weapon.detail
                    } == true
            })
            assertUniquePngAssets(weapons.map { it.assetPath }, expected)
        }
    }

    @Test
    fun deep_floor_monster_weapon_assignments_reference_real_weapons() {
        val assignedCodes = setOf(
            "exp_epic_sword_01", "exp_epic_spear_02", "exp_epic_bow_03", "exp_epic_gun_00",
            "exp_unique_sword_00", "exp_unique_spear_01", "exp_unique_bow_02", "exp_unique_gun_03",
            "exp_legendary_spear_00", "exp_mythic_bow_00", "exp_legendary_sword_03", "exp_mythic_gun_01",
            "exp_legendary_bow_01", "exp_mythic_spear_03", "exp_legendary_gun_02", "exp_mythic_sword_02"
        )
        val weaponsByCode = ExpandedWeaponCatalog.all().associateBy { it.code }
        assertTrue(assignedCodes.all(weaponsByCode::containsKey))

        val assetRoot = listOf(File("app/src/main/assets"), File("src/main/assets")).first { it.isDirectory }
        val deepAssets = listOf(
            "ui/dungeon/concepts/dungeon_floors_21_25_abyssal_sanctuary.png",
            "ui/dungeon/monsters/floors_21_25/void_hound_animation_sheet.png",
            "ui/dungeon/monsters/floors_21_25/abyss_lancer_animation_sheet.png",
            "ui/dungeon/monsters/floors_21_25/starved_oracle_animation_sheet.png",
            "ui/dungeon/monsters/floors_21_25/blackpowder_apostle_animation_sheet.png",
            "ui/dungeon/monsters/floors_21_25/eclipse_archon_animation_sheet.png",
            "ui/dungeon/monsters/floors_21_25/abyss_maw_animation_sheet.png"
        )
        assertTrue(deepAssets.map { File(assetRoot, it) }.all { it.isFile && it.length() > 10_000L })
    }

    private fun assertUniquePngAssets(paths: List<String>, expected: Int) {
        val assetRoot = listOf(File("app/src/main/assets"), File("src/main/assets")).first { it.isDirectory }
        val files = paths.map { File(assetRoot, it) }
        assertTrue(files.all { it.isFile && it.length() > 10_000L })
        assertTrue(files.all { it.inputStream().use { stream -> stream.readNBytes(8).contentEquals(PNG_SIGNATURE) } })
        val hashes = files.map { file ->
            MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        }
        assertEquals(expected, hashes.distinct().size)
    }

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    }
}
