package es.kim.crpg.game.rules

object EquipmentDropRules {
    fun gradeForFloor(floor: Int, roll: Int): String {
        val value = roll.coerceIn(0, 99)
        return when (floor.coerceIn(1, 30)) {
            in 1..5 -> if (value < 80) "NORMAL" else "HIGH"
            in 6..10 -> if (value < 80) "HIGH" else "RARE"
            in 11..15 -> when {
                value < 80 -> "RARE"
                value < 98 -> "EPIC"
                else -> "UNIQUE"
            }
            in 16..20 -> when {
                value < 80 -> "EPIC"
                value < 98 -> "UNIQUE"
                else -> "LEGENDARY"
            }
            in 21..25 -> when {
                value < 80 -> "UNIQUE"
                value < 98 -> "LEGENDARY"
                else -> "MYTHIC"
            }
            else -> if (value < 70) "LEGENDARY" else "MYTHIC"
        }
    }
}
