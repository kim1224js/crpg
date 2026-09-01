package es.kim.crpg.ui.settings.guide

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import es.kim.crpg.data.GameDatabase
import es.kim.crpg.data.ItemDefinitionEntity
import es.kim.crpg.data.MonsterDefinitionEntity
import es.kim.crpg.data.MonsterDropEntity
import es.kim.crpg.data.MonsterFloorSpawnEntity
import es.kim.crpg.game.catalog.ItemCatalog
import es.kim.crpg.ui.common.GameUiTheme
import es.kim.crpg.ui.common.antiquePanel
import es.kim.crpg.ui.common.dp
import es.kim.crpg.ui.common.gameScrollView
import es.kim.crpg.ui.common.matchParentParams
import es.kim.crpg.ui.event.RedMoonView
import java.util.concurrent.Executor

class GameGuideController(
    private val activity: android.app.Activity,
    private val host: FrameLayout,
    private val database: GameDatabase,
    private val executor: Executor
) {
    private var guideOverlay: FrameLayout? = null
    private var equipmentRows: List<EquipmentGuideRow>? = null
    private var monsterRows: List<MonsterGuideRow>? = null
    private var eventRules: EventGuideRules? = null
    private val itemPortraitCache = mutableMapOf<String, Bitmap?>()

    fun show() {
        if (guideOverlay != null) return
        val blocker = FrameLayout(activity).apply {
            isClickable = true
            setBackgroundColor(0xE6000000.toInt())
        }
        guideOverlay = blocker
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(18), activity.dp(12), activity.dp(18), activity.dp(16))
            background = activity.antiquePanel(GameUiTheme.LEATHER_DARK, GameUiTheme.GOLD, 12f, 2)
        }
        val body = FrameLayout(activity)
        val ruleTab = tabButton("게임 규칙")
        val equipmentTab = tabButton("장비 도감")
        val monsterTab = tabButton("몬스터 도감")
        val eventTab = tabButton("이벤트 도감")
        panel.addView(header(blocker), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(48)))
        panel.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(ruleTab, LinearLayout.LayoutParams(0, activity.dp(44), 1f).apply { marginEnd = activity.dp(4) })
            addView(equipmentTab, LinearLayout.LayoutParams(0, activity.dp(44), 1f).apply { leftMargin = activity.dp(4); rightMargin = activity.dp(4) })
            addView(monsterTab, LinearLayout.LayoutParams(0, activity.dp(44), 1f).apply { leftMargin = activity.dp(4); rightMargin = activity.dp(4) })
            addView(eventTab, LinearLayout.LayoutParams(0, activity.dp(44), 1f).apply { marginStart = activity.dp(4) })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(50)))
        panel.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = activity.dp(8) })

        fun selectRules() {
            selectTab(ruleTab, true); selectTab(equipmentTab, false); selectTab(monsterTab, false); selectTab(eventTab, false)
            body.removeAllViews(); body.addView(createRulesView(), activity.matchParentParams())
        }
        fun selectEquipment() {
            selectTab(ruleTab, false); selectTab(equipmentTab, true); selectTab(monsterTab, false); selectTab(eventTab, false)
            body.removeAllViews()
            val rows = equipmentRows
            if (rows == null) {
                body.addView(TextView(activity).apply {
                    text = "장비 정보를 불러오는 중입니다..."
                    setTextColor(Color.WHITE); textSize = 17f; gravity = Gravity.CENTER
                }, activity.matchParentParams())
                loadEquipment { loaded ->
                    if (guideOverlay === blocker && equipmentTab.isSelected) {
                        body.removeAllViews(); body.addView(createEquipmentTable(loaded), activity.matchParentParams())
                    }
                }
            } else body.addView(createEquipmentTable(rows), activity.matchParentParams())
        }
        fun selectMonsters() {
            selectTab(ruleTab, false); selectTab(equipmentTab, false); selectTab(monsterTab, true); selectTab(eventTab, false)
            body.removeAllViews()
            val rows = monsterRows
            if (rows == null) {
                body.addView(TextView(activity).apply {
                    text = "몬스터 정보를 불러오는 중입니다..."
                    setTextColor(Color.WHITE); textSize = 17f; gravity = Gravity.CENTER
                }, activity.matchParentParams())
                loadMonsters { loaded ->
                    if (guideOverlay === blocker && monsterTab.isSelected) {
                        body.removeAllViews(); body.addView(createMonsterTable(loaded), activity.matchParentParams())
                    }
                }
            } else body.addView(createMonsterTable(rows), activity.matchParentParams())
        }
        fun selectEvents() {
            selectTab(ruleTab, false); selectTab(equipmentTab, false); selectTab(monsterTab, false); selectTab(eventTab, true)
            body.removeAllViews()
            eventRules?.let { body.addView(createEventCatalog(it), activity.matchParentParams()); return }
            body.addView(TextView(activity).apply {
                text = "이벤트 정보를 불러오는 중입니다..."; setTextColor(Color.WHITE); textSize = 17f; gravity = Gravity.CENTER
            }, activity.matchParentParams())
            executor.execute {
                val dao = database.gameMasterDao()
                val rules = EventGuideRules(
                    dao.getConfigInt("red_moon_interval_days") ?: 10,
                    dao.getConfigInt("red_moon_monster_attack_percent") ?: 150,
                    dao.getConfigInt("red_moon_drop_rate_percent") ?: 200,
                    dao.getConfigInt("red_moon_return_floor_interval") ?: 5
                )
                eventRules = rules
                activity.runOnUiThread {
                    if (guideOverlay === blocker && eventTab.isSelected) {
                        body.removeAllViews(); body.addView(createEventCatalog(rules), activity.matchParentParams())
                    }
                }
            }
        }
        ruleTab.setOnClickListener { selectRules() }
        equipmentTab.setOnClickListener { selectEquipment() }
        monsterTab.setOnClickListener { selectMonsters() }
        eventTab.setOnClickListener { selectEvents() }
        selectRules()

        blocker.addView(panel, FrameLayout.LayoutParams(
            activity.resources.displayMetrics.widthPixels - activity.dp(32),
            activity.resources.displayMetrics.heightPixels - activity.dp(24),
            Gravity.CENTER
        ))
        host.addView(blocker, activity.matchParentParams())
        blocker.bringToFront()
    }

    private fun loadEquipment(onLoaded: (List<EquipmentGuideRow>) -> Unit) {
        executor.execute {
            runCatching {
                val dao = database.gameMasterDao()
                val items = dao.getItems()
                buildRows(items, dao.getMonsters(), dao.getMonsterDrops())
            }.onSuccess { rows ->
                equipmentRows = rows
                activity.runOnUiThread { onLoaded(rows) }
            }.onFailure {
                activity.runOnUiThread { Toast.makeText(activity, "장비 정보를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun loadMonsters(onLoaded: (List<MonsterGuideRow>) -> Unit) {
        executor.execute {
            runCatching {
                val dao = database.gameMasterDao()
                buildMonsterRows(dao.getMonsters(), dao.getMonsterFloorSpawns(), dao.getMonsterDrops(), dao.getItems())
            }.onSuccess { rows ->
                monsterRows = rows
                activity.runOnUiThread { onLoaded(rows) }
            }.onFailure {
                activity.runOnUiThread { Toast.makeText(activity, "몬스터 정보를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun buildMonsterRows(
        monsters: List<MonsterDefinitionEntity>,
        spawns: List<MonsterFloorSpawnEntity>,
        drops: List<MonsterDropEntity>,
        items: List<ItemDefinitionEntity>
    ): List<MonsterGuideRow> {
        val itemNames = items.associate { it.code to it.name }
        val floorsByMonster = spawns.groupBy { it.monsterCode }.mapValues { (_, values) -> values.map { it.floor }.distinct().sorted() }
        val dropsByMonster = drops.groupBy { it.monsterCode }
        return monsters.sortedBy { it.sortOrder }.map { monster ->
            val information = buildList {
                add("HP ${monster.maxHp} · 공격력 ${monster.attackPower}")
                add("사거리 ${monster.attackRange}칸${if (monster.openingAttackRange != monster.attackRange) " · 최초 ${monster.openingAttackRange}칸" else ""}")
                add("이동 ${monster.moveDistance}칸${if (monster.moveEveryTurns > 1) " · ${monster.moveEveryTurns}턴마다" else ""}")
                add("민감도 ${monster.sensitivity}칸")
                if (monster.goldDrop > 0) add("골드 ${monster.goldDrop}G · ${(monster.goldDropRate * 100).toInt()}%")
            }.joinToString("\n")
            val dropText = dropsByMonster[monster.code].orEmpty().mapNotNull { drop ->
                itemNames[drop.itemCode]?.let { "$it ${(drop.dropRate * 100).let { rate -> if (rate < 1) "%.1f".format(rate) else rate.toInt().toString() }}%" }
            }.ifEmpty { listOf("골드 및 공통 장비") }.joinToString("\n")
            MonsterGuideRow(monster, formatFloors(floorsByMonster[monster.code].orEmpty()), information, dropText)
        }
    }

    private fun buildRows(
        items: List<ItemDefinitionEntity>,
        monsters: List<MonsterDefinitionEntity>,
        drops: List<MonsterDropEntity>
    ): List<EquipmentGuideRow> {
        val monsterNames = monsters.associate { it.code to it.name }
        val dropSources = drops.groupBy { it.itemCode }.mapValues { (_, entries) ->
            entries.mapNotNull { monsterNames[it.monsterCode] }.distinct()
        }
        return items.asSequence()
            .filter { it.category in EQUIPMENT_CATEGORIES }
            .sortedWith(compareBy<ItemDefinitionEntity>({ GRADE_ORDER[it.grade] ?: 0 }, { it.sortOrder }))
            .map { item ->
                val option = ItemCatalog.equipmentOption(item)
                val information = buildList {
                    add(option?.category ?: categoryName(item.category))
                    option?.lines?.let(::addAll)
                    option?.specialEffect?.takeIf { it.isNotBlank() }?.let(::add)
                    item.detail?.takeIf { it.isNotBlank() && it !in this }?.let(::add)
                }.joinToString("\n")
                EquipmentGuideRow(item, information, acquisitionSource(item, dropSources[item.code].orEmpty()))
            }.toList()
    }

    private fun acquisitionSource(item: ItemDefinitionEntity, monsterSources: List<String>): String = buildList {
        val stores = item.storeType.split('_')
        if ("BLACKSMITH" in stores) add("대장간 구매")
        if ("GENERAL" in stores) add("일반상점 구매")
        if ("MERCHANT" in stores) add("떠돌이 상인")
        if (monsterSources.isNotEmpty()) add(monsterSources.joinToString(", "))
        else if (item.dropRate > 0.0) add("몬스터 공통 드랍")
        if (item.code.startsWith("exp_")) add(expandedWeaponSource(item.grade))
        else if (item.storeType == "DROP_ONLY" && monsterSources.isEmpty()) add("던전 상자·보스")
    }.distinct().ifEmpty { listOf("특수 획득") }.joinToString("\n")

    private fun expandedWeaponSource(grade: String): String = when (grade) {
        "NORMAL" -> "1~5층 몬스터·상자"
        "HIGH" -> "1~10층 몬스터·상자"
        "RARE" -> "5층 보스·6~15층 몬스터·상자"
        "EPIC" -> "10층 보스·11~20층 몬스터·상자"
        "UNIQUE" -> "10층 이상 보스·11~25층 몬스터·상자"
        "LEGENDARY" -> "15층·20층·25층 보스"
        "MYTHIC" -> "20층·25층 보스 전용"
        else -> "던전 몬스터·상자"
    }

    private fun createRulesView(): View {
        val files = listOf("01_COMMON_CONFIG.txt", "02_GAME_FLOW.txt", "03_VILLAGE.txt", "04_ITEMS.txt", "05_COMBAT.txt", "06_DUNGEON.txt", "07_MONSTERS.txt", "09_UI_AUDIO.txt", "10_ITEM_VALUES.txt", "11_GACHA_BOXES.txt", "12_EVENTS.txt", "13_WEAPON_EXPANSION.txt")
        val body = files.mapNotNull { name ->
            runCatching { activity.assets.open("game_book/$name").bufferedReader().use { it.readText() } }.getOrNull()
        }.joinToString("\n\n━━━━━━━━━━━━━━━━━━━━\n\n")
        return activity.gameScrollView(TextView(activity).apply {
            text = body
            setTextColor(0xFFE5D9C2.toInt()); textSize = 14f
            setLineSpacing(activity.dp(3).toFloat(), 1f)
            setPadding(activity.dp(12), activity.dp(8), activity.dp(12), activity.dp(12))
        })
    }

    private fun createEquipmentTable(rows: List<EquipmentGuideRow>): View {
        val table = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        table.addView(tableHeader())
        rows.forEachIndexed { index, row -> table.addView(equipmentRow(row, index)) }
        return activity.gameScrollView(table)
    }

    private fun createMonsterTable(rows: List<MonsterGuideRow>): View {
        val table = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        table.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = activity.antiquePanel(0xFF3A2418.toInt(), GameUiTheme.GOLD, 5f, 1)
            addCell("이미지", IMAGE_WIDTH, header = true)
            addCell("몬스터 이름", NAME_WIDTH, header = true)
            addCell("출현층", GRADE_WIDTH, header = true)
            addCell("전투 정보", 0, 1f, header = true)
            addCell("주요 드랍", SOURCE_WIDTH, header = true)
        }.also { it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(44)) })
        rows.forEachIndexed { index, row -> table.addView(monsterRow(row, index)) }
        return activity.gameScrollView(table)
    }

    private fun createEventCatalog(rules: EventGuideRules): View {
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(activity.dp(16), activity.dp(12), activity.dp(16), activity.dp(20))
            addView(RedMoonView(activity), LinearLayout.LayoutParams(activity.dp(150), activity.dp(150)))
            addView(TextView(activity).apply {
                text = "붉은 달"; setTextColor(0xFFFF6868.toInt()); textSize = 25f
                typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(48)))
            addView(TextView(activity).apply {
                text = "${rules.intervalDays}일마다 하늘이 붉게 물들고 던전의 괴물들이 흉포해집니다. 위험이 커지는 만큼 전리품을 얻을 기회도 크게 늘어납니다.\n\n" +
                    "발생 주기  생존 ${rules.intervalDays}일마다\n" +
                    "몬스터 공격력  ${rules.attackPercent}%\n" +
                    "몬스터 드랍률  ${rules.dropPercent}%  최대 100%\n" +
                    "귀환 가능 층  ${rules.returnInterval}층 단위\n\n" +
                    "귀환 제한은 붉은 달이 뜬 날의 던전 탐사 전체에 적용됩니다. 기존 심층 전투 중 귀환 제한도 함께 적용됩니다."
                setTextColor(0xFFEADFCC.toInt()); textSize = 16f; setLineSpacing(activity.dp(5).toFloat(), 1f)
                setPadding(activity.dp(18), activity.dp(14), activity.dp(18), activity.dp(18))
                background = activity.antiquePanel(0xE6291515.toInt(), 0xFF9D3434.toInt(), 8f, 2)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return activity.gameScrollView(content)
    }

    private fun monsterRow(row: MonsterGuideRow, index: Int) = LinearLayout(activity).apply {
        val boss = !row.monster.code.startsWith("mimic_") && row.monster.goldDropRate >= 1.0 && row.monster.maxHp >= 30
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, activity.dp(5), 0, activity.dp(5))
        background = activity.antiquePanel(
            if (index % 2 == 0) 0xE6251A13.toInt() else 0xE61B140F.toInt(),
            if (boss) 0xFFFF5959.toInt() else 0xFF5C4934.toInt(), 3f, if (boss) 2 else 1
        )
        addView(FrameLayout(activity).apply {
            background = activity.antiquePanel(0xFF100C09.toInt(), if (boss) 0xFFFF5959.toInt() else 0xFF9B805B.toInt(), 6f, 2)
            addView(ImageView(activity).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                monsterPortrait(row.monster)?.let(::setImageBitmap)
                contentDescription = row.monster.name
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                setMargins(activity.dp(4), activity.dp(4), activity.dp(4), activity.dp(4))
            })
        }, LinearLayout.LayoutParams(activity.dp(IMAGE_WIDTH), activity.dp(72)).apply { setMargins(activity.dp(5), 0, activity.dp(5), 0) })
        addCell(if (boss) "${row.monster.name}\n보스" else row.monster.name, NAME_WIDTH, color = if (boss) 0xFFFF8A82.toInt() else Color.WHITE, bold = true)
        addCell(row.floors, GRADE_WIDTH, color = GameUiTheme.GOLD, bold = true)
        addCell(row.information, 0, 1f, color = 0xFFE5D9C2.toInt())
        addCell(row.drops, SOURCE_WIDTH, color = 0xFFFFD58A.toInt())
    }.also { it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(92)).apply { topMargin = activity.dp(4) } }

    private fun monsterPortrait(monster: MonsterDefinitionEntity): Bitmap? = runCatching {
        val sheet = activity.assets.open(monster.spritePath).use(BitmapFactory::decodeStream)
        val frameWidth = sheet.width / 4
        val frameHeight = if (monster.code.startsWith("mimic_")) sheet.height else sheet.height / 4
        Bitmap.createBitmap(sheet, 0, 0, frameWidth, frameHeight)
    }.getOrNull()

    private fun itemPortrait(path: String): Bitmap? = itemPortraitCache.getOrPut(path) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            activity.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 256) sample *= 2
            activity.assets.open(path).use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }.getOrNull()
    }

    private fun formatFloors(floors: List<Int>): String {
        if (floors.isEmpty()) return "특수"
        val ranges = mutableListOf<String>()
        var start = floors.first()
        var previous = start
        floors.drop(1).forEach { floor ->
            if (floor == previous + 1) previous = floor
            else {
                ranges += if (start == previous) "$start" else "$start~$previous"
                start = floor; previous = floor
            }
        }
        ranges += if (start == previous) "$start" else "$start~$previous"
        return ranges.joinToString(", ")
    }

    private fun tableHeader() = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = activity.antiquePanel(0xFF3A2418.toInt(), GameUiTheme.GOLD, 5f, 1)
        addCell("이미지", IMAGE_WIDTH, header = true)
        addCell("아이템 이름", NAME_WIDTH, header = true)
        addCell("등급", GRADE_WIDTH, header = true)
        addCell("정보", 0, 1f, header = true)
        addCell("획득처", SOURCE_WIDTH, header = true)
    }.also { it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(44)) }

    private fun equipmentRow(row: EquipmentGuideRow, index: Int) = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, activity.dp(5), 0, activity.dp(5))
        background = activity.antiquePanel(if (index % 2 == 0) 0xE6251A13.toInt() else 0xE61B140F.toInt(), 0xFF5C4934.toInt(), 3f, 1)
        addView(FrameLayout(activity).apply {
            background = activity.antiquePanel(0xFF100C09.toInt(), gradeColor(row.item.grade), 6f, 2)
            addView(ImageView(activity).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                itemPortrait(row.item.assetPath)?.let(::setImageBitmap)
                contentDescription = row.item.name
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                setMargins(activity.dp(4), activity.dp(4), activity.dp(4), activity.dp(4))
            })
        }, LinearLayout.LayoutParams(activity.dp(IMAGE_WIDTH), activity.dp(70)).apply { setMargins(activity.dp(5), 0, activity.dp(5), 0) })
        addCell(row.item.name, NAME_WIDTH, color = Color.WHITE, bold = true)
        addCell(gradeName(row.item.grade), GRADE_WIDTH, color = gradeColor(row.item.grade), bold = true)
        addCell(row.information, 0, 1f, color = 0xFFE5D9C2.toInt())
        addCell(row.source, SOURCE_WIDTH, color = 0xFFFFD58A.toInt())
    }.also { it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(82)).apply { topMargin = activity.dp(4) } }

    private fun LinearLayout.addCell(textValue: String, widthDp: Int, weight: Float = 0f, header: Boolean = false, color: Int = Color.WHITE, bold: Boolean = false) {
        addView(TextView(activity).apply {
            text = textValue
            setTextColor(if (header) GameUiTheme.GOLD else color)
            textSize = if (header) 14f else 12f
            typeface = if (header || bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            gravity = Gravity.CENTER
            setPadding(activity.dp(6), activity.dp(3), activity.dp(6), activity.dp(3))
            maxLines = if (header) 1 else 4
        }, LinearLayout.LayoutParams(if (weight > 0f) 0 else activity.dp(widthDp), ViewGroup.LayoutParams.MATCH_PARENT, weight))
    }

    private fun header(blocker: View) = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(TextView(activity).apply {
            text = "게임 가이드"; setTextColor(Color.WHITE); textSize = 24f; typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        addView(TextView(activity).apply {
            text = "×"; contentDescription = "가이드 닫기"; setTextColor(Color.WHITE); textSize = 28f
            gravity = Gravity.CENTER; isClickable = true
            setOnClickListener { host.removeView(blocker); guideOverlay = null }
        }, LinearLayout.LayoutParams(activity.dp(42), ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun tabButton(label: String) = TextView(activity).apply {
        text = label; setTextColor(Color.WHITE); textSize = 16f; typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER; isClickable = true
    }

    private fun selectTab(tab: TextView, selected: Boolean) {
        tab.isSelected = selected
        tab.background = activity.antiquePanel(
            if (selected) 0xFF725322.toInt() else 0xFF3C2B20.toInt(),
            if (selected) GameUiTheme.GOLD else 0xFF6B5840.toInt(), 7f, if (selected) 2 else 1
        )
    }

    private fun categoryName(category: String) = when (category) {
        "WEAPON" -> "무기"; "ARMOR" -> "갑옷"; "HELMET" -> "투구"; "BOOTS" -> "신발"
        "AUXILIARY" -> "보조장비"; "ACCESSORY" -> "악세서리"; "RELIC" -> "유물"; else -> category
    }

    private fun gradeName(grade: String) = when (grade) {
        "HIGH" -> "고급"; "RARE" -> "레어"; "EPIC" -> "에픽"; "UNIQUE" -> "유니크"
        "LEGENDARY" -> "전설"; "MYTHIC" -> "신화"; else -> "노말"
    }

    private fun gradeColor(grade: String) = when (grade) {
        "HIGH" -> 0xFF65D57A.toInt(); "RARE" -> 0xFF61AFFF.toInt(); "EPIC" -> 0xFFC36BFF.toInt()
        "UNIQUE" -> 0xFFFFAD45.toInt(); "LEGENDARY" -> 0xFFFF5959.toInt(); "MYTHIC" -> 0xFFFFE586.toInt()
        else -> 0xFFB89458.toInt()
    }

    private data class EquipmentGuideRow(val item: ItemDefinitionEntity, val information: String, val source: String)
    private data class MonsterGuideRow(
        val monster: MonsterDefinitionEntity,
        val floors: String,
        val information: String,
        val drops: String
    )
    private data class EventGuideRules(val intervalDays: Int, val attackPercent: Int, val dropPercent: Int, val returnInterval: Int)

    private companion object {
        val EQUIPMENT_CATEGORIES = setOf("WEAPON", "ARMOR", "HELMET", "BOOTS", "AUXILIARY", "ACCESSORY", "RELIC")
        val GRADE_ORDER = mapOf("NORMAL" to 0, "HIGH" to 1, "RARE" to 2, "EPIC" to 3, "UNIQUE" to 4, "LEGENDARY" to 5, "MYTHIC" to 6)
        const val IMAGE_WIDTH = 72
        const val NAME_WIDTH = 138
        const val GRADE_WIDTH = 72
        const val SOURCE_WIDTH = 150
    }
}
