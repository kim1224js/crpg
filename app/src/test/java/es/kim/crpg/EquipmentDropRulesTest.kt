package es.kim.crpg

import es.kim.crpg.game.rules.EquipmentDropRules
import org.junit.Assert.assertEquals
import org.junit.Test

class EquipmentDropRulesTest {
    @Test
    fun `층별 장비 등급 경계값을 적용한다`() {
        assertEquals("NORMAL", EquipmentDropRules.gradeForFloor(1, 79))
        assertEquals("HIGH", EquipmentDropRules.gradeForFloor(1, 80))
        assertEquals("RARE", EquipmentDropRules.gradeForFloor(11, 79))
        assertEquals("EPIC", EquipmentDropRules.gradeForFloor(11, 80))
        assertEquals("UNIQUE", EquipmentDropRules.gradeForFloor(11, 98))
        assertEquals("LEGENDARY", EquipmentDropRules.gradeForFloor(21, 80))
        assertEquals("MYTHIC", EquipmentDropRules.gradeForFloor(21, 98))
        assertEquals("LEGENDARY", EquipmentDropRules.gradeForFloor(30, 69))
        assertEquals("MYTHIC", EquipmentDropRules.gradeForFloor(30, 70))
    }
}
