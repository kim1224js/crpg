package es.kim.crpg.game.rules

/** Numeric options are read from item_definition.specialEffect, not inferred from item names. */
object EquipmentSpecialRules {
    fun values(effect: String?): Map<String, Int> = effect.orEmpty().split('|').mapNotNull {
        val value = it.substringAfter('=', "").toIntOrNull() ?: return@mapNotNull null
        it.substringBefore('=') to value
    }.toMap()

    fun highHealth(hp: Int, maxHp: Int) = hp > 0 && hp * 100 >= maxHp * 80
    fun lowHealth(hp: Int, maxHp: Int) = hp > 0 && hp * 100 <= maxHp * 30
    fun reducedDamage(damage: Int, percent: Int): Int =
        (damage.coerceAtLeast(0) * (100 - percent.coerceIn(0, 100)) + 99) / 100
    fun lifeSteal(damage: Int, targetHp: Int, percent: Int): Int =
        (damage.coerceAtLeast(0).coerceAtMost(targetHp.coerceAtLeast(0)) * percent.coerceIn(0, 100)) / 100

    fun description(key: String, value: Int): String = when (key) {
        "SWORD_BEAM" -> "HP 80% 이상: 직선 ${value}칸 검기"
        "KILL_SLASH" -> "검으로 처치 후 주변 적 1마리에게 추가 피해 $value (연쇄 없음)"
        "PIERCE" -> "직선 화살 관통: 뒤쪽 적에게 원래 피해의 $value%"
        "RICOCHET" -> "화살 도탄: 2칸 내 다른 적 1마리에게 원래 피해의 $value%"
        "SLOW" -> "적중 시 $value% 확률로 2행동 동안 이동 거리 1 감소"
        "ARMOR_PIERCE" -> "총탄 관통력: 피해 $value% 증가"
        "WALL_IMPACT" -> "총 적중 시 1칸 밀치기, 벽 충돌 시 추가 피해 $value"
        "KILL_RELOAD" -> "총으로 ${value}회 연속 처치 시 해당 공격 재장전 1턴 생략"
        "REFLECT" -> "원거리 공격을 $value% 확률로 무효화하고 반사"
        "AILMENT_GUARD" -> "독·화상 부여를 $value% 확률로 방어"
        "RANGED_REDUCE" -> "원거리 공격 피해 $value% 경감"
        "MELEE_REDUCE" -> "근접 공격 피해 $value% 경감"
        "FIRST_APPROACH" -> "층마다 적에게 다가가는 첫 ${value}회 이동 무료"
        "MOVE_DODGE" -> "이동 직후 적 행동 동안 회피율 $value%"
        "FIRST_DODGE" -> "각 몬스터의 첫 공격을 $value% 확률로 회피"
        "CRIT_PREVIEW" -> "다음 타격의 치명타 발동 여부 예고"
        "LIFESTEAL" -> "무기 직접 피해의 $value% 흡혈 (실제 깎은 HP 기준, 소수점 버림)"
        "KILL_ROOT" -> "처치 지점 주변 1칸의 적을 ${value}행동 속박"
        "LOW_HP_POWER" -> "HP 30% 이하에서 무기 공격력 +$value"
        "POWER" -> "무기 공격력 +$value"
        "TAKEN_MORE" -> "대가: 받는 일반 공격 피해 $value% 증가"
        else -> "$key=$value"
    }
}
