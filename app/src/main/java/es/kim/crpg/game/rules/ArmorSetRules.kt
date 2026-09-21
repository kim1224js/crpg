package es.kim.crpg.game.rules

import es.kim.crpg.data.ItemDefinitionEntity

object ArmorSetRules {
    fun setKey(item: ItemDefinitionEntity?): String? = item?.specialEffect.orEmpty()
        .split('|')
        .firstOrNull { it.startsWith("SET=") }
        ?.substringAfter('=')
        ?.takeIf { it.isNotBlank() }

    fun totalHealthBonus(codes: Collection<String>, definitions: Map<String, ItemDefinitionEntity>): Int =
        codes.mapNotNull(definitions::get)
            .groupBy(::setKey)
            .filterKeys { it != null }
            .values
            .sumOf { pieces -> healthBonus(pieces.size, pieces.maxByOrNull(::gradeTier)?.grade ?: "NORMAL") }

    fun twoPieceBonus(grade: String): Int = 1 + gradeTier(grade) / 2

    fun threePieceBonus(grade: String): Int = 1 + (gradeTier(grade) + 1) / 2

    fun healthBonus(pieceCount: Int, grade: String): Int = when {
        pieceCount >= 3 -> twoPieceBonus(grade) + threePieceBonus(grade)
        pieceCount >= 2 -> twoPieceBonus(grade)
        else -> 0
    }

    private fun gradeTier(item: ItemDefinitionEntity): Int = gradeTier(item.grade)

    private fun gradeTier(grade: String): Int = when (grade) {
        "HIGH" -> 1; "RARE" -> 2; "EPIC" -> 3; "UNIQUE" -> 4
        "LEGENDARY" -> 5; "MYTHIC" -> 6; else -> 0
    }
}
