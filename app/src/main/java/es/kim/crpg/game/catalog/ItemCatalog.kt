package es.kim.crpg.game.catalog

import es.kim.crpg.data.ItemDefinitionEntity

data class EquipmentOption(val category: String, val lines: List<String>, val specialEffect: String? = null)

data class StoreItem(
    val code: String, val name: String, val price: Int, val unitsPerPurchase: Int,
    val assetPath: String, val detail: String? = null,
    val equipmentOption: EquipmentOption? = null, val isConsumable: Boolean = false
)

object ItemCatalog {
    private var definitions: List<ItemDefinitionEntity> = emptyList()
    private var byCode: Map<String, ItemDefinitionEntity> = emptyMap()

    fun initialize(items: List<ItemDefinitionEntity>) {
        definitions = items.sortedBy { it.sortOrder }
        byCode = definitions.associateBy { it.code }
    }

    val allDefinitions: List<ItemDefinitionEntity> get() = definitions
    val generalStore: List<StoreItem> get() = definitions.filter { "GENERAL" in it.storeType.split('_') }.map(::toStoreItem)
    val blacksmith: List<StoreItem> get() = definitions.filter { it.storeType == "BLACKSMITH" }.map(::toStoreItem)
    val travelingMerchant: List<StoreItem> get() = definitions.filter { "MERCHANT" in it.storeType.split('_') }.map(::toStoreItem)

    fun definition(code: String): ItemDefinitionEntity? = byCode[code]
    fun get(code: String): StoreItem? = byCode[code]?.let(::toStoreItem)
    fun assetPath(code: String): String = byCode[code]?.assetPath ?: "ui/items/item_return_stone.png"
    fun equipmentOption(code: String): EquipmentOption? = byCode[code]?.let(::toEquipmentOption)
    fun equipmentOption(item: ItemDefinitionEntity): EquipmentOption? = toEquipmentOption(item)
    fun isConsumable(code: String): Boolean = byCode[code]?.isConsumable == true
    fun isWeapon(code: String): Boolean = byCode[code]?.category == "WEAPON"
    fun category(code: String): String? = byCode[code]?.category
    fun grade(code: String): String = byCode[code]?.grade ?: "NORMAL"
    fun gradeColor(code: String): Int = when (grade(code)) {
        "HIGH" -> 0xFF65D57A.toInt(); "RARE" -> 0xFF61AFFF.toInt(); "EPIC" -> 0xFFC36BFF.toInt()
        "UNIQUE" -> 0xFFFFAD45.toInt(); "LEGENDARY" -> 0xFFFF5959.toInt(); "MYTHIC" -> 0xFFFFE586.toInt()
        else -> 0xFF866837.toInt()
    }
    fun salePrice(code: String): Int = (byCode[code]?.basePrice ?: 0) / 2

    private fun toStoreItem(item: ItemDefinitionEntity) = StoreItem(
        item.code, item.name, item.basePrice, item.unitsPerPurchase, item.assetPath,
        item.detail, toEquipmentOption(item), item.isConsumable
    )

