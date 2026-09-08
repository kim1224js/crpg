package es.kim.crpg.game.catalog

import es.kim.crpg.data.ItemDefinitionEntity

object ExpandedArmorCatalog {
    private data class Grade(val code: String, val label: String, val tier: Int, val price: Int)

    private val grades = listOf(
        Grade("NORMAL", "낡은", 0, 8),
        Grade("HIGH", "단련된", 1, 16),
        Grade("RARE", "저주받은", 2, 32),
        Grade("EPIC", "마력이 깃든", 3, 65),
        Grade("UNIQUE", "이름 없는", 4, 130),
        Grade("LEGENDARY", "전설의", 5, 300),
        Grade("MYTHIC", "신화의", 6, 700)
    )

    private val themes = listOf(
        "순례자의", "묘지기의", "까마귀", "검은비", "쇠사슬", "잊힌 왕의", "파수꾼의", "핏빛", "황혼",
        "새벽을 가르는", "늑대사냥꾼의", "거미줄", "재의", "침묵", "장송", "유배자의", "깊은 우물의",
        "부서진 맹세의", "마녀사냥꾼의", "달 없는 밤의", "뼈무덤", "성벽", "폭풍 전야의", "굶주린",
        "망자의", "심연", "별을 삼킨", "왕좌를 꿰뚫는", "종말", "신을 베는"
    )

    fun all(): List<ItemDefinitionEntity> = buildList(83) {
        addCategory("HELMET", "투구", "ui/items/item_crude_helmet.png", 27)
        addCategory("ARMOR", "갑옷", "ui/items/item_crude_armor.png", 28)
        addCategory("BOOTS", "장화", "ui/items/item_crude_boots.png", 28)
    }

    private fun MutableList<ItemDefinitionEntity>.addCategory(
        category: String,
        label: String,
        assetPath: String,
        count: Int
    ) {
        repeat(count) { index ->
            val grade = grades[(index / 4).coerceAtMost(grades.lastIndex)]
            val health = when (category) {
                "ARMOR" -> 2 + grade.tier * 2 + index % 3
                "HELMET" -> 1 + grade.tier + (index + 1) % 3
                else -> 1 + grade.tier + index % 2
            }
            val specialEffect = when {
                category == "HELMET" && grade.tier >= 2 && index % 3 != 0 -> "RANGED_BLOCK_30"
                category == "ARMOR" && grade.tier >= 2 && index % 3 == 1 -> "MELEE_BLOCK_30"
                category == "BOOTS" && grade.tier >= 2 && index % 3 == 2 -> "FREE_MOVE_30"
                else -> null
            }
            val optionText = when (specialEffect) {
                "RANGED_BLOCK_30" -> "원거리 공격을 30% 확률로 완전히 방어"
                "MELEE_BLOCK_30" -> "인접 일반 공격을 30% 확률로 완전히 방어"
                "FREE_MOVE_30" -> "이동 시 30% 확률로 행동을 소모하지 않음"
                else -> "${themes[index]}의 가호로 생명력 강화"
            }
            val code = "exp_${category.lowercase()}_${index.toString().padStart(2, '0')}"
            add(
                ItemDefinitionEntity(
                    code = code,
                    name = "${grade.label} ${themes[index]} $label",
                    category = category,
                    grade = grade.code,
                    storeType = "DROP_ONLY",
                    basePrice = grade.price + index * (grade.tier + 1),
                    unitsPerPurchase = 1,
                    assetPath = assetPath,
                    isConsumable = false,
                    maxStack = 1,
                    attackPower = 0,
                    attackTurnCost = 0,
                    attackRange = 0,
                    healthBonus = health,
                    detail = "세트 계열: ${themes[index]} · 최대 체력 +$health · $optionText",
                    specialEffect = specialEffect,
                    dropRate = .002,
                    playerSheetPath = null,
                    sortOrder = 2_000 + when (category) {
                        "HELMET" -> index
                        "ARMOR" -> 100 + index
                        else -> 200 + index
                    }
                )
            )
        }
    }
}
