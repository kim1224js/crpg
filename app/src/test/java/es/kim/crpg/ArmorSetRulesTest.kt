package es.kim.crpg

import es.kim.crpg.game.catalog.ExpandedArmorCatalog
import es.kim.crpg.game.catalog.ExpandedSupplementCatalog
import es.kim.crpg.game.rules.ArmorSetRules
import org.junit.Assert.assertEquals
import org.junit.Test

class ArmorSetRulesTest {
    private val definitions = (ExpandedArmorCatalog.all() + ExpandedSupplementCatalog.all())
        .associateBy { it.code }

    @Test
    fun healthIsGrantedOnlyAtTwoAndThreeMatchingPieces() {
        val set = listOf("exp_helmet_08", "exp_armor_08", "exp_boots_08", "exp_cloak_08")

        assertEquals(0, ArmorSetRules.totalHealthBonus(set.take(1), definitions))
        assertEquals(2, ArmorSetRules.totalHealthBonus(set.take(2), definitions))
        assertEquals(4, ArmorSetRules.totalHealthBonus(set.take(3), definitions))
        assertEquals(4, ArmorSetRules.totalHealthBonus(set, definitions))
    }

    @Test
    fun unrelatedPiecesDoNotCreateASetBonus() {
        val mixed = listOf("exp_helmet_00", "exp_armor_01", "exp_boots_02")

        assertEquals(0, ArmorSetRules.totalHealthBonus(mixed, definitions))
    }
}
