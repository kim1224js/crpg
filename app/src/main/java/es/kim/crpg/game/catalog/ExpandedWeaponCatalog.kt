package es.kim.crpg.game.catalog

import es.kim.crpg.data.ItemDefinitionEntity

object ExpandedWeaponCatalog {
    private data class Family(val code: String, val label: String, val attack: Int, val turns: Int, val range: Int, val style: String, val icon: String, val sheet: String)
    private data class Grade(val code: String, val theme: String, val bonus: Int, val price: Int, val options: Int)

    private val families = listOf(
        Family("sword", "검", 3, 1, 1, "ADJACENT_SWEEP", "ui/items/weapons/expanded/weapon_sword.png", "ui/dungeon/player/player_sword_animation_sheet.png"),
        Family("spear", "창", 4, 2, 3, "LINE_THRUST", "ui/items/weapons/expanded/weapon_spear.png", "ui/dungeon/player/player_spear_animation_sheet.png"),
        Family("bow", "활", 3, 1, 4, "DOUBLE_SHOT_50", "ui/items/weapons/expanded/weapon_bow.png", "ui/dungeon/player/player_bow_animation_sheet.png"),
        Family("gun", "총", 5, 2, 6, "KILL_PIERCE", "ui/items/weapons/expanded/weapon_gun.png", "ui/dungeon/player/player_gun_animation_sheet.png")
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
        val concept = conceptTrait(index, tier, chance, power)
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
        return (listOf(concept) + List(count + 2) { pool[(index + tier + it) % pool.size] })
            .distinctBy { it.first.substringBefore('=') }
            .take(count)
    }

    private fun conceptTrait(index: Int, tier: Int, chance: Int, power: Int): Pair<String, String> = when (index) {
        0 -> "KILL_HEAL=${(18 + tier * 5).coerceAtMost(55)}" to "순례의 축복: 처치 시 ${(18 + tier * 5).coerceAtMost(55)}% 확률로 HP 1 회복"
        1 -> "EXECUTE=${14 + tier * 3}:$power" to "묘지기의 선고: 체력 ${14 + tier * 3}% 이하 적에게 피해 +$power"
        2 -> "FIRST=${power + 1}" to "까마귀의 급습: 상처 없는 적에게 첫 타격 피해 +${power + 1}"
        3 -> "CRIT=${(chance + 6).coerceAtMost(70)}" to "검은비의 흉조: ${(chance + 6).coerceAtMost(70)}% 확률로 치명타 2배"
        4 -> "ROOT=${(chance + 4).coerceAtMost(70)}" to "쇠사슬 구속: ${(chance + 4).coerceAtMost(70)}% 확률로 1턴 속박"
        5 -> "BOSS=${power + 1}" to "왕의 몰락: 보스에게 피해 +${power + 1}"
        6 -> "FOCUS=${power + 1}" to "파수꾼의 응시: 같은 적 연속 공격 시 피해 +${power + 1}"
        7 -> "BLEED=${(chance + 8).coerceAtMost(75)}" to "핏빛 의식: ${(chance + 8).coerceAtMost(75)}% 확률로 2턴 출혈 중첩"
        8 -> "EXECUTE=${18 + tier * 4}:${power + 1}" to "황혼의 마침표: 체력 ${18 + tier * 4}% 이하 적에게 피해 +${power + 1}"
        9 -> "FIRST=${power + 2}" to "새벽 가르기: 상처 없는 적에게 첫 타격 피해 +${power + 2}"
        10 -> "DISTANCE=${1 + tier / 2}" to "늑대사냥: 원거리에서 피해 +${1 + tier / 2}"
        11 -> "ROOT=${(chance + 2).coerceAtMost(70)}" to "거미줄 덫: ${(chance + 2).coerceAtMost(70)}% 확률로 1턴 속박"
        12 -> "SPLASH=${power + 1}" to "재의 폭발: 주 대상 주변 적에게 피해 ${power + 1}"
        13 -> "FOCUS=${power + 2}" to "침묵의 추적: 같은 적 연속 공격 시 피해 +${power + 2}"
        14 -> "KILL_HEAL=${(22 + tier * 5).coerceAtMost(60)}" to "장송의 회복: 처치 시 ${(22 + tier * 5).coerceAtMost(60)}% 확률로 HP 1 회복"
        15 -> "CRIT=${(chance + 9).coerceAtMost(75)}" to "유배자의 반격: ${(chance + 9).coerceAtMost(75)}% 확률로 치명타 2배"
        16 -> "PUSH=${(chance + 5).coerceAtMost(70)}" to "깊은 우물의 파동: ${(chance + 5).coerceAtMost(70)}% 확률로 1칸 밀치기"
        17 -> "WOUNDED=${power + 1}" to "부서진 맹세: 출혈 중인 적에게 피해 +${power + 1}"
        18 -> "BOSS=${power + 2}" to "마녀사냥: 보스에게 피해 +${power + 2}"
        19 -> "CRIT=${(chance + 4).coerceAtMost(70)}" to "월식의 칼날: ${(chance + 4).coerceAtMost(70)}% 확률로 치명타 2배"
        20 -> "EXECUTE=${20 + tier * 3}:${power + 1}" to "뼈무덤의 선고: 체력 ${20 + tier * 3}% 이하 적에게 피해 +${power + 1}"
        21 -> "PUSH=${(chance + 10).coerceAtMost(75)}" to "성벽의 충격: ${(chance + 10).coerceAtMost(75)}% 확률로 1칸 밀치기"
        22 -> "SPLASH=${power + 2}" to "폭풍 전야: 주 대상 주변 적에게 피해 ${power + 2}"
        23 -> "WOUNDED=${power + 2}" to "굶주림: 출혈 중인 적에게 피해 +${power + 2}"
        24 -> "KILL_HEAL=${(25 + tier * 5).coerceAtMost(65)}" to "망자의 포식: 처치 시 ${(25 + tier * 5).coerceAtMost(65)}% 확률로 HP 1 회복"
        25 -> "ROOT=${(chance + 10).coerceAtMost(75)}" to "심연의 손: ${(chance + 10).coerceAtMost(75)}% 확률로 1턴 속박"
        26 -> "DISTANCE=${2 + tier / 2}" to "별 포식: 원거리에서 피해 +${2 + tier / 2}"
        27 -> "BOSS=${power + 3}" to "왕좌 관통: 보스에게 피해 +${power + 3}"
        28 -> "EXECUTE=${24 + tier * 3}:${power + 2}" to "종말 선고: 체력 ${24 + tier * 3}% 이하 적에게 피해 +${power + 2}"
        else -> "CRIT=${(chance + 12).coerceAtMost(80)}" to "신살: ${(chance + 12).coerceAtMost(80)}% 확률로 치명타 2배"
    }
}
