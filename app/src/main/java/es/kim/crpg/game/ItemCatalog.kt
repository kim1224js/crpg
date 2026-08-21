package es.kim.crpg.game

data class EquipmentOption(
    val category: String,
    val lines: List<String>,
    val specialEffect: String? = null
)

data class StoreItem(
    val code: String,
    val name: String,
    val price: Int,
    val unitsPerPurchase: Int,
    val assetPath: String,
    val detail: String? = null,
    val equipmentOption: EquipmentOption? = null,
    val isConsumable: Boolean = false
)

object ItemCatalog {
    val generalStore = listOf(
        StoreItem("torch", "횃불 5개", 1, 5, "ui/items/item_torch.png", isConsumable = true),
        StoreItem("return_stone", "귀환석", 2, 1, "ui/items/item_return_stone.png", isConsumable = true),
        StoreItem("camping_kit", "야영 세트", 3, 1, "ui/items/item_camping_kit.png", isConsumable = true),
        StoreItem("fire_bomb", "화염병", 2, 1, "ui/items/item_fire_bomb.png", isConsumable = true)
    )

    val blacksmith = listOf(
        StoreItem("crude_sword", "조잡한 검", 5, 1, "ui/items/item_crude_sword.png", "공격 3 · 1턴 · 사거리 1", EquipmentOption("무기 · 검", listOf("공격력  3", "공격 소모  1턴", "사거리  1칸"))),
        StoreItem("crude_spear", "조잡한 창", 5, 1, "ui/items/item_crude_spear.png", "공격 4 · 2턴 · 사거리 3", EquipmentOption("무기 · 창", listOf("공격력  4", "공격 소모  2턴", "사거리  3칸"))),
        StoreItem("crude_bow", "조잡한 활", 5, 1, "ui/items/item_crude_bow.png", "공격 2 · 1턴 · 사거리 4", EquipmentOption("무기 · 활", listOf("공격력  2", "공격 소모  1턴", "사거리  4칸"))),
        StoreItem("crude_gun", "조잡한 총", 5, 1, "ui/items/item_crude_gun.png", "공격 3 · 2턴 · 사거리 5", EquipmentOption("무기 · 총", listOf("공격력  3", "공격 소모  2턴", "사거리  5칸"))),
        StoreItem("crude_armor", "조잡한 갑옷", 5, 1, "ui/items/item_crude_armor.png", "체력 +1", EquipmentOption("방어구 · 갑옷", listOf("최대 체력  +1"))),
        StoreItem("crude_helmet", "조잡한 투구", 5, 1, "ui/items/item_crude_helmet.png", "체력 +1", EquipmentOption("방어구 · 투구", listOf("최대 체력  +1"))),
        StoreItem("crude_boots", "조잡한 신발", 5, 1, "ui/items/item_crude_boots.png", "이동 시 50% 확률로 +1칸", EquipmentOption("방어구 · 신발", listOf("기본 이동  1칸"), "이동 시 50% 확률로 이동 거리 +1칸"))
    )

    private val byCode = (generalStore + blacksmith).associateBy { it.code }

    fun get(code: String): StoreItem? = byCode[code]
    fun assetPath(code: String): String = byCode[code]?.assetPath ?: "ui/items/item_return_stone.png"
    fun equipmentOption(code: String): EquipmentOption? = byCode[code]?.equipmentOption
    fun isConsumable(code: String): Boolean = byCode[code]?.isConsumable == true
}
