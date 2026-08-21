package es.kim.crpg.ui.dungeon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class DungeonDemoView(
    context: Context,
    private val inventoryCounts: Map<String, Int> = emptyMap(),
    equippedWeaponCode: String? = null,
    private val onUseReturnStone: () -> Unit = {}
) : View(context) {
    private enum class MonsterKind { SPIDER, BANDIT, WILD_DOG, SLIME }
    private enum class Phase { PLAYER, MONSTERS }

    private data class UnitSprite(
        val name: String, var column: Int, var row: Int, val sheet: Bitmap,
        val kind: MonsterKind? = null, var hp: Int, val maxHp: Int,
        var alive: Boolean = true, var spiderOpeningAttack: Boolean = true,
        var drawColumn: Float = column.toFloat(), var drawRow: Float = row.toFloat(),
        var actionRow: Int = 0, var actionUntil: Long = 0L
    )

    private data class Weapon(
        val code: String, val name: String, val range: Int, val damage: Int, val turnCost: Int,
        val sheetPath: String, val iconPath: String
    )

    private val background = bitmap("ui/dungeon/concepts/dungeon_gameplay_grid_concept.png")
    private val weapons = listOf(
        Weapon("crude_sword", "조잡한 검", 1, 3, 1, "ui/dungeon/player/player_sword_animation_sheet.png", "ui/items/item_crude_sword.png"),
        Weapon("crude_spear", "조잡한 창", 3, 4, 2, "ui/dungeon/player/player_spear_animation_sheet.png", "ui/items/item_crude_spear.png"),
        Weapon("crude_bow", "조잡한 활", 4, 2, 1, "ui/dungeon/player/player_bow_animation_sheet.png", "ui/items/item_crude_bow.png"),
        Weapon("crude_gun", "조잡한 총", 5, 3, 2, "ui/dungeon/player/player_gun_animation_sheet.png", "ui/items/item_crude_gun.png")
    )
    private val equippedWeapon = weapons.firstOrNull { it.code == equippedWeaponCode && itemCount(it.code) > 0 }
    private val player = UnitSprite(
        "플레이어", 2, 8,
        bitmap(equippedWeapon?.sheetPath ?: "ui/dungeon/player/player_base_animation_sheet.png"),
        hp = 10, maxHp = 10
    )
    private val monsters = mutableListOf(
        UnitSprite("거미", 11, 3, bitmap("ui/dungeon/monsters/spider_animation_sheet.png"), MonsterKind.SPIDER, 4, 4),
        UnitSprite("들개", 15, 8, bitmap("ui/dungeon/monsters/wild_dog_animation_sheet.png"), MonsterKind.WILD_DOG, 6, 6),
        UnitSprite("도적", 18, 4, bitmap("ui/dungeon/monsters/bandit_animation_sheet.png"), MonsterKind.BANDIT, 8, 8),
        UnitSprite("슬라임", 8, 9, bitmap("ui/dungeon/monsters/slime_animation_sheet.png"), MonsterKind.SLIME, 7, 7)
    )
    private val columns = 24
    private val rows = 12
    private val visibleColumns = 8f
    private val visibleRows = 7f
    private val obstacles = linkedSetOf<Pair<Int, Int>>()
    private var cameraColumn = 0f
    private var cameraRow = 4f
    private var phase = Phase.PLAYER
    private var focusedMonster: UnitSprite? = null
    private var turn = 1
    private var pendingMonsterRounds = 0
    private var animationFrame = 0
    private var lastFrameAt = 0L
    private var message = "파란 칸으로 이동하거나 노란 몬스터를 공격하세요"
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var impactColumn = -1
    private var impactRow = -1
    private var impactUntil = 0L
    private val inventoryIcons = inventoryCounts.keys.mapNotNull { code ->
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
        drawBattlefield(canvas); drawSidePanel(canvas); drawHud(canvas)
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
                    obstacles.contains(column to row) -> drawTileFill(canvas, rect, 0x70B52A25)
                    phase == Phase.PLAYER && isAdjacent(column, row) && !occupied(column, row) -> drawTileFill(canvas, rect, 0x653A91D8)
                    phase == Phase.PLAYER && inWeaponRange(column, row) -> drawTileFill(canvas, rect, if (hasLineOfSight(player.column, player.row, column, row)) 0x66E9B62F else 0x70B52A25)
                }
                canvas.drawRect(rect, gridPaint)
                if (obstacles.contains(column to row)) drawObstacle(canvas, rect, column, row)
            }
        }
        (monsters.filter { it.alive } + player).sortedBy { it.drawRow }.forEach { drawUnit(canvas, area, it) }
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
        textPaint.textSize = dp(18f); textPaint.color = 0xFFFFD47A.toInt(); canvas.drawText("지하 1층 · 턴 $turn", left + dp(18f), dp(104f), textPaint)
        textPaint.textSize = dp(14f); textPaint.color = Color.WHITE
        canvas.drawText(if (phase == Phase.PLAYER) "플레이어 행동" else "${focusedMonster?.name ?: "몬스터"} 행동 관찰 중", left + dp(18f), dp(132f), textPaint)
        canvas.drawText("지도를 드래그하여 전체 탐색", left + dp(18f), dp(159f), textPaint)
        var y = dp(197f)
        monsters.forEach {
            textPaint.color = if (it.alive) Color.WHITE else 0xFF777777.toInt()
            canvas.drawText("${it.name}   ${if (it.alive) "HP ${it.hp}/${it.maxHp}" else "처치"}", left + dp(20f), y, textPaint); y += dp(28f)
        }
        textPaint.color = 0xFFFFD47A.toInt(); textPaint.textSize = dp(13f)
        canvas.drawText("거미: 첫 공격 사거리 2", left + dp(18f), y + dp(14f), textPaint)
        canvas.drawText("들개: 턴당 최대 2칸 이동", left + dp(18f), y + dp(38f), textPaint)
        canvas.drawText("도적: 사거리 3 공격", left + dp(18f), y + dp(62f), textPaint)
        canvas.drawText("슬라임: 격턴 이동", left + dp(18f), y + dp(86f), textPaint)
    }

    private fun drawHud(canvas: Canvas) {
        val top = height - dp(116f)
        paint.color = 0xF00B0806.toInt(); canvas.drawRect(0f, top, width.toFloat(), height.toFloat(), paint)

        val orbX = dp(86f); val orbY = top + dp(57f); val orbRadius = dp(48f)
        paint.color = 0xFF281B12.toInt(); canvas.drawCircle(orbX, orbY, orbRadius + dp(8f), paint)
        paint.color = 0xFFB48A42.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(4f); canvas.drawCircle(orbX, orbY, orbRadius + dp(4f), paint)
        paint.color = 0xFF6E1715.toInt(); paint.style = Paint.Style.FILL; canvas.drawCircle(orbX, orbY, orbRadius, paint)
        paint.color = 0xFFB82E27.toInt(); canvas.drawArc(RectF(orbX - orbRadius, orbY - orbRadius, orbX + orbRadius, orbY + orbRadius), 90f, 360f * player.hp / player.maxHp, true, paint)
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
            if (rect.contains(x, y) && inventoryEntry(index)?.first == "return_stone") {
                if (phase == Phase.PLAYER && itemCount("return_stone") > 0) onUseReturnStone()
                return
            }
        }
        if (phase != Phase.PLAYER) { message = "몬스터 행동이 끝날 때까지 기다리세요"; return }
        val area = dungeonArea(); if (!area.contains(x, y)) return
        val column = floor(cameraColumn + (x - area.left) / (area.width() / visibleColumns)).toInt().coerceIn(0, columns - 1)
        val row = floor(cameraRow + (y - area.top) / (area.height() / visibleRows)).toInt().coerceIn(0, rows - 1)
        monsters.firstOrNull { it.alive && it.column == column && it.row == row }?.let { attackMonster(it); return }
        when {
            obstacles.contains(column to row) -> message = "장애물 때문에 이동할 수 없습니다"
            isAdjacent(column, row) && !occupied(column, row) -> {
                player.column = column; player.row = row; player.drawColumn = column.toFloat(); player.drawRow = row.toFloat()
                player.actionRow = 1; player.actionUntil = System.currentTimeMillis() + 450L; beginMonsterTurns(1)
            }
            else -> message = "파란색 인접 타일만 이동할 수 있습니다"
        }
        invalidate()
    }

    private fun attackMonster(monster: UnitSprite) {
        val weapon = equippedWeapon ?: run { message = "착용한 무기가 없어 공격할 수 없습니다"; invalidate(); return }
        val distance = distance(player.column, player.row, monster.column, monster.row)
        if (distance > weapon.range) { message = "${weapon.name} 사거리 밖입니다"; return }
        if (!hasLineOfSight(player.column, player.row, monster.column, monster.row)) { message = "장애물에 공격 경로가 막혔습니다"; return }
        player.actionRow = 2; player.actionUntil = System.currentTimeMillis() + 650L
        impactColumn = monster.column; impactRow = monster.row; impactUntil = System.currentTimeMillis() + 650L
        monster.hp -= weapon.damage
        if (monster.hp <= 0) { monster.hp = 0; monster.alive = false; message = "${monster.name} 처치" } else message = "${monster.name}에게 ${weapon.damage} 피해"
        beginMonsterTurns(weapon.turnCost)
    }

    private fun beginMonsterTurns(rounds: Int) { phase = Phase.MONSTERS; pendingMonsterRounds = rounds; runMonsterRound(0) }

    private fun runMonsterRound(index: Int) {
        val living = monsters.filter { it.alive }
        if (index >= living.size) {
            pendingMonsterRounds--
            if (pendingMonsterRounds > 0) { postDelayed({ runMonsterRound(0) }, 350L); return }
            focusedMonster = null; phase = Phase.PLAYER; turn++; focusCamera(player.column, player.row)
            message = if (player.hp > 0) "플레이어 행동 차례" else "플레이어가 쓰러졌습니다"; invalidate(); return
        }
        val monster = living[index]; focusedMonster = monster; focusCamera(monster.column, monster.row); message = "${monster.name}의 행동"; invalidate()
        postDelayed({ performMonsterAction(monster); invalidate(); postDelayed({ runMonsterRound(index + 1) }, 520L) }, 420L)
    }

    private fun performMonsterAction(monster: UnitSprite) {
        val dist = distance(monster.column, monster.row, player.column, player.row)
        val attackRange = when (monster.kind) { MonsterKind.SPIDER -> if (monster.spiderOpeningAttack) 2 else 1; MonsterKind.BANDIT -> 3; else -> 1 }
        if (dist <= attackRange && hasLineOfSight(monster.column, monster.row, player.column, player.row)) {
            val damage = when (monster.kind) { MonsterKind.BANDIT, MonsterKind.WILD_DOG -> 2; else -> 1 }
            monster.actionRow = 2; monster.actionUntil = System.currentTimeMillis() + 600L
            impactColumn = player.column; impactRow = player.row; impactUntil = System.currentTimeMillis() + 600L
            player.hp = max(0, player.hp - damage); if (monster.kind == MonsterKind.SPIDER) monster.spiderOpeningAttack = false
            if (player.hp == 0) { player.actionRow = 3; player.actionUntil = Long.MAX_VALUE }
            message = "${monster.name} 공격 · 피해 $damage"; return
        }
        if (monster.kind == MonsterKind.SLIME && turn % 2 == 0) { message = "슬라임이 몸을 웅크립니다"; return }
        repeat(if (monster.kind == MonsterKind.WILD_DOG) 2 else 1) {
            nextStep(monster)?.let { next ->
                monster.column = next.first; monster.row = next.second; monster.drawColumn = next.first.toFloat(); monster.drawRow = next.second.toFloat()
                monster.actionRow = 1; monster.actionUntil = System.currentTimeMillis() + 450L; focusCamera(monster.column, monster.row)
            }
        }
        message = "${monster.name} 이동"
    }

    private fun nextStep(monster: UnitSprite): Pair<Int, Int>? {
        val candidates = mutableListOf<Pair<Int, Int>>()
        fun horizontal() { if (player.column != monster.column) candidates += (monster.column + if (player.column > monster.column) 1 else -1) to monster.row }
        fun vertical() { if (player.row != monster.row) candidates += monster.column to (monster.row + if (player.row > monster.row) 1 else -1) }
        if (abs(player.column - monster.column) >= abs(player.row - monster.row)) { horizontal(); vertical() } else { vertical(); horizontal() }
        return candidates.firstOrNull { (c, r) -> c in 0 until columns && r in 0 until rows && !obstacles.contains(c to r) && !occupied(c, r) }
    }

    private fun createObstacles() {
        val reserved = mutableSetOf(player.column to player.row).apply { addAll(monsters.map { it.column to it.row }) }
        val random = Random(System.currentTimeMillis())
        while (obstacles.size < 34) {
            val cell = random.nextInt(columns) to random.nextInt(rows)
            if (cell !in reserved && distance(cell.first, cell.second, player.column, player.row) > 2) obstacles += cell
        }
    }

    private fun drawObstacle(canvas: Canvas, rect: RectF, column: Int, row: Int) {
        paint.color = when ((column * 7 + row * 13) % 4) { 0 -> 0xFF514B45.toInt(); 1 -> 0xFF3B4A35.toInt(); 2 -> 0xFF665A4D.toInt(); else -> 0xFF263B43.toInt() }
        val inset = min(rect.width(), rect.height()) * .18f
        canvas.drawRoundRect(RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset * .35f), dp(8f), dp(8f), paint)
    }

    private fun drawUnit(canvas: Canvas, area: RectF, unit: UnitSprite) {
        val frameWidth = unit.sheet.width / 4; val frameHeight = unit.sheet.height / 4
        val row = if (System.currentTimeMillis() < unit.actionUntil) unit.actionRow else 0
        val source = Rect(animationFrame * frameWidth, row * frameHeight, (animationFrame + 1) * frameWidth, (row + 1) * frameHeight)
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

    private fun scrollMap(dx: Float, dy: Float) {
        val area = dungeonArea(); cameraColumn = (cameraColumn + dx / (area.width() / visibleColumns)).coerceIn(0f, columns - visibleColumns)
        cameraRow = (cameraRow + dy / (area.height() / visibleRows)).coerceIn(0f, rows - visibleRows); invalidate()
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
    private fun inWeaponRange(column: Int, row: Int) = equippedWeapon?.let { distance(player.column, player.row, column, row) in 1..it.range } == true
    private fun isAdjacent(column: Int, row: Int) = distance(player.column, player.row, column, row) == 1
    private fun distance(c1: Int, r1: Int, c2: Int, r2: Int) = abs(c1 - c2) + abs(r1 - r2)
    private fun occupied(column: Int, row: Int) = (player.column == column && player.row == row) || monsters.any { it.alive && it.column == column && it.row == row }
    private fun itemCount(code: String) = inventoryCounts[code] ?: 0
    private fun inventoryEntry(index: Int): Pair<String, Int>? = inventoryCounts.entries.elementAtOrNull(index)?.let { it.key to it.value }
    private fun inventorySlotRects(top: Float): List<RectF> {
        val slotWidth = dp(67f); val slotHeight = dp(43f); val startX = dp(170f); val gap = dp(5f)
        return List(10) { index ->
            val column = index % 5; val row = index / 5
            RectF(startX + column * (slotWidth + gap), top + dp(9f) + row * (slotHeight + gap), startX + column * (slotWidth + gap) + slotWidth, top + dp(9f) + row * (slotHeight + gap) + slotHeight)
        }
    }
    private fun itemAssetPath(code: String): String? = when (code) {
        "torch" -> "ui/items/item_torch.png"; "return_stone" -> "ui/items/item_return_stone.png"
        "camping_kit" -> "ui/items/item_camping_kit.png"; "fire_bomb" -> "ui/items/item_fire_bomb.png"
        "crude_sword" -> "ui/items/item_crude_sword.png"; "crude_spear" -> "ui/items/item_crude_spear.png"
        "crude_bow" -> "ui/items/item_crude_bow.png"; "crude_gun" -> "ui/items/item_crude_gun.png"
        "crude_armor" -> "ui/items/item_crude_armor.png"; "crude_helmet" -> "ui/items/item_crude_helmet.png"
        "crude_boots" -> "ui/items/item_crude_boots.png"; else -> null
    }
    private fun isConsumable(code: String) = code in setOf("torch", "return_stone", "camping_kit", "fire_bomb")
    private fun dungeonArea() = RectF(width * .025f, dp(62f), width * .615f, height - dp(116f))
    private fun tileRect(area: RectF, column: Int, row: Int): RectF {
        val tw = area.width() / visibleColumns; val th = area.height() / visibleRows
        return RectF(area.left + (column - cameraColumn) * tw, area.top + (row - cameraRow) * th, area.left + (column + 1 - cameraColumn) * tw, area.top + (row + 1 - cameraRow) * th)
    }
    private fun drawTileFill(canvas: Canvas, rect: RectF, color: Int) { paint.color = color; paint.style = Paint.Style.FILL; canvas.drawRect(rect, paint) }
    private fun bitmap(path: String): Bitmap = context.assets.open(path).use(BitmapFactory::decodeStream)
    private fun dp(value: Float) = value * resources.displayMetrics.density
}
