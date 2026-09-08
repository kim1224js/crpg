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

        assertEquals(83, armor.size)
        assertEquals(83, armor.map { it.code }.distinct().size)
        assertEquals(27, armor.count { it.category == "HELMET" })
        assertEquals(28, armor.count { it.category == "ARMOR" })
        assertEquals(28, armor.count { it.category == "BOOTS" })
    }

    @Test
    fun expandedArmorHasUsablePlaceholderAndOptions() {
        val armor = ExpandedArmorCatalog.all()

        assertTrue(armor.all { it.assetPath.startsWith("ui/items/item_crude_") })
        assertTrue(armor.all { it.healthBonus > 0 })
        assertTrue(armor.all { !it.detail.isNullOrBlank() })
        assertTrue(armor.filter { it.category == "HELMET" }.map { it.healthBonus }.distinct().size > 5)
        assertTrue(armor.filter { it.category == "ARMOR" }.map { it.detail }.distinct().size == 28)
        assertTrue(armor.any { it.specialEffect == "RANGED_BLOCK_30" })
        assertTrue(armor.any { it.specialEffect == "MELEE_BLOCK_30" })
        assertTrue(armor.any { it.specialEffect == "FREE_MOVE_30" })
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
