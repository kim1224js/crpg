package es.kim.crpg.ui.dungeon

import android.app.AlertDialog
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
import android.view.MotionEvent
import android.view.View
import es.kim.crpg.data.ItemDefinitionEntity
import es.kim.crpg.data.MonsterDefinitionEntity
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class DungeonDemoView(
    context: Context,
    initialInventoryCounts: Map<String, Int> = emptyMap(),
    equippedWeaponCode: String? = null,
    itemDefinitions: List<ItemDefinitionEntity> = emptyList(),
    private val monsterDefinitions: List<MonsterDefinitionEntity> = emptyList(),
    playerBaseHp: Int = 10,
    private val inventoryCapacity: Int = 10,
    private val onUseReturnStone: (Map<String, Int>, Int) -> Unit = { _, _ -> },
    private val onExitDungeon: (Map<String, Int>, Int) -> Unit = { _, _ -> },
    private val onPlayerDeath: (Int, Int, String?, String?) -> Unit = { _, _, _, _ -> }
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
        var burnTurnsRemaining: Int = 0, var burnAppliedRound: Int = -1
    )

    private data class LootPile(val column: Int, val row: Int, var gold: Int, val items: MutableMap<String, Int>)
    private data class FireZone(val centerColumn: Int, val centerRow: Int, var remainingDamageTurns: Int = 2)
    private data class Projectile(
        val weaponCode: String, val fromColumn: Int, val fromRow: Int,
        val toColumn: Int, val toRow: Int, val startedAt: Long, val duration: Long
    )

    private data class Weapon(
        val code: String, val name: String, val range: Int, val damage: Int, val turnCost: Int,
        val sheetPath: String, val iconPath: String, val specialEffect: String?
    )

    private val background = bitmap("ui/dungeon/concepts/dungeon_gameplay_grid_concept.png")
    private val fireEffect = bitmap("ui/dungeon/effects/fire_ground_vfx.png")
    private val obstacleBitmaps = ObstacleKind.entries.associateWith {
        bitmap("ui/dungeon/terrain/${it.assetName}.png")
    }
    private val itemByCode = itemDefinitions.associateBy { it.code }
    private val weapons = itemDefinitions.filter { it.category == "WEAPON" }.map {
        Weapon(it.code, it.name, it.attackRange, it.attackPower, it.attackTurnCost, it.playerSheetPath ?: "ui/dungeon/player/player_base_animation_sheet.png", it.assetPath, it.specialEffect)
    }
    private val dropItemCodes = itemDefinitions.filter { it.dropRate > 0.0 }.map { it.code }
    private val inventoryCounts = initialInventoryCounts.toMutableMap()
    private val acquiredCounts = linkedMapOf<String, Int>()
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
    private val monsterPositions = mapOf("spider" to (11 to 3), "wild_dog" to (15 to 8), "bandit" to (18 to 4), "slime" to (8 to 9))
    private val monsters = createFloorMonsters()
    private val columns = 24
    private val rows = 12
    private val visibleColumns = 8f
    private val visibleRows = 7f
    private val obstacles = linkedMapOf<Pair<Int, Int>, ObstacleKind>()
    private val floorObstacleSeed = Random.nextLong()
    private var cameraColumn = 0f
    private var cameraRow = 4f
    private var phase = Phase.PLAYER
    private var focusedMonster: UnitSprite? = null
    private var turn = 1
    private var currentFloor = 1
    private val stairsColumn = 22
    private val stairsRow = 1
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
    private var projectile: Projectile? = null
    private val inventoryIcons = (inventoryCounts.keys + dropItemCodes).distinct().mapNotNull { code ->
        itemAssetPath(code)?.let { code to bitmap(it) }
    }.toMap()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; setShadowLayer(dp(2f), 0f, dp(1f), Color.BLACK)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x9AFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = dp(1.1f)
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(dp(5f), dp(5f)), 0f)
    }

    init { createObstacles(); isClickable = true }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawBitmap(background, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), paint)
        canvas.drawColor(0x43000000)
        drawBattlefield(canvas); drawFloorHeader(canvas); drawSidePanel(canvas); drawHud(canvas)
        val now = System.currentTimeMillis()
        if (now - lastFrameAt >= 180L) { animationFrame = (animationFrame + 1) % 4; lastFrameAt = now }
        postInvalidateDelayed(50L)
    }

    private fun drawBattlefield(canvas: Canvas) {
        val area = dungeonArea()
        canvas.save(); canvas.clipRect(area)
        paint.color = 0x9A080B0E.toInt(); canvas.drawRect(area, paint)
        for (column in floor(cameraColumn).toInt()..min(columns - 1, (cameraColumn + visibleColumns).toInt())) {
            for (row in floor(cameraRow).toInt()..min(rows - 1, (cameraRow + visibleRows).toInt())) {
                val rect = tileRect(area, column, row)
                when {
                    phase == Phase.PLAYER && inWeaponRange(column, row) && obstacles.contains(column to row) -> drawTileFill(canvas, rect, 0x70B52A25)
                    obstacles.contains(column to row) -> drawTileFill(canvas, rect, 0x25191412)
                    phase == Phase.PLAYER && isAdjacent(column, row) && !occupied(column, row) -> drawTileFill(canvas, rect, 0x653A91D8)
                    phase == Phase.PLAYER && inWeaponRange(column, row) -> drawTileFill(canvas, rect, if (hasLineOfSight(player.column, player.row, column, row)) 0x66E9B62F else 0x70B52A25)
                }
                canvas.drawRect(rect, gridPaint)
            }
        }
        drawFlatObstacles(canvas, area)
        drawStairs(canvas, area)
        lootPiles.forEach { drawLootPile(canvas, area, it) }
        drawFireZones(canvas, area)
        drawDepthSortedScene(canvas, area)
        drawProjectile(canvas, area)
        if (System.currentTimeMillis() < impactUntil) drawAttackImpact(canvas, area)
        focusedMonster?.takeIf { phase == Phase.MONSTERS }?.let {
            paint.color = 0xFFE8BD55.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(3f)
            canvas.drawRoundRect(tileRect(area, it.column, it.row), dp(7f), dp(7f), paint); paint.style = Paint.Style.FILL
        }
        canvas.restore()
        paint.color = 0xFFD8C28C.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(2f)
        canvas.drawRect(area, paint); paint.style = Paint.Style.FILL
    }

    private fun drawSidePanel(canvas: Canvas) {
        val left = width * 0.64f
        paint.color = 0xD9120D0A.toInt(); canvas.drawRoundRect(left, dp(70f), width - dp(18f), height - dp(118f), dp(12f), dp(12f), paint)
        textPaint.textSize = dp(14f); textPaint.color = Color.WHITE
        canvas.drawText(if (phase == Phase.PLAYER) "플레이어 행동" else "${focusedMonster?.name ?: "몬스터"} 행동 관찰 중", left + dp(18f), dp(105f), textPaint)
        canvas.drawText("지도를 드래그하여 전체 탐색", left + dp(18f), dp(132f), textPaint)
        var y = dp(170f)
        monsters.forEach {
            textPaint.color = if (it.alive) Color.WHITE else 0xFF777777.toInt()
            val burnText = if (it.alive && it.burnTurnsRemaining > 0) " · 화상 ${it.burnTurnsRemaining}턴" else ""
            canvas.drawText("${it.name}   ${if (it.alive) "HP ${it.hp}/${it.maxHp}$burnText" else "처치"}", left + dp(20f), y, textPaint); y += dp(28f)
        }
        textPaint.color = 0xFFFFD47A.toInt(); textPaint.textSize = dp(13f)
        monsters.forEachIndexed { index, monster ->
            val data = monster.definition ?: return@forEachIndexed
            canvas.drawText("${data.name}: 공격 ${data.attackPower} · 사거리 ${data.attackRange} · 이동 ${data.moveDistance}", left + dp(18f), y + dp(14f + index * 24f), textPaint)
        }
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

        inventorySlotRects(top).forEachIndexed { index, rect ->
            paint.color = 0xFF211710.toInt(); canvas.drawRoundRect(rect, dp(5f), dp(5f), paint)
            paint.color = 0xFF9C7538.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(2f); canvas.drawRoundRect(rect, dp(5f), dp(5f), paint); paint.style = Paint.Style.FILL
            inventoryEntry(index)?.let { (code, quantity) ->
                inventoryIcons[code]?.let { icon -> canvas.drawBitmap(icon, null, RectF(rect.left + dp(4f), rect.top + dp(4f), rect.right - dp(4f), rect.bottom - dp(4f)), paint) }
                if (isConsumable(code)) {
                    textPaint.color = Color.WHITE; textPaint.textSize = dp(13f); textPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(quantity.toString(), rect.right - dp(4f), rect.bottom - dp(4f), textPaint); textPaint.textAlign = Paint.Align.LEFT
                }
            }
        }
        val equippedText = equippedWeapon?.let { "착용: ${it.name} · 공격 ${it.damage} · 사거리 ${it.range}" } ?: "착용 무기 없음"
        textPaint.color = 0xFFFFD786.toInt(); textPaint.textSize = dp(12f); canvas.drawText(equippedText, dp(170f), top + dp(111f), textPaint)

        val dayX = width - dp(75f)
        paint.color = 0xFFB48A42.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(3f); canvas.drawCircle(dayX, orbY, dp(25f), paint); paint.style = Paint.Style.FILL
        textPaint.color = 0xFFFFD786.toInt(); textPaint.textSize = dp(22f); textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("1", dayX, orbY + dp(7f), textPaint); textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = dp(11f); canvas.drawText("생존 일", dayX - dp(21f), orbY + dp(43f), textPaint)
        textPaint.textSize = dp(13f); canvas.drawText("전리품 ${lootedGold}G", dayX - dp(34f), orbY - dp(36f), textPaint)
        textPaint.color = Color.WHITE; textPaint.textSize = dp(12f); canvas.drawText(message, width * .64f, top - dp(8f), textPaint)
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
        inventorySlotRects(height - dp(116f)).forEachIndexed { index, rect ->
            if (rect.contains(x, y)) {
                when (inventoryEntry(index)?.first) {
                    "return_stone" -> if (phase == Phase.PLAYER && itemCount("return_stone") > 0) {
                        onUseReturnStone(acquiredCounts.toMap(), lootedGold)
                    }
                    "fire_bomb" -> if (phase == Phase.PLAYER && itemCount("fire_bomb") > 0) {
                        selectingFireBombTarget = true
                        message = "화염병을 던질 중심 타일을 선택하세요 · 3×3 범위"
                        invalidate()
                    }
                    else -> inventoryEntry(index)?.first?.let { code ->
                        if (itemByCode[code]?.category == "WEAPON") switchEquippedWeapon(code)
                    }
                }
                return
            }
        }
        if (phase != Phase.PLAYER) { message = "몬스터 행동이 끝날 때까지 기다리세요"; return }
        val area = dungeonArea(); if (!area.contains(x, y)) return
        val column = floor(cameraColumn + (x - area.left) / (area.width() / visibleColumns)).toInt().coerceIn(0, columns - 1)
        val row = floor(cameraRow + (y - area.top) / (area.height() / visibleRows)).toInt().coerceIn(0, rows - 1)
        if (selectingFireBombTarget) { throwFireBomb(column, row); return }
        if (column == stairsColumn && row == stairsRow) { tryDescendFloor(); return }
        lootPiles.firstOrNull { it.column == column && it.row == row }?.let { collectLoot(it); return }
        monsters.firstOrNull { it.alive && it.column == column && it.row == row }?.let { attackMonster(it); return }
        when {
            obstacles.contains(column to row) -> message = "장애물 때문에 이동할 수 없습니다"
            isAdjacent(column, row) && !occupied(column, row) -> {
                player.column = column; player.row = row; player.drawColumn = column.toFloat(); player.drawRow = row.toFloat()
                phase = Phase.MONSTERS; playAction(player, 1, 520L); postDelayed({ beginMonsterTurns(1) }, 520L)
            }
            else -> message = "파란색 인접 타일만 이동할 수 있습니다"
        }
        invalidate()
    }

    private fun tryDescendFloor() {
        if (distance(player.column, player.row, stairsColumn, stairsRow) != 1) {
            message = "아래층 계단 바로 앞에서 진입할 수 있습니다"; invalidate(); return
        }
        if (monsters.any { it.alive }) {
            message = "층의 몬스터를 모두 처치해야 계단이 열립니다"; invalidate(); return
        }
        if (currentFloor >= 5) {
            showFloorChoice(canDescend = false); return
        }
        showFloorChoice(canDescend = true)
    }

    private fun showFloorChoice(canDescend: Boolean) {
        phase = Phase.MONSTERS
        val builder = AlertDialog.Builder(context)
            .setTitle("지하 ${currentFloor}층 탐험 완료")
            .setMessage(
                if (canDescend) "현재 전리품을 가지고 마을로 나가시겠습니까?\n아니면 지하 ${currentFloor + 1}층을 계속 탐험하시겠습니까?"
                else "지하 5층까지 탐험했습니다. 현재 전리품을 가지고 마을로 돌아갈 수 있습니다."
            )
            .setNegativeButton("마을로 나가기") { _, _ -> onExitDungeon(acquiredCounts.toMap(), lootedGold) }
            .setNeutralButton("머무르기") { _, _ -> phase = Phase.PLAYER; invalidate() }
            .setOnCancelListener { phase = Phase.PLAYER; invalidate() }
        if (canDescend) {
            builder.setPositiveButton("다음 층 탐험") { _, _ ->
                message = "지하 ${currentFloor + 1}층으로 내려갑니다"
                postDelayed({ enterNextFloor() }, 500L)
            }
        }
        builder.show()
    }

    private fun enterNextFloor() {
        currentFloor++
        monsters.clear(); monsters.addAll(createFloorMonsters())
        obstacles.clear(); lootPiles.clear(); fireZones.clear()
        player.column = 2; player.row = 8; player.drawColumn = 2f; player.drawRow = 8f
        focusedMonster = null; pendingMonsterRounds = 0
        createObstacles(); focusCamera(player.column, player.row)
        phase = Phase.PLAYER
        message = "지하 ${currentFloor}층에 진입했습니다"
        invalidate()
    }

    private fun throwFireBomb(column: Int, row: Int) {
        selectingFireBombTarget = false
        consumeInventoryItem("fire_bomb")
        fireZones += FireZone(column, row)
        phase = Phase.MONSTERS
        playAction(player, 2, 620L)
        message = "화염병 투척 · 3×3 지역이 2턴 동안 불타오릅니다"
        postDelayed({ beginMonsterTurns(1) }, 620L)
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
        player.sheet = weaponSheets.getValue(weapon.code)
        phase = Phase.MONSTERS
        playAction(player, 0, 520L)
        message = "${weapon.name}으로 교체 · 1행동 소모"
        postDelayed({ beginMonsterTurns(1) }, 520L)
        invalidate()
    }

    private fun consumeInventoryItem(code: String) {
        val remaining = itemCount(code) - 1
        if (remaining > 0) inventoryCounts[code] = remaining else inventoryCounts.remove(code)
        acquiredCounts[code] = (acquiredCounts[code] ?: 0) - 1
        if (acquiredCounts[code] == 0) acquiredCounts.remove(code)
    }

    private fun attackMonster(monster: UnitSprite) {
        val weapon = equippedWeapon ?: run { message = "착용한 무기가 없어 공격할 수 없습니다"; invalidate(); return }
        val distance = distance(player.column, player.row, monster.column, monster.row)
        if (distance > weapon.range) { message = "${weapon.name} 사거리 밖입니다"; return }
        if (!hasLineOfSight(player.column, player.row, monster.column, monster.row)) { message = "장애물에 공격 경로가 막혔습니다"; return }
        val direction = alignedDirection(monster)
        if (weapon.specialEffect == "LINE_THRUST" && direction == null) {
            message = "창은 상하좌우 일직선으로만 찌를 수 있습니다"; invalidate(); return
        }
        val targets = when {
            weapon.specialEffect == "LINE_THRUST" -> lineTargets(direction!!, weapon.range)
            weapon.specialEffect == "KILL_PIERCE" && direction != null -> lineTargets(direction, weapon.range)
            else -> listOf(monster)
        }
        if (targets.isEmpty()) return
        phase = Phase.MONSTERS; playAction(player, 2, 760L)
        soundPlayer.playWeaponAttack(weapon.code)
        val travelDuration = if (weapon.code == "crude_bow" || weapon.code == "crude_gun") 520L else 240L
        if (weapon.code == "crude_bow" || weapon.code == "crude_gun") {
            launchProjectile(weapon.code, player.column, player.row, targets.first(), travelDuration)
        }
        postDelayed({
            when (weapon.specialEffect) {
                "DOUBLE_HIT" -> resolveDoubleHit(targets.first(), weapon)
                "LINE_THRUST" -> resolveLineThrust(targets, weapon)
                "KILL_PIERCE" -> resolveGunPierce(targets, 0, weapon)
                else -> finishWeaponAttack(weapon, applyWeaponHit(targets.first(), weapon))
            }
        }, travelDuration)
    }

    private fun resolveDoubleHit(monster: UnitSprite, weapon: Weapon) {
        val firstKilled = applyWeaponHit(monster, weapon)
        if (firstKilled) { finishWeaponAttack(weapon, true); return }
        postDelayed({
            playAction(player, 2, 620L)
            soundPlayer.playWeaponAttack(weapon.code)
            val secondKilled = applyWeaponHit(monster, weapon)
            message = if (secondKilled) "2연타 · ${monster.name} 처치" else "${monster.name}에게 2연타 · 총 ${weapon.damage * 2} 피해"
            finishWeaponAttack(weapon, secondKilled)
        }, 330L)
    }

    private fun resolveLineThrust(targets: List<UnitSprite>, weapon: Weapon) {
        var killedAny = false
        targets.forEachIndexed { index, target ->
            postDelayed({
                killedAny = applyWeaponHit(target, weapon) || killedAny
                if (index == targets.lastIndex) {
                    message = "직선 찌르기 · ${targets.size}마리 타격"
                    finishWeaponAttack(weapon, killedAny)
                }
            }, index * 110L)
        }
    }

    private fun resolveGunPierce(targets: List<UnitSprite>, index: Int, weapon: Weapon) {
        val target = targets[index]
        val killed = applyWeaponHit(target, weapon)
        val next = targets.getOrNull(index + 1)
        if (killed && next != null) {
            message = "${target.name} 처치 · 탄환 관통"
            launchProjectile(weapon.code, target.column, target.row, next, 220L)
            postDelayed({ resolveGunPierce(targets, index + 1, weapon) }, 220L)
        } else {
            finishWeaponAttack(weapon, killed)
        }
    }

    private fun applyWeaponHit(monster: UnitSprite, weapon: Weapon): Boolean {
        projectile = null
        impactColumn = monster.column; impactRow = monster.row; impactUntil = System.currentTimeMillis() + 650L
        soundPlayer.playWeaponImpact(weapon.code)
        monster.hp -= weapon.damage
        return if (monster.hp <= 0) {
            soundPlayer.playDeath(monster.definition?.code)
            monster.hp = 0; monster.dying = true; playAction(monster, 3, 1050L); message = "${monster.name} 처치"
            createLoot(monster)
            postDelayed({ monster.alive = false; monster.dying = false; invalidate() }, 1050L)
            true
        } else {
            soundPlayer.playHit(monster.definition?.code)
            message = "${monster.name}에게 ${weapon.damage} 피해"
            false
        }
    }

    private fun finishWeaponAttack(weapon: Weapon, killedAny: Boolean) {
        postDelayed({ beginMonsterTurns(weapon.turnCost) }, if (killedAny) 1050L else 800L)
        invalidate()
    }

    private fun launchProjectile(code: String, fromColumn: Int, fromRow: Int, target: UnitSprite, duration: Long) {
        projectile = Projectile(code, fromColumn, fromRow, target.column, target.row, System.currentTimeMillis(), duration)
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
        val gold = if (Random.nextDouble() < definition.goldDropRate) definition.goldDrop else 0
        val items = linkedMapOf<String, Int>()
        dropItemCodes.forEach { code -> if (Random.nextDouble() < (itemByCode[code]?.dropRate ?: 0.0)) items[code] = 1 }
        if (gold > 0 || items.isNotEmpty()) lootPiles += LootPile(monster.column, monster.row, gold, items)
    }

    private fun collectLoot(pile: LootPile) {
        if (distance(player.column, player.row, pile.column, pile.row) != 1) {
            message = "전리품 바로 앞 칸에서 습득할 수 있습니다"
            invalidate(); return
        }
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
        postDelayed({ beginMonsterTurns(1) }, 300L)
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
        runMonsterRound(0)
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
            if (zone.remainingDamageTurns == 0) postDelayed({ fireZones.remove(zone); invalidate() }, 700L)
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
        monster.hp = max(0, monster.hp - damage)
        impactColumn = monster.column; impactRow = monster.row; impactUntil = System.currentTimeMillis() + 520L
        if (monster.hp == 0) {
            soundPlayer.playDeath(monster.definition?.code)
            monster.dying = true; playAction(monster, 3, 1050L); createLoot(monster)
            postDelayed({ monster.alive = false; monster.dying = false; invalidate() }, 1050L)
        } else {
            soundPlayer.playHit(monster.definition?.code)
            playAction(monster, 2, 480L)
        }
    }

    private fun runMonsterRound(index: Int) {
        val living = monsters.filter { it.alive && it.hp > 0 }
        if (index >= living.size) {
            pendingMonsterRounds--
            if (pendingMonsterRounds > 0) { postDelayed({ startMonsterRound() }, 350L); return }
            focusedMonster = null; phase = Phase.PLAYER; turn++; focusCamera(player.column, player.row)
            message = if (player.hp > 0) "플레이어 행동 차례" else "플레이어가 쓰러졌습니다"; invalidate(); return
        }
        val monster = living[index]; focusedMonster = monster; focusCamera(monster.column, monster.row); message = "${monster.name}의 행동"; invalidate()
        postDelayed({
            val actionDuration = performMonsterAction(monster)
            invalidate()
            postDelayed({
                if (player.hp <= 0) finishPlayerDeath() else runMonsterRound(index + 1)
            }, actionDuration)
        }, 420L)
    }

    private fun performMonsterAction(monster: UnitSprite): Long {
        val dist = distance(monster.column, monster.row, player.column, player.row)
        val definition = monster.definition ?: return 0L
        val attackRange = if (monster.spiderOpeningAttack) definition.openingAttackRange else definition.attackRange
        if (dist <= attackRange && hasLineOfSight(monster.column, monster.row, player.column, player.row)) {
            val damage = definition.attackPower
            soundPlayer.playAttack(definition.code)
            playAction(monster, 2, 760L)
            impactColumn = player.column; impactRow = player.row; impactUntil = System.currentTimeMillis() + 760L
            player.hp = max(0, player.hp - damage); if (monster.kind == MonsterKind.SPIDER) monster.spiderOpeningAttack = false
            if (player.hp == 0) {
                deathCause = monster
                player.dying = true
                playAction(player, 3, 1150L)
            }
            message = "${monster.name} 공격 · 피해 $damage"; return if (player.hp == 0) 1200L else 820L
        }
        if (definition.moveEveryTurns > 1 && turn % definition.moveEveryTurns != 0) { message = "${monster.name}이 몸을 웅크립니다"; return 520L }
        repeat(definition.moveDistance) {
            nextStep(monster)?.let { next ->
                monster.column = next.first; monster.row = next.second; monster.drawColumn = next.first.toFloat(); monster.drawRow = next.second.toFloat()
                playAction(monster, 1, 560L); focusCamera(monster.column, monster.row)
            }
        }
        message = "${monster.name} 이동"
        return 620L
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

    private fun nextStep(monster: UnitSprite): Pair<Int, Int>? {
        val candidates = mutableListOf<Pair<Int, Int>>()
        fun horizontal() { if (player.column != monster.column) candidates += (monster.column + if (player.column > monster.column) 1 else -1) to monster.row }
        fun vertical() { if (player.row != monster.row) candidates += monster.column to (monster.row + if (player.row > monster.row) 1 else -1) }
        if (abs(player.column - monster.column) >= abs(player.row - monster.row)) { horizontal(); vertical() } else { vertical(); horizontal() }
        return candidates.firstOrNull { (c, r) -> c in 0 until columns && r in 0 until rows && !obstacles.contains(c to r) && !occupied(c, r) }
    }

    private fun createFloorMonsters(): MutableList<UnitSprite> = monsterDefinitions.mapNotNull { definition ->
        monsterPositions[definition.code]?.let { position ->
            UnitSprite(
                definition.name, position.first, position.second, bitmap(definition.spritePath),
                kind = when (definition.code) {
                    "spider" -> MonsterKind.SPIDER; "bandit" -> MonsterKind.BANDIT
                    "wild_dog" -> MonsterKind.WILD_DOG; else -> MonsterKind.SLIME
                },
                definition = definition, hp = definition.maxHp, maxHp = definition.maxHp
            )
        }
    }.toMutableList()

    private fun createObstacles() {
        val reserved = mutableSetOf(player.column to player.row, stairsColumn to stairsRow).apply {
            addAll(monsters.map { it.column to it.row })
            addAll(listOf(stairsColumn - 1 to stairsRow, stairsColumn + 1 to stairsRow, stairsColumn to stairsRow - 1, stairsColumn to stairsRow + 1))
            addAll(listOf(player.column - 1 to player.row, player.column + 1 to player.row, player.column to player.row - 1, player.column to player.row + 1))
        }
        val random = Random(floorObstacleSeed)
        while (obstacles.size < 34) {
            val cell = random.nextInt(columns) to random.nextInt(rows)
            if (cell !in reserved && distance(cell.first, cell.second, player.column, player.row) > 2) {
                obstacles[cell] = ObstacleKind.entries[random.nextInt(ObstacleKind.entries.size)]
            }
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
        paint.color = 0x77000000
        canvas.drawOval(
            RectF(rect.centerX() - width * .34f, bottom - rect.height() * .2f, rect.centerX() + width * .34f, bottom + rect.height() * .08f),
            paint
        )
        paint.setShadowLayer(dp(7f), dp(3f), dp(7f), 0xC0000000.toInt())
        canvas.drawBitmap(
            obstacleBitmaps.getValue(kind), null,
            RectF(rect.centerX() - width / 2f, bottom - height, rect.centerX() + width / 2f, bottom), paint
        )
        paint.clearShadowLayer()
    }

    private fun drawLootPile(canvas: Canvas, area: RectF, pile: LootPile) {
        val rect = tileRect(area, pile.column, pile.row)
        if (!RectF.intersects(rect, area)) return
        paint.color = 0x5548D66A
        canvas.drawCircle(rect.centerX(), rect.centerY(), min(rect.width(), rect.height()) * .36f, paint)
        if (pile.items.isNotEmpty()) {
            val code = pile.items.keys.first()
            inventoryIcons[code]?.let { icon ->
                val size = min(rect.width(), rect.height()) * .58f
                canvas.drawBitmap(icon, null, RectF(rect.centerX() - size / 2, rect.centerY() - size / 2, rect.centerX() + size / 2, rect.centerY() + size / 2), paint)
            }
        }
        if (pile.gold > 0) {
            paint.color = 0xFFFFCF4D.toInt()
            canvas.drawCircle(rect.right - rect.width() * .25f, rect.bottom - rect.height() * .25f, dp(8f), paint)
            textPaint.color = 0xFF2A1905.toInt(); textPaint.textSize = dp(10f); textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(pile.gold.toString(), rect.right - rect.width() * .25f, rect.bottom - rect.height() * .25f + dp(3f), textPaint)
            textPaint.textAlign = Paint.Align.LEFT
        }
        if (pile.items.size > 1) {
            textPaint.color = Color.WHITE; textPaint.textSize = dp(12f); textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("+${pile.items.size - 1}", rect.right - dp(5f), rect.top + dp(15f), textPaint); textPaint.textAlign = Paint.Align.LEFT
        }
    }

    private fun drawStairs(canvas: Canvas, area: RectF) {
        val rect = tileRect(area, stairsColumn, stairsRow)
        if (!RectF.intersects(rect, area)) return
        val unlocked = monsters.none { it.alive }
        paint.color = if (unlocked) 0x664ED879 else 0x665E2420
        canvas.drawRect(rect, paint)
        val inset = min(rect.width(), rect.height()) * .16f
        repeat(4) { step ->
            val offset = inset * step * .42f
            paint.color = if (unlocked) 0xFFD0A653.toInt() else 0xFF63594E.toInt()
            canvas.drawRect(rect.left + inset + offset, rect.top + inset + offset, rect.right - inset, rect.top + inset + offset + dp(5f), paint)
        }
        textPaint.color = Color.WHITE; textPaint.textSize = dp(11f); textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(if (unlocked) "아래층" else "봉인", rect.centerX(), rect.bottom - dp(7f), textPaint); textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawUnit(canvas: Canvas, area: RectF, unit: UnitSprite) {
        val frameWidth = unit.sheet.width / 4; val frameHeight = unit.sheet.height / 4
        val now = System.currentTimeMillis()
        val actionPlaying = now < unit.actionUntil
        val row = if (actionPlaying) unit.actionRow else 0
        val frame = when {
            unit.actionUntil == Long.MAX_VALUE -> 3
            actionPlaying -> (((now - unit.actionStartedAt).coerceAtLeast(0L) / 170L).toInt()).coerceIn(0, 3)
            else -> animationFrame
        }
        val source = Rect(frame * frameWidth, row * frameHeight, (frame + 1) * frameWidth, (row + 1) * frameHeight)
        val tw = area.width() / visibleColumns; val th = area.height() / visibleRows
        val centerX = area.left + (unit.drawColumn - cameraColumn + .5f) * tw
        val bottom = area.top + (unit.drawRow - cameraRow + 1f) * th + th * .1f
        val targetHeight = th * if (unit === player) 1.45f else 1.16f; val targetWidth = targetHeight * frameWidth / frameHeight
        canvas.drawBitmap(unit.sheet, source, RectF(centerX - targetWidth / 2, bottom - targetHeight, centerX + targetWidth / 2, bottom), paint)
        if (unit !== player) {
            paint.color = 0xCC17110E.toInt(); canvas.drawRect(centerX - tw * .3f, bottom - targetHeight - dp(7f), centerX + tw * .3f, bottom - targetHeight - dp(2f), paint)
            paint.color = 0xFFB83A32.toInt(); canvas.drawRect(centerX - tw * .3f, bottom - targetHeight - dp(7f), centerX - tw * .3f + tw * .6f * unit.hp / unit.maxHp, bottom - targetHeight - dp(2f), paint)
        }
    }

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
        if (shot.weaponCode == "crude_bow") {
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

    private fun scrollMap(dx: Float, dy: Float) {
        val area = dungeonArea(); cameraColumn = (cameraColumn + dx / (area.width() / visibleColumns)).coerceIn(0f, columns - visibleColumns)
        cameraRow = (cameraRow + dy / (area.height() / visibleRows)).coerceIn(0f, rows - visibleRows); invalidate()
    }
    override fun onDetachedFromWindow() {
        soundPlayer.release()
        super.onDetachedFromWindow()
    }
    private fun playAction(unit: UnitSprite, row: Int, duration: Long) {
        unit.actionRow = row
        unit.actionStartedAt = System.currentTimeMillis()
        unit.actionUntil = unit.actionStartedAt + duration
    }
    private fun focusCamera(column: Int, row: Int) {
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
        val inRange = distance(player.column, player.row, column, row) in 1..weapon.range
        inRange && (weapon.specialEffect != "LINE_THRUST" || column == player.column || row == player.row)
    } == true
    private fun isAdjacent(column: Int, row: Int) = distance(player.column, player.row, column, row) == 1
    private fun distance(c1: Int, r1: Int, c2: Int, r2: Int) = abs(c1 - c2) + abs(r1 - r2)
    private fun occupied(column: Int, row: Int) = (player.column == column && player.row == row) || monsters.any { it.alive && it.column == column && it.row == row }
    private fun itemCount(code: String) = inventoryCounts[code] ?: 0
    private fun inventoryEntry(index: Int): Pair<String, Int>? = inventoryCounts.entries.elementAtOrNull(index)?.let { it.key to it.value }
    private fun inventorySlotRects(top: Float): List<RectF> {
        val slotWidth = dp(56f); val slotHeight = dp(43f); val startX = dp(170f); val gap = dp(4f)
        return List(inventoryCapacity) { index ->
            val column = index % 8; val row = index / 8
            RectF(startX + column * (slotWidth + gap), top + dp(9f) + row * (slotHeight + gap), startX + column * (slotWidth + gap) + slotWidth, top + dp(9f) + row * (slotHeight + gap) + slotHeight)
        }
    }
    private fun itemAssetPath(code: String): String? = itemByCode[code]?.assetPath
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
