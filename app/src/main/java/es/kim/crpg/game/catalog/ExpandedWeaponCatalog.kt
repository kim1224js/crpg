package es.kim.crpg.game.catalog

import es.kim.crpg.data.ItemDefinitionEntity

object ExpandedWeaponCatalog {
    private data class Family(val code: String, val label: String, val attack: Int, val turns: Int, val range: Int, val style: String, val icon: String, val sheet: String)
    private data class Grade(val code: String, val theme: String, val bonus: Int, val price: Int, val options: Int)

    private val families = listOf(
        Family("sword", "검", 3, 1, 1, "ADJACENT_SWEEP", "ui/items/weapons/expanded/weapon_sword.png", "ui/dungeon/player/player_sword_animation_sheet.png"),
        Family("spear", "창", 4, 2, 3, "LINE_THRUST", "ui/items/weapons/expanded/weapon_spear.png", "ui/dungeon/player/player_spear_animation_sheet.png"),
        Family("bow", "활", 3, 1, 4, "DOUBLE_SHOT_50", "ui/items/weapons/expanded/weapon_bow.png", "ui/dungeon/player/player_bow_animation_sheet.png"),
        Family("gun", "총", 5, 2, 5, "KILL_PIERCE", "ui/items/weapons/expanded/weapon_gun.png", "ui/dungeon/player/player_gun_animation_sheet.png")
    )
    private val grades = listOf(
        Grade("NORMAL", "낡은", 0, 8, 1), Grade("HIGH", "단련된", 1, 16, 1),
        Grade("RARE", "저주받은", 2, 32, 2), Grade("EPIC", "마력이 깃든", 3, 65, 3),
        Grade("UNIQUE", "이름 없는", 4, 130, 3), Grade("LEGENDARY", "전설의", 6, 300, 4),
        Grade("MYTHIC", "신화의", 8, 700, 5)
    )
    private val epithets = listOf(
        "순례자의", "묘지기의", "까마귀", "검은비", "쇠사슬", "잊힌 왕의", "파수꾼의", "핏빛", "황혼",
        "새벽을 가르는", "늑대사냥꾼의", "거미줄", "재의", "침묵", "장송", "유배자의", "깊은 우물의",
        "부서진 맹세의", "마녀사냥꾼의", "달 없는 밤의", "뼈무덤", "성벽", "폭풍 전야의", "굶주린",
        "망자의", "심연", "별을 삼킨", "왕좌를 꿰뚫는", "종말", "신을 베는"
    )

    fun all(): List<ItemDefinitionEntity> = buildList(210) {
        grades.forEachIndexed { tier, grade ->
            repeat(30) { index ->
                val family = families[(index + tier) % families.size]
                val traits = traitsFor(family, tier, index, grade.options)
                val code = "exp_${grade.code.lowercase()}_${family.code}_${index.toString().padStart(2, '0')}"
                add(ItemDefinitionEntity(
                    code = code,
                    name = "${grade.theme} ${epithets[index]} ${family.label}", category = "WEAPON", grade = grade.code,
                    storeType = "DROP_ONLY", basePrice = grade.price + index * (tier + 1), unitsPerPurchase = 1,
                    assetPath = when (family.code) {
                        "sword" -> "ui/items/weapons/expanded/swords/$code.png"
                        "bow" -> "ui/items/weapons/expanded/bows/$code.png"
                        "spear" -> "ui/items/weapons/expanded/spears/$code.png"
                        "gun" -> "ui/items/weapons/expanded/guns/$code.png"
                        else -> family.icon
                    },
                    isConsumable = false, maxStack = 1, attackPower = family.attack + grade.bonus,
                    attackTurnCost = family.turns, attackRange = family.range, healthBonus = 0,
                    detail = (listOf("공격 ${family.attack + grade.bonus} · ${family.turns}턴 · 사거리 ${family.range}") + traits.map { it.second }).joinToString(" · "),
                    specialEffect = (listOf(family.style) + traits.map { it.first }).joinToString("|"), dropRate = 0.0,
                    playerSheetPath = family.sheet, sortOrder = 1_000 + tier * 100 + index
                ))
            }
        }
    }

    private fun traitsFor(family: Family, tier: Int, index: Int, count: Int): List<Pair<String, String>> {
        val chance = (12 + tier * 7 + index % 4 * 3).coerceAtMost(65)
        val power = 1 + tier / 2
        val common = listOf(
            "CRIT=$chance" to "${chance}% 확률로 치명타 2배",
            "EXECUTE=${10 + tier * 4}:$power" to "체력 ${10 + tier * 4}% 이하 적에게 피해 +$power",
            "FIRST=$power" to "상처 없는 적에게 첫 타격 피해 +$power",
            "BOSS=$power" to "보스에게 피해 +$power",
            "KILL_HEAL=${(chance / 2).coerceAtLeast(8)}" to "처치 시 ${(chance / 2).coerceAtLeast(8)}% 확률로 HP 1 회복"
        )
        val specific = when (family.code) {
            "sword" -> listOf("BLEED=$chance" to "${chance}% 확률로 2턴 출혈 중첩", "SPLASH=$power" to "주 대상 주변 적에게 피해 $power", "WOUNDED=$power" to "출혈 중인 적에게 피해 +$power")
            "spear" -> listOf("ROOT=$chance" to "${chance}% 확률로 1턴 속박", "PUSH=$chance" to "${chance}% 확률로 1칸 밀치기", "LINE_POWER=$power" to "동시에 둘 이상 적중 시 피해 +$power")
            "bow" -> listOf("DISTANCE=${1 + tier / 3}" to "3칸 이상 거리에서 피해 +${1 + tier / 3}", "ROOT=${(chance - 5).coerceAtLeast(8)}" to "${(chance - 5).coerceAtLeast(8)}% 확률로 1턴 속박", "FOCUS=$power" to "같은 적 연속 공격 시 피해 +$power")
            else -> listOf("PUSH=$chance" to "${chance}% 확률로 1칸 밀치기", "SPLASH=$power" to "충격 지점 주변 적에게 피해 $power", "DISTANCE=${1 + tier / 3}" to "4칸 이상 거리에서 피해 +${1 + tier / 3}")
        }
        val pool = specific + common
        return List(count) { pool[(index + tier + it) % pool.size] }.distinctBy { it.first.substringBefore('=') }
    }
}
