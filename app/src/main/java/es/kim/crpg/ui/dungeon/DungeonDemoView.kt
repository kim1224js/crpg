package es.kim.crpg.ui.dungeon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import es.kim.crpg.data.ItemDefinitionEntity
import es.kim.crpg.data.MonsterDefinitionEntity
import es.kim.crpg.data.MonsterDropEntity
import es.kim.crpg.data.MonsterFloorSpawnEntity
import es.kim.crpg.data.DungeonInteractableDefinitionEntity
import es.kim.crpg.data.DungeonInteractableSpawnEntity
import es.kim.crpg.ui.common.AntiqueGameDialog
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class DungeonDemoView(
    context: Context,
    initialInventoryCounts: Map<String, Int> = emptyMap(),
    equippedWeaponCode: String? = null,
    equippedItemCodes: Set<String> = emptySet(),
    private val appraisedAttackPowerByCode: Map<String, Int> = emptyMap(),
    itemDefinitions: List<ItemDefinitionEntity> = emptyList(),
    private val monsterDefinitions: List<MonsterDefinitionEntity> = emptyList(),
    private val monsterDrops: List<MonsterDropEntity> = emptyList(),
    private val monsterFloorSpawns: List<MonsterFloorSpawnEntity> = emptyList(),
    private val interactableDefinitions: List<DungeonInteractableDefinitionEntity> = emptyList(),
    private val interactableSpawns: List<DungeonInteractableSpawnEntity> = emptyList(),
    private val healingObjectChancePercent: Int = 30,
    private val monsterCountMin: Int = 5,
    private val monsterCountMax: Int = 10,
    private val returnStoneCombatLockFloor: Int = 11,
    private val dungeonChestSpawnPercent: Int = 25,
    private val dungeonChestMimicPercent: Int = 25,
    private val savedRunPayload: String? = null,
    villageGold: Int = 0,
    playerBaseHp: Int = 10,
    private val survivalDay: Int = 1,
    private val inventoryCapacity: Int = 10,
    private val onUseReturnStone: (Map<String, Int>, Int, Map<String, Int>, Set<String>) -> Unit = { _, _, _, _ -> },
    private val onExitDungeon: (Map<String, Int>, Int, Map<String, Int>, Set<String>) -> Unit = { _, _, _, _ -> },
    private val onPlayerDeath: (Int, Int, String?, String?) -> Unit = { _, _, _, _ -> },
    private val onSpendReviveGold: (Int) -> Unit = {},
    private val onEquipItem: (String, String) -> Unit = { _, _ -> },
    private val onFloorChanged: (Int) -> Unit = {},
    private val onPersistRun: (String) -> Unit = {}
) : View(context) {
    private enum class MonsterKind { SPIDER, BANDIT, WILD_DOG, SLIME }
    private enum class Phase { PLAYER, MONSTERS }
    private enum class ObstacleKind(
        val assetName: String, val flat: Boolean, val widthScale: Float, val heightScale: Float
    ) {
        STONE_PILLAR("stone_pillar", false, 1.35f, 2.35f), DEAD_TREE("dead_tree", false, 1.85f, 2.45f),
        BROKEN_WALL("broken_wall", false, 1.8f, 1.65f), WATER_POOL("water_pool", true, 1.2f, 1.15f),
        RUBBLE("rubble", false, 1.45f, 1.15f), SEALED_DOOR("sealed_door", false, 1.5f, 2.05f),
        OPEN_GATE("open_gate", false, 1.9f, 2.05f), BRAZIER("brazier", false, 1.35f, 1.75f),
        TAR_POOL("tar_pool", true, 1.2f, 1.1f), SPIKE_TRAP("spike_trap", true, 1.15f, 1.15f),
        CHASM("chasm", true, 1.2f, 1.15f), STONE_ALTAR("stone_altar", false, 1.65f, 1.5f),
        WOOD_BRIDGE("wood_bridge", true, 1.3f, 1.2f), CLAY_JARS("clay_jars", false, 1.55f, 1.45f),
        POISON_VENT("poison_vent", false, 1.5f, 1.65f), SPIDER_WEB("spider_web", false, 1.8f, 1.75f)
    }

    private data class UnitSprite(
        val name: String, var column: Int, var row: Int, var sheet: Bitmap,
        val kind: MonsterKind? = null, val definition: MonsterDefinitionEntity? = null, var hp: Int, val maxHp: Int,
        var alive: Boolean = true, var spiderOpeningAttack: Boolean = true,
        var drawColumn: Float = column.toFloat(), var drawRow: Float = row.toFloat(),
        var actionRow: Int = 0, var actionStartedAt: Long = 0L, var actionUntil: Long = 0L,
        var dying: Boolean = false, var droppedLoot: Boolean = false,
        var burnTurnsRemaining: Int = 0, var burnAppliedRound: Int = -1,
        var blockedMoveTurns: Int = 0, var rootTurns: Int = 0,
        val bleedTurns: MutableList<Int> = mutableListOf(), var firstAttackUsed: Boolean = false,
        var revivedOnce: Boolean = false, var attackCooldown: Int = 0,
        var alerted: Boolean = false, var guaranteedChestGrade: String? = null
    )

    private data class LootPile(
        val column: Int,
        val row: Int,
        var gold: Int,
        val items: MutableMap<String, Int>,
        val droppedAt: Long = System.currentTimeMillis(),
        var openingStartedAt: Long = 0L
    )
    private data class FireZone(val centerColumn: Int, val centerRow: Int, var remainingDamageTurns: Int = 2)
    private data class Projectile(
        val weaponCode: String, val fromColumn: Int, val fromRow: Int,
        val toColumn: Int, val toRow: Int, val startedAt: Long, val duration: Long
    )
    private data class EffectAnimation(val code: String, val column: Int, val row: Int, val startedAt: Long = System.currentTimeMillis(), val duration: Long = 850L)
    private data class PlayerMoveAnimation(
        val fromColumn: Float,
        val fromRow: Float,
        val toColumn: Float,
        val toRow: Float,
        val startedAt: Long,
        val duration: Long
    )
    private data class HealingObject(
        val definition: DungeonInteractableDefinitionEntity,
        val column: Int,
        val row: Int,
        var used: Boolean = false
    )
    private data class TreasureChest(
        val column: Int, val row: Int, val grade: String, val mimic: Boolean,
        var openingStartedAt: Long = 0L
    )

    private data class Weapon(
        val code: String, val name: String, val range: Int, val damage: Int, val turnCost: Int,
        val sheetPath: String, val iconPath: String, val specialEffect: String?
    )

    private val upperDungeonBackground = bitmap("ui/dungeon/concepts/dungeon_gameplay_grid_concept.png")
    private val floodedCatacombBackground = bitmap("ui/dungeon/concepts/dungeon_floors_06_10_flooded_catacomb.png")
    private val ashenFurnaceBackground = bitmap("ui/dungeon/concepts/dungeon_floors_11_15_ashen_furnace.png")
    private val fireEffect = bitmap("ui/dungeon/effects/fire_ground_vfx.png")
    private val obstacleBitmaps = listOf(ObstacleKind.STONE_PILLAR, ObstacleKind.DEAD_TREE).associateWith {
        bitmap("ui/dungeon/terrain/${it.assetName}.png")
    }
    private val lootBitmaps = mapOf(
        "gold_coins" to bitmap("ui/dungeon/loot/gold_coins.png"),
        "gold_ingot" to bitmap("ui/dungeon/loot/gold_ingot.png"),
        "NORMAL" to bitmap("ui/dungeon/loot/chest_normal.png"),
        "HIGH" to bitmap("ui/dungeon/loot/chest_high.png"),
        "RARE" to bitmap("ui/dungeon/loot/chest_rare.png"),
        "EPIC" to bitmap("ui/dungeon/loot/chest_epic.png"),
        "UNIQUE" to bitmap("ui/dungeon/loot/chest_unique.png"),
        "LEGENDARY" to bitmap("ui/dungeon/loot/chest_legendary.png"),
        "MYTHIC" to bitmap("ui/dungeon/loot/chest_mythic.png")
    )
    private val chestOpeningSheet = bitmap("ui/dungeon/loot/chest_opening_sheet.png")
    private val equipmentEffectBitmaps = listOf(
        "web_bind", "poison_shot", "dodge_counter", "bleed", "thrown_dagger",
        "queen_burst", "slime_block", "split_survive", "gold_revive", "regeneration"
    ).associateWith { bitmap("ui/dungeon/effects/equipment/vfx_$it.png") }
    private val interactableByCode = interactableDefinitions.associateBy { it.code }
    private val interactableBitmaps = interactableDefinitions.associate { it.code to bitmap(it.assetPath) }
    private val itemByCode = itemDefinitions.associateBy { it.code }
    private val weapons = itemDefinitions.filter { it.category == "WEAPON" }.map {
        Weapon(it.code, it.name, it.attackRange, appraisedAttackPowerByCode[it.code] ?: it.attackPower, it.attackTurnCost, it.playerSheetPath ?: "ui/dungeon/player/player_base_animation_sheet.png", it.assetPath, it.specialEffect)
    }
    private val dropItemCodes = (itemDefinitions.filter { it.dropRate > 0.0 }.map { it.code } + monsterDrops.map { it.itemCode }).distinct()
    private val inventoryCounts = initialInventoryCounts.toMutableMap()
    private val equippedArmorByCategory = listOf("HELMET", "ARMOR", "BOOTS").associateWith { category ->
        equippedItemCodes.firstOrNull { code -> itemByCode[code]?.category == category && initialInventoryCounts.containsKey(code) }
            ?: initialInventoryCounts.keys.firstOrNull { code -> itemByCode[code]?.category == category }
    }.toMutableMap()
    private var equippedAuxiliary = equippedItemCodes.firstOrNull { itemByCode[it]?.category == "AUXILIARY" && initialInventoryCounts.containsKey(it) }
        ?: initialInventoryCounts.keys.firstOrNull { itemByCode[it]?.category == "AUXILIARY" }
    private var equippedAccessory = equippedItemCodes.firstOrNull { itemByCode[it]?.category == "ACCESSORY" && initialInventoryCounts.containsKey(it) }
        ?: initialInventoryCounts.keys.firstOrNull { itemByCode[it]?.category == "ACCESSORY" }
    private val defenseChance: Double
        get() = equippedArmorByCategory.values.filterNotNull()
            .filter { code -> itemByCode[code]?.specialEffect == "BLOCK_CHANCE_30" }
            .maxOfOrNull(::optionChance) ?: .0

    private fun optionChance(code: String): Double = when (itemByCode[code]?.specialEffect) {
            "DOUBLE_SHOT_50" -> .50; "BLOCK_CHANCE_30", "RANGED_POISON_SHOT_30", "DODGE_COUNTER_30",
            "FREE_MOVE_30", "RELIC_RANGED_PULL_30" -> .30
            "RANGED_ROOT_20", "GOLD_BONUS_20", "RANGED_BLOCK_20" -> .20
            else -> .0
        }

    private fun itemAttack(code: String, fallback: Int): Int = appraisedAttackPowerByCode[code] ?: fallback
    private val acquiredCounts = linkedMapOf<String, Int>()
    private val consumedCounts = linkedMapOf<String, Int>()
    private val lootPiles = mutableListOf<LootPile>()
    private val fireZones = mutableListOf<FireZone>()
    private val soundPlayer = DungeonSoundPlayer(context)
    private var lootedGold = 0
    private var equippedWeapon = weapons.firstOrNull { it.code == equippedWeaponCode && itemCount(it.code) > 0 }
        ?: weapons.firstOrNull { itemCount(it.code) > 0 }
    private val weaponSheets = weapons.associate { it.code to bitmap(it.sheetPath) }
    private val player = UnitSprite(
        "플레이어", 2, 8,
        equippedWeapon?.let { weaponSheets.getValue(it.code) } ?: bitmap("ui/dungeon/player/player_base_animation_sheet.png"),
        hp = playerBaseHp, maxHp = playerBaseHp
    )
    private val columns = 24
    private val rows = 12
    private val stairsColumn = 22
    private val stairsRow = 1
    private val monsterSpawnSeed = Random.nextLong()
    private val monsters = createFloorMonsters(1)
    private val visibleColumns = 8f
    private val visibleRows = 7f
    private val obstacles = linkedMapOf<Pair<Int, Int>, ObstacleKind>()
    private val floorObstacleSeed = Random.nextLong()
    private val healingObjects = createHealingObjects(1)
    private val treasureChests = createTreasureChests(1)
    private var cameraColumn = 0f
    private var cameraRow = 4f
    private var phase = Phase.PLAYER
    private var focusedMonster: UnitSprite? = null
    private var turn = 1
    private var currentFloor = 1
    private var pendingMonsterRounds = 0
    private var monsterRoundSequence = 0
    private var animationFrame = 0
    private var lastFrameAt = 0L
    private var message = "파란 칸으로 이동하거나 노란 몬스터를 공격하세요"
    private var deathReported = false
    private var deathCause: UnitSprite? = null
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var selectingFireBombTarget = false
    private var impactColumn = -1
    private var impactRow = -1
    private var impactUntil = 0L
    private var defenseMissUntil = 0L
    private var projectile: Projectile? = null
    private val effectAnimations = mutableListOf<EffectAnimation>()
    private var autoWalking = false
    private var playerMoveAnimation: PlayerMoveAnimation? = null
    private var combatSpeed = 1
    private var torchEmpoweredFloor: Int? = null
    private var availableVillageGold = villageGold
    private var greedCoinUsed = false
    private var playerPoisonTurns = 0
    private var saintChaliceUsedThisFloor = false
    private var funeralBellPowerReady = false
    private var firstHitRelicUsedThisFloor = false
    private var relicKillCount = 0
    private var furnaceCoreAttackCount = 0
    private var playerBurnTurns = 0
    private var movedTilesSinceAttack = 0
    private var turnsWithoutDamage = 0
    private var regenHealsThisFloor = 0
    private val inventoryIcons = (inventoryCounts.keys + dropItemCodes).distinct().mapNotNull { code ->
        itemAssetPath(code)?.let { code to bitmap(it) }
    }.toMap()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; setShadowLayer(dp(2f), 0f, dp(1f), Color.BLACK)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x62FFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = dp(1.1f)
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(dp(5f), dp(5f)), 0f)
    }

    init {
        if (!restoreRun(savedRunPayload)) createObstacles()
        onFloorChanged(currentFloor)
        persistRun()
        isClickable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updatePlayerMovement()
        updateProjectileCamera()
        paint.alpha = 255
        canvas.drawBitmap(activeBackground(), null, RectF(0f, 0f, width.toFloat(), height.toFloat()), paint)
        canvas.drawColor(0x24000000)
        drawBattlefield(canvas); drawFloorHeader(canvas); drawSidePanel(canvas); drawHud(canvas)
        val now = System.currentTimeMillis()
        if (now - lastFrameAt >= 180L) { animationFrame = (animationFrame + 1) % 4; lastFrameAt = now }
        postInvalidateDelayed(50L)
    }

    private fun drawBattlefield(canvas: Canvas) {
        val area = dungeonArea()
        canvas.save(); canvas.clipRect(area)
        drawSynchronizedDungeonBackground(canvas, area)
        paint.color = 0x3A080B0E; canvas.drawRect(area, paint)
        for (column in floor(cameraColumn).toInt()..min(columns - 1, (cameraColumn + visibleColumns).toInt())) {
            for (row in floor(cameraRow).toInt()..min(rows - 1, (cameraRow + visibleRows).toInt())) {
                val rect = tileRect(area, column, row)
                when {
                    selectingFireBombTarget && distance(player.column, player.row, column, row) in 1..3 -> drawTileFill(canvas, rect, 0x55E87824)
                    phase == Phase.PLAYER && inWeaponRange(column, row) && obstacles.contains(column to row) -> drawTileFill(canvas, rect, 0x46B52A25)
                    obstacles.contains(column to row) -> drawTileFill(canvas, rect, 0x18191412)
                    phase == Phase.PLAYER && isAdjacent(column, row) && !occupied(column, row) -> drawTileFill(canvas, rect, 0x403A91D8)
                    phase == Phase.PLAYER && inWeaponRange(column, row) -> drawTileFill(canvas, rect, if (hasLineOfSight(player.column, player.row, column, row)) 0x40E9B62F else 0x46B52A25)
                }
                canvas.drawRect(rect, gridPaint)
            }
        }
        drawFlatObstacles(canvas, area)
        drawStairs(canvas, area)
        drawFireZones(canvas, area)
        drawDepthSortedScene(canvas, area)
        lootPiles.forEach { drawLootPile(canvas, area, it) }
        treasureChests.forEach { drawTreasureChest(canvas, area, it) }
        drawProjectile(canvas, area)
        drawEquipmentEffects(canvas, area)
        if (System.currentTimeMillis() < impactUntil) drawAttackImpact(canvas, area)
        if (System.currentTimeMillis() < defenseMissUntil) drawDefenseMiss(canvas, area)
        focusedMonster?.takeIf { phase == Phase.MONSTERS }?.let {
            paint.color = 0xFFE8BD55.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(3f)
            canvas.drawRoundRect(tileRect(area, it.column, it.row), dp(7f), dp(7f), paint); paint.style = Paint.Style.FILL
        }
        canvas.restore()
        paint.color = 0xFFD8C28C.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(2f)
        canvas.drawRect(area, paint); paint.style = Paint.Style.FILL
    }

    private fun drawSynchronizedDungeonBackground(canvas: Canvas, area: RectF) {
        val background = activeBackground()
        val viewportWidth = max(1, (background.width * visibleColumns / columns).toInt())
        val viewportHeight = max(1, (background.height * visibleRows / rows).toInt())
        val maxSourceX = (background.width - viewportWidth).coerceAtLeast(0)
        val maxSourceY = (background.height - viewportHeight).coerceAtLeast(0)
        val sourceX = if (columns > visibleColumns) {
            (cameraColumn / (columns - visibleColumns) * maxSourceX).toInt()
        } else 0
        val sourceY = if (rows > visibleRows) {
            (cameraRow / (rows - visibleRows) * maxSourceY).toInt()
        } else 0
        paint.alpha = 255
        canvas.drawBitmap(
            background,
            Rect(sourceX, sourceY, sourceX + viewportWidth, sourceY + viewportHeight),
            area,
            paint
        )
    }

    private fun drawSidePanel(canvas: Canvas) {
        val left = width * 0.64f
        paint.color = 0xD9120D0A.toInt(); canvas.drawRoundRect(left, dp(70f), width - dp(18f), height - dp(118f), dp(12f), dp(12f), paint)
        textPaint.textSize = dp(14f); textPaint.color = Color.WHITE; textPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(if (phase == Phase.PLAYER) "플레이어 행동" else "${focusedMonster?.name ?: "몬스터"} 행동 관찰 중", left + dp(18f), dp(105f), textPaint)
        textPaint.typeface = Typeface.DEFAULT; textPaint.textSize = dp(12f); textPaint.color = 0xFFC9B996.toInt()
        canvas.drawText("지도를 드래그하여 전체 탐색", left + dp(18f), dp(130f), textPaint)
        if (hasRelic("flooded_star_map")) {
            val horizontal = if (stairsColumn > player.column) "동쪽" else "서쪽"
            val vertical = if (stairsRow > player.row) "남쪽" else "북쪽"
            textPaint.color = 0xFF79D6C1.toInt()
            canvas.drawText("별지도 감응 · 계단 $horizontal · $vertical", left + dp(18f), dp(151f), textPaint)
        }
        if (hasRelic("ash_compass")) {
            monsters.filter { it.alive && it.hp > 0 }.minByOrNull {
                distance(player.column, player.row, it.column, it.row)
            }?.let { nearest ->
                val horizontal = if (nearest.column >= player.column) "동" else "서"
                val vertical = if (nearest.row >= player.row) "남" else "북"
                textPaint.color = 0xFFFFA45C.toInt()
                canvas.drawText("재의 나침반 · $horizontal$vertical ${distance(player.column, player.row, nearest.column, nearest.row)}칸", left + dp(18f), dp(171f), textPaint)
            }
        }
        drawInventoryAndEquipment(canvas)
    }

    private fun drawInventoryAndEquipment(canvas: Canvas) {
        val inventoryRects = inventorySlotRects()
        val inventoryTop = inventoryRects.firstOrNull()?.top ?: return
        val inventoryLeft = inventoryRects.first().left
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.color = 0xFFFFD786.toInt()
        textPaint.textSize = dp(13f)
        canvas.drawText("인벤토리", inventoryLeft, inventoryTop - dp(9f), textPaint)

        inventoryRects.forEachIndexed { index, rect ->
            val entry = inventoryEntry(index)
            paint.color = 0xE0211711.toInt()
            canvas.drawRoundRect(rect, dp(5f), dp(5f), paint)
            paint.color = entry?.first?.let(::itemGradeColor) ?: 0xFF514634.toInt()
            paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(if (entry == null) 1f else 2f)
            canvas.drawRoundRect(rect, dp(5f), dp(5f), paint); paint.style = Paint.Style.FILL
            entry?.let { (code, quantity) ->
                inventoryIcons[code]?.let { icon ->
                    paint.alpha = 255
                    canvas.drawBitmap(icon, null, RectF(
                        rect.left + dp(3f), rect.top + dp(3f), rect.right - dp(3f), rect.bottom - dp(3f)
                    ), paint)
                }
                if (isConsumable(code)) {
                    textPaint.color = Color.WHITE; textPaint.textSize = dp(10f); textPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(quantity.toString(), rect.right - dp(3f), rect.bottom - dp(3f), textPaint)
                }
            }
        }

        val labels = listOf("무기", "투구", "갑옷", "신발", "보조", "장신구")
        val codes = listOf(
            equippedWeapon?.code,
            equippedArmorByCategory["HELMET"],
            equippedArmorByCategory["ARMOR"],
            equippedArmorByCategory["BOOTS"],
            equippedAuxiliary,
            equippedAccessory
        )
        val equipmentRects = equipmentSlotRects()
        val equipmentLeft = equipmentRects.first().left

        textPaint.color = 0xFFFFD786.toInt()
        textPaint.textSize = dp(13f)
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("장비", equipmentLeft, inventoryTop - dp(9f), textPaint)

        codes.forEachIndexed { index, code ->
            val rect = equipmentRects[index]
            paint.color = if (code == null) 0xB516110E.toInt() else 0xE0292018.toInt()
            canvas.drawRoundRect(rect, dp(7f), dp(7f), paint)
            paint.color = if (code == null) 0xFF514634.toInt() else itemGradeColor(code)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(if (code == null) 1f else 2f)
            canvas.drawRoundRect(rect, dp(7f), dp(7f), paint)
            paint.style = Paint.Style.FILL
            code?.let { equippedCode ->
                inventoryIcons[equippedCode]?.let { icon ->
                    paint.alpha = 255
                    canvas.drawBitmap(icon, null, RectF(
                        rect.left + dp(5f), rect.top + dp(5f),
                        rect.right - dp(5f), rect.bottom - dp(5f)
                    ), paint)
                }
            }
            textPaint.color = if (code == null) 0xFF817563.toInt() else Color.WHITE
            textPaint.textSize = dp(11f)
            textPaint.typeface = Typeface.DEFAULT
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(labels[index], rect.right + dp(7f), rect.centerY() + dp(4f), textPaint)
        }
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawFloorHeader(canvas: Canvas) {
        val centerX = width * .5f
        val header = RectF(centerX - dp(92f), dp(12f), centerX + dp(92f), dp(54f))
        paint.shader = RadialGradient(centerX, header.top, header.width(), 0xFF59411F.toInt(), 0xF20C0907.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(header, dp(9f), dp(9f), paint); paint.shader = null
        paint.color = 0xFFD0A653.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(2f)
        canvas.drawRoundRect(header, dp(9f), dp(9f), paint); paint.style = Paint.Style.FILL
        textPaint.textAlign = Paint.Align.CENTER; textPaint.color = Color.WHITE; textPaint.textSize = dp(20f)
        canvas.drawText("지하 ${currentFloor}층", centerX, header.centerY() + dp(7f), textPaint); textPaint.textAlign = Paint.Align.LEFT
        speedButtonRects().forEachIndexed { index, rect ->
            val speed = index + 1
            val selected = combatSpeed == speed
            paint.color = if (selected) 0xFFD0A653.toInt() else 0xE51B1510.toInt()
            canvas.drawRoundRect(rect, dp(7f), dp(7f), paint)
            paint.color = if (selected) 0xFFFFE0A0.toInt() else 0xFF796039.toInt()
            paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(if (selected) 2f else 1f)
            canvas.drawRoundRect(rect, dp(7f), dp(7f), paint); paint.style = Paint.Style.FILL
            textPaint.color = if (selected) 0xFF241607.toInt() else Color.WHITE
            textPaint.textSize = dp(14f); textPaint.typeface = Typeface.DEFAULT_BOLD; textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("${speed}×", rect.centerX(), rect.centerY() + dp(5f), textPaint)
        }
        val waitRect = waitButtonRect()
        val canWait = phase == Phase.PLAYER && monsters.any { it.alive } && !autoWalking
        paint.color = if (canWait) 0xE54A2B1A.toInt() else 0xB51A1714.toInt()
        canvas.drawRoundRect(waitRect, dp(7f), dp(7f), paint)
        paint.color = if (canWait) 0xFFD0A653.toInt() else 0xFF5D554A.toInt()
        paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(if (canWait) 2f else 1f)
        canvas.drawRoundRect(waitRect, dp(7f), dp(7f), paint); paint.style = Paint.Style.FILL
        textPaint.color = if (canWait) Color.WHITE else 0xFF81796F.toInt()
        textPaint.textSize = dp(14f); textPaint.typeface = Typeface.DEFAULT_BOLD; textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("대기", waitRect.centerX(), waitRect.centerY() + dp(5f), textPaint)
        textPaint.textAlign = Paint.Align.LEFT; textPaint.typeface = Typeface.DEFAULT
    }

    private fun waitButtonRect() = RectF(dp(18f), dp(14f), dp(86f), dp(52f))

    private fun speedButtonRects(): List<RectF> {
        val buttonWidth = dp(42f)
        val gap = dp(6f)
        val totalWidth = buttonWidth * 3f + gap * 2f
        val startX = width * .5f - dp(104f) - totalWidth
        val top = dp(14f)
        return List(3) { index ->
            val left = startX + index * (buttonWidth + gap)
            RectF(left, top, left + buttonWidth, top + dp(38f))
        }
    }

    private fun drawHud(canvas: Canvas) {
        val top = height - dp(116f)
        paint.color = 0xF00B0806.toInt(); canvas.drawRect(0f, top, width.toFloat(), height.toFloat(), paint)

        val orbX = dp(86f); val orbY = top + dp(57f); val orbRadius = dp(48f)
        paint.setShadowLayer(dp(9f), 0f, dp(5f), 0xE0000000.toInt()); paint.color = 0xFF17100B.toInt(); canvas.drawCircle(orbX, orbY, orbRadius + dp(10f), paint); paint.clearShadowLayer()
        paint.shader = RadialGradient(orbX - orbRadius * .35f, orbY - orbRadius * .42f, orbRadius * 1.55f, intArrayOf(0xFFFF6A58.toInt(), 0xFF9E211F.toInt(), 0xFF310707.toInt()), floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP)
        canvas.save(); val hpTop = orbY + orbRadius - orbRadius * 2f * player.hp / player.maxHp
        canvas.clipRect(orbX - orbRadius, hpTop, orbX + orbRadius, orbY + orbRadius); canvas.drawCircle(orbX, orbY, orbRadius, paint); canvas.restore(); paint.shader = null
        paint.color = 0xFF180707.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(3f); canvas.drawCircle(orbX, orbY, orbRadius, paint)
        paint.color = 0xFF8B6129.toInt(); paint.strokeWidth = dp(7f); canvas.drawCircle(orbX, orbY, orbRadius + dp(5f), paint)
        paint.color = 0xFFFFD889.toInt(); paint.strokeWidth = dp(2f); canvas.drawCircle(orbX, orbY, orbRadius + dp(5f), paint); paint.style = Paint.Style.FILL
        paint.color = 0x55FFFFFF; canvas.drawOval(RectF(orbX - orbRadius * .55f, orbY - orbRadius * .68f, orbX + orbRadius * .18f, orbY - orbRadius * .22f), paint)
        textPaint.color = Color.WHITE; textPaint.textSize = dp(18f); textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("${player.hp}/${player.maxHp}", orbX, orbY + dp(6f), textPaint); textPaint.textAlign = Paint.Align.LEFT

        val defenseText = if (defenseChance > 0.0) " · 방어 30%" else ""
        val equippedText = equippedWeapon?.let { "착용: ${it.name} · 공격 ${effectiveDamage(it)} · 사거리 ${it.range}$defenseText" }
            ?: "착용 무기 없음$defenseText"
        textPaint.color = 0xFFFFD786.toInt(); textPaint.textSize = dp(13f); canvas.drawText(equippedText, dp(165f), top + dp(48f), textPaint)
        textPaint.color = Color.WHITE; textPaint.textSize = dp(13f); canvas.drawText(message, dp(165f), top + dp(77f), textPaint)

        val dayX = width - dp(75f)
        paint.color = 0xFFB48A42.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(3f); canvas.drawCircle(dayX, orbY, dp(25f), paint); paint.style = Paint.Style.FILL
        textPaint.color = 0xFFFFD786.toInt(); textPaint.textSize = dp(22f); textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(survivalDay.toString(), dayX, orbY + dp(7f), textPaint); textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = dp(11f); canvas.drawText("생존 일", dayX - dp(21f), orbY + dp(43f), textPaint)
        textPaint.textSize = dp(13f); canvas.drawText("전리품 ${lootedGold}G", dayX - dp(34f), orbY - dp(36f), textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; lastX = event.x; lastY = event.y; dragging = false; return true }
            MotionEvent.ACTION_MOVE -> {
                if (dungeonArea().contains(downX, downY)) {
                    if (abs(event.x - downX) + abs(event.y - downY) > dp(10f)) dragging = true
                    if (dragging) scrollMap(lastX - event.x, lastY - event.y)
                }
                lastX = event.x; lastY = event.y; return true
            }
            MotionEvent.ACTION_UP -> { if (!dragging) handleTap(event.x, event.y); return true }
        }
        return true
    }

    private fun handleTap(x: Float, y: Float) {
        if (waitButtonRect().contains(x, y)) {
            waitPlayerTurn()
            return
        }
        speedButtonRects().forEachIndexed { index, rect ->
            if (rect.contains(x, y)) {
                combatSpeed = index + 1
                message = "전투 속도 ${combatSpeed}배"
                invalidate()
                return
            }
        }
        inventorySlotRects().forEachIndexed { index, rect ->
            if (rect.contains(x, y)) {
                inventoryEntry(index)?.first?.let(::showDungeonItemInfo)
                return
            }
        }
        if (phase != Phase.PLAYER) { message = "몬스터 행동이 끝날 때까지 기다리세요"; return }
        val area = dungeonArea(); if (!area.contains(x, y)) return
        val column = floor(cameraColumn + (x - area.left) / (area.width() / visibleColumns)).toInt().coerceIn(0, columns - 1)
        val row = floor(cameraRow + (y - area.top) / (area.height() / visibleRows)).toInt().coerceIn(0, rows - 1)
        if (selectingFireBombTarget) { throwFireBomb(column, row); return }
        healingObjects.firstOrNull { column to row in occupiedCells(it) }?.let {
            if (monsters.none { monster -> monster.alive } && !it.used && !isAdjacentTo(it)) {
                autoApproach(occupiedCells(it), "${it.definition.name}으로 자동 이동 중") { useHealingObject(it) }
            } else {
                useHealingObject(it)
            }
            return
        }
        treasureChests.firstOrNull { it.column == column && it.row == row }?.let { chest ->
            if (distance(player.column, player.row, chest.column, chest.row) == 1) openTreasureChest(chest)
            else if (monsters.none { it.alive }) autoApproach(listOf(chest.column to chest.row), "상자로 자동 이동 중") { openTreasureChest(chest) }
            else message = "상자 바로 앞 칸까지 이동해야 열 수 있습니다"
            invalidate()
            return
        }
        monsterAtScreenPoint(area, x, y)?.let { monster ->
            attackMonster(monster)
            return
        }
        if (column == stairsColumn && row == stairsRow) { tryDescendFloor(); return }
        lootPiles.firstOrNull { it.column == column && it.row == row }?.let { pile ->
            if (monsters.none { it.alive } && distance(player.column, player.row, pile.column, pile.row) != 1) {
                autoApproach(listOf(pile.column to pile.row), "전리품으로 자동 이동 중") { collectLoot(pile) }
            } else {
                collectLoot(pile)
            }
            return
        }
        monsters.firstOrNull { it.alive && it.column == column && it.row == row }?.let { attackMonster(it); return }
        when {
            obstacles.contains(column to row) -> message = "장애물 때문에 이동할 수 없습니다"
            isAdjacent(column, row) && !occupied(column, row) -> {
                startPlayerMovement(column, row, combatDuration(520L))
                movedTilesSinceAttack++
                playAction(player, 1, 520L)
                if (equippedArmorByCategory["BOOTS"] == "wild_dog_boots" && Random.nextDouble() < optionChance("wild_dog_boots")) {
                    message = "들개의 가죽신 발동 · 추가 이동 가능"
                } else {
                    phase = Phase.MONSTERS
                    postDelayed({ beginMonsterTurns(1) }, combatDuration(520L))
                }
            }
            else -> message = "파란색 인접 타일만 이동할 수 있습니다"
        }
        invalidate()
    }

    private fun waitPlayerTurn() {
        when {
            phase != Phase.PLAYER || autoWalking -> message = "현재는 대기할 수 없습니다"
            monsters.none { it.alive } -> message = "전투가 끝나 대기할 필요가 없습니다"
            else -> {
                selectingFireBombTarget = false
                phase = Phase.MONSTERS
                message = "아무 행동 없이 1턴 대기합니다"
                postDelayed({ beginMonsterTurns(1) }, combatDuration(260L))
            }
        }
        invalidate()
    }

    private fun showDungeonItemInfo(code: String) {
        val item = itemByCode[code] ?: return
        val quantity = itemCount(code)
        val unidentifiedLoot = item.grade != "NORMAL" && (acquiredCounts[code] ?: 0) >= quantity
        if (unidentifiedLoot) {
            val gradeName = gradeDisplayName(item.grade)
            AntiqueGameDialog.show(
                context,
                AntiqueGameDialog.Config(
                    title = "미확인 $gradeName 장비",
                    subtitle = "감정 필요",
                    body = "마을 감정소에서 감정하기 전에는 이름과 옵션을 확인하거나 장착할 수 없습니다.",
                    actions = listOf(AntiqueGameDialog.Action("닫기")),
                    bodyHeightDp = 110
                )
            )
            return
        }
        val equipped = code == equippedWeapon?.code || code in equippedArmorByCategory.values ||
            code == equippedAuxiliary || code == equippedAccessory
        val categoryName = when (item.category) {
            "CONSUMABLE" -> "소모품"; "WEAPON" -> "무기"; "HELMET" -> "투구"
            "ARMOR" -> "갑옷"; "BOOTS" -> "신발"; "AUXILIARY" -> "보조장비"
            "ACCESSORY" -> "악세서리"; "RELIC" -> "유물"; else -> item.category
        }
        val gradeName = when (item.grade) {
            "HIGH" -> "고급"; "RARE" -> "레어"; "EPIC" -> "에픽"; "UNIQUE" -> "유니크"
            "LEGENDARY" -> "전설"; "MYTHIC" -> "신화"; else -> "노말"
        }
        val returnStoneRestricted = code == "return_stone" && currentFloor >= returnStoneCombatLockFloor
        val actions = mutableListOf(AntiqueGameDialog.Action("닫기"))
        when {
            item.category == "CONSUMABLE" -> actions += AntiqueGameDialog.Action("사용", primary = true) {
                useDungeonInventoryItem(code)
            }
            item.category in setOf("WEAPON", "HELMET", "ARMOR", "BOOTS", "AUXILIARY", "ACCESSORY") && !equipped ->
                actions += AntiqueGameDialog.Action("장착", primary = true) {
                    if (item.category == "WEAPON") switchEquippedWeapon(code) else switchEquippedArmor(code)
                }
        }
        AntiqueGameDialog.show(
            context,
            AntiqueGameDialog.Config(
                title = item.name,
                subtitle = "$gradeName · $categoryName${if (equipped) " · 장착 중" else ""}",
                body = buildString {
                    append("수량  $quantity\n\n${item.detail ?: "추가 옵션 없음"}")
                    if (returnStoneRestricted) {
                        append("\n\n※ 지하 ${returnStoneCombatLockFloor}층부터는 몬스터가 남아 있는 전투 중에 사용할 수 없습니다.")
                    }
                },
                actions = actions,
                bodyHeightDp = 150
            )
        )
    }

    private fun useDungeonInventoryItem(code: String) {
        if (phase != Phase.PLAYER) {
            message = "몬스터 행동이 끝난 뒤 아이템을 사용할 수 있습니다"
            invalidate()
            return
        }
        when (code) {
            "return_stone" -> showDungeonExitConfirmation()
            "fire_bomb" -> {
                selectingFireBombTarget = true
                message = "화염병을 던질 중심 타일을 선택하세요 · 3×3 범위"
                invalidate()
            }
            "torch" -> useTorch()
            "camping_kit" -> useCampingKit()
            else -> {
                message = "던전에서 직접 사용할 수 없는 아이템입니다"
                invalidate()
            }
        }
    }

    private fun showDungeonExitConfirmation() {
        if (phase != Phase.PLAYER) {
            message = "몬스터 행동이 끝난 뒤 나갈 수 있습니다"
            invalidate()
            return
        }
        if (itemCount("return_stone") <= 0) {
            message = "던전을 나가려면 귀환석이 필요합니다"
            invalidate()
            return
        }
        if (currentFloor >= returnStoneCombatLockFloor && monsters.any { it.alive }) {
            message = "지하 ${returnStoneCombatLockFloor}층부터는 전투 중 귀환석을 사용할 수 없습니다"
            invalidate()
            return
        }
        val acquiredItemCount = acquiredCounts.values.filter { it > 0 }.sum()
        AntiqueGameDialog.show(
            context,
            AntiqueGameDialog.Config(
                title = "던전에서 나가기",
                subtitle = "어둠을 등지고 마을로 귀환합니다",
                body = "현재 위치  지하 ${currentFloor}층\n획득 골드  ${lootedGold}G\n획득 아이템  ${acquiredItemCount}개",
                warning = "귀환석 1개를 소비합니다. 현재까지 획득한 전리품은 안전하게 가져갑니다.",
                actions = listOf(
                    AntiqueGameDialog.Action("계속 탐험"),
                    AntiqueGameDialog.Action("귀환석 사용", primary = true) {
                        onUseReturnStone(acquiredCounts.toMap(), lootedGold, consumedCounts.toMap(), equippedCodes())
                    }
                ),
                bodyHeightDp = 145
            )
        )
    }

    private fun tryDescendFloor() {
        if (monsters.any { it.alive }) {
            message = "층의 몬스터를 모두 처치해야 계단이 열립니다"; invalidate(); return
        }
        if (autoWalking) return
        val path = findPathToGoals(setOf(stairsColumn to stairsRow))
        if (path == null) {
            message = "계단으로 이동할 수 있는 경로가 없습니다"
            invalidate()
            return
        }
        if (path.isEmpty()) {
            showFloorChoice(canDescend = currentFloor < 15)
            return
        }
        autoWalking = true
        phase = Phase.MONSTERS
        message = "계단으로 자동 이동 중"
        walkPathWithoutTurns(path, 0, "아래층 계단에 도착했습니다") {
            showFloorChoice(canDescend = currentFloor < 15)
        }
    }

    private fun findPathToGoals(goals: Set<Pair<Int, Int>>): List<Pair<Int, Int>>? {
        val start = player.column to player.row
        if (start in goals) return emptyList()
        val queue = ArrayDeque<Pair<Int, Int>>()
        val previous = mutableMapOf<Pair<Int, Int>, Pair<Int, Int>?>()
        var reachedGoal: Pair<Int, Int>? = null
        queue.addLast(start)
        previous[start] = null
        val directions = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current in goals) { reachedGoal = current; break }
            directions.forEach { direction ->
                val next = current.first + direction.first to current.second + direction.second
                if (next.first !in 0 until columns || next.second !in 0 until rows) return@forEach
                if (next in previous || obstacles.contains(next)) return@forEach
                if (next !in goals && healingObjects.any { next in occupiedCells(it) }) return@forEach
                previous[next] = current
                queue.addLast(next)
            }
        }
        val goal = reachedGoal ?: return null
        val reversed = mutableListOf<Pair<Int, Int>>()
        var cursor: Pair<Int, Int>? = goal
        while (cursor != null && cursor != start) {
            reversed += cursor
            cursor = previous[cursor]
        }
        return reversed.asReversed()
    }

    private fun autoApproach(targetCells: List<Pair<Int, Int>>, movingMessage: String, onArrived: () -> Unit) {
        if (autoWalking || monsters.any { it.alive }) return
        val goals = targetCells.flatMap { target ->
            listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).map { direction ->
                target.first + direction.first to target.second + direction.second
            }
        }.filter { cell ->
            cell.first in 0 until columns && cell.second in 0 until rows &&
                !obstacles.contains(cell) && healingObjects.none { cell in occupiedCells(it) }
        }.toSet()
        val path = findPathToGoals(goals)
        if (path == null) {
            message = "이동할 수 있는 경로가 없습니다"
            invalidate()
            return
        }
        if (path.isEmpty()) { onArrived(); return }
        autoWalking = true
        phase = Phase.MONSTERS
        message = movingMessage
        walkPathWithoutTurns(path, 0, "목적지에 도착했습니다", onArrived)
    }

    private fun walkPathWithoutTurns(
        path: List<Pair<Int, Int>>,
        index: Int,
        arrivalMessage: String,
        onArrived: () -> Unit
    ) {
        if (index >= path.size) {
            autoWalking = false
            player.actionUntil = 0L
            phase = Phase.PLAYER
            message = arrivalMessage
            invalidate()
            onArrived()
            return
        }
        val next = path[index]
        startPlayerMovement(next.first, next.second, 240L)
        playAction(player, 1, 240L, scaleWithCombat = false)
        focusCamera(player.column, player.row)
        invalidate()
        postDelayed({ walkPathWithoutTurns(path, index + 1, arrivalMessage, onArrived) }, 245L)
    }

    private fun showFloorChoice(canDescend: Boolean) {
        phase = Phase.MONSTERS
        val actions = mutableListOf(
            AntiqueGameDialog.Action("마을가기", primary = !canDescend) {
                onExitDungeon(acquiredCounts.toMap(), lootedGold, consumedCounts.toMap(), equippedCodes())
            }
        )
        if (canDescend) actions += AntiqueGameDialog.Action("내려가기", primary = true) {
                message = "지하 ${currentFloor + 1}층으로 내려갑니다"
                postDelayed({ enterNextFloor() }, 500L)
        }
        AntiqueGameDialog.show(
            context,
            AntiqueGameDialog.Config(
                title = "지하 ${currentFloor}층 탐험 완료",
                body = acquiredEquipmentSummary(),
                actions = actions,
                onCancel = {
                    phase = Phase.PLAYER
                    invalidate()
                }
            )
        )
    }

    private fun acquiredEquipmentSummary(): String {
        val equipmentCategories = setOf("WEAPON", "ARMOR", "HELMET", "BOOTS", "AUXILIARY", "ACCESSORY", "RELIC")
        val equipment = acquiredCounts.entries.mapNotNull { (code, count) ->
            val definition = itemByCode[code] ?: return@mapNotNull null
            if (count <= 0 || definition.category !in equipmentCategories) return@mapNotNull null
            if (definition.grade == "NORMAL") "${definition.name}  ×$count"
            else "미확인 ${gradeDisplayName(definition.grade)} 장비  ×$count"
        }
        return if (equipment.isEmpty()) "획득한 장비가 없습니다." else equipment.joinToString("\n\n")
    }

    private fun gradeDisplayName(grade: String): String = when (grade) {
        "HIGH" -> "고급"; "RARE" -> "레어"; "EPIC" -> "에픽"; "UNIQUE" -> "유니크"
        "LEGENDARY" -> "전설"; "MYTHIC" -> "신화"; else -> "노말"
    }

    private fun equippedCodes(): Set<String> = buildSet {
        equippedWeapon?.code?.let(::add)
        equippedArmorByCategory.values.filterNotNull().forEach(::add)
        equippedAuxiliary?.let(::add)
        equippedAccessory?.let(::add)
    }

    private fun enterNextFloor() {
        currentFloor++
        onFloorChanged(currentFloor)
        torchEmpoweredFloor = null
        turnsWithoutDamage = 0
        regenHealsThisFloor = 0
        saintChaliceUsedThisFloor = false
        playerPoisonTurns = 0
        playerBurnTurns = 0
        firstHitRelicUsedThisFloor = false
        player.column = 2; player.row = 8; player.drawColumn = 2f; player.drawRow = 8f
        monsters.clear(); monsters.addAll(createFloorMonsters(currentFloor))
        obstacles.clear(); lootPiles.clear(); fireZones.clear()
        healingObjects.clear(); healingObjects.addAll(createHealingObjects(currentFloor))
        treasureChests.clear(); treasureChests.addAll(createTreasureChests(currentFloor))
        focusedMonster = null; pendingMonsterRounds = 0
        createObstacles(); focusCamera(player.column, player.row)
        phase = Phase.PLAYER
        message = "지하 ${currentFloor}층에 진입했습니다"
        persistRun()
        invalidate()
    }

    private fun throwFireBomb(column: Int, row: Int) {
        val fireBombRange = if (hasRelic("forgemaster_tongs")) 4 else 3
        if (distance(player.column, player.row, column, row) !in 1..fireBombRange) {
            message = "화염병은 ${fireBombRange}칸 이내의 타일에만 던질 수 있습니다"
            invalidate(); return
        }
        selectingFireBombTarget = false
        consumeInventoryItem("fire_bomb")
        fireZones += FireZone(column, row, if (hasRelic("forgemaster_tongs")) 3 else 2)
        phase = Phase.MONSTERS
        playAction(player, 2, 620L)
        message = "화염병 투척 · 3×3 지역이 2턴 동안 불타오릅니다"
        postDelayed({ beginMonsterTurns(1) }, combatDuration(620L))
        invalidate()
    }

    private fun switchEquippedWeapon(code: String) {
        if (phase != Phase.PLAYER) {
            message = "몬스터 행동이 끝난 뒤 장비를 교체할 수 있습니다"
            invalidate(); return
        }
        val weapon = weapons.firstOrNull { it.code == code && itemCount(code) > 0 } ?: return
        if (equippedWeapon?.code == weapon.code) {
            message = "이미 ${weapon.name}을 착용 중입니다"
            invalidate(); return
        }
        selectingFireBombTarget = false
        equippedWeapon = weapon
        onEquipItem(code, "WEAPON")
        player.sheet = weaponSheets.getValue(weapon.code)
        phase = Phase.MONSTERS
        playAction(player, 0, 520L)
        message = "${weapon.name}으로 교체 · 1행동 소모"
        postDelayed({ beginMonsterTurns(1) }, combatDuration(520L))
        invalidate()
    }

    private fun switchEquippedArmor(code: String) {
        if (phase != Phase.PLAYER) {
            message = "몬스터 행동이 끝난 뒤 방어구를 교체할 수 있습니다"
            invalidate(); return
        }
        val definition = itemByCode[code] ?: return
        if (definition.category !in setOf("HELMET", "ARMOR", "BOOTS", "AUXILIARY", "ACCESSORY") || itemCount(code) <= 0) return
        val currentCode = when (definition.category) {
            "AUXILIARY" -> equippedAuxiliary; "ACCESSORY" -> equippedAccessory
            else -> equippedArmorByCategory[definition.category]
        }
        if (currentCode == code) {
            message = "이미 ${definition.name}을 착용 중입니다"
            invalidate(); return
        }
        when (definition.category) {
            "AUXILIARY" -> equippedAuxiliary = code
            "ACCESSORY" -> equippedAccessory = code
            else -> equippedArmorByCategory[definition.category] = code
        }
        onEquipItem(code, definition.category)
        phase = Phase.MONSTERS
        playAction(player, 0, 520L)
        message = "${definition.name} 착용 · 1행동 소모"
        postDelayed({ beginMonsterTurns(1) }, combatDuration(520L))
        invalidate()
    }

    private fun useTorch() {
        if (torchEmpoweredFloor == currentFloor) {
            message = "현재 층에서 이미 횃불 효과를 받고 있습니다"
            invalidate(); return
        }
        consumeInventoryItem("torch")
        torchEmpoweredFloor = currentFloor
        phase = Phase.MONSTERS
        playAction(player, 0, 520L)
        message = "횃불 사용 · 현재 층 동안 공격력 +1"
        postDelayed({ beginMonsterTurns(1) }, combatDuration(520L))
        invalidate()
    }

    private fun useCampingKit() {
        if (player.hp >= player.maxHp) {
            message = "이미 체력이 가득 차 있습니다"
            invalidate(); return
        }
        val recovered = player.maxHp - player.hp
        consumeInventoryItem("camping_kit")
        player.hp = player.maxHp
        phase = Phase.MONSTERS
        playAction(player, 0, 720L)
        message = "야영 완료 · 체력 $recovered 회복"
        postDelayed({ beginMonsterTurns(1) }, combatDuration(720L))
        invalidate()
    }

    private fun consumeInventoryItem(code: String) {
        val remaining = itemCount(code) - 1
        if (remaining > 0) inventoryCounts[code] = remaining else inventoryCounts.remove(code)
        val acquired = acquiredCounts[code] ?: 0
        if (acquired > 0) {
            if (acquired == 1) acquiredCounts.remove(code) else acquiredCounts[code] = acquired - 1
        } else {
            consumedCounts[code] = (consumedCounts[code] ?: 0) + 1
        }
    }

    private fun attackMonster(monster: UnitSprite) {
        val weapon = equippedWeapon ?: run { message = "착용한 무기가 없어 공격할 수 없습니다"; invalidate(); return }
        val targetDistance = if (weapon.specialEffect == "ADJACENT_SWEEP") {
            max(abs(player.column - monster.column), abs(player.row - monster.row))
        } else distance(player.column, player.row, monster.column, monster.row)
        if (targetDistance > weapon.range) { message = "${weapon.name} 사거리 밖입니다"; return }
        if (weapon.specialEffect != "ADJACENT_SWEEP" && !hasLineOfSight(player.column, player.row, monster.column, monster.row)) { message = "장애물에 공격 경로가 막혔습니다"; return }
        val direction = alignedDirection(monster)
        if (weapon.specialEffect == "LINE_THRUST" && direction == null) {
            message = "창은 상하좌우 일직선으로만 찌를 수 있습니다"; invalidate(); return
        }
        val targets = when {
            weapon.specialEffect == "ADJACENT_SWEEP" -> monsters.filter {
                it.alive && it.hp > 0 && max(abs(player.column - it.column), abs(player.row - it.row)) == 1
            }
            weapon.specialEffect == "LINE_THRUST" -> lineTargets(direction!!, weapon.range)
            weapon.specialEffect == "KILL_PIERCE" && direction != null -> lineTargets(direction, weapon.range)
            else -> listOf(monster)
        }
        if (targets.isEmpty()) return
        phase = Phase.MONSTERS; playAction(player, 2, 760L)
        soundPlayer.playWeaponAttack(weapon.code)
        val travelDuration = combatDuration(if (weapon.code == "crude_bow" || weapon.code == "crude_gun") 520L else 240L)
        if (weapon.code == "crude_bow" || weapon.code == "crude_gun") {
            launchProjectile(weapon.code, player.column, player.row, targets.first(), travelDuration)
        }
        postDelayed({
            when (weapon.specialEffect) {
                "ADJACENT_SWEEP" -> resolveAdjacentSweep(targets, weapon)
                "DOUBLE_SHOT_50" -> resolveBowShot(targets.first(), weapon)
                "LINE_THRUST" -> resolveLineThrust(targets, weapon)
                "KILL_PIERCE" -> resolveGunPierce(targets, 0, weapon)
                else -> finishWeaponAttack(weapon, applyWeaponHit(targets.first(), weapon))
            }
        }, travelDuration)
    }

    private fun resolveAdjacentSweep(targets: List<UnitSprite>, weapon: Weapon) {
        var killedAny = false
        targets.forEach { target -> killedAny = applyWeaponHit(target, weapon) || killedAny }
        message = "주변 휩쓸기 · ${targets.size}마리 공격 · ${weapon.turnCost}턴 소모"
        finishWeaponAttack(weapon, killedAny)
    }

    private fun resolveDoubleHit(monster: UnitSprite, weapon: Weapon) {
        val firstKilled = applyWeaponHit(monster, weapon)
        if (firstKilled) { finishWeaponAttack(weapon, true); return }
        postDelayed({
            playAction(player, 2, 620L)
            soundPlayer.playWeaponAttack(weapon.code)
            val secondKilled = applyWeaponHit(monster, weapon)
            message = if (secondKilled) "2연타 · ${monster.name} 처치" else "${monster.name}에게 2연타 · 총 ${effectiveDamage(weapon) * 2} 피해"
            finishWeaponAttack(weapon, secondKilled)
        }, combatDuration(330L))
    }

    private fun resolveBowShot(monster: UnitSprite, weapon: Weapon) {
        val firstKilled = applyWeaponHit(monster, weapon)
        if (firstKilled) {
            message = "${monster.name} 처치 · 1턴 소모"
            finishWeaponAttack(weapon, true)
            return
        }
        if (Random.nextDouble() >= optionChance(weapon.code)) {
            message = "${monster.name}에게 ${weapon.damage} 피해 · 1턴 소모"
            finishWeaponAttack(weapon, false)
            return
        }
        message = "연속 사격 발동"
        postDelayed({
            playAction(player, 2, 620L)
            soundPlayer.playWeaponAttack(weapon.code)
            val secondShotDuration = combatDuration(420L)
            launchProjectile(weapon.code, player.column, player.row, monster, secondShotDuration)
            postDelayed({
                val secondKilled = applyWeaponHit(monster, weapon)
                message = if (secondKilled) {
                    "연속 사격 · ${monster.name} 처치 · 1턴 소모"
                } else {
                    "연속 사격 · 총 ${effectiveDamage(weapon) * 2} 피해 · 1턴 소모"
                }
                finishWeaponAttack(weapon, secondKilled)
            }, secondShotDuration)
        }, combatDuration(260L))
    }

    private fun resolveLineThrust(targets: List<UnitSprite>, weapon: Weapon) {
        var killedAny = false
        targets.forEachIndexed { index, target ->
            postDelayed({
                killedAny = applyWeaponHit(target, weapon) || killedAny
                if (index == targets.lastIndex) {
                    message = "직선 찌르기 · ${targets.size}마리 타격 · ${weapon.turnCost}턴 소모"
                    finishWeaponAttack(weapon, killedAny)
                }
            }, combatDuration(index * 110L))
        }
    }

    private fun resolveGunPierce(targets: List<UnitSprite>, index: Int, weapon: Weapon) {
        val target = targets[index]
        val killed = applyWeaponHit(target, weapon)
        val next = targets.getOrNull(index + 1)
        if (killed && next != null) {
            message = "${target.name} 처치 · 탄환 관통"
            val pierceDuration = combatDuration(220L)
            launchProjectile(weapon.code, target.column, target.row, next, pierceDuration)
            postDelayed({ resolveGunPierce(targets, index + 1, weapon) }, pierceDuration)
        } else {
            finishWeaponAttack(weapon, killed)
        }
    }

    private fun applyWeaponHit(monster: UnitSprite, weapon: Weapon): Boolean {
        alertMonstersFromCombat(monster)
        projectile = null
        focusCamera(monster.column, monster.row)
        impactColumn = monster.column; impactRow = monster.row; impactUntil = System.currentTimeMillis() + 650L
        soundPlayer.playWeaponImpact(weapon.code)
        var damage = effectiveDamage(weapon)
        if (funeralBellPowerReady) funeralBellPowerReady = false
        if (equippedAuxiliary == "bloody_hook" && weapon.code == "crude_sword") {
            damage += itemAttack("bloody_hook", 2)
            monster.bleedTurns += 2
            showEffect("bleed", monster)
        }
        if (equippedAuxiliary == "throwing_dagger" && weapon.code == "crude_spear") {
            damage += itemAttack("throwing_dagger", 3)
            showEffect("thrown_dagger", monster)
        }
        val ranged = weapon.code == "crude_bow" || weapon.code == "crude_gun"
        if (ranged && equippedAuxiliary == "venom_dagger" && Random.nextDouble() < optionChance("venom_dagger")) {
            damage += itemAttack("venom_dagger", 2)
            showEffect("poison_shot", monster)
        }
        if (ranged && equippedAuxiliary == "web_glove" && Random.nextDouble() < optionChance("web_glove")) {
            monster.rootTurns = max(monster.rootTurns, 1)
            showEffect("web_bind", monster)
        }
        monster.hp -= damage
        if (ranged && monster.hp > 0 && hasRelic("gravekeeper_chain") && Random.nextDouble() < optionChance("gravekeeper_chain")) {
            pullMonsterTowardPlayer(monster)
        }
        return if (monster.hp <= 0) {
            defeatMonster(monster)
            true
        } else {
            soundPlayer.playHit(monster.definition?.code)
            message = "${monster.name}에게 ${damage} 피해"
            false
        }
    }

    private fun triggerKillAccessory(defeated: UnitSprite) {
        if (equippedAccessory != "spider_queen_heart") return
        monsters.filter {
            it !== defeated && it.alive && it.hp > 0 && max(abs(it.column - defeated.column), abs(it.row - defeated.row)) <= 1
        }.forEach { nearby ->
            alertMonstersFromCombat(nearby)
            nearby.hp = max(0, nearby.hp - itemAttack("spider_queen_heart", 2))
            nearby.rootTurns = max(nearby.rootTurns, 1)
            showEffect("queen_burst", nearby)
            if (nearby.hp == 0) {
                defeatMonster(nearby, triggerAreaEffect = false)
            }
        }
    }

    private fun defeatMonster(monster: UnitSprite, triggerAreaEffect: Boolean = true) {
        soundPlayer.playDeath(monster.definition?.code)
        monster.hp = 0
        monster.dying = true
        playAction(monster, 3, 1050L)
        if (monster.definition?.code == "drowned_dead" && !monster.revivedOnce) {
            monster.revivedOnce = true
            message = "${monster.name}이 쓰러졌지만 시체가 꿈틀거립니다"
            postDelayed({
                monster.hp = 4
                monster.dying = false
                monster.alive = true
                message = "${monster.name}이 HP 4로 다시 일어났습니다"
                invalidate()
            }, combatDuration(1400L))
            return
        }
        message = "${monster.name} 처치"
        createLoot(monster)
        if (triggerAreaEffect) triggerKillAccessory(monster)
        if (hasRelic("funeral_bell_heart")) funeralBellPowerReady = true
        if (hasRelic("ember_eater_medal")) {
            relicKillCount++
            if (relicKillCount % 3 == 0 && player.hp < player.maxHp) {
                player.hp++
                message = "불씨 포식 훈장 · 세 번째 처치로 HP 1 회복"
            }
        }
        postDelayed({ monster.alive = false; monster.dying = false; invalidate() }, combatDuration(1050L))
    }

    private fun showEffect(code: String, unit: UnitSprite) {
        effectAnimations += EffectAnimation(code, unit.column, unit.row)
    }

    private fun finishWeaponAttack(weapon: Weapon, killedAny: Boolean) {
        movedTilesSinceAttack = 0
        if (hasRelic("furnace_core")) {
            furnaceCoreAttackCount++
            if (furnaceCoreAttackCount % 5 == 0) {
                player.hp = max(0, player.hp - 1)
                message = "성자의 용광로 핵 과열 · 자신에게 피해 1"
                if (player.hp == 0) {
                    player.dying = true
                    playAction(player, 3, 1150L)
                    postDelayed({ finishPlayerDeath() }, combatDuration(1150L))
                    invalidate()
                    return
                }
            }
        }
        postDelayed({ beginMonsterTurns(weapon.turnCost) }, combatDuration(if (killedAny) 1050L else 800L))
        invalidate()
    }

    private fun effectiveDamage(weapon: Weapon): Int {
        var bonus = if (torchEmpoweredFloor == currentFloor) 1 else 0
        if (equippedAccessory == "fang_necklace") bonus++
        if (equippedAuxiliary == "alpha_fang" && movedTilesSinceAttack >= 2) bonus += 2
        if (funeralBellPowerReady) {
            bonus += 2
        }
        if (hasRelic("furnace_core")) bonus++
        return weapon.damage + bonus
    }

    private fun hasRelic(code: String): Boolean = itemCount(code) > 0

    private fun pullMonsterTowardPlayer(monster: UnitSprite) {
        val nextColumn = monster.column + (player.column - monster.column).coerceIn(-1, 1)
        val nextRow = monster.row + (player.row - monster.row).coerceIn(-1, 1)
        if (nextColumn to nextRow !in obstacles && !occupied(nextColumn, nextRow)) {
            monster.column = nextColumn
            monster.row = nextRow
            monster.drawColumn = nextColumn.toFloat()
            monster.drawRow = nextRow.toFloat()
            message = "묘지기의 쇠사슬 · ${monster.name}을 1칸 끌어당겼습니다"
        }
    }

    private fun launchProjectile(code: String, fromColumn: Int, fromRow: Int, target: UnitSprite, duration: Long) {
        projectile = Projectile(code, fromColumn, fromRow, target.column, target.row, System.currentTimeMillis(), duration)
        focusCamera(fromColumn, fromRow)
    }

    private fun alignedDirection(target: UnitSprite): Pair<Int, Int>? = when {
        target.column == player.column && target.row != player.row -> 0 to (if (target.row > player.row) 1 else -1)
        target.row == player.row && target.column != player.column -> (if (target.column > player.column) 1 else -1) to 0
        else -> null
    }

    private fun lineTargets(direction: Pair<Int, Int>, range: Int): List<UnitSprite> {
        val result = mutableListOf<UnitSprite>()
        for (step in 1..range) {
            val column = player.column + direction.first * step
            val row = player.row + direction.second * step
            if (column !in 0 until columns || row !in 0 until rows || obstacles.contains(column to row)) break
            monsters.firstOrNull { it.alive && it.column == column && it.row == row }?.let(result::add)
        }
        return result
    }

    private fun createLoot(monster: UnitSprite) {
        if (monster.droppedLoot) return
        monster.droppedLoot = true
        val definition = monster.definition ?: return
        var gold = if (Random.nextDouble() < definition.goldDropRate) definition.goldDrop else 0
        if (gold > 0 && equippedAccessory == "thief_coin_pouch" && Random.nextDouble() < optionChance("thief_coin_pouch")) gold++
        val items = linkedMapOf<String, Int>()
        monster.guaranteedChestGrade?.let { grade ->
            randomEquipmentForGrade(grade)?.let { items[it.code] = 1 }
        }
        itemByCode.values.filter { it.dropRate > 0.0 }.forEach { item -> if (Random.nextDouble() < item.dropRate) items[item.code] = 1 }
        monsterDrops.filter { it.monsterCode == definition.code }.forEach { drop ->
            if (Random.nextDouble() < drop.dropRate) items[drop.itemCode] = 1
        }
        if (gold > 0 || items.isNotEmpty()) lootPiles += LootPile(monster.column, monster.row, gold, items)
    }

    private fun openTreasureChest(chest: TreasureChest) {
        if (phase != Phase.PLAYER || chest.openingStartedAt != 0L) return
        chest.openingStartedAt = System.currentTimeMillis()
        phase = Phase.MONSTERS
        soundPlayer.playChestLatch()
        message = "${gradeDisplayName(chest.grade)} 상자의 잠금장치를 해제합니다"
        postDelayed({ soundPlayer.playChestOpen(); invalidate() }, 260L)
        postDelayed({
            treasureChests.remove(chest)
            if (chest.mimic) revealMimic(chest) else grantDungeonChestReward(chest.grade)
        }, 900L)
        invalidate()
    }

    private fun revealMimic(chest: TreasureChest) {
        val code = when (currentFloor) {
            in 1..5 -> "mimic_01_05"; in 6..10 -> "mimic_06_10"
            in 11..15 -> "mimic_11_15"; else -> "mimic_16_20"
        }
        val definition = monsterDefinitions.firstOrNull { it.code == code } ?: run {
            phase = Phase.PLAYER; message = "상자는 비어 있었습니다"; invalidate(); return
        }
        val mimic = UnitSprite(
            definition.name, chest.column, chest.row, bitmap(definition.spritePath), definition = definition,
            hp = definition.maxHp, maxHp = definition.maxHp, alerted = true, guaranteedChestGrade = chest.grade
        )
        monsters += mimic
        focusedMonster = mimic
        focusCamera(mimic.column, mimic.row)
        message = "${mimic.name}이 튀어나와 선공합니다"
        soundPlayer.playChestReveal()
        val duration = performMonsterAction(mimic)
        postDelayed({
            focusedMonster = null
            if (player.hp <= 0) finishPlayerDeath() else {
                phase = Phase.PLAYER
                message = "미믹의 선공이 끝났습니다"
                persistRun()
            }
            invalidate()
        }, combatDuration(duration))
        invalidate()
    }

    private fun grantDungeonChestReward(grade: String) {
        val equipment = randomEquipmentForGrade(grade)
        if (equipment != null && (inventoryCounts.containsKey(equipment.code) || inventoryCounts.size < inventoryCapacity)) {
            inventoryCounts[equipment.code] = itemCount(equipment.code) + 1
            acquiredCounts[equipment.code] = (acquiredCounts[equipment.code] ?: 0) + 1
            message = "${gradeDisplayName(grade)} 상자 · ${equipment.name} 획득"
        } else {
            val gold = when (grade) {
                "HIGH" -> 8; "RARE" -> 15; "EPIC" -> 30; "UNIQUE" -> 60
                "LEGENDARY" -> 120; "MYTHIC" -> 250; else -> 4
            }
            lootedGold += gold
            message = "${gradeDisplayName(grade)} 상자 · ${gold}G 획득"
        }
        soundPlayer.playChestReveal()
        phase = Phase.PLAYER
        persistRun()
        invalidate()
    }

    private fun randomEquipmentForGrade(grade: String): ItemDefinitionEntity? =
        itemByCode.values.filter { !it.isConsumable && it.grade == grade }.randomOrNull()

    private fun collectLoot(pile: LootPile) {
        if (System.currentTimeMillis() - pile.droppedAt < LOOT_DROP_DURATION_MS) {
            message = "전리품이 바닥에 떨어지는 중입니다"
            invalidate(); return
        }
        if (distance(player.column, player.row, pile.column, pile.row) != 1) {
            message = "전리품 바로 앞 칸에서 습득할 수 있습니다"
            invalidate(); return
        }
        if (pile.items.isNotEmpty() && pile.openingStartedAt == 0L) {
            startOpeningChest(pile)
            return
        }
        finishCollectLoot(pile)
    }

    private fun startOpeningChest(pile: LootPile) {
        pile.openingStartedAt = System.currentTimeMillis()
        phase = Phase.MONSTERS
        message = "상자의 잠금장치를 해제합니다"
        soundPlayer.playChestLatch()
        postDelayed({
            soundPlayer.playChestOpen()
            message = "상자가 열리며 빛이 새어 나옵니다"
            invalidate()
        }, 230L)
        postDelayed({ soundPlayer.playChestReveal(); invalidate() }, 690L)
        postDelayed({
            finishCollectLoot(pile)
        }, CHEST_OPEN_DURATION_MS)
        invalidate()
    }

    private fun finishCollectLoot(pile: LootPile) {
        var pickedItems = 0
        lootedGold += pile.gold
        val pickedGold = pile.gold
        pile.gold = 0
        val iterator = pile.items.iterator()
        while (iterator.hasNext()) {
            val (code, quantity) = iterator.next()
            if (inventoryCounts.containsKey(code) || inventoryCounts.size < inventoryCapacity) {
                inventoryCounts[code] = itemCount(code) + quantity
                acquiredCounts[code] = (acquiredCounts[code] ?: 0) + quantity
                pickedItems += quantity
                iterator.remove()
            }
        }
        if (pile.gold == 0 && pile.items.isEmpty()) lootPiles.remove(pile)
        message = when {
            pickedGold > 0 && pickedItems > 0 -> "전리품 습득 · ${pickedGold}G · 아이템 ${pickedItems}개"
            pickedGold > 0 -> "골드 ${pickedGold}G 습득"
            pickedItems > 0 -> "아이템 ${pickedItems}개 습득"
            else -> "인벤토리가 가득 찼습니다"
        }
        phase = Phase.MONSTERS
        postDelayed({ beginMonsterTurns(1) }, combatDuration(300L))
        invalidate()
    }

    private fun beginMonsterTurns(rounds: Int) {
        phase = Phase.MONSTERS
        pendingMonsterRounds = rounds
        startMonsterRound()
    }

    private fun startMonsterRound() {
        monsterRoundSequence++
        tickFireZones()
        tickBurningDamage()
        tickBleedingDamage()
        runMonsterRound(0)
    }

    private fun tickBleedingDamage() {
        monsters.filter { it.alive && it.hp > 0 && it.bleedTurns.isNotEmpty() }.forEach { monster ->
            val damage = monster.bleedTurns.size * 2
            monster.hp = max(0, monster.hp - damage)
            monster.bleedTurns.replaceAll { it - 1 }
            monster.bleedTurns.removeAll { it <= 0 }
            showEffect("bleed", monster)
            if (monster.hp == 0) {
                defeatMonster(monster)
            }
        }
    }

    private fun tickFireZones() {
        val activeZones = fireZones.filter { it.remainingDamageTurns > 0 }
        if (activeZones.isEmpty()) return
        var hitCount = 0
        activeZones.forEach { zone ->
            monsters.filter { monster ->
                monster.alive && monster.hp > 0 &&
                    abs(monster.column - zone.centerColumn) <= 1 && abs(monster.row - zone.centerRow) <= 1
            }.forEach { monster ->
                hitCount++
                damageMonsterFromFire(monster, 5)
                if (monster.hp > 0) {
                    if (monster.burnTurnsRemaining == 0) monster.burnAppliedRound = monsterRoundSequence
                    monster.burnTurnsRemaining = max(monster.burnTurnsRemaining, 3)
                }
            }
            zone.remainingDamageTurns--
            if (zone.remainingDamageTurns == 0) postDelayed({ fireZones.remove(zone); invalidate() }, combatDuration(700L))
        }
        if (hitCount > 0) message = "화염 지대 · 몬스터 ${hitCount}마리에게 5 피해"
    }

    private fun tickBurningDamage() {
        var burningTargets = 0
        monsters.filter {
            it.alive && it.hp > 0 && it.burnTurnsRemaining > 0 && it.burnAppliedRound < monsterRoundSequence
        }.forEach { monster ->
            burningTargets++
            damageMonsterFromFire(monster, 3)
            monster.burnTurnsRemaining--
            if (monster.hp == 0) monster.burnTurnsRemaining = 0
        }
        if (burningTargets > 0) message = "화상 피해 · 몬스터 ${burningTargets}마리에게 3 피해"
    }

    private fun damageMonsterFromFire(monster: UnitSprite, damage: Int) {
        alertMonstersFromCombat(monster)
        monster.hp = max(0, monster.hp - damage)
        impactColumn = monster.column; impactRow = monster.row; impactUntil = System.currentTimeMillis() + 520L
        if (monster.hp == 0) {
            defeatMonster(monster)
        } else {
            soundPlayer.playHit(monster.definition?.code)
            playAction(monster, 2, 480L)
        }
    }

    private fun runMonsterRound(index: Int) {
        val living = monsters.filter { it.alive && it.hp > 0 }
        if (index >= living.size) {
            pendingMonsterRounds--
            if (pendingMonsterRounds > 0) { postDelayed({ startMonsterRound() }, combatDuration(350L)); return }
            focusedMonster = null; phase = Phase.PLAYER; turn++; focusCamera(player.column, player.row)
            tickPlayerPoison()
            tickPlayerBurn()
            message = if (player.hp > 0) "플레이어 행동 차례" else "플레이어가 쓰러졌습니다"
            tickPlayerRegeneration()
            if (player.hp <= 0) finishPlayerDeath()
            else persistRun()
            invalidate(); return
        }
        val monster = living[index]; focusedMonster = monster; focusCamera(monster.column, monster.row); message = "${monster.name}의 행동"; invalidate()
        postDelayed({
            val actionDuration = performMonsterAction(monster)
            invalidate()
            postDelayed({
                if (player.hp <= 0) finishPlayerDeath() else runMonsterRound(index + 1)
            }, combatDuration(actionDuration))
        }, combatDuration(420L))
    }

    private fun performMonsterAction(monster: UnitSprite): Long {
        val dist = distance(monster.column, monster.row, player.column, player.row)
        val definition = monster.definition ?: return 0L
        if (!monster.alerted) {
            val seesPlayer = dist <= definition.sensitivity &&
                hasLineOfSight(monster.column, monster.row, player.column, player.row)
            if (seesPlayer) {
                monster.alerted = true
                message = "${monster.name}이 플레이어를 발견했습니다"
            } else {
                message = "${monster.name}이 주변을 경계합니다"
                return 420L
            }
        }
        if (definition.code == "ash_arbalist" && monster.attackCooldown > 0) {
            monster.attackCooldown--
            message = "${monster.name}이 쇠뇌를 재장전합니다"
            return 620L
        }
        if (definition.code == "ember_deacon" && monsterRoundSequence % 3 == 0) {
            monsters.filter { it.alive && it.hp in 1 until it.maxHp }.minByOrNull { it.hp.toFloat() / it.maxHp }?.let { ally ->
                ally.hp = min(ally.maxHp, ally.hp + 2)
                playAction(monster, 2, 760L)
                message = "${monster.name}의 잿불 기도 · ${ally.name} HP 2 회복"
                return 820L
            }
        }
        val attackRange = if (monster.spiderOpeningAttack) definition.openingAttackRange else definition.attackRange
        val rooted = monster.rootTurns > 0
        if (rooted) monster.rootTurns--
        if (dist <= attackRange && hasLineOfSight(monster.column, monster.row, player.column, player.row)) {
            alertMonstersFromCombat(monster)
            var damage = definition.attackPower
            if (definition.code in setOf("plague_bell_keeper", "furnace_saint") && monsterRoundSequence % 3 == 0) damage += 2
            if (definition.code == "ash_arbalist") monster.attackCooldown = 1
            soundPlayer.playAttack(definition.code)
            playAction(monster, 2, 760L)
            if (dist > 1) {
                projectile = Projectile(definition.code, monster.column, monster.row, player.column, player.row, System.currentTimeMillis(), combatDuration(620L))
                postDelayed({ projectile = null; invalidate() }, combatDuration(650L))
            }
            if (monster.kind == MonsterKind.SPIDER) monster.spiderOpeningAttack = false
            if (!monster.firstAttackUsed) {
                monster.firstAttackUsed = true
                if (equippedArmorByCategory["HELMET"] == "black_hood") {
                    defenseMissUntil = System.currentTimeMillis() + 950L
                    showEffect("dodge_counter", player)
                    message = "검은 두건 · 첫 공격 완전 회피"
                    return 820L
                }
            }
            if (equippedArmorByCategory["HELMET"] == "spider_eye_helmet" && Random.nextDouble() < optionChance("spider_eye_helmet")) {
                defenseMissUntil = System.currentTimeMillis() + 950L
                showEffect("dodge_counter", player)
                val counterDamage = itemAttack("spider_eye_helmet", 2)
                damageMonsterDirect(monster, counterDamage)
                message = "거미눈 투구 · 회피 후 반격 $counterDamage"
                return 820L
            }
            if (equippedAuxiliary == "slime_shield" && dist > 1 && Random.nextDouble() < optionChance("slime_shield")) {
                defenseMissUntil = System.currentTimeMillis() + 950L
                showEffect("slime_block", player)
                message = "점액 방패 · 원거리 공격 무효"
                return 820L
            }
            if (defenseChance > 0.0 && Random.nextDouble() < defenseChance) {
                defenseMissUntil = System.currentTimeMillis() + 950L
                message = "${monster.name} 공격 · 방어 성공 · MISS"
                return 820L
            }
            if (hasRelic("cold_iron_rosary") && !firstHitRelicUsedThisFloor) {
                firstHitRelicUsedThisFloor = true
                damage = max(0, damage - 3)
                message = "냉철 묵주 · 첫 피해 3 감소"
            }
            impactColumn = player.column; impactRow = player.row; impactUntil = System.currentTimeMillis() + 760L
            player.hp = max(0, player.hp - damage)
            turnsWithoutDamage = 0
            when (definition.code) {
                "plague_rat" -> playerPoisonTurns = max(playerPoisonTurns, relicAdjustedAilmentTurns(3))
                "spore_body" -> playerPoisonTurns = max(playerPoisonTurns, relicAdjustedAilmentTurns(2))
                "hook_jailer" -> pullPlayerToward(monster)
                "molten_bombardier" -> playerBurnTurns = max(playerBurnTurns, relicAdjustedAilmentTurns(2))
            }
            if (definition.code == "cinder_gargoyle") retreatMonsterFromPlayer(monster)
            if (player.hp in 1..3 && hasRelic("sleeping_saint_chalice") && !saintChaliceUsedThisFloor) {
                saintChaliceUsedThisFloor = true
                player.hp = min(player.maxHp, player.hp + 4)
                message = "잠든 성자의 성배 · HP 4 회복"
            }
            if (player.hp == 0) {
                when {
                    equippedAccessory == "splitting_core" -> {
                        consumeInventoryItem("splitting_core")
                        equippedAccessory = null
                        player.hp = 1
                        showEffect("split_survive", player)
                        message = "분열하는 핵 파괴 · HP 1로 생존"
                        return 1000L
                    }
                    equippedAccessory == "greed_coin" && !greedCoinUsed && availableVillageGold >= 50 -> {
                        greedCoinUsed = true
                        availableVillageGold -= 50
                        onSpendReviveGold(50)
                        player.hp = 5
                        showEffect("gold_revive", player)
                        message = "탐욕의 금화 · 50G를 지불하고 부활"
                        return 1000L
                    }
                    else -> {
                        deathCause = monster
                        player.dying = true
                        playAction(player, 3, 1150L)
                    }
                }
            }
            message = "${monster.name} 공격 · 피해 $damage"; return if (player.hp == 0) 1200L else 820L
        }
        if (rooted) { message = "${monster.name}이 속박되어 이동하지 못합니다"; return 520L }
        if (definition.moveEveryTurns > 1 && turn % definition.moveEveryTurns != 0) { message = "${monster.name}이 몸을 웅크립니다"; return 520L }
        var moved = false
        repeat(definition.moveDistance) {
            val next = nextStep(monster, seekDetour = monster.blockedMoveTurns > 0)
            next?.let {
                monster.column = next.first; monster.row = next.second; monster.drawColumn = next.first.toFloat(); monster.drawRow = next.second.toFloat()
                playAction(monster, 1, 560L); focusCamera(monster.column, monster.row)
                moved = true
            }
        }
        if (moved) {
            monster.blockedMoveTurns = 0
            message = "${monster.name} 이동"
        } else {
            monster.blockedMoveTurns++
            message = "${monster.name}이 길을 찾습니다"
        }
        return 620L
    }

    private fun damageMonsterDirect(monster: UnitSprite, damage: Int) {
        alertMonstersFromCombat(monster)
        monster.hp = max(0, monster.hp - damage)
        impactColumn = monster.column; impactRow = monster.row; impactUntil = System.currentTimeMillis() + 650L
        if (monster.hp == 0) {
            defeatMonster(monster)
        }
    }

    private fun relicAdjustedAilmentTurns(baseTurns: Int): Int =
        if (hasRelic("plague_doctor_censer")) max(1, baseTurns - 1) else baseTurns

    private fun alertMonstersFromCombat(attacked: UnitSprite) {
        attacked.alerted = true
        monsters.filter { listener ->
            listener.alive && listener.hp > 0 && listener !== attacked && !listener.alerted &&
                distance(listener.column, listener.row, attacked.column, attacked.row) <=
                (listener.definition?.sensitivity ?: 0)
        }.forEach { it.alerted = true }
    }

    private fun tickPlayerPoison() {
        if (playerPoisonTurns <= 0 || player.hp <= 0) return
        player.hp = max(0, player.hp - 1)
        playerPoisonTurns--
        message = "역병 독 피해 1 · ${playerPoisonTurns}턴 남음"
        if (player.hp in 1..3 && hasRelic("sleeping_saint_chalice") && !saintChaliceUsedThisFloor) {
            saintChaliceUsedThisFloor = true
            player.hp = min(player.maxHp, player.hp + 4)
            message = "잠든 성자의 성배 · 독성 치명상을 막고 HP 4 회복"
        }
        if (player.hp == 0) {
            player.dying = true
            playAction(player, 3, 1150L)
        }
    }

    private fun tickPlayerBurn() {
        if (playerBurnTurns <= 0 || player.hp <= 0) return
        player.hp = max(0, player.hp - 2)
        playerBurnTurns--
        message = "용융 화상 피해 2 · ${playerBurnTurns}턴 남음"
        if (player.hp in 1..3 && hasRelic("sleeping_saint_chalice") && !saintChaliceUsedThisFloor) {
            saintChaliceUsedThisFloor = true
            player.hp = min(player.maxHp, player.hp + 4)
            message = "잠든 성자의 성배 · 화상 치명상을 막고 HP 4 회복"
        }
        if (player.hp == 0) {
            player.dying = true
            playAction(player, 3, 1150L)
        }
    }

    private fun retreatMonsterFromPlayer(monster: UnitSprite) {
        val awayColumn = monster.column + (monster.column - player.column).coerceIn(-1, 1)
        val awayRow = monster.row + (monster.row - player.row).coerceIn(-1, 1)
        if (awayColumn in 0 until columns && awayRow in 0 until rows &&
            awayColumn to awayRow !in obstacles && !occupied(awayColumn, awayRow)
        ) {
            monster.column = awayColumn
            monster.row = awayRow
            monster.drawColumn = awayColumn.toFloat()
            monster.drawRow = awayRow.toFloat()
        }
    }

    private fun pullPlayerToward(monster: UnitSprite) {
        val nextColumn = player.column + (monster.column - player.column).coerceIn(-1, 1)
        val nextRow = player.row + (monster.row - player.row).coerceIn(-1, 1)
        if (nextColumn to nextRow !in obstacles && monsters.none { it.alive && it.column == nextColumn && it.row == nextRow }) {
            player.column = nextColumn
            player.row = nextRow
            player.drawColumn = nextColumn.toFloat()
            player.drawRow = nextRow.toFloat()
        }
    }

    private fun tickPlayerRegeneration() {
        if (equippedArmorByCategory["ARMOR"] != "regen_slime_armor" || player.hp >= player.maxHp || regenHealsThisFloor >= 2) return
        turnsWithoutDamage++
        if (turnsWithoutDamage >= 5) {
            player.hp++
            regenHealsThisFloor++
            turnsWithoutDamage = 0
            showEffect("regeneration", player)
            message = "재생 점액 갑옷 · HP 1 회복"
        }
    }

    private fun finishPlayerDeath() {
        focusedMonster = null
        player.dying = false
        player.actionRow = 3
        player.actionStartedAt = System.currentTimeMillis() - 480L
        player.actionUntil = Long.MAX_VALUE
        message = "플레이어가 쓰러졌습니다"
        focusCamera(player.column, player.row)
        invalidate()
        if (!deathReported) {
            deathReported = true
            onPlayerDeath(currentFloor, turn, deathCause?.definition?.code, deathCause?.name)
        }
    }

    private fun nextStep(monster: UnitSprite, seekDetour: Boolean): Pair<Int, Int>? {
        if (seekDetour) findDetourStep(monster)?.let { return it }
        val candidates = mutableListOf<Pair<Int, Int>>()
        fun horizontal() { if (player.column != monster.column) candidates += (monster.column + if (player.column > monster.column) 1 else -1) to monster.row }
        fun vertical() { if (player.row != monster.row) candidates += monster.column to (monster.row + if (player.row > monster.row) 1 else -1) }
        if (abs(player.column - monster.column) >= abs(player.row - monster.row)) { horizontal(); vertical() } else { vertical(); horizontal() }
        return candidates.firstOrNull { (c, r) -> c in 0 until columns && r in 0 until rows && !obstacles.contains(c to r) && !occupied(c, r) }
    }

    private fun findDetourStep(monster: UnitSprite): Pair<Int, Int>? {
        val start = monster.column to monster.row
        val queue = ArrayDeque<Pair<Int, Int>>()
        val previous = mutableMapOf<Pair<Int, Int>, Pair<Int, Int>?>()
        queue += start
        previous[start] = null
        var best = start
        var bestDistance = distance(start.first, start.second, player.column, player.row)
        val directions = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val currentDistance = distance(current.first, current.second, player.column, player.row)
            if (currentDistance < bestDistance) {
                best = current
                bestDistance = currentDistance
                if (bestDistance <= 1) break
            }
            directions.forEach { direction ->
                val next = current.first + direction.first to current.second + direction.second
                if (next in previous || next.first !in 0 until columns || next.second !in 0 until rows) return@forEach
                if (obstacles.contains(next) || next == (player.column to player.row)) return@forEach
                if (monsters.any { it !== monster && it.alive && it.column == next.first && it.row == next.second }) return@forEach
                previous[next] = current
                queue += next
            }
        }
        if (best == start) return null
        var step = best
        while (previous[step] != start) step = previous[step] ?: return null
        return step
    }

    private fun createFloorMonsters(floor: Int): MutableList<UnitSprite> {
        val random = Random(monsterSpawnSeed xor (floor.toLong() * 0x6A09E667L))
        val occupiedSpawns = mutableSetOf(player.column to player.row, stairsColumn to stairsRow)
        val templates = monsterFloorSpawns.filter { it.floor == floor }
        val baseDefinitions = templates.mapNotNull { spawn -> monsterDefinitions.firstOrNull { it.code == spawn.monsterCode } }
        if (baseDefinitions.isEmpty()) return mutableListOf()
        val minimum = monsterCountMin.coerceAtLeast(1)
        val maximum = monsterCountMax.coerceAtLeast(minimum)
        val targetCount = random.nextInt(minimum, maximum + 1)
        val selectedDefinitions = baseDefinitions.take(targetCount).toMutableList()
        val regularPool = baseDefinitions.filterNot { it.goldDropRate >= 1.0 && it.maxHp >= 30 }
            .ifEmpty { baseDefinitions }
        while (selectedDefinitions.size < targetCount) {
            selectedDefinitions += regularPool[random.nextInt(regularPool.size)]
        }
        return selectedDefinitions.mapIndexed { index, definition ->
            var selected: Pair<Int, Int>? = null
            repeat(100) { attempt ->
                if (selected != null) return@repeat
                val entranceSpawn = index == 0 && attempt < 60
                val column = if (entranceSpawn) random.nextInt(4, 8) else random.nextInt(1, columns - 1)
                val row = if (entranceSpawn) random.nextInt(4, rows - 1) else random.nextInt(1, rows - 1)
                val cell = column to row
                if (cell !in occupiedSpawns && distance(column, row, player.column, player.row) >= 2 &&
                    distance(column, row, stairsColumn, stairsRow) >= 2
                ) selected = cell
            }
            val fallback = templates[index % templates.size]
            val cell = selected ?: (fallback.column to fallback.row)
            occupiedSpawns += cell
            UnitSprite(
                definition.name, cell.first, cell.second, bitmap(definition.spritePath),
                kind = when (definition.code) {
                    "spider" -> MonsterKind.SPIDER; "bandit" -> MonsterKind.BANDIT
                    "wild_dog" -> MonsterKind.WILD_DOG; "slime" -> MonsterKind.SLIME; else -> null
                },
                definition = definition, hp = definition.maxHp, maxHp = definition.maxHp
            )
        }.toMutableList()
    }

    private fun createObstacles() {
        val reserved = mutableSetOf(player.column to player.row, stairsColumn to stairsRow).apply {
            addAll(monsters.map { it.column to it.row })
            monsters.forEach { monster ->
                addAll(listOf(
                    monster.column - 1 to monster.row, monster.column + 1 to monster.row,
                    monster.column to monster.row - 1, monster.column to monster.row + 1
                ).filter { it.first in 0 until columns && it.second in 0 until rows })
            }
            addAll(healingObjects.flatMap(::occupiedCells))
            addAll(treasureChests.map { it.column to it.row })
            addAll(listOf(stairsColumn - 1 to stairsRow, stairsColumn + 1 to stairsRow, stairsColumn to stairsRow - 1, stairsColumn to stairsRow + 1))
            addAll(listOf(player.column - 1 to player.row, player.column + 1 to player.row, player.column to player.row - 1, player.column to player.row + 1))
        }
        val layoutSeed = if (currentFloor <= 5) floorObstacleSeed else floorObstacleSeed xor (currentFloor.toLong() * 0x5DEECE66DL)
        val random = Random(layoutSeed)
        val naturalObstacles = listOf(ObstacleKind.STONE_PILLAR, ObstacleKind.DEAD_TREE)
        while (obstacles.size < 18) {
            val cell = random.nextInt(columns) to random.nextInt(rows)
            if (cell !in reserved && distance(cell.first, cell.second, player.column, player.row) > 2) {
                obstacles[cell] = naturalObstacles[random.nextInt(naturalObstacles.size)]
            }
        }
    }

    private fun createHealingObjects(floor: Int): MutableList<HealingObject> {
        if (interactableDefinitions.isEmpty()) return mutableListOf()
        val random = Random(floorObstacleSeed xor (floor.toLong() * 0x2F6E2B1DL))
        if (random.nextInt(100) >= healingObjectChancePercent.coerceIn(0, 100)) return mutableListOf()
        val definition = interactableDefinitions[random.nextInt(interactableDefinitions.size)]
        val blocked = buildSet {
            add(player.column to player.row)
            add(stairsColumn to stairsRow)
            addAll(monsters.map { it.column to it.row })
        }
        repeat(80) {
            val column = random.nextInt(4, columns - definition.footprintWidth - 1)
            val row = random.nextInt(1, rows - definition.footprintHeight)
            val candidate = HealingObject(definition, column, row)
            if (occupiedCells(candidate).none { it in blocked } &&
                occupiedCells(candidate).all { distance(it.first, it.second, player.column, player.row) > 2 }) {
                return mutableListOf(candidate)
            }
        }
        return mutableListOf()
    }

    private fun createTreasureChests(floor: Int): MutableList<TreasureChest> {
        val random = Random(floorObstacleSeed xor (floor.toLong() * 0x45D9F3BL))
        if (random.nextInt(100) >= dungeonChestSpawnPercent.coerceIn(0, 100)) return mutableListOf()
        val grade = rollDungeonChestGrade(floor, random.nextInt(100))
        val blocked = buildSet {
            add(player.column to player.row); add(stairsColumn to stairsRow)
            addAll(monsters.map { it.column to it.row })
            addAll(healingObjects.flatMap(::occupiedCells))
        }
        repeat(80) {
            val column = random.nextInt(4, columns - 2)
            val row = random.nextInt(1, rows - 1)
            if (column to row !in blocked && distance(column, row, player.column, player.row) > 3) {
                return mutableListOf(TreasureChest(
                    column, row, grade,
                    random.nextInt(100) < dungeonChestMimicPercent.coerceIn(0, 100)
                ))
            }
        }
        return mutableListOf()
    }

    private fun rollDungeonChestGrade(floor: Int, roll: Int): String = when (floor) {
        in 1..5 -> when { roll < 70 -> "NORMAL"; roll < 95 -> "HIGH"; else -> "RARE" }
        in 6..10 -> when { roll < 55 -> "HIGH"; roll < 90 -> "RARE"; else -> "EPIC" }
        in 11..15 -> when { roll < 55 -> "RARE"; roll < 90 -> "EPIC"; else -> "UNIQUE" }
        else -> when { roll < 55 -> "EPIC"; roll < 85 -> "UNIQUE"; roll < 97 -> "LEGENDARY"; else -> "MYTHIC" }
    }

    private fun occupiedCells(obj: HealingObject): List<Pair<Int, Int>> = buildList {
        repeat(obj.definition.footprintWidth) { x ->
            repeat(obj.definition.footprintHeight) { y -> add(obj.column + x to obj.row + y) }
        }
    }

    private fun isAdjacentTo(obj: HealingObject): Boolean = occupiedCells(obj).any {
        distance(player.column, player.row, it.first, it.second) == 1
    }

    private fun useHealingObject(obj: HealingObject) {
        when {
            obj.used -> message = "${obj.definition.name}의 힘은 이미 사라졌습니다"
            !isAdjacentTo(obj) -> message = "${obj.definition.name} 바로 옆으로 이동해야 합니다"
            player.hp >= player.maxHp -> message = "이미 체력이 가득 차 있습니다"
            else -> {
                val before = player.hp
                val healAmount = if (obj.definition.healPercent >= 100) {
                    player.maxHp
                } else {
                    (player.maxHp * obj.definition.healPercent + 99) / 100
                }
                player.hp = min(player.maxHp, player.hp + healAmount)
                obj.used = true
                message = "${obj.definition.name} · HP ${player.hp - before} 회복"
                phase = Phase.MONSTERS
                postDelayed({ beginMonsterTurns(1) }, combatDuration(520L))
            }
        }
        invalidate()
    }

    private fun drawHealingObject(canvas: Canvas, area: RectF, obj: HealingObject) {
        val topLeft = tileRect(area, obj.column, obj.row)
        val bottomRight = tileRect(
            area,
            obj.column + obj.definition.footprintWidth - 1,
            obj.row + obj.definition.footprintHeight - 1
        )
        val rect = RectF(topLeft.left, topLeft.top, bottomRight.right, bottomRight.bottom)
        if (!RectF.intersects(rect, area)) return
        val image = interactableBitmaps[obj.definition.code] ?: return
        val isAngel = obj.definition.code == "angel_statue"
        val width = rect.width() * if (isAngel) 1.0f else .96f
        val height = rect.height() * if (isAngel) 1.35f else .96f
        val bottom = rect.bottom + rect.height() * .08f
        paint.color = if (obj.used) 0x44181818 else if (isAngel) 0x66FFD878 else 0x6655DFFF
        canvas.drawOval(
            RectF(rect.centerX() - width * .36f, bottom - rect.height() * .18f, rect.centerX() + width * .36f, bottom + rect.height() * .08f),
            paint
        )
        paint.alpha = if (obj.used) 90 else 255
        canvas.drawBitmap(image, null, RectF(rect.centerX() - width / 2f, bottom - height, rect.centerX() + width / 2f, bottom), paint)
        paint.alpha = 255
        if (!obj.used && phase == Phase.PLAYER && isAdjacentTo(obj)) {
            textPaint.color = Color.WHITE
            textPaint.textSize = dp(10f)
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("터치", rect.centerX(), bottom - height - dp(3f), textPaint)
            textPaint.textAlign = Paint.Align.LEFT
        }
    }

    private fun drawTreasureChest(canvas: Canvas, area: RectF, chest: TreasureChest) {
        val rect = tileRect(area, chest.column, chest.row)
        if (!RectF.intersects(rect, area)) return
        val elapsed = System.currentTimeMillis() - chest.openingStartedAt
        if (chest.openingStartedAt > 0L) {
            val frameWidth = chestOpeningSheet.width / 4
            val frame = (elapsed / 210L).toInt().coerceIn(0, 3)
            canvas.drawBitmap(chestOpeningSheet, Rect(frame * frameWidth, 0, (frame + 1) * frameWidth, chestOpeningSheet.height), rect, paint)
        } else {
            canvas.drawBitmap(lootBitmaps[chest.grade] ?: lootBitmaps.getValue("NORMAL"), null, rect, paint)
        }
        if (phase == Phase.PLAYER && distance(player.column, player.row, chest.column, chest.row) == 1) {
            textPaint.color = Color.WHITE; textPaint.textSize = dp(10f); textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("열기", rect.centerX(), rect.top - dp(3f), textPaint)
            textPaint.textAlign = Paint.Align.LEFT
        }
    }

    private fun drawFlatObstacles(canvas: Canvas, area: RectF) {
        obstacles.filterValues { it.flat }.forEach { (cell, kind) ->
            val rect = tileRect(area, cell.first, cell.second)
            if (!RectF.intersects(rect, area)) return@forEach
            val width = rect.width() * kind.widthScale
            val height = rect.height() * kind.heightScale
            paint.alpha = 235
            canvas.drawBitmap(
                obstacleBitmaps.getValue(kind), null,
                RectF(rect.centerX() - width / 2f, rect.centerY() - height / 2f, rect.centerX() + width / 2f, rect.centerY() + height / 2f), paint
            )
            paint.alpha = 255
        }
    }

    private fun drawDepthSortedScene(canvas: Canvas, area: RectF) {
        val units = monsters.filter { it.alive } + player
        val firstRow = floor(cameraRow).toInt() - 1
        val lastRow = min(rows - 1, (cameraRow + visibleRows).toInt() + 1)
        for (row in firstRow..lastRow) {
            units.filter { it.drawRow.toInt() == row }.sortedBy { it.drawColumn }.forEach { drawUnit(canvas, area, it) }
            healingObjects.filter { it.row + it.definition.footprintHeight - 1 == row }
                .sortedBy { it.column }.forEach { drawHealingObject(canvas, area, it) }
            obstacles.filter { (cell, kind) -> cell.second == row && !kind.flat }
                .toList().sortedBy { it.first.first }
                .forEach { (cell, kind) -> drawRaisedObstacle(canvas, area, cell.first, cell.second, kind) }
        }
    }

    private fun drawRaisedObstacle(canvas: Canvas, area: RectF, column: Int, row: Int, kind: ObstacleKind) {
        val rect = tileRect(area, column, row)
        if (!RectF.intersects(rect, area)) return
        val bottom = rect.bottom + rect.height() * .08f
        val width = rect.width() * kind.widthScale
        val height = rect.height() * kind.heightScale
        paint.color = 0xA0000000.toInt()
        canvas.drawOval(
            RectF(rect.centerX() - width * .34f, bottom - rect.height() * .2f, rect.centerX() + width * .34f, bottom + rect.height() * .08f),
            paint
        )
        paint.alpha = 255
        paint.setShadowLayer(dp(10f), dp(3f), dp(8f), 0xE6000000.toInt())
        canvas.drawBitmap(
            obstacleBitmaps.getValue(kind), null,
            RectF(rect.centerX() - width / 2f, bottom - height, rect.centerX() + width / 2f, bottom), paint
        )
        paint.clearShadowLayer()
        paint.alpha = 255
    }

    private fun drawLootPile(canvas: Canvas, area: RectF, pile: LootPile) {
        val rect = tileRect(area, pile.column, pile.row)
        if (!RectF.intersects(rect, area)) return
        val elapsed = (System.currentTimeMillis() - pile.droppedAt).coerceAtLeast(0L)
        val progress = (elapsed.toFloat() / LOOT_DROP_DURATION_MS).coerceIn(0f, 1f)
        val airborne = progress < 1f
        val arc = if (airborne) sin(progress * Math.PI).toFloat() else 0f
        val dropOffsetY = -rect.height() * 1.45f * arc
        val landingBounce = if (progress in .78f..1f) {
            -rect.height() * .13f * sin(((progress - .78f) / .22f) * Math.PI).toFloat()
        } else 0f
        val itemOffsetX = if (airborne) -rect.width() * .16f * progress else 0f
        val goldOffsetX = if (airborne) rect.width() * .2f * progress else 0f
        val tileSurfaceLift = -rect.height() * .18f
        val animatedOffsetY = dropOffsetY + landingBounce + tileSurfaceLift
        val highestGrade = highestLootGrade(pile)
        paint.color = gradeGlowColor(highestGrade)
        canvas.drawOval(
            RectF(rect.centerX() - rect.width() * .31f, rect.bottom - rect.height() * .36f,
                rect.centerX() + rect.width() * .31f, rect.bottom - rect.height() * .20f),
            paint
        )
        if (pile.items.isNotEmpty()) {
            val openingElapsed = if (pile.openingStartedAt > 0L) elapsedSince(pile.openingStartedAt) else 0L
            val opening = pile.openingStartedAt > 0L
            val size = min(rect.width(), rect.height()) * .82f
            val centerX = (if (pile.gold > 0) rect.centerX() - rect.width() * .08f else rect.centerX()) + itemOffsetX
            val centerY = rect.centerY() + animatedOffsetY
            if (opening) {
                val frameWidth = chestOpeningSheet.width / 4
                val frame = (openingElapsed / (CHEST_OPEN_DURATION_MS / 4)).toInt().coerceIn(0, 3)
                val source = Rect(frame * frameWidth, 0, (frame + 1) * frameWidth, chestOpeningSheet.height)
                val openSize = size * 1.22f
                canvas.drawBitmap(chestOpeningSheet, source, RectF(
                    centerX - openSize / 2, centerY - openSize * .66f,
                    centerX + openSize / 2, centerY + openSize * .34f
                ), paint)
                val revealProgress = (openingElapsed.toFloat() / CHEST_OPEN_DURATION_MS).coerceIn(0f, 1f)
                if (revealProgress > .35f) {
                    val burst = ((revealProgress - .35f) / .65f).coerceIn(0f, 1f)
                    paint.color = gradeSolidColor(highestGrade)
                    paint.alpha = ((1f - burst) * 190).toInt().coerceIn(35, 190)
                    canvas.drawCircle(centerX, centerY - size * .28f, size * (.25f + burst * .9f), paint)
                    paint.alpha = 255
                }
            } else {
                val chest = lootBitmaps[highestGrade] ?: lootBitmaps.getValue("NORMAL")
                canvas.drawBitmap(chest, null, RectF(centerX - size / 2, centerY - size / 2, centerX + size / 2, centerY + size / 2), paint)
            }
        }
        if (pile.gold > 0) {
            val gold = lootBitmaps.getValue(if (pile.gold >= 10) "gold_ingot" else "gold_coins")
            val size = min(rect.width(), rect.height()) * if (pile.items.isEmpty()) .72f else .42f
            val centerX = (if (pile.items.isEmpty()) rect.centerX() else rect.right - size * .42f) + goldOffsetX
            val centerY = (if (pile.items.isEmpty()) rect.centerY() else rect.bottom - size * .42f) + animatedOffsetY
            canvas.drawBitmap(gold, null, RectF(centerX - size / 2, centerY - size / 2, centerX + size / 2, centerY + size / 2), paint)
            textPaint.color = Color.WHITE; textPaint.textSize = dp(10f); textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("${pile.gold}G", centerX, centerY + size * .46f, textPaint)
            textPaint.textAlign = Paint.Align.LEFT
        }
        if (pile.items.size > 1) {
            textPaint.color = Color.WHITE; textPaint.textSize = dp(12f); textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("+${pile.items.size - 1}", rect.right - dp(5f), rect.top + dp(15f), textPaint); textPaint.textAlign = Paint.Align.LEFT
        }
    }

    private fun highestLootGrade(pile: LootPile): String {
        val priority = mapOf("NORMAL" to 0, "HIGH" to 1, "RARE" to 2, "EPIC" to 3, "UNIQUE" to 4, "LEGENDARY" to 5, "MYTHIC" to 6)
        return pile.items.keys.mapNotNull { itemByCode[it]?.grade }
            .maxByOrNull { priority[it] ?: -1 } ?: "NORMAL"
    }

    private fun gradeGlowColor(grade: String): Int = when (grade) {
        "HIGH" -> 0x5548D66A; "RARE" -> 0x554EA5FF; "EPIC" -> 0x55C05CFF
        "UNIQUE" -> 0x55FF9D32; "LEGENDARY" -> 0x55FF3D3D; "MYTHIC" -> 0x66FFE9A3
        else -> 0x443F444A
    }

    private fun gradeSolidColor(grade: String): Int = when (grade) {
        "HIGH" -> 0xFF48D66A.toInt(); "RARE" -> 0xFF4EA5FF.toInt(); "EPIC" -> 0xFFC05CFF.toInt()
        "UNIQUE" -> 0xFFFF9D32.toInt(); "LEGENDARY" -> 0xFFFF3D3D.toInt(); "MYTHIC" -> 0xFFFFE9A3.toInt()
        else -> 0xFFD8C58B.toInt()
    }

    private fun elapsedSince(startedAt: Long) = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)

    private companion object {
        const val LOOT_DROP_DURATION_MS = 900L
        const val CHEST_OPEN_DURATION_MS = 1_050L
    }

    private fun drawStairs(canvas: Canvas, area: RectF) {
        val rect = tileRect(area, stairsColumn, stairsRow)
        if (!RectF.intersects(rect, area)) return
        val unlocked = monsters.none { it.alive }
        val stairTarget = RectF(
            rect.left - rect.width() * .18f,
            rect.top - rect.height() * .28f,
            rect.right + rect.width() * .18f,
            rect.bottom + rect.height() * .08f
        )
        paint.alpha = if (unlocked) 255 else 125
        val background = activeBackground()
        val stairSource = Rect(
            (background.width * .43f).toInt(),
            (background.height * .45f).toInt(),
            (background.width * .57f).toInt(),
            (background.height * .66f).toInt()
        )
        canvas.drawBitmap(background, stairSource, stairTarget, paint)
        paint.alpha = 255
        paint.color = if (unlocked) 0x553FDF7A else 0x885E2420.toInt()
        canvas.drawRoundRect(rect, dp(5f), dp(5f), paint)
        paint.color = if (unlocked) 0xFFD0A653.toInt() else 0xFF63594E.toInt()
        paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(2f)
        canvas.drawRoundRect(rect, dp(5f), dp(5f), paint); paint.style = Paint.Style.FILL
        textPaint.color = Color.WHITE; textPaint.textSize = dp(11f); textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(if (unlocked) "계단 이동" else "봉인", rect.centerX(), rect.bottom - dp(6f), textPaint); textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawUnit(canvas: Canvas, area: RectF, unit: UnitSprite) {
        val isMimic = unit.definition?.code?.startsWith("mimic_") == true
        val frameWidth = unit.sheet.width / 4; val frameHeight = if (isMimic) unit.sheet.height else unit.sheet.height / 4
        val now = System.currentTimeMillis()
        val actionPlaying = now < unit.actionUntil
        val row = if (actionPlaying) unit.actionRow else 0
        val rawFrame = when {
            unit.actionUntil == Long.MAX_VALUE -> 3
            actionPlaying -> (((now - unit.actionStartedAt).coerceAtLeast(0L) / 170L).toInt()).coerceIn(0, 3)
            else -> animationFrame
        }
        val frame = if (isMimic && actionPlaying) row.coerceIn(0, 3) else if (unit === player) stablePlayerEquipmentFrame(row, rawFrame) else rawFrame
        val sourceRow = if (isMimic) 0 else row
        val source = Rect(frame * frameWidth, sourceRow * frameHeight, (frame + 1) * frameWidth, (sourceRow + 1) * frameHeight)
        val target = unitScreenRect(area, unit)
        val tw = area.width() / visibleColumns
        val centerX = target.centerX(); val bottom = target.bottom; val targetHeight = target.height()
        if (unit === player && row == 1 && actionPlaying) {
            val stepPhase = (now - unit.actionStartedAt).coerceAtLeast(0L) / 85f
            val bob = -kotlin.math.abs(sin(stepPhase)) * dp(3f)
            val sway = sin(stepPhase) * 1.8f
            canvas.save()
            canvas.translate(0f, bob)
            canvas.rotate(sway, target.centerX(), target.bottom)
            canvas.drawBitmap(unit.sheet, source, target, paint)
            canvas.restore()
        } else {
            canvas.drawBitmap(unit.sheet, source, target, paint)
        }
        if (unit !== player) {
            paint.color = 0xCC17110E.toInt(); canvas.drawRect(centerX - tw * .3f, bottom - targetHeight - dp(7f), centerX + tw * .3f, bottom - targetHeight - dp(2f), paint)
            paint.color = 0xFFB83A32.toInt(); canvas.drawRect(centerX - tw * .3f, bottom - targetHeight - dp(7f), centerX - tw * .3f + tw * .6f * unit.hp / unit.maxHp, bottom - targetHeight - dp(2f), paint)
        }
    }

    private fun stablePlayerEquipmentFrame(row: Int, rawFrame: Int): Int {
        if (equippedWeapon?.code != "crude_bow") return rawFrame
        val helmetEquipped = equippedArmorByCategory["HELMET"] != null
        return when {
            helmetEquipped && row == 0 -> intArrayOf(0, 1, 3, 1)[rawFrame]
            helmetEquipped && row == 1 -> intArrayOf(1, 2, 1, 2)[rawFrame]
            helmetEquipped && row == 3 -> intArrayOf(2, 3, 3, 3)[rawFrame]
            !helmetEquipped && row == 0 -> 2
            !helmetEquipped && row == 1 -> intArrayOf(0, 3, 0, 3)[rawFrame]
            !helmetEquipped && row == 3 -> intArrayOf(0, 1, 1, 1)[rawFrame]
            else -> rawFrame
        }
    }

    private fun startPlayerMovement(column: Int, row: Int, duration: Long) {
        val fromColumn = player.drawColumn
        val fromRow = player.drawRow
        player.column = column
        player.row = row
        playerMoveAnimation = PlayerMoveAnimation(
            fromColumn, fromRow, column.toFloat(), row.toFloat(),
            System.currentTimeMillis(), duration.coerceAtLeast(1L)
        )
    }

    private fun updatePlayerMovement() {
        val movement = playerMoveAnimation ?: return
        val progress = ((System.currentTimeMillis() - movement.startedAt).toFloat() / movement.duration).coerceIn(0f, 1f)
        val eased = progress * progress * (3f - 2f * progress)
        player.drawColumn = movement.fromColumn + (movement.toColumn - movement.fromColumn) * eased
        player.drawRow = movement.fromRow + (movement.toRow - movement.fromRow) * eased
        if (progress >= 1f) {
            player.drawColumn = movement.toColumn
            player.drawRow = movement.toRow
            playerMoveAnimation = null
        }
    }

    private fun unitScreenRect(area: RectF, unit: UnitSprite): RectF {
        val frameWidth = unit.sheet.width / 4f
        val frameHeight = unit.sheet.height / 4f
        val tileWidth = area.width() / visibleColumns
        val tileHeight = area.height() / visibleRows
        val centerX = area.left + (unit.drawColumn - cameraColumn + .5f) * tileWidth
        val bottom = area.top + (unit.drawRow - cameraRow + 1f) * tileHeight + tileHeight * .1f
        val targetHeight = tileHeight * if (unit === player) 1.45f else 1.16f
        val targetWidth = targetHeight * frameWidth / frameHeight
        return RectF(centerX - targetWidth / 2f, bottom - targetHeight, centerX + targetWidth / 2f, bottom)
    }

    private fun monsterAtScreenPoint(area: RectF, x: Float, y: Float): UnitSprite? = monsters
        .asSequence()
        .filter { it.alive && it.hp > 0 }
        .sortedByDescending { it.drawRow }
        .firstOrNull { unitScreenRect(area, it).contains(x, y) }

    private fun drawAttackImpact(canvas: Canvas, area: RectF) {
        val rect = tileRect(area, impactColumn, impactRow)
        val remaining = ((impactUntil - System.currentTimeMillis()).coerceAtLeast(0L) / 650f).coerceIn(0f, 1f)
        val radius = min(rect.width(), rect.height()) * (.22f + (1f - remaining) * .38f)
        paint.color = ((remaining * 220).toInt() shl 24) or 0x00FFD25A
        paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(4f)
        canvas.drawCircle(rect.centerX(), rect.centerY(), radius, paint)
        canvas.drawLine(rect.centerX() - radius, rect.centerY() + radius * .65f, rect.centerX() + radius, rect.centerY() - radius * .65f, paint)
        paint.style = Paint.Style.FILL
        repeat(6) { index ->
            val direction = if (index % 2 == 0) 1f else -1f
            val x = rect.centerX() + direction * radius * (index + 2) / 7f
            val y = rect.centerY() + (index - 2.5f) * radius / 5f
            canvas.drawCircle(x, y, dp(2.5f), paint)
        }
    }

    private fun drawDefenseMiss(canvas: Canvas, area: RectF) {
        val rect = tileRect(area, player.column, player.row)
        val remaining = ((defenseMissUntil - System.currentTimeMillis()).coerceAtLeast(0L) / 950f).coerceIn(0f, 1f)
        val rise = (1f - remaining) * dp(18f)
        paint.color = ((remaining * 150).toInt() shl 24) or 0x00D8E8FF
        canvas.drawCircle(rect.centerX(), rect.centerY(), min(rect.width(), rect.height()) * .46f, paint)
        textPaint.color = ((remaining * 255).toInt() shl 24) or 0x00FFFFFF
        textPaint.textSize = dp(23f)
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("MISS", rect.centerX(), rect.top - dp(5f) - rise, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT
    }

    private fun drawFireZones(canvas: Canvas, area: RectF) {
        val pulse = if (animationFrame % 2 == 0) 0.94f else 1.02f
        fireZones.forEach { zone ->
            for (column in zone.centerColumn - 1..zone.centerColumn + 1) {
                for (row in zone.centerRow - 1..zone.centerRow + 1) {
                    if (column !in 0 until columns || row !in 0 until rows) continue
                    val tile = tileRect(area, column, row)
                    paint.color = 0x55FF4B16
                    canvas.drawRect(tile, paint)
                    val width = tile.width() * pulse
                    val height = tile.height() * 1.18f * pulse
                    val target = RectF(
                        tile.centerX() - width / 2f,
                        tile.bottom - height,
                        tile.centerX() + width / 2f,
                        tile.bottom
                    )
                    paint.alpha = if ((column + row + animationFrame) % 2 == 0) 220 else 185
                    canvas.drawBitmap(fireEffect, null, target, paint)
                    paint.alpha = 255
                }
            }
        }
    }

    private fun drawProjectile(canvas: Canvas, area: RectF) {
        val shot = projectile ?: return
        val progress = ((System.currentTimeMillis() - shot.startedAt).toFloat() / shot.duration).coerceIn(0f, 1f)
        val from = tileRect(area, shot.fromColumn, shot.fromRow)
        val to = tileRect(area, shot.toColumn, shot.toRow)
        val x = from.centerX() + (to.centerX() - from.centerX()) * progress
        val y = from.centerY() + (to.centerY() - from.centerY()) * progress
        val angle = Math.toDegrees(atan2(to.centerY() - from.centerY(), to.centerX() - from.centerX()).toDouble()).toFloat()
        canvas.save(); canvas.translate(x, y); canvas.rotate(angle)
        if (shot.weaponCode in setOf("crude_bow", "ash_arbalist")) {
            paint.color = 0xFFCFB278.toInt(); paint.strokeWidth = dp(2.2f); paint.style = Paint.Style.STROKE
            canvas.drawLine(-dp(13f), 0f, dp(12f), 0f, paint)
            canvas.drawLine(dp(12f), 0f, dp(6f), -dp(4f), paint); canvas.drawLine(dp(12f), 0f, dp(6f), dp(4f), paint)
            paint.color = 0xFF8B3B2A.toInt(); canvas.drawLine(-dp(12f), 0f, -dp(7f), -dp(4f), paint); canvas.drawLine(-dp(12f), 0f, -dp(7f), dp(4f), paint)
        } else {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(3f); paint.color = 0x66FFC85A
            canvas.drawLine(-dp(20f), 0f, -dp(5f), 0f, paint)
            paint.style = Paint.Style.FILL; paint.color = 0xFFFFD36A.toInt(); canvas.drawOval(RectF(-dp(4f), -dp(2f), dp(6f), dp(2f)), paint)
        }
        paint.style = Paint.Style.FILL; canvas.restore()
    }

    private fun drawEquipmentEffects(canvas: Canvas, area: RectF) {
        val now = System.currentTimeMillis()
        effectAnimations.removeAll { now - it.startedAt >= it.duration }
        effectAnimations.forEach { effect ->
            val progress = ((now - effect.startedAt).toFloat() / effect.duration).coerceIn(0f, 1f)
            val tile = tileRect(area, effect.column, effect.row)
            val pulse = 1f + sin(progress * Math.PI).toFloat() * .28f
            val size = min(tile.width(), tile.height()) * 1.25f * pulse
            val alpha = ((1f - progress) * 255).toInt().coerceIn(0, 255)
            paint.alpha = alpha
            equipmentEffectBitmaps[effect.code]?.let { image ->
                canvas.drawBitmap(image, null, RectF(
                    tile.centerX() - size / 2f, tile.centerY() - size / 2f,
                    tile.centerX() + size / 2f, tile.centerY() + size / 2f
                ), paint)
            }
            paint.alpha = 255
        }
    }

    private fun updateProjectileCamera() {
        val shot = projectile ?: return
        val progress = ((System.currentTimeMillis() - shot.startedAt).toFloat() / shot.duration).coerceIn(0f, 1f)
        val cameraProgress = (progress / .88f).coerceIn(0f, 1f)
        val column = shot.fromColumn + (shot.toColumn - shot.fromColumn) * cameraProgress
        val row = shot.fromRow + (shot.toRow - shot.fromRow) * cameraProgress
        focusCamera(column, row)
    }

    private fun scrollMap(dx: Float, dy: Float) {
        val area = dungeonArea(); cameraColumn = (cameraColumn + dx / (area.width() / visibleColumns)).coerceIn(0f, columns - visibleColumns)
        cameraRow = (cameraRow + dy / (area.height() / visibleRows)).coerceIn(0f, rows - visibleRows); invalidate()
    }
    override fun onDetachedFromWindow() {
        soundPlayer.release()
        super.onDetachedFromWindow()
    }
    private fun playAction(unit: UnitSprite, row: Int, duration: Long, scaleWithCombat: Boolean = true) {
        val actualDuration = if (scaleWithCombat) combatDuration(duration) else duration
        unit.actionRow = row
        unit.actionStartedAt = System.currentTimeMillis()
        unit.actionUntil = unit.actionStartedAt + actualDuration
    }
    private fun combatDuration(duration: Long): Long = (duration / combatSpeed).coerceAtLeast(1L)
    private fun focusCamera(column: Int, row: Int) {
        focusCamera(column.toFloat(), row.toFloat())
    }
    private fun focusCamera(column: Float, row: Float) {
        cameraColumn = (column - visibleColumns / 2f).coerceIn(0f, columns - visibleColumns)
        cameraRow = (row - visibleRows / 2f).coerceIn(0f, rows - visibleRows)
    }
    private fun hasLineOfSight(fromC: Int, fromR: Int, toC: Int, toR: Int): Boolean {
        var c = fromC; var r = fromR
        while (c != toC || r != toR) {
            if (c != toC) c += if (toC > c) 1 else -1 else r += if (toR > r) 1 else -1
            if ((c != toC || r != toR) && obstacles.contains(c to r)) return false
        }
        return true
    }
    private fun inWeaponRange(column: Int, row: Int) = equippedWeapon?.let { weapon ->
        val tileDistance = if (weapon.specialEffect == "ADJACENT_SWEEP") {
            max(abs(player.column - column), abs(player.row - row))
        } else distance(player.column, player.row, column, row)
        val inRange = tileDistance in 1..weapon.range
        inRange && (weapon.specialEffect != "LINE_THRUST" || column == player.column || row == player.row)
    } == true
    private fun isAdjacent(column: Int, row: Int) = distance(player.column, player.row, column, row) == 1
    private fun distance(c1: Int, r1: Int, c2: Int, r2: Int) = abs(c1 - c2) + abs(r1 - r2)
    private fun occupied(column: Int, row: Int) =
        (player.column == column && player.row == row) ||
            monsters.any { it.alive && it.column == column && it.row == row } ||
            healingObjects.any { column to row in occupiedCells(it) }

    private fun persistRun() {
        val root = JSONObject()
            .put("floor", currentFloor).put("turn", turn)
            .put("hp", player.hp).put("playerColumn", player.column).put("playerRow", player.row)
            .put("gold", lootedGold)
            .put("monsterRound", monsterRoundSequence)
            .put("torchFloor", torchEmpoweredFloor ?: JSONObject.NULL)
            .put("villageGold", availableVillageGold).put("greedUsed", greedCoinUsed)
            .put("poison", playerPoisonTurns).put("burn", playerBurnTurns)
            .put("chaliceUsed", saintChaliceUsedThisFloor).put("bellReady", funeralBellPowerReady)
            .put("firstHitUsed", firstHitRelicUsedThisFloor).put("relicKills", relicKillCount)
            .put("furnaceAttacks", furnaceCoreAttackCount)
            .put("inventory", mapJson(inventoryCounts))
            .put("acquired", mapJson(acquiredCounts))
            .put("consumed", mapJson(consumedCounts))
            .put("weapon", equippedWeapon?.code ?: JSONObject.NULL)
            .put("armor", JSONObject(equippedArmorByCategory.filterValues { it != null }.mapValues { it.value }))
            .put("auxiliary", equippedAuxiliary ?: JSONObject.NULL)
            .put("accessory", equippedAccessory ?: JSONObject.NULL)
        root.put("monsters", JSONArray().apply {
            monsters.forEach { monster -> put(JSONObject()
                .put("code", monster.definition?.code).put("column", monster.column).put("row", monster.row)
                .put("hp", monster.hp).put("alive", monster.alive).put("opening", monster.spiderOpeningAttack)
                .put("root", monster.rootTurns).put("burn", monster.burnTurnsRemaining)
                .put("blocked", monster.blockedMoveTurns).put("cooldown", monster.attackCooldown)
                .put("alerted", monster.alerted).put("chestGrade", monster.guaranteedChestGrade ?: JSONObject.NULL)) }
        })
        root.put("obstacles", JSONArray().apply {
            obstacles.forEach { (cell, kind) -> put(JSONObject().put("column", cell.first).put("row", cell.second).put("kind", kind.name)) }
        })
        root.put("healing", JSONArray().apply {
            healingObjects.forEach { obj -> put(JSONObject().put("code", obj.definition.code)
                .put("column", obj.column).put("row", obj.row).put("used", obj.used)) }
        })
        root.put("treasureChests", JSONArray().apply {
            treasureChests.forEach { chest -> put(JSONObject().put("column", chest.column).put("row", chest.row)
                .put("grade", chest.grade).put("mimic", chest.mimic)) }
        })
        root.put("loot", JSONArray().apply {
            lootPiles.forEach { pile -> put(JSONObject().put("column", pile.column).put("row", pile.row)
                .put("gold", pile.gold).put("items", mapJson(pile.items))) }
        })
        root.put("fireZones", JSONArray().apply {
            fireZones.forEach { zone -> put(JSONObject().put("column", zone.centerColumn)
                .put("row", zone.centerRow).put("turns", zone.remainingDamageTurns)) }
        })
        onPersistRun(root.toString())
    }

    private fun mapJson(values: Map<String, Int>) = JSONObject().apply {
        values.forEach { (key, value) -> put(key, value) }
    }

    private fun restoreRun(payload: String?): Boolean = runCatching {
        if (payload.isNullOrBlank()) return false
        val root = JSONObject(payload)
        currentFloor = root.getInt("floor")
        turn = root.getInt("turn")
        player.hp = root.getInt("hp").coerceIn(1, player.maxHp)
        player.column = root.getInt("playerColumn"); player.row = root.getInt("playerRow")
        player.drawColumn = player.column.toFloat(); player.drawRow = player.row.toFloat()
        lootedGold = root.optInt("gold", 0)
        monsterRoundSequence = root.optInt("monsterRound", 0)
        torchEmpoweredFloor = root.optInt("torchFloor", -1).takeIf { it >= 0 }
        availableVillageGold = root.optInt("villageGold", availableVillageGold)
        greedCoinUsed = root.optBoolean("greedUsed")
        playerPoisonTurns = root.optInt("poison"); playerBurnTurns = root.optInt("burn")
        saintChaliceUsedThisFloor = root.optBoolean("chaliceUsed")
        funeralBellPowerReady = root.optBoolean("bellReady")
        firstHitRelicUsedThisFloor = root.optBoolean("firstHitUsed")
        relicKillCount = root.optInt("relicKills"); furnaceCoreAttackCount = root.optInt("furnaceAttacks")
        restoreMap(root.getJSONObject("inventory"), inventoryCounts)
        restoreMap(root.getJSONObject("acquired"), acquiredCounts)
        restoreMap(root.getJSONObject("consumed"), consumedCounts)
        root.optString("weapon").takeIf { it.isNotBlank() && it != "null" }?.let { code ->
            equippedWeapon = weapons.firstOrNull { it.code == code }
            equippedWeapon?.let { player.sheet = weaponSheets.getValue(it.code) }
        }
        root.optJSONObject("armor")?.let { armor ->
            armor.keys().forEach { category -> equippedArmorByCategory[category] = armor.getString(category) }
        }
        equippedAuxiliary = root.optString("auxiliary").takeIf { it.isNotBlank() && it != "null" }
        equippedAccessory = root.optString("accessory").takeIf { it.isNotBlank() && it != "null" }
        monsters.clear()
        val monsterArray = root.getJSONArray("monsters")
        repeat(monsterArray.length()) { index ->
            val data = monsterArray.getJSONObject(index)
            val definition = monsterDefinitions.firstOrNull { it.code == data.getString("code") } ?: return@repeat
            monsters += UnitSprite(definition.name, data.getInt("column"), data.getInt("row"), bitmap(definition.spritePath),
                definition = definition, hp = data.getInt("hp"), maxHp = definition.maxHp,
                alive = data.getBoolean("alive"), spiderOpeningAttack = data.optBoolean("opening", false),
                rootTurns = data.optInt("root"), burnTurnsRemaining = data.optInt("burn"),
                blockedMoveTurns = data.optInt("blocked"), attackCooldown = data.optInt("cooldown"),
                alerted = data.optBoolean("alerted", false),
                guaranteedChestGrade = data.optString("chestGrade").takeIf { it.isNotBlank() && it != "null" })
        }
        obstacles.clear()
        val obstacleArray = root.getJSONArray("obstacles")
        repeat(obstacleArray.length()) { index -> obstacleArray.getJSONObject(index).let {
            obstacles[it.getInt("column") to it.getInt("row")] = ObstacleKind.valueOf(it.getString("kind"))
        } }
        healingObjects.clear()
        val healingArray = root.optJSONArray("healing") ?: JSONArray()
        repeat(healingArray.length()) { index -> healingArray.getJSONObject(index).let { data ->
            interactableByCode[data.getString("code")]?.let { definition ->
                healingObjects += HealingObject(definition, data.getInt("column"), data.getInt("row"), data.optBoolean("used"))
            }
        } }
        treasureChests.clear()
        val chestArray = root.optJSONArray("treasureChests") ?: JSONArray()
        repeat(chestArray.length()) { index -> chestArray.getJSONObject(index).let { data ->
            treasureChests += TreasureChest(data.getInt("column"), data.getInt("row"), data.getString("grade"), data.getBoolean("mimic"))
        } }
        lootPiles.clear()
        val lootArray = root.optJSONArray("loot") ?: JSONArray()
        repeat(lootArray.length()) { index -> lootArray.getJSONObject(index).let { data ->
            val items = linkedMapOf<String, Int>(); restoreMap(data.getJSONObject("items"), items)
            lootPiles += LootPile(data.getInt("column"), data.getInt("row"), data.getInt("gold"), items)
        } }
        fireZones.clear()
        val fireArray = root.optJSONArray("fireZones") ?: JSONArray()
        repeat(fireArray.length()) { index -> fireArray.getJSONObject(index).let { data ->
            fireZones += FireZone(data.getInt("column"), data.getInt("row"), data.getInt("turns"))
        } }
        focusCamera(player.column, player.row)
        phase = Phase.PLAYER
        message = "저장된 지하 ${currentFloor}층 탐사를 이어갑니다"
        true
    }.getOrDefault(false)

    private fun restoreMap(json: JSONObject, target: MutableMap<String, Int>) {
        target.clear()
        json.keys().forEach { key -> target[key] = json.getInt(key) }
    }
    private fun itemCount(code: String) = inventoryCounts[code] ?: 0
    private fun inventoryEntry(index: Int): Pair<String, Int>? = inventoryCounts.entries.elementAtOrNull(index)?.let { it.key to it.value }
    private fun inventorySlotRects(): List<RectF> {
        val gap = dp(4f)
        val slotSize = dp(36f)
        val startX = width * .64f + dp(18f)
        val gridHeight = slotSize * 5f + gap * 4f
        val top = height - dp(118f) - dp(54f) - gridHeight
        return List(inventoryCapacity) { index ->
            val column = index % 5; val row = index / 5
            RectF(
                startX + column * (slotSize + gap),
                top + row * (slotSize + gap),
                startX + column * (slotSize + gap) + slotSize,
                top + row * (slotSize + gap) + slotSize
            )
        }
    }
    private fun equipmentSlotRects(): List<RectF> {
        val inventoryRects = inventorySlotRects()
        val slotSize = dp(40f)
        val gap = dp(6f)
        val startX = inventoryRects[4].right + dp(18f)
        val startY = inventoryRects.first().top
        return List(6) { index ->
            val column = index % 2; val row = index / 2
            val left = startX + column * (slotSize + gap)
            val top = startY + row * (slotSize + gap)
            RectF(left, top, left + slotSize, top + slotSize)
        }
    }
    private fun itemAssetPath(code: String): String? = itemByCode[code]?.assetPath
    private fun itemGradeColor(code: String): Int = when (itemByCode[code]?.grade) {
        "HIGH" -> 0xFF65D57A.toInt(); "RARE" -> 0xFF61AFFF.toInt(); "EPIC" -> 0xFFC36BFF.toInt()
        "UNIQUE" -> 0xFFFFAD45.toInt(); "LEGENDARY" -> 0xFFFF5959.toInt(); "MYTHIC" -> 0xFFFFE586.toInt()
        else -> 0xFF866837.toInt()
    }
    private fun activeBackground(): Bitmap = when (currentFloor) {
        in 1..5 -> upperDungeonBackground
        in 6..10 -> floodedCatacombBackground
        else -> ashenFurnaceBackground
    }
    private fun isConsumable(code: String) = itemByCode[code]?.isConsumable == true
    private fun dungeonArea() = RectF(width * .025f, dp(62f), width * .615f, height - dp(116f))
    private fun tileRect(area: RectF, column: Int, row: Int): RectF {
        val tw = area.width() / visibleColumns; val th = area.height() / visibleRows
        return RectF(area.left + (column - cameraColumn) * tw, area.top + (row - cameraRow) * th, area.left + (column + 1 - cameraColumn) * tw, area.top + (row + 1 - cameraRow) * th)
    }
    private fun drawTileFill(canvas: Canvas, rect: RectF, color: Int) { paint.color = color; paint.style = Paint.Style.FILL; canvas.drawRect(rect, paint) }
    private fun bitmap(path: String): Bitmap = context.assets.open(path).use(BitmapFactory::decodeStream)
    private fun dp(value: Float) = value * resources.displayMetrics.density
}
