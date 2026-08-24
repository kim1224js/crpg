package es.kim.crpg.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LoginProfileEntity::class, OwnedItemEntity::class, ItemDefinitionEntity::class,
        MonsterDefinitionEntity::class, AppraisalRuleEntity::class, GameConfigEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class GameDatabase : RoomDatabase() {
    abstract fun loginProfileDao(): LoginProfileDao
    abstract fun ownedItemDao(): OwnedItemDao
    abstract fun gameMasterDao(): GameMasterDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE login_profile ADD COLUMN gold INTEGER NOT NULL DEFAULT 10")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE owned_item ADD COLUMN isEquipped INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE owned_item ADD COLUMN dungeonUseCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE owned_item ADD COLUMN durability INTEGER NOT NULL DEFAULT 3")
                db.execSQL("ALTER TABLE owned_item ADD COLUMN isIdentified INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE owned_item ADD COLUMN appraisedGrade TEXT")
                createMasterTables(db)
                seedMasterData(db)
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE game_config SET intValue = 16 WHERE `key` = 'inventory_capacity'")
            }
        }

        private val CREATE_AND_SEED = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                seedMasterData(db)
            }
        }

        private fun createMasterTables(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS item_definition (code TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, category TEXT NOT NULL, grade TEXT NOT NULL, storeType TEXT NOT NULL, basePrice INTEGER NOT NULL, unitsPerPurchase INTEGER NOT NULL, assetPath TEXT NOT NULL, isConsumable INTEGER NOT NULL, maxStack INTEGER NOT NULL, attackPower INTEGER NOT NULL, attackTurnCost INTEGER NOT NULL, attackRange INTEGER NOT NULL, healthBonus INTEGER NOT NULL, detail TEXT, specialEffect TEXT, dropRate REAL NOT NULL, playerSheetPath TEXT, sortOrder INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS monster_definition (code TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, maxHp INTEGER NOT NULL, attackPower INTEGER NOT NULL, attackRange INTEGER NOT NULL, openingAttackRange INTEGER NOT NULL, moveDistance INTEGER NOT NULL, moveEveryTurns INTEGER NOT NULL, goldDrop INTEGER NOT NULL, goldDropRate REAL NOT NULL, spritePath TEXT NOT NULL, sortOrder INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS appraisal_rule (grade TEXT NOT NULL PRIMARY KEY, cost INTEGER NOT NULL, successRate REAL NOT NULL, colorValue INTEGER NOT NULL, sortOrder INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS game_config (`key` TEXT NOT NULL PRIMARY KEY, intValue INTEGER, doubleValue REAL, textValue TEXT)")
        }

        private fun seedMasterData(db: SupportSQLiteDatabase) {
            val itemSql = "INSERT OR REPLACE INTO item_definition VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
            listOf<Array<Any?>>(
                arrayOf("torch", "횃불", "CONSUMABLE", "NORMAL", "GENERAL", 1, 5, "ui/items/item_torch.png", 1, 99, 0, 0, 0, 0, "시야 5칸 · 지속 10턴", "시야를 5칸으로 확장", .10, null, 1),
                arrayOf("return_stone", "귀환석", "CONSUMABLE", "NORMAL", "GENERAL", 2, 1, "ui/items/item_return_stone.png", 1, 99, 0, 0, 0, 0, "현재 층에서 마을로 귀환", "생존 중 사용 가능", .10, null, 2),
                arrayOf("camping_kit", "야영 세트", "CONSUMABLE", "NORMAL", "GENERAL", 3, 1, "ui/items/item_camping_kit.png", 1, 99, 0, 0, 0, 0, "체력을 모두 회복", "야영 시 최대 체력 회복", .10, null, 3),
                arrayOf("fire_bomb", "화염병", "CONSUMABLE", "NORMAL", "GENERAL", 2, 1, "ui/items/item_fire_bomb.png", 1, 99, 0, 0, 0, 0, "투척형 공격 소모품", "범위 화염 피해", .10, null, 4),
                arrayOf("crude_sword", "조잡한 검", "WEAPON", "NORMAL", "BLACKSMITH", 5, 1, "ui/items/item_crude_sword.png", 0, 1, 3, 1, 1, 0, "공격 3 · 1턴 · 사거리 1", null, .10, "ui/dungeon/player/player_sword_animation_sheet.png", 10),
                arrayOf("crude_spear", "조잡한 창", "WEAPON", "NORMAL", "BLACKSMITH", 5, 1, "ui/items/item_crude_spear.png", 0, 1, 4, 2, 3, 0, "공격 4 · 2턴 · 사거리 3", null, .10, "ui/dungeon/player/player_spear_animation_sheet.png", 11),
                arrayOf("crude_bow", "조잡한 활", "WEAPON", "NORMAL", "BLACKSMITH", 5, 1, "ui/items/item_crude_bow.png", 0, 1, 2, 1, 4, 0, "공격 2 · 1턴 · 사거리 4", null, .10, "ui/dungeon/player/player_bow_animation_sheet.png", 12),
                arrayOf("crude_gun", "조잡한 총", "WEAPON", "NORMAL", "BLACKSMITH", 5, 1, "ui/items/item_crude_gun.png", 0, 1, 3, 2, 5, 0, "공격 3 · 2턴 · 사거리 5", null, .10, "ui/dungeon/player/player_gun_animation_sheet.png", 13),
                arrayOf("crude_armor", "조잡한 갑옷", "ARMOR", "NORMAL", "BLACKSMITH", 5, 1, "ui/items/item_crude_armor.png", 0, 1, 0, 0, 0, 1, "체력 +1", null, .10, null, 14),
                arrayOf("crude_helmet", "조잡한 투구", "HELMET", "NORMAL", "BLACKSMITH", 5, 1, "ui/items/item_crude_helmet.png", 0, 1, 0, 0, 0, 1, "체력 +1", null, .10, null, 15),
                arrayOf("crude_boots", "조잡한 신발", "BOOTS", "NORMAL", "BLACKSMITH", 5, 1, "ui/items/item_crude_boots.png", 0, 1, 0, 0, 0, 0, "이동 시 50% 확률로 +1칸", "이동 거리 +1 확률 50%", .10, null, 16)
            ).forEach { db.execSQL(itemSql, it) }

            val monsterSql = "INSERT OR REPLACE INTO monster_definition VALUES (?,?,?,?,?,?,?,?,?,?,?,?)"
            listOf<Array<Any?>>(
                arrayOf("spider", "거미", 4, 1, 1, 2, 1, 1, 1, .70, "ui/dungeon/monsters/spider_animation_sheet.png", 1),
                arrayOf("wild_dog", "들개", 6, 2, 1, 1, 2, 1, 1, .70, "ui/dungeon/monsters/wild_dog_animation_sheet.png", 2),
                arrayOf("bandit", "도적", 8, 2, 3, 3, 1, 1, 2, .70, "ui/dungeon/monsters/bandit_animation_sheet.png", 3),
                arrayOf("slime", "슬라임", 7, 1, 1, 1, 1, 2, 1, .70, "ui/dungeon/monsters/slime_animation_sheet.png", 4)
            ).forEach { db.execSQL(monsterSql, it) }

            val appraisalSql = "INSERT OR REPLACE INTO appraisal_rule VALUES (?,?,?,?,?)"
            listOf<Array<Any?>>(
                arrayOf("RARE", 10, .80, 0xFF4EA5FF, 1), arrayOf("EPIC", 20, .70, 0xFFC05CFF, 2),
                arrayOf("UNIQUE", 50, .60, 0xFFFFB13B, 3), arrayOf("LEGENDARY", 100, .50, 0xFFFF5151, 4),
                arrayOf("MYTHIC", 200, .40, 0xFFFFE27A, 5)
            ).forEach { db.execSQL(appraisalSql, it) }

            val configSql = "INSERT OR REPLACE INTO game_config VALUES (?,?,?,?)"
            listOf<Array<Any?>>(
                arrayOf("starting_gold", 10, null, null),
                arrayOf("initial_return_stones", 5, null, null),
                arrayOf("base_player_hp", 10, null, null),
                arrayOf("inventory_capacity", 16, null, null),
                arrayOf("storage_capacity", 20, null, null),
                arrayOf("base_vision", 2, null, null),
                arrayOf("torch_vision", 5, null, null),
                arrayOf("torch_duration_turns", 10, null, null)
            ).forEach { db.execSQL(configSql, it) }
        }

        @Volatile
        private var instance: GameDatabase? = null

        fun getInstance(context: Context): GameDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    GameDatabase::class.java,
                    "crpg_game.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).addCallback(CREATE_AND_SEED).build().also { instance = it }
            }
        }
    }
}
