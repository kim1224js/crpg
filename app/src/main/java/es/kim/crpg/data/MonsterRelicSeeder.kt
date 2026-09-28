package es.kim.crpg.data

import androidx.sqlite.db.SupportSQLiteDatabase
import es.kim.crpg.game.rules.EquipmentSpecialRules

/** One dedicated relic per monster, including mimics. Existing relic identities are preserved. */
object MonsterRelicSeeder {
    fun seed(db: SupportSQLiteDatabase) {
        val monsters = mutableListOf<Triple<String, String, Boolean>>()
        db.query("SELECT code,name,goldDropRate,maxHp FROM monster_definition ORDER BY sortOrder,code").use { c ->
            while (c.moveToNext()) monsters += Triple(c.getString(0), c.getString(1),
                c.getDouble(2) >= 1.0 && c.getInt(3) >= 30 && !c.getString(0).startsWith("mimic_"))
        }
        monsters.forEachIndexed { index, (monster, name, boss) ->
            var code = "relic_$monster"
            var previousEffect = ""
            var previousDetail = ""
            var rate = if (boss) .20 else .02
            var existing = false
            db.query("SELECT i.code,i.specialEffect,i.detail,d.dropRate FROM monster_drop d JOIN item_definition i ON i.code=d.itemCode WHERE d.monsterCode=? AND i.category='RELIC' ORDER BY i.sortOrder,i.code LIMIT 1", arrayOf(monster)).use { c ->
                if (c.moveToFirst()) {
                    existing = true
                    code = c.getString(0)
                    previousEffect = c.getString(1).orEmpty()
                    previousDetail = c.getString(2).orEmpty()
                    rate = c.getDouble(3)
                }
            }
            // The equipment branch may already have applied these options in DB v62.
            if (existing && previousDetail.endsWith("$name 전용 드랍 유물")) return@forEachIndexed
            // A stable pool gives each species a different combination; all values are persisted in DB.
            val pool = listOf(
                "KILL_ROOT" to (if (boss) 2 else 1), "LIFESTEAL" to (if (boss) 30 else 15),
                "REFLECT" to (if (boss) 30 else 15), "LOW_HP_POWER" to (if (boss) 5 else 2),
                "AILMENT_GUARD" to (if (boss) 70 else 35), "POWER" to (if (boss) 3 else 1),
                "RANGED_REDUCE" to (if (boss) 30 else 15), "MELEE_REDUCE" to (if (boss) 30 else 15)
            )
            val count = if (boss) 5 else 2 + index % 3
            val benefits = List(count - if (existing) 1 else 0) { pool[(index + it * 3) % pool.size] }
            val drawback = if (code == "furnace_core") emptyList() else listOf("TAKEN_MORE" to if (boss) 20 else 10)
            val effects = benefits + drawback
            val effectText = (listOf(previousEffect).filter { it.isNotBlank() } + effects.map { "${it.first}=${it.second}" }).joinToString("|")
            val detail = (listOf(previousDetail).filter { it.isNotBlank() } + effects.map { EquipmentSpecialRules.description(it.first, it.second) } + "$name 전용 드랍 유물").joinToString(" · ")
            if (existing) {
                db.execSQL("UPDATE item_definition SET specialEffect=?,detail=? WHERE code=?", arrayOf(effectText, detail, code))
                if (boss) db.execSQL("UPDATE item_definition SET grade='MYTHIC',basePrice=MAX(basePrice,700) WHERE code=?", arrayOf(code))
            } else {
                val grade = if (boss) "MYTHIC" else "UNIQUE"
                val suffix = listOf("봉인된 심장", "핏빛 맹세", "뒤틀린 성물", "영혼 인장")[index % 4]
                val icon = listOf("ui/items/rare/item_spider_queen_heart.png", "ui/items/rare/item_alpha_fang.png", "ui/items/relics/item_plague_doctor_censer.png", "ui/items/relics/item_gravekeeper_chain.png")[index % 4]
                db.execSQL("INSERT OR IGNORE INTO item_definition VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", arrayOf<Any?>(
                    code, "${name}의 $suffix", "RELIC", grade, "DROP_ONLY", if (boss) 900 else 100,
                    1, icon, 0, 1, 0, 0, 0, 0, detail, effectText, .0, null, 4000 + index))
            }
            db.execSQL("INSERT OR REPLACE INTO monster_drop(monsterCode,itemCode,dropRate,dropQuantity) VALUES (?,?,?,1)", arrayOf<Any?>(monster, code, rate))
        }
    }
}
