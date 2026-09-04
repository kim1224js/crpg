package es.kim.crpg.data

class InventoryRepository(private val database: GameDatabase) {
    data class Result(
        val message: String,
        val items: List<OwnedItemEntity>,
        val gold: Int? = null
    )

    fun purchase(
        ownerId: Long,
        itemCode: String,
        displayName: String,
        unitPrice: Int,
        unitsPerPurchase: Int,
        purchaseQuantity: Int
    ): Result {
        var message = "구매할 수 없습니다."
        var updatedGold: Int? = null
        database.runInTransaction {
            val profile = database.loginProfileDao().getById(ownerId)
            val totalPrice = unitPrice * purchaseQuantity
            if (profile == null) {
                message = "로그인 정보를 찾을 수 없습니다."
                return@runInTransaction
            }
            if (profile.gold < totalPrice) {
                message = "골드가 부족합니다."
                return@runInTransaction
            }
            val dao = database.ownedItemDao()
            val existing = dao.findItem(ownerId, "INVENTORY", itemCode)
            val addedQuantity = unitsPerPurchase * purchaseQuantity
            if (existing != null) {
                dao.updateQuantity(existing.id, existing.quantity + addedQuantity)
            } else {
                val usedSlots = dao.getForOwner(ownerId).filter { it.container == "INVENTORY" }.map { it.slotIndex }.toSet()
                val inventoryCapacity = database.gameMasterDao().getConfigInt("inventory_capacity") ?: 25
                val emptySlot = (0 until inventoryCapacity).firstOrNull { it !in usedSlots }
                if (emptySlot == null) {
                    message = "인벤토리가 가득 찼습니다."
                    return@runInTransaction
                }
                dao.insert(OwnedItemEntity(
                    ownerId = ownerId,
                    characterId = profile.activeCharacterId,
                    itemCode = itemCode,
                    displayName = displayName,
                    quantity = addedQuantity,
                    container = "INVENTORY",
                    slotIndex = emptySlot
                ))
            }
            updatedGold = profile.gold - totalPrice
            database.loginProfileDao().updateGold(ownerId, updatedGold!!)
            message = "$displayName ${addedQuantity}개를 구매했습니다."
        }
        return Result(message, database.ownedItemDao().getForOwner(ownerId), updatedGold)
    }

    fun transfer(ownerId: Long, item: OwnedItemEntity): Result {
        val targetContainer = if (item.container == "INVENTORY") "STORAGE" else "INVENTORY"
        val targetCapacity = if (targetContainer == "INVENTORY") {
            database.gameMasterDao().getConfigInt("inventory_capacity") ?: 25
        } else {
            database.gameMasterDao().getConfigInt("storage_capacity") ?: 20
        }
        var message = "이동할 수 없습니다."
        database.runInTransaction {
            val dao = database.ownedItemDao()
            val allItems = dao.getForOwner(ownerId)
            val sameTarget = allItems.firstOrNull { it.container == targetContainer && it.itemCode == item.itemCode }
            if (sameTarget != null) {
                dao.updateQuantity(sameTarget.id, sameTarget.quantity + item.quantity)
                dao.deleteById(item.id)
            } else {
                val used = allItems.filter { it.container == targetContainer }.map { it.slotIndex }.toSet()
                val emptySlot = (0 until targetCapacity).firstOrNull { it !in used }
                if (emptySlot == null) {
                    message = if (targetContainer == "INVENTORY") "인벤토리가 가득 찼습니다." else "창고가 가득 찼습니다."
                    return@runInTransaction
                }
                dao.updateLocation(item.id, targetContainer, emptySlot)
            }
            message = if (targetContainer == "INVENTORY") "인벤토리로 이동했습니다." else "창고에 보관했습니다."
        }
        return Result(message, database.ownedItemDao().getForOwner(ownerId))
    }

