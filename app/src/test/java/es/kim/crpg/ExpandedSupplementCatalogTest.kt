package es.kim.crpg

import es.kim.crpg.game.catalog.ExpandedSupplementCatalog
import es.kim.crpg.game.catalog.ExpandedWeaponCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandedSupplementCatalogTest {
    @Test
    fun fillsExistingCategoriesToThirtyAndCreatesThirtyCloaks() {
        val items = ExpandedSupplementCatalog.all()
        assertEquals(79, items.size)
        assertEquals(25, items.count { it.category == "ACCESSORY" })
        assertEquals(24, items.count { it.category == "AUXILIARY" })
        assertEquals(30, items.count { it.category == "CLOAK" })
        assertEquals(items.size, items.map { it.code }.distinct().size)
    }

    @Test
    fun everyNewItemHasNamedStatsAndAnAsset() {
        val items = ExpandedSupplementCatalog.all()
        assertTrue(items.all { it.healthBonus >= 0 && !it.detail.isNullOrBlank() && it.assetPath.endsWith(".png") })
        assertTrue(items.filter { it.grade !in setOf("NORMAL", "HIGH") }.any { it.specialEffect != null })
        assertTrue(items.filter { it.category == "CLOAK" }.map { it.detail }.distinct().size == 30)
    }

    @Test
    fun everyCloakUsesAWeaponSetTheme() {
        val weaponNames = ExpandedWeaponCatalog.all().map { it.name }
        ExpandedSupplementCatalog.all().filter { it.category == "CLOAK" }.forEach { cloak ->
            val theme = cloak.detail.orEmpty().substringAfter("세트 계열: ").substringBefore(" ·")
            assertTrue(weaponNames.any { it.contains(theme) })
        }
    }
}
