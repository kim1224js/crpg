package es.kim.crpg.game.catalog

import es.kim.crpg.data.ItemDefinitionEntity

object ExpandedSupplementCatalog {
    private data class Grade(val code: String, val label: String, val tier: Int, val price: Int)
    private val grades = listOf(
        Grade("NORMAL", "낡은", 0, 9), Grade("HIGH", "단련된", 1, 18),
        Grade("RARE", "저주받은", 2, 36), Grade("EPIC", "마력이 깃든", 3, 72),
        Grade("UNIQUE", "이름 없는", 4, 145), Grade("LEGENDARY", "전설의", 5, 320),
        Grade("MYTHIC", "신화의", 6, 760)
    )
    private val themes = listOf(
        "순례자의", "묘지기의", "까마귀", "검은비", "쇠사슬", "잊힌 왕의", "파수꾼의", "핏빛", "황혼",
        "새벽을 가르는", "늑대사냥꾼의", "거미줄", "재의", "침묵", "장송", "유배자의", "깊은 우물의",
        "부서진 맹세의", "마녀사냥꾼의", "달 없는 밤의", "뼈무덤", "성벽", "폭풍 전야의", "굶주린",
        "망자의", "심연", "별을 삼킨", "왕좌를 꿰뚫는", "종말", "신을 베는"
    )

    fun all(): List<ItemDefinitionEntity> = buildList(79) {
        addItems("ACCESSORY", "부적", 25, listOf(
            "ui/items/rare/item_fang_necklace.png", "ui/items/rare/item_thief_coin_pouch.png",
            "ui/items/rare/item_spider_queen_heart.png", "ui/items/rare/item_greed_coin.png"
        ))
        addItems("AUXILIARY", "보조구", 24, listOf(
            "ui/items/rare/item_web_glove.png", "ui/items/rare/item_venom_dagger.png",
            "ui/items/rare/item_slime_shield.png", "ui/items/rare/item_throwing_dagger.png"
        ))
        addItems("CLOAK", "망토", 30, listOf("ui/items/item_crude_armor.png"))
    }

    private fun MutableList<ItemDefinitionEntity>.addItems(category: String, label: String, count: Int, assets: List<String>) {
        repeat(count) { index ->
            val grade = grades[(index / 4).coerceAtMost(grades.lastIndex)]
            val health = when (category) {
                "CLOAK" -> 2 + grade.tier + index % 3
                else -> grade.tier + index % 2
            }
            val effect = when (category) {
                "ACCESSORY" -> listOf("ATTACK_PLUS_1", "GOLD_BONUS_20", "MOVE2_ATTACK_PLUS_2")[index % 3]
                "AUXILIARY" -> listOf("SPEAR_BONUS_3", "RANGED_ROOT_20", "RANGED_BLOCK_20")[index % 3]
                else -> listOf(null, "MELEE_BLOCK_30", "RANGED_BLOCK_30")[index % 3]
            }.takeIf { grade.tier >= 2 }
            val effectText = when (effect) {
                "ATTACK_PLUS_1" -> "모든 무기 공격력 +1"
                "GOLD_BONUS_20" -> "골드 획득 시 20% 확률로 1G 추가"
                "MOVE2_ATTACK_PLUS_2" -> "2칸 이상 이동 후 공격력 +2"
                "SPEAR_BONUS_3" -> "창 공격 시 추가 피해 3"
                "RANGED_ROOT_20" -> "원거리 적중 시 20% 확률로 1턴 속박"
                "RANGED_BLOCK_20" -> "원거리 공격을 20% 확률로 무효화"
                "MELEE_BLOCK_30" -> "근접 공격을 30% 확률로 무효화"
                "RANGED_BLOCK_30" -> "원거리 공격을 30% 확률로 무효화"
                else -> "${themes[index]} 기운으로 최대 체력을 강화"
            }
            add(ItemDefinitionEntity(
                code = "exp_${category.lowercase()}_${index.toString().padStart(2, '0')}",
                name = "${grade.label} ${themes[index]} $label", category = category, grade = grade.code,
                storeType = "DROP_ONLY", basePrice = grade.price + index * (grade.tier + 1), unitsPerPurchase = 1,
                assetPath = assets[index % assets.size], isConsumable = false, maxStack = 1,
                attackPower = 0, attackTurnCost = 0, attackRange = 0, healthBonus = health,
                detail = "세트 계열: ${themes[index]} · 최대 체력 +$health · $effectText", specialEffect = effect, dropRate = .002,
                playerSheetPath = null, sortOrder = 2_500 + when (category) { "ACCESSORY" -> index; "AUXILIARY" -> 100 + index; else -> 200 + index }
            ))
        }
    }
}
