package es.kim.crpg

import es.kim.crpg.game.catalog.ExpandedArmorCatalog
import es.kim.crpg.game.catalog.ExpandedSupplementCatalog
import es.kim.crpg.game.catalog.ExpandedWeaponCatalog
import es.kim.crpg.game.catalog.SpecialEquipmentCatalog
import es.kim.crpg.game.rules.EquipmentSpecialRules
import org.junit.Assert.*
import org.junit.Test

class EquipmentSpecialRulesTest {
    @Test fun `흡혈은 오버킬로 증가하지 않고 음수나 초과 비율을 제한한다`() {
        assertEquals(1, EquipmentSpecialRules.lifeSteal(100, 5, 30))
        assertEquals(0, EquipmentSpecialRules.lifeSteal(3, 10, 30))
        assertEquals(0, EquipmentSpecialRules.lifeSteal(-5, 10, 30))
        assertEquals(5, EquipmentSpecialRules.lifeSteal(10, 5, 200))
    }

    @Test fun `체력 조건과 피해 경감 경계가 정확하다`() {
        assertTrue(EquipmentSpecialRules.highHealth(8, 10))
        assertFalse(EquipmentSpecialRules.highHealth(7, 10))
        assertTrue(EquipmentSpecialRules.lowHealth(3, 10))
        assertFalse(EquipmentSpecialRules.lowHealth(4, 10))
        assertFalse(EquipmentSpecialRules.lowHealth(0, 10))
        assertEquals(1, EquipmentSpecialRules.reducedDamage(1, 30))
        assertEquals(7, EquipmentSpecialRules.reducedDamage(10, 30))
        assertEquals(0, EquipmentSpecialRules.reducedDamage(10, 100))
    }

    @Test fun `모든 신규 효과가 실제 카탈로그에 배정되고 기존 장비 정체성과 세트가 유지된다`() {
        val original = ExpandedWeaponCatalog.all() + ExpandedArmorCatalog.all() + ExpandedSupplementCatalog.all()
        val updated = original.map(SpecialEquipmentCatalog::enrich)
        original.zip(updated).forEach { (before, after) ->
            assertEquals(before.code, after.code)
            assertEquals(before.name, after.name)
            assertEquals(before.assetPath, after.assetPath)
            assertEquals(before.grade, after.grade)
            assertEquals(before.healthBonus, after.healthBonus)
            assertTrue(after.specialEffect.orEmpty().startsWith(before.specialEffect.orEmpty()))
            assertEquals("duplicate effect: ${after.code}", after.specialEffect.orEmpty().split('|').size,
                after.specialEffect.orEmpty().split('|').map { it.substringBefore('=') }.distinct().size)
        }
        val keys = updated.flatMap { EquipmentSpecialRules.values(it.specialEffect).keys }.toSet()
        val required = setOf("SWORD_BEAM", "KILL_SLASH", "PIERCE", "RICOCHET", "SLOW", "ARMOR_PIERCE",
            "WALL_IMPACT", "KILL_RELOAD", "REFLECT", "AILMENT_GUARD", "RANGED_REDUCE", "MELEE_REDUCE",
            "FIRST_APPROACH", "MOVE_DODGE", "FIRST_DODGE", "CRIT_PREVIEW",
            "LIFESTEAL", "KILL_ROOT", "LOW_HP_POWER")
        assertTrue("Missing effects: ${required - keys}", keys.containsAll(required))
        assertFalse(keys.contains("TRAP_IMMUNE"))
        assertFalse(keys.contains("DETECT"))
        assertEquals(original.filter { it.grade == "NORMAL" }, updated.filter { it.grade == "NORMAL" })
    }
}
