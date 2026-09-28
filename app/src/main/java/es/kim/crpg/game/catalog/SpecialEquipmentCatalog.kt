package es.kim.crpg.game.catalog

import es.kim.crpg.data.ItemDefinitionEntity
import es.kim.crpg.game.rules.EquipmentSpecialRules

/** Enriches the existing catalog without changing identities, sets or owned instances. */
object SpecialEquipmentCatalog {
    private val grades = listOf("NORMAL", "HIGH", "RARE", "EPIC", "UNIQUE", "LEGENDARY", "MYTHIC")

    fun enrich(item: ItemDefinitionEntity): ItemDefinitionEntity {
        val tier = grades.indexOf(item.grade)
        if (tier < 1) return item
        val index = item.code.substringAfterLast('_').toIntOrNull() ?: return item
        val options = when (item.category) {
            "WEAPON" -> when (item.specialEffect?.substringBefore('|')) {
                "ADJACENT_SWEEP" -> listOf("SWORD_BEAM" to (2 + tier / 3), "KILL_SLASH" to (1 + tier))
                "LINE_THRUST" -> return item // Existing ROOT, PUSH and LINE_THRUST are already implemented.
                "DOUBLE_SHOT_50" -> listOf("PIERCE" to (30 + tier * 5), "RICOCHET" to (25 + tier * 5), "SLOW" to (15 + tier * 5))
                "KILL_PIERCE" -> listOf("ARMOR_PIERCE" to (10 + tier * 3), "WALL_IMPACT" to (1 + tier), "KILL_RELOAD" to 2)
                else -> return item
            }
            "ARMOR", "CLOAK" -> listOf("REFLECT" to (10 + tier * 3), "AILMENT_GUARD" to (20 + tier * 7), "RANGED_REDUCE" to (10 + tier * 4), "MELEE_REDUCE" to (10 + tier * 4))
            "BOOTS" -> listOf("FIRST_APPROACH" to 1, "MOVE_DODGE" to (10 + tier * 4))
            "HELMET" -> listOf("FIRST_DODGE" to 100, "CRIT_PREVIEW" to 1)
            "ACCESSORY" -> listOf("LIFESTEAL" to (10 + tier * 4), "KILL_ROOT" to 1, "LOW_HP_POWER" to (1 + tier / 2))
            else -> return item
        }
        // Weapon families advance in steps of four in the expanded catalog.
        val pick = if (item.category == "WEAPON") index / 4 else index
        val (key, value) = options[pick % options.size]
        return append(item, listOf(key to value))
    }

    fun append(item: ItemDefinitionEntity, options: List<Pair<String, Int>>): ItemDefinitionEntity = item.copy(
        specialEffect = (listOfNotNull(item.specialEffect?.takeIf { it.isNotBlank() }) + options.map { "${it.first}=${it.second}" }).joinToString("|"),
        detail = (listOfNotNull(item.detail?.takeIf { it.isNotBlank() }) + options.map { EquipmentSpecialRules.description(it.first, it.second) }).joinToString(" · ")
    )
}
