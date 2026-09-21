package es.kim.crpg.game.catalog

import es.kim.crpg.data.ItemDefinitionEntity
import es.kim.crpg.game.rules.ArmorSetRules

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
            val health = if (category == "CLOAK") 0 else grade.tier + index % 2
            val baseEffect = when (category) {
                "ACCESSORY" -> listOf("ATTACK_PLUS_1", "GOLD_BONUS_20", "MOVE2_ATTACK_PLUS_2")[index % 3]
                "AUXILIARY" -> listOf("SPEAR_BONUS_3", "RANGED_ROOT_20", "RANGED_BLOCK_20")[index % 3]
                else -> cloakEffect(index, grade.tier)
            }.takeIf { grade.tier >= 2 || category == "CLOAK" }
            val effect = if (category == "CLOAK" && index < 28) {
                listOfNotNull("SET=${index.toString().padStart(2, '0')}", baseEffect).joinToString("|")
            } else baseEffect
            val effectText = when {
                baseEffect?.startsWith("RANGED_BLOCK=") == true -> "원거리 공격을 ${baseEffect.substringAfter('=')}% 확률로 완전히 방어"
                baseEffect?.startsWith("MELEE_BLOCK=") == true -> "인접 공격을 ${baseEffect.substringAfter('=')}% 확률로 완전히 방어"
                baseEffect?.startsWith("DUAL_BLOCK=") == true -> "근접·원거리 공격을 각각 ${baseEffect.substringAfter('=')}% 확률로 완전히 방어"
                baseEffect?.startsWith("DODGE=") == true -> "모든 공격을 ${baseEffect.substringAfter('=')}% 확률로 회피"
                baseEffect?.startsWith("DAMAGE_REDUCE=") == true -> "받는 피해를 ${baseEffect.substringAfter('=')} 감소"
                baseEffect == "ATTACK_PLUS_1" -> "모든 무기 공격력 +1"
                baseEffect == "GOLD_BONUS_20" -> "골드 획득 시 20% 확률로 1G 추가"
                baseEffect == "MOVE2_ATTACK_PLUS_2" -> "2칸 이상 이동 후 공격력 +2"
                baseEffect == "SPEAR_BONUS_3" -> "창 공격 시 추가 피해 3"
                baseEffect == "RANGED_ROOT_20" -> "원거리 적중 시 20% 확률로 1턴 속박"
                baseEffect == "RANGED_BLOCK_20" -> "원거리 공격을 20% 확률로 무효화"
                baseEffect == "MELEE_BLOCK_30" -> "근접 공격을 30% 확률로 무효화"
                baseEffect == "RANGED_BLOCK_30" -> "원거리 공격을 30% 확률로 무효화"
                else -> "고유 효과 없음"
            }
            val setText = if (category == "CLOAK" && index < 28) {
                "세트 계열: ${themes[index]} · 2세트 최대 체력 +${ArmorSetRules.twoPieceBonus(grade.code)} · " +
                    "3세트 추가 최대 체력 +${ArmorSetRules.threePieceBonus(grade.code)} · "
            } else if (category == "CLOAK") "독립 계열: ${themes[index]} · " else "세트 계열: ${themes[index]} · "
            add(ItemDefinitionEntity(
                code = "exp_${category.lowercase()}_${index.toString().padStart(2, '0')}",
                name = "${grade.label} ${themes[index]} $label", category = category, grade = grade.code,
                storeType = "DROP_ONLY", basePrice = grade.price + index * (grade.tier + 1), unitsPerPurchase = 1,
                assetPath = assets[index % assets.size], isConsumable = false, maxStack = 1,
                attackPower = 0, attackTurnCost = 0, attackRange = 0, healthBonus = health,
                detail = "$setText$effectText", specialEffect = effect, dropRate = .002,
                playerSheetPath = null, sortOrder = 2_500 + when (category) { "ACCESSORY" -> index; "AUXILIARY" -> 100 + index; else -> 200 + index }
            ))
        }
    }

    private fun cloakEffect(index: Int, tier: Int): String {
        val block = (15 + tier * 4).coerceAtMost(40)
        val dodge = (8 + tier * 3).coerceAtMost(26)
        return when (index % 6) {
            0 -> "RANGED_BLOCK=$block"
            1 -> "MELEE_BLOCK=$block"
            2 -> "DODGE=$dodge"
            3 -> "DAMAGE_REDUCE=${1 + tier / 2}"
            4 -> "DUAL_BLOCK=${(block - 5).coerceAtLeast(10)}"
            else -> "RANGED_BLOCK=${(block + 5).coerceAtMost(40)}"
        }
    }
}
