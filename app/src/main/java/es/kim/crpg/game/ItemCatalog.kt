package es.kim.crpg.game

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
    val generalStore: List<StoreItem> get() = definitions.filter { it.storeType == "GENERAL" }.map(::toStoreItem)
    val blacksmith: List<StoreItem> get() = definitions.filter { it.storeType == "BLACKSMITH" }.map(::toStoreItem)

    fun definition(code: String): ItemDefinitionEntity? = byCode[code]
    fun get(code: String): StoreItem? = byCode[code]?.let(::toStoreItem)
    fun assetPath(code: String): String = byCode[code]?.assetPath ?: "ui/items/item_return_stone.png"
    fun equipmentOption(code: String): EquipmentOption? = byCode[code]?.let(::toEquipmentOption)
    fun isConsumable(code: String): Boolean = byCode[code]?.isConsumable == true
    fun isWeapon(code: String): Boolean = byCode[code]?.category == "WEAPON"

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
        }
        val categoryName = when (item.category) {
            "WEAPON" -> "무기"; "ARMOR" -> "방어구 · 갑옷"; "HELMET" -> "방어구 · 투구"
            "BOOTS" -> "방어구 · 신발"; else -> item.category
        }
        return EquipmentOption(categoryName, lines, item.specialEffect)
    }
}
