package es.kim.crpg

import es.kim.crpg.game.catalog.ExpandedArmorCatalog
import es.kim.crpg.game.catalog.ExpandedWeaponCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandedArmorCatalogTest {
    @Test
    fun expandedArmorHasExpectedUniqueCounts() {
        val armor = ExpandedArmorCatalog.all()

        assertEquals(84, armor.size)
        assertEquals(84, armor.map { it.code }.distinct().size)
        assertEquals(28, armor.count { it.category == "HELMET" })
        assertEquals(28, armor.count { it.category == "ARMOR" })
        assertEquals(28, armor.count { it.category == "BOOTS" })
    }

    @Test
    fun expandedArmorHasUsablePlaceholderAndOptions() {
        val armor = ExpandedArmorCatalog.all()

        assertTrue(armor.all { it.assetPath.startsWith("ui/items/item_crude_") })
        assertTrue(armor.all { it.healthBonus == 0 })
        assertTrue(armor.all { !it.detail.isNullOrBlank() })
        assertTrue(armor.all { it.specialEffect.orEmpty().startsWith("SET=") })
        assertTrue(armor.filter { it.category == "ARMOR" }.map { it.detail }.distinct().size == 28)
        assertTrue(armor.any { "RANGED_BLOCK=" in it.specialEffect.orEmpty() })
        assertTrue(armor.any { "MELEE_BLOCK=" in it.specialEffect.orEmpty() })
        assertTrue(armor.any { "FREE_MOVE=" in it.specialEffect.orEmpty() })
    }

    @Test
    fun everyExpandedArmorUsesAWeaponSetTheme() {
        val weaponNames = ExpandedWeaponCatalog.all().map { it.name }
        ExpandedArmorCatalog.all().forEach { armor ->
            val theme = armor.detail.orEmpty().substringAfter("세트 계열: ").substringBefore(" ·")
            assertTrue(weaponNames.any { it.contains(theme) })
        }
    }
}
