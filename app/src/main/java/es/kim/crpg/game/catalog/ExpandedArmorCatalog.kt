package es.kim.crpg.game.catalog

import es.kim.crpg.data.ItemDefinitionEntity
import es.kim.crpg.game.rules.ArmorSetRules

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

    fun all(): List<ItemDefinitionEntity> = buildList(84) {
        addCategory("HELMET", "투구", "ui/items/item_crude_helmet.png", 28)
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
            val (effect, optionText) = namedOption(category, index, grade.tier)
            val setKey = index.toString().padStart(2, '0')
            val specialEffect = "SET=$setKey|$effect"
            val twoPieceHp = ArmorSetRules.twoPieceBonus(grade.code)
            val threePieceHp = ArmorSetRules.threePieceBonus(grade.code)
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
                    healthBonus = 0,
                    detail = "세트 계열: ${themes[index]} · $optionText · 2세트 최대 체력 +$twoPieceHp · 3세트 추가 최대 체력 +$threePieceHp",
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

    private fun namedOption(category: String, index: Int, tier: Int): Pair<String, String> {
        val block = (15 + tier * 4 + if (index in setOf(5, 6, 18, 21, 27)) 4 else 0).coerceAtMost(45)
        val dodge = (8 + tier * 3 + if (index in setOf(2, 8, 19, 26)) 2 else 0).coerceAtMost(28)
        val freeMove = (15 + tier * 4).coerceAtMost(40)
        val reduction = 1 + tier / 2
        val option = when (index) {
            0, 7, 12, 17, 23 -> "DAMAGE_REDUCE=$reduction" to "받는 피해를 $reduction 감소"
            1, 4, 6, 14, 20, 21, 24 -> "MELEE_BLOCK=$block" to "인접 공격을 $block% 확률로 완전히 방어"
            2, 8, 11, 15, 19, 26 -> "DODGE=$dodge" to "모든 공격을 $dodge% 확률로 회피"
            3, 5, 9, 13, 16, 18, 25 -> "RANGED_BLOCK=$block" to "원거리 공격을 $block% 확률로 완전히 방어"
            10, 22 -> "FREE_MOVE=$freeMove" to "이동 시 $freeMove% 확률로 행동을 소모하지 않음"
            27 -> "DUAL_BLOCK=$block" to "근접·원거리 공격을 각각 $block% 확률로 완전히 방어"
            else -> "DAMAGE_REDUCE=$reduction" to "받는 피해를 $reduction 감소"
        }
        return when (category) {
            "HELMET" -> option
            "ARMOR" -> if (option.first.startsWith("RANGED_BLOCK")) "DAMAGE_REDUCE=$reduction" to "받는 피해를 $reduction 감소" else option
            "BOOTS" -> if (index % 5 == 0) "FREE_MOVE=$freeMove" to "이동 시 $freeMove% 확률로 행동을 소모하지 않음" else option
            else -> option
        }
    }
}
