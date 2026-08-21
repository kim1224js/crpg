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
                val emptySlot = (0 until 10).firstOrNull { it !in usedSlots }
                if (emptySlot == null) {
                    message = "인벤토리가 가득 찼습니다."
                    return@runInTransaction
                }
                dao.insert(OwnedItemEntity(
                    ownerId = ownerId,
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
        val targetCapacity = if (targetContainer == "INVENTORY") 10 else 20
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
}