    private fun toEquipmentOption(item: ItemDefinitionEntity): EquipmentOption? {
        if (item.category == "CONSUMABLE") return null
        val lines = buildList {
            if (item.attackPower > 0) add("공격력  ${item.attackPower}")
            if (item.attackTurnCost > 0) add("공격 소모  ${item.attackTurnCost}턴")
            if (item.attackRange > 0) add("사거리  ${item.attackRange}칸")
            if (item.healthBonus > 0) add("최대 체력  +${item.healthBonus}")
            if (item.category == "BOOTS") add("기본 이동  1칸")
            if (item.category in setOf("ARMOR", "HELMET", "BOOTS") && item.grade in setOf("UNIQUE", "LEGENDARY", "MYTHIC")) {
                add("등급 방호  피해 10 이상을 50% 경감")
            }
        }
        val categoryName = when (item.category) {
            "WEAPON" -> "무기"; "ARMOR" -> "방어구 · 갑옷"; "HELMET" -> "방어구 · 투구"
            "BOOTS" -> "방어구 · 신발"; "AUXILIARY" -> "보조장비"
            "ACCESSORY" -> "액세서리"; "RELIC" -> "유물 · 소지 시 발동"; else -> item.category
        }
        val effectDescription = if ('|' in item.specialEffect.orEmpty()) item.detail else when (item.specialEffect) {
            "ADJACENT_SWEEP" -> "휩쓸기: 캐릭터 주변 1칸의 모든 적을 한 번에 공격"
            "LINE_THRUST" -> "직선 찌르기: 사거리 안의 일직선 적을 모두 공격"
            "KILL_PIERCE" -> "처치 관통: 앞의 적이 죽으면 뒤의 적에게 탄환 관통"
            "DOUBLE_SHOT_50" -> "연속 사격: 50% 확률로 같은 대상에게 화살을 한 발 더 발사"
            "BLOCK_CHANCE_30" -> "방어: 몬스터 공격을 30% 확률로 완전히 무효화"
            "MELEE_BLOCK_30" -> "일반 방어: 인접한 적의 공격을 30% 확률로 완전히 무효화"
            "RANGED_BLOCK_30" -> "원거리 방어: 2칸 이상 떨어진 적의 공격을 30% 확률로 완전히 무효화"
            "RANGED_ROOT_20" -> "원거리 적중 시 20% 확률로 1턴 속박"
            "RANGED_POISON_SHOT_30" -> "원거리 공격 시 30% 확률로 피해 2 독침 추가 발사"
            "DODGE_COUNTER_30" -> "30% 확률로 공격을 회피하고 피해 2 반격"
            "KILL_BIND_BURST" -> "처치 시 주변 적에게 피해 2와 1턴 속박"
            "FREE_MOVE_30" -> "이동 시 30% 확률로 행동 미소모, 연속 발동 가능"
            "ATTACK_PLUS_1" -> "모든 무기 공격력 +1"
            "SWORD_BLEED" -> "검 공격 시 추가 피해 2와 중첩 출혈"
            "MOVE2_ATTACK_PLUS_2" -> "2칸 이상 이동 후 공격력 +2"
            "GOLD_BONUS_20" -> "골드 드랍 성공 시 20% 확률로 1G 추가"
            "FIRST_ATTACK_DODGE" -> "각 몬스터의 첫 공격을 100% 회피"
            "SPEAR_BONUS_3" -> "창 공격 시 대상에게 추가 피해 3"
            "REVIVE_50G" -> "치명 피해 시 50G를 내고 HP 5로 부활, 던전당 1회"
            "RANGED_BLOCK_20" -> "원거리 몬스터 공격을 20% 확률로 무효화"
            "REGEN_5T" -> "5턴 무피해 시 HP 1 회복, 층당 최대 2회"
            "LETHAL_SURVIVE" -> "치명 피해를 HP 1로 생존하고 즉시 파괴"
            "RELIC_EXPLORE_SENSE" -> "계단 방향을 표시하고 반경 5칸의 전리품을 감지"
            "RELIC_AILMENT_REDUCE_1" -> "독·화상·출혈 지속시간 1턴 감소"
            "RELIC_RANGED_PULL_30" -> "원거리 적중 시 30% 확률로 적을 1칸 끌어당김"
            "RELIC_LOW_HP_HEAL_4" -> "층마다 최초 HP 3 이하 진입 시 HP 4 회복"
            "RELIC_KILL_POWER_ALERT" -> "처치 후 다음 공격력 +2, 주변 적 즉시 감지"
            "RELIC_NEAREST_ENEMY_SENSE" -> "가장 가까운 생존 몬스터의 방향과 거리를 표시"
            "RELIC_FIRST_HIT_MINUS_3" -> "층마다 처음 받는 피해를 3 감소"
            "RELIC_THIRD_KILL_HEAL_1" -> "몬스터 3마리 처치마다 HP 1 회복"
            "RELIC_FIRE_BOMB_ENHANCE" -> "화염병 사거리 4, 화염 지대 지속시간 3턴"
            "RELIC_ATTACK_PLUS_1_RECOIL" -> "모든 무기 공격력 +1, 5회 공격마다 자신에게 피해 1"
            else -> item.specialEffect
        }
        return EquipmentOption(categoryName, lines, effectDescription)
    }
}