    fun moveToSlot(ownerId: Long, itemId: Long, targetContainer: String, targetSlot: Int): Result {
        var message = "아이템을 이동할 수 없습니다."
        database.runInTransaction {
            val capacityKey = if (targetContainer == "STORAGE") "storage_capacity" else "inventory_capacity"
            val fallbackCapacity = if (targetContainer == "STORAGE") 20 else 25
            val targetCapacity = database.gameMasterDao().getConfigInt(capacityKey) ?: fallbackCapacity
            if (targetContainer !in setOf("INVENTORY", "STORAGE") || targetSlot !in 0 until targetCapacity) {
                message = "사용할 수 없는 아이템 칸입니다."
                return@runInTransaction
            }
            val dao = database.ownedItemDao()
            val source = dao.getForOwner(ownerId).firstOrNull { it.id == itemId } ?: return@runInTransaction
            if (source.container == targetContainer && source.slotIndex == targetSlot) return@runInTransaction
            val target = dao.findAtSlot(ownerId, targetContainer, targetSlot)
            if (target == null) {
                dao.updateLocation(source.id, targetContainer, targetSlot)
                message = "${source.displayName}을 이동했습니다."
            } else {
                val temporarySlot = -1_000_000 - target.id.toInt().coerceAtMost(999_999)
                dao.updateLocation(target.id, target.container, temporarySlot)
                dao.updateLocation(source.id, targetContainer, targetSlot)
                dao.updateLocation(target.id, source.container, source.slotIndex)
                message = "아이템 위치를 바꿨습니다."
            }
        }
        return Result(message, database.ownedItemDao().getForOwner(ownerId))
    }

    fun sell(ownerId: Long, itemId: Long): Result {
        var message = "판매할 수 없습니다."
        var updatedGold: Int? = null
        database.runInTransaction {
            val dao = database.ownedItemDao()
            val item = dao.getForOwner(ownerId).firstOrNull { it.id == itemId } ?: return@runInTransaction
            if (!item.isSellable) { message = "무료로 받은 아이템은 판매할 수 없습니다."; return@runInTransaction }
            val definition = database.gameMasterDao().getItem(item.itemCode) ?: return@runInTransaction
            if (definition.isConsumable) { message = "장비만 판매할 수 있습니다."; return@runInTransaction }
            val profile = database.loginProfileDao().getById(ownerId) ?: return@runInTransaction
            val salePrice = definition.basePrice / 2
            dao.deleteById(item.id)
            updatedGold = profile.gold + salePrice
            database.loginProfileDao().updateGold(ownerId, updatedGold!!)
            message = "${item.displayName}을 ${salePrice}G에 판매했습니다."
        }
        return Result(message, database.ownedItemDao().getForOwner(ownerId), updatedGold)
    }

    fun grantFreeMerchantBox(ownerId: Long, itemCode: String, displayName: String): Result {
        var message = "무료 상자를 받을 수 없습니다."
        var successGold: Int? = null
        database.runInTransaction {
            val profile = database.loginProfileDao().getById(ownerId) ?: return@runInTransaction
            val dao = database.ownedItemDao()
            val usedSlots = dao.getForOwner(ownerId).filter { it.container == "INVENTORY" }.map { it.slotIndex }.toSet()
            val capacity = database.gameMasterDao().getConfigInt("inventory_capacity") ?: 25
            val emptySlot = (0 until capacity).firstOrNull { it !in usedSlots }
            if (emptySlot == null) {
                message = "인벤토리가 가득 찼습니다."
                return@runInTransaction
            }
            dao.insert(OwnedItemEntity(
                ownerId = ownerId, characterId = profile.activeCharacterId,
                itemCode = itemCode, displayName = displayName, quantity = 1,
                container = "INVENTORY", slotIndex = emptySlot, isSellable = false
            ))
            successGold = profile.gold
            message = "$displayName 1개를 받았습니다."
        }
        return Result(message, database.ownedItemDao().getForOwner(ownerId), successGold)
    }
}
