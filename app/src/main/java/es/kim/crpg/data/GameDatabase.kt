package es.kim.crpg.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import es.kim.crpg.game.catalog.ExpandedWeaponCatalog

@Database(
    entities = [
        LoginProfileEntity::class, OwnedItemEntity::class, ItemDefinitionEntity::class,
        MonsterDefinitionEntity::class, AppraisalRuleEntity::class, GameConfigEntity::class,
        DeceasedCharacterEntity::class, NicknameAccountEntity::class, MonsterDropEntity::class,
        MonsterFloorSpawnEntity::class, DungeonInteractableDefinitionEntity::class,
        DungeonInteractableSpawnEntity::class, DungeonRunEntity::class
    ],
    version = 42,
    exportSchema = true
)
abstract class GameDatabase : RoomDatabase() {
    abstract fun loginProfileDao(): LoginProfileDao
    abstract fun ownedItemDao(): OwnedItemDao
    abstract fun gameMasterDao(): GameMasterDao
    abstract fun deceasedCharacterDao(): DeceasedCharacterDao
    abstract fun nicknameAccountDao(): NicknameAccountDao
    abstract fun dungeonRunDao(): DungeonRunDao

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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS deceased_character (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, playerName TEXT NOT NULL, generation INTEGER NOT NULL, reachedFloor INTEGER NOT NULL, survivedTurns INTEGER NOT NULL, diedAt INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE item_definition SET attackPower = 3, attackTurnCost = 1, attackRange = 1, detail = '공격 3 × 2연타 · 1턴 · 사거리 1', specialEffect = 'DOUBLE_HIT' WHERE code = 'crude_sword'")
                db.execSQL("UPDATE item_definition SET attackPower = 4, attackTurnCost = 2, attackRange = 3, detail = '공격 4 · 2턴 · 직선 사거리 3 전체 찌르기', specialEffect = 'LINE_THRUST' WHERE code = 'crude_spear'")
                db.execSQL("UPDATE item_definition SET attackPower = 3, attackTurnCost = 2, attackRange = 4, detail = '공격 3 · 2턴 · 사거리 4', specialEffect = NULL WHERE code = 'crude_bow'")
                db.execSQL("UPDATE item_definition SET attackPower = 5, attackTurnCost = 2, attackRange = 5, detail = '공격 5 · 2턴 · 사거리 5 · 처치 시 직선 관통', specialEffect = 'KILL_PIERCE' WHERE code = 'crude_gun'")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE item_definition SET attackPower = 5, detail = '3×3 화염 지대 · 2턴 · 턴당 피해 5', specialEffect = 'FIRE_ZONE_3X3_2T' WHERE code = 'fire_bomb'")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE item_definition SET detail = '3×3 화염 지대 · 2턴 · 턴당 피해 5 · 화상 3턴간 턴당 피해 3', specialEffect = 'FIRE_ZONE_3X3_2T_BURN_3T' WHERE code = 'fire_bomb'")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE login_profile ADD COLUMN introSeen INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE login_profile ADD COLUMN survivalDay INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE login_profile ADD COLUMN lastManorSearchDay INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE item_definition SET healthBonus = 0, detail = '방어 확률 30% · 방어 성공 시 피해 무효', specialEffect = 'BLOCK_CHANCE_30' WHERE code IN ('crude_armor', 'crude_helmet')")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE game_config SET intValue = 25 WHERE `key` = 'inventory_capacity'")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE monster_definition SET attackRange = 2, openingAttackRange = 2 WHERE code = 'bandit'")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE item_definition SET attackTurnCost = 1, detail = '공격 3 · 1턴 · 사거리 4 · 50% 확률로 추가 사격', specialEffect = 'DOUBLE_SHOT_50' WHERE code = 'crude_bow'")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS nickname_account (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, nickname TEXT COLLATE NOCASE NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_nickname_account_nickname ON nickname_account(nickname)")
                db.execSQL("INSERT OR IGNORE INTO nickname_account(id, nickname, createdAt) SELECT id, playerName, lastLoginAt FROM login_profile")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE item_definition SET detail = '공격 3 · 1턴 · 주변 1칸의 모든 적 공격', specialEffect = 'ADJACENT_SWEEP' WHERE code = 'crude_sword'")
                db.execSQL("UPDATE item_definition SET detail = '사용한 현재 층 동안 모든 무기 공격력 +1', specialEffect = 'FLOOR_ATTACK_PLUS_1' WHERE code = 'torch'")
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS monster_drop (monsterCode TEXT NOT NULL, itemCode TEXT NOT NULL, dropRate REAL NOT NULL, PRIMARY KEY(monsterCode, itemCode))")
                seedRareMonsterItems(db)
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS monster_floor_spawn (floor INTEGER NOT NULL, spawnOrder INTEGER NOT NULL, monsterCode TEXT NOT NULL, `column` INTEGER NOT NULL, `row` INTEGER NOT NULL, PRIMARY KEY(floor, spawnOrder))")
                seedFloorsSixToTen(db)
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                seedFloorsElevenToFifteen(db)
            }
        }

        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createDungeonInteractableTables(db)
                seedDungeonInteractables(db)
            }
        }

        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE dungeon_interactable_definition RENAME TO dungeon_interactable_definition_old")
                db.execSQL("CREATE TABLE dungeon_interactable_definition (code TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, healPercent INTEGER NOT NULL, assetPath TEXT NOT NULL, footprintWidth INTEGER NOT NULL, footprintHeight INTEGER NOT NULL)")
                db.execSQL("INSERT INTO dungeon_interactable_definition SELECT code,name,healPercent,assetPath,2,2 FROM dungeon_interactable_definition_old")
                db.execSQL("DROP TABLE dungeon_interactable_definition_old")
                db.execSQL("INSERT OR REPLACE INTO game_config VALUES ('dungeon_healing_object_chance_percent',30,NULL,NULL)")
                db.execSQL("DELETE FROM dungeon_interactable_spawn")
            }
        }

        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS dungeon_run (playerId INTEGER NOT NULL PRIMARY KEY, payloadJson TEXT NOT NULL, savedAt INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE item_definition SET dropRate = 0.05 WHERE grade = 'NORMAL' AND category IN ('WEAPON','ARMOR','HELMET','BOOTS')")
            }
        }

        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("INSERT OR REPLACE INTO game_config VALUES ('dungeon_monster_count_min',5,NULL,NULL)")
                db.execSQL("INSERT OR REPLACE INTO game_config VALUES ('dungeon_monster_count_max',10,NULL,NULL)")
                db.execSQL("INSERT OR REPLACE INTO game_config VALUES ('return_stone_combat_lock_floor',11,NULL,NULL)")
            }
        }

        private val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE appraisal_rule SET cost = 0")
                db.execSQL("INSERT OR REPLACE INTO appraisal_rule VALUES ('HIGH',0,0.90,4287349578,0)")
                db.execSQL("UPDATE owned_item SET isIdentified = 0, appraisedGrade = NULL, displayName = '미확인 장비', isEquipped = 0 WHERE itemCode IN (SELECT code FROM item_definition WHERE grade != 'NORMAL' AND category IN ('WEAPON','ARMOR','HELMET','BOOTS','AUXILIARY','ACCESSORY','RELIC'))")
            }
        }

        private val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE login_profile ADD COLUMN highestFloor INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE owned_item ADD COLUMN appraisedAttackPower INTEGER")
                db.execSQL("ALTER TABLE owned_item ADD COLUMN appraisedEffectChance INTEGER")
                db.execSQL("UPDATE owned_item SET appraisedAttackPower = MAX(1, ((SELECT attackPower FROM item_definition WHERE code = owned_item.itemCode) + 1) / 2 + (ABS(RANDOM()) % (((SELECT attackPower FROM item_definition WHERE code = owned_item.itemCode) * 3 / 2) - (((SELECT attackPower FROM item_definition WHERE code = owned_item.itemCode) + 1) / 2) + 1))) WHERE isIdentified = 1 AND itemCode IN (SELECT code FROM item_definition WHERE grade != 'NORMAL' AND attackPower > 0)")
                db.execSQL("UPDATE owned_item SET appraisedEffectChance = 1 + (ABS(RANDOM()) % 100) WHERE isIdentified = 1 AND itemCode IN (SELECT code FROM item_definition WHERE grade != 'NORMAL' AND specialEffect IN ('DOUBLE_SHOT_50','BLOCK_CHANCE_30','RANGED_ROOT_20','RANGED_POISON_SHOT_30','DODGE_COUNTER_30','FREE_MOVE_30','GOLD_BONUS_20','RANGED_BLOCK_20','RELIC_RANGED_PULL_30'))")
            }
        }

        private val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE owned_item SET durability = CASE (SELECT grade FROM item_definition WHERE code = owned_item.itemCode) WHEN 'HIGH' THEN 4 WHEN 'RARE' THEN 5 WHEN 'EPIC' THEN 6 WHEN 'UNIQUE' THEN 7 WHEN 'LEGENDARY' THEN 8 WHEN 'MYTHIC' THEN 9 ELSE 3 END WHERE itemCode IN (SELECT code FROM item_definition WHERE category IN ('WEAPON','ARMOR','HELMET','BOOTS','AUXILIARY','ACCESSORY','RELIC'))")
                db.execSQL("UPDATE owned_item SET durability = durability + (ABS(RANDOM()) % 11) WHERE isIdentified = 1 AND itemCode IN (SELECT code FROM item_definition WHERE grade != 'NORMAL' AND category IN ('WEAPON','ARMOR','HELMET','BOOTS','AUXILIARY','ACCESSORY','RELIC'))")
            }
        }

        private val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE login_profile ADD COLUMN pendingEstateLossCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE login_profile ADD COLUMN pendingEstateKeptNames TEXT")
            }
        }

        private val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE owned_item SET appraisedEffectChance = NULL")
            }
        }

        private val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                seedTravelingMerchantBoxes(db)
                db.execSQL("INSERT OR REPLACE INTO game_config VALUES ('traveling_merchant_interval_days',5,NULL,NULL)")
            }
        }

        private val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE login_profile ADD COLUMN lastMerchantFreeDay INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE login_profile ADD COLUMN merchantFreeClaimMask INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE owned_item ADD COLUMN isSellable INTEGER NOT NULL DEFAULT 1")
                db.execSQL("UPDATE login_profile SET merchantFreeClaimMask = 1 WHERE lastMerchantFreeDay != 0")
            }
        }

        private val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE monster_definition ADD COLUMN sensitivity INTEGER NOT NULL DEFAULT 4")
                listOf(
                    "spider" to 4, "wild_dog" to 6, "bandit" to 5, "slime" to 3,
                    "plague_rat" to 6, "drowned_dead" to 3, "spore_body" to 5,
                    "hook_jailer" to 5, "plague_bell_keeper" to 8,
                    "ash_arbalist" to 7, "cinder_gargoyle" to 6, "ember_deacon" to 8,
                    "molten_bombardier" to 7, "furnace_saint" to 10
                ).forEach { (code, sensitivity) ->
                    db.execSQL("UPDATE monster_definition SET sensitivity = ? WHERE code = ?", arrayOf<Any>(sensitivity, code))
                }
            }
        }

        private val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(db: SupportSQLiteDatabase) {
                seedDungeonChestRules(db)
            }
        }

        private val MIGRATION_34_35 = object : Migration(34, 35) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE item_definition SET storeType = 'MERCHANT_GENERAL' WHERE code IN ('gacha_normal','gacha_high')")
                db.execSQL("INSERT OR REPLACE INTO game_config VALUES ('general_store_gacha_price_multiplier',2,NULL,NULL)")
            }
        }

        private val MIGRATION_35_36 = object : Migration(35, 36) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE login_profile ADD COLUMN unlockedDungeonStartFloor INTEGER NOT NULL DEFAULT 1")
                db.execSQL("UPDATE login_profile SET unlockedDungeonStartFloor = 10 WHERE highestFloor >= 11")
            }
        }

        private val MIGRATION_36_37 = object : Migration(36, 37) {
            override fun migrate(db: SupportSQLiteDatabase) {
                seedRedMoonRules(db)
            }
        }

        private val MIGRATION_37_38 = object : Migration(37, 38) {
            override fun migrate(db: SupportSQLiteDatabase) = seedExpandedWeapons(db)
        }

        private val MIGRATION_38_39 = object : Migration(38, 39) {
            override fun migrate(db: SupportSQLiteDatabase) = seedExpandedWeapons(db)
        }

        private val MIGRATION_39_40 = object : Migration(39, 40) {
            override fun migrate(db: SupportSQLiteDatabase) = seedExpandedWeapons(db)
        }

        private val MIGRATION_40_41 = object : Migration(40, 41) {
            override fun migrate(db: SupportSQLiteDatabase) = seedExpandedWeapons(db)
        }

        private val MIGRATION_41_42 = object : Migration(41, 42) {
            override fun migrate(db: SupportSQLiteDatabase) = seedFloorsSixteenToTwentyFive(db)
        }

        private val CREATE_AND_SEED = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                seedMasterData(db)
            }
        }

        private fun createMasterTables(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS item_definition (code TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, category TEXT NOT NULL, grade TEXT NOT NULL, storeType TEXT NOT NULL, basePrice INTEGER NOT NULL, unitsPerPurchase INTEGER NOT NULL, assetPath TEXT NOT NULL, isConsumable INTEGER NOT NULL, maxStack INTEGER NOT NULL, attackPower INTEGER NOT NULL, attackTurnCost INTEGER NOT NULL, attackRange INTEGER NOT NULL, healthBonus INTEGER NOT NULL, detail TEXT, specialEffect TEXT, dropRate REAL NOT NULL, playerSheetPath TEXT, sortOrder INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS monster_definition (code TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, maxHp INTEGER NOT NULL, attackPower INTEGER NOT NULL, attackRange INTEGER NOT NULL, openingAttackRange INTEGER NOT NULL, moveDistance INTEGER NOT NULL, moveEveryTurns INTEGER NOT NULL, goldDrop INTEGER NOT NULL, goldDropRate REAL NOT NULL, spritePath TEXT NOT NULL, sensitivity INTEGER NOT NULL, sortOrder INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS appraisal_rule (grade TEXT NOT NULL PRIMARY KEY, cost INTEGER NOT NULL, successRate REAL NOT NULL, colorValue INTEGER NOT NULL, sortOrder INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS game_config (`key` TEXT NOT NULL PRIMARY KEY, intValue INTEGER, doubleValue REAL, textValue TEXT)")
            db.execSQL("CREATE TABLE IF NOT EXISTS monster_drop (monsterCode TEXT NOT NULL, itemCode TEXT NOT NULL, dropRate REAL NOT NULL, PRIMARY KEY(monsterCode, itemCode))")
            db.execSQL("CREATE TABLE IF NOT EXISTS monster_floor_spawn (floor INTEGER NOT NULL, spawnOrder INTEGER NOT NULL, monsterCode TEXT NOT NULL, `column` INTEGER NOT NULL, `row` INTEGER NOT NULL, PRIMARY KEY(floor, spawnOrder))")
            createDungeonInteractableTables(db)
        }

        private fun seedMasterData(db: SupportSQLiteDatabase) {
            val itemSql = "INSERT OR REPLACE INTO item_definition VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
            listOf<Array<Any?>>(
                arrayOf("torch", "횃불", "CONSUMABLE", "NORMAL", "GENERAL", 1, 5, "ui/items/item_torch.png", 1, 99, 0, 0, 0, 0, "사용한 현재 층 동안 모든 무기 공격력 +1", "FLOOR_ATTACK_PLUS_1", .10, null, 1),
                arrayOf("return_stone", "귀환석", "CONSUMABLE", "NORMAL", "GENERAL", 2, 1, "ui/items/item_return_stone.png", 1, 99, 0, 0, 0, 0, "현재 층에서 마을로 귀환", "생존 중 사용 가능", .10, null, 2),
                arrayOf("camping_kit", "야영 세트", "CONSUMABLE", "NORMAL", "GENERAL", 3, 1, "ui/items/item_camping_kit.png", 1, 99, 0, 0, 0, 0, "체력을 모두 회복", "야영 시 최대 체력 회복", .10, null, 3),
                arrayOf("fire_bomb", "화염병", "CONSUMABLE", "NORMAL", "GENERAL", 2, 1, "ui/items/item_fire_bomb.png", 1, 99, 5, 0, 0, 0, "3×3 화염 지대 · 2턴 · 턴당 피해 5 · 화상 3턴간 턴당 피해 3", "FIRE_ZONE_3X3_2T_BURN_3T", .10, null, 4),
                arrayOf("crude_sword", "조잡한 검", "WEAPON", "NORMAL", "BLACKSMITH", 5, 1, "ui/items/item_crude_sword.png", 0, 1, 3, 1, 1, 0, "공격 3 · 1턴 · 주변 1칸의 모든 적 공격", "ADJACENT_SWEEP", .05, "ui/dungeon/player/player_sword_animation_sheet.png", 10),
                arrayOf("crude_spear", "조잡한 창", "WEAPON", "NORMAL", "BLACKSMITH", 5, 1, "ui/items/item_crude_spear.png", 0, 1, 4, 2, 3, 0, "공격 4 · 2턴 · 직선 사거리 3 전체 찌르기", "LINE_THRUST", .05, "ui/dungeon/player/player_spear_animation_sheet.png", 11),
                arrayOf("crude_bow", "조잡한 활", "WEAPON", "NORMAL", "BLACKSMITH", 5, 1, "ui/items/item_crude_bow.png", 0, 1, 3, 1, 4, 0, "공격 3 · 1턴 · 사거리 4 · 50% 확률로 추가 사격", "DOUBLE_SHOT_50", .05, "ui/dungeon/player/player_bow_animation_sheet.png", 12),
                arrayOf("crude_gun", "조잡한 총", "WEAPON", "NORMAL", "BLACKSMITH", 5, 1, "ui/items/item_crude_gun.png", 0, 1, 5, 2, 5, 0, "공격 5 · 2턴 · 사거리 5 · 처치 시 직선 관통", "KILL_PIERCE", .05, "ui/dungeon/player/player_gun_animation_sheet.png", 13),
                arrayOf("crude_armor", "조잡한 갑옷", "ARMOR", "NORMAL", "BLACKSMITH", 5, 1, "ui/items/item_crude_armor.png", 0, 1, 0, 0, 0, 0, "방어 확률 30% · 방어 성공 시 피해 무효", "BLOCK_CHANCE_30", .05, null, 14),
                arrayOf("crude_helmet", "조잡한 투구", "HELMET", "NORMAL", "BLACKSMITH", 5, 1, "ui/items/item_crude_helmet.png", 0, 1, 0, 0, 0, 0, "방어 확률 30% · 방어 성공 시 피해 무효", "BLOCK_CHANCE_30", .05, null, 15),
                arrayOf("crude_boots", "조잡한 신발", "BOOTS", "NORMAL", "BLACKSMITH", 5, 1, "ui/items/item_crude_boots.png", 0, 1, 0, 0, 0, 0, "이동 시 50% 확률로 +1칸", "이동 거리 +1 확률 50%", .05, null, 16)
            ).forEach { db.execSQL(itemSql, it) }

            seedRareMonsterItems(db)
            seedExpandedWeapons(db)
            seedTravelingMerchantBoxes(db)
            seedFloorsSixToTen(db)
            seedFloorsElevenToFifteen(db)
            seedFloorsSixteenToTwentyFive(db)
            seedDungeonInteractables(db)
            seedDungeonChestRules(db)

            val monsterSql = "INSERT OR REPLACE INTO monster_definition VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)"
            listOf<Array<Any?>>(
                arrayOf("spider", "거미", 4, 1, 1, 2, 1, 1, 1, .70, "ui/dungeon/monsters/spider_animation_sheet.png", 4, 1),
                arrayOf("wild_dog", "들개", 6, 2, 1, 1, 2, 1, 1, .70, "ui/dungeon/monsters/wild_dog_animation_sheet.png", 6, 2),
                arrayOf("bandit", "도적", 8, 2, 2, 2, 1, 1, 2, .70, "ui/dungeon/monsters/bandit_animation_sheet.png", 5, 3),
                arrayOf("slime", "슬라임", 7, 1, 1, 1, 1, 2, 1, .70, "ui/dungeon/monsters/slime_animation_sheet.png", 3, 4)
            ).forEach { db.execSQL(monsterSql, it) }

            val appraisalSql = "INSERT OR REPLACE INTO appraisal_rule VALUES (?,?,?,?,?)"
            listOf<Array<Any?>>(
                arrayOf("HIGH", 0, .90, 0xFF8FC58A, 0), arrayOf("RARE", 0, .80, 0xFF4EA5FF, 1),
                arrayOf("EPIC", 0, .70, 0xFFC05CFF, 2), arrayOf("UNIQUE", 0, .60, 0xFFFFB13B, 3),
                arrayOf("LEGENDARY", 0, .50, 0xFFFF5151, 4), arrayOf("MYTHIC", 0, .40, 0xFFFFE27A, 5)
            ).forEach { db.execSQL(appraisalSql, it) }

            val configSql = "INSERT OR REPLACE INTO game_config VALUES (?,?,?,?)"
            listOf<Array<Any?>>(
                arrayOf("starting_gold", 10, null, null),
                arrayOf("initial_return_stones", 5, null, null),
                arrayOf("base_player_hp", 10, null, null),
                arrayOf("inventory_capacity", 25, null, null),
                arrayOf("storage_capacity", 20, null, null),
                arrayOf("base_vision", 2, null, null),
                arrayOf("torch_vision", 5, null, null),
                arrayOf("torch_duration_turns", 10, null, null),
                arrayOf("dungeon_monster_count_min", 5, null, null),
                arrayOf("dungeon_monster_count_max", 10, null, null),
                arrayOf("return_stone_combat_lock_floor", 11, null, null),
                arrayOf("traveling_merchant_interval_days", 5, null, null),
                arrayOf("general_store_gacha_price_multiplier", 2, null, null),
                arrayOf("red_moon_interval_days", 10, null, null),
                arrayOf("red_moon_monster_attack_percent", 150, null, null),
                arrayOf("red_moon_drop_rate_percent", 200, null, null),
                arrayOf("red_moon_return_floor_interval", 5, null, null)
            ).forEach { db.execSQL(configSql, it) }
        }

        private fun seedRedMoonRules(db: SupportSQLiteDatabase) {
            db.execSQL("INSERT OR REPLACE INTO game_config VALUES ('red_moon_interval_days',10,NULL,NULL)")
            db.execSQL("INSERT OR REPLACE INTO game_config VALUES ('red_moon_monster_attack_percent',150,NULL,NULL)")
            db.execSQL("INSERT OR REPLACE INTO game_config VALUES ('red_moon_drop_rate_percent',200,NULL,NULL)")
            db.execSQL("INSERT OR REPLACE INTO game_config VALUES ('red_moon_return_floor_interval',5,NULL,NULL)")
        }

        private fun seedExpandedWeapons(db: SupportSQLiteDatabase) {
            val sql = "INSERT OR REPLACE INTO item_definition VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
            ExpandedWeaponCatalog.all().forEach { item ->
                db.execSQL(sql, arrayOf<Any?>(
                    item.code, item.name, item.category, item.grade, item.storeType, item.basePrice,
                    item.unitsPerPurchase, item.assetPath, if (item.isConsumable) 1 else 0, item.maxStack,
                    item.attackPower, item.attackTurnCost, item.attackRange, item.healthBonus, item.detail,
                    item.specialEffect, item.dropRate, item.playerSheetPath, item.sortOrder
                ))
            }
            db.execSQL("INSERT OR REPLACE INTO game_config VALUES ('expanded_weapon_drop_percent',8,NULL,NULL)")
        }

        private fun seedTravelingMerchantBoxes(db: SupportSQLiteDatabase) {
            val itemSql = "INSERT OR REPLACE INTO item_definition VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
            listOf<Array<Any?>>(
                arrayOf("gacha_normal", "낡은 뽑기상자", "CONSUMABLE", "NORMAL", "MERCHANT_GENERAL", 100, 1, "ui/dungeon/loot/chest_normal.png", 1, 99, 0, 0, 0, 0, "당첨 30% · 당첨 시 골드 또는 장비", "GACHA_BOX_NORMAL", 0.0, null, 300),
                arrayOf("gacha_high", "고급 뽑기상자", "CONSUMABLE", "HIGH", "MERCHANT_GENERAL", 200, 1, "ui/dungeon/loot/chest_high.png", 1, 99, 0, 0, 0, 0, "당첨 30% · 당첨 시 골드 또는 고급 이상 장비", "GACHA_BOX_HIGH", 0.0, null, 301),
                arrayOf("gacha_rare", "레어 뽑기상자", "CONSUMABLE", "RARE", "MERCHANT", 400, 1, "ui/dungeon/loot/chest_rare.png", 1, 99, 0, 0, 0, 0, "당첨 30% · 당첨 시 골드 또는 레어 이상 장비", "GACHA_BOX_RARE", 0.0, null, 302),
                arrayOf("gacha_epic", "에픽 뽑기상자", "CONSUMABLE", "EPIC", "MERCHANT", 800, 1, "ui/dungeon/loot/chest_epic.png", 1, 99, 0, 0, 0, 0, "당첨 30% · 당첨 시 골드 또는 에픽 이상 장비", "GACHA_BOX_EPIC", 0.0, null, 303),
                arrayOf("gacha_unique", "유니크 뽑기상자", "CONSUMABLE", "UNIQUE", "MERCHANT", 1600, 1, "ui/dungeon/loot/chest_unique.png", 1, 99, 0, 0, 0, 0, "당첨 30% · 당첨 시 골드 또는 유니크 장비", "GACHA_BOX_UNIQUE", 0.0, null, 304),
                arrayOf("gacha_legendary", "전설 뽑기상자", "CONSUMABLE", "LEGENDARY", "MERCHANT", 3200, 1, "ui/dungeon/loot/chest_legendary.png", 1, 99, 0, 0, 0, 0, "당첨 30% · 당첨 시 골드 또는 전설 장비", "GACHA_BOX_LEGENDARY", 0.0, null, 305),
                arrayOf("gacha_mythic", "신화 뽑기상자", "CONSUMABLE", "MYTHIC", "MERCHANT", 6400, 1, "ui/dungeon/loot/chest_mythic.png", 1, 99, 0, 0, 0, 0, "당첨 30% · 당첨 시 골드 또는 신화 장비", "GACHA_BOX_MYTHIC", 0.0, null, 306)
            ).forEach { db.execSQL(itemSql, it) }
        }

        private fun createDungeonInteractableTables(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS dungeon_interactable_definition (code TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, healPercent INTEGER NOT NULL, assetPath TEXT NOT NULL, footprintWidth INTEGER NOT NULL, footprintHeight INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS dungeon_interactable_spawn (floor INTEGER NOT NULL, spawnOrder INTEGER NOT NULL, interactableCode TEXT NOT NULL, `column` INTEGER NOT NULL, `row` INTEGER NOT NULL, PRIMARY KEY(floor, spawnOrder))")
        }

        private fun seedDungeonInteractables(db: SupportSQLiteDatabase) {
            db.execSQL("INSERT OR REPLACE INTO dungeon_interactable_definition VALUES (?,?,?,?,?,?)", arrayOf<Any?>("healing_spring", "회복의 샘물", 50, "ui/dungeon/interactables/healing_spring.png", 2, 2))
            db.execSQL("INSERT OR REPLACE INTO dungeon_interactable_definition VALUES (?,?,?,?,?,?)", arrayOf<Any?>("angel_statue", "천사의 성상", 100, "ui/dungeon/interactables/angel_statue.png", 2, 2))
            db.execSQL("DELETE FROM dungeon_interactable_spawn")
            db.execSQL("INSERT OR REPLACE INTO game_config VALUES ('dungeon_healing_object_chance_percent',30,NULL,NULL)")
        }

        private fun seedRareMonsterItems(db: SupportSQLiteDatabase) {
            val itemSql = "INSERT OR REPLACE INTO item_definition VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
            listOf<Array<Any?>>(
                arrayOf("web_glove", "거미줄 장갑", "AUXILIARY", "HIGH", "DROP_ONLY", 8, 1, "ui/items/rare/item_web_glove.png", 0, 1, 0, 0, 0, 0, "원거리 공격 적중 시 20% 확률로 1턴 속박", "RANGED_ROOT_20", .0, null, 100),
                arrayOf("venom_dagger", "독니 단검", "AUXILIARY", "RARE", "DROP_ONLY", 15, 1, "ui/items/rare/item_venom_dagger.png", 0, 1, 2, 0, 0, 0, "원거리 공격 시 30% 확률로 피해 2 독침 추가 발사", "RANGED_POISON_SHOT_30", .0, null, 101),
                arrayOf("spider_eye_helmet", "거미눈 투구", "HELMET", "RARE", "DROP_ONLY", 18, 1, "ui/items/rare/item_spider_eye_helmet.png", 0, 1, 2, 0, 0, 0, "30% 확률로 공격 회피 후 피해 2 반격", "DODGE_COUNTER_30", .0, null, 102),
                arrayOf("spider_queen_heart", "거미 여왕의 심장", "ACCESSORY", "UNIQUE", "DROP_ONLY", 40, 1, "ui/items/rare/item_spider_queen_heart.png", 0, 1, 2, 0, 0, 0, "처치 시 주변 적에게 피해 2와 1턴 속박", "KILL_BIND_BURST", .0, null, 103),
                arrayOf("wild_dog_boots", "들개의 가죽신", "BOOTS", "HIGH", "DROP_ONLY", 9, 1, "ui/items/rare/item_wild_dog_boots.png", 0, 1, 0, 0, 0, 0, "이동 시 30% 확률로 행동을 소모하지 않으며 연속 발동 가능", "FREE_MOVE_30", .0, null, 110),
                arrayOf("fang_necklace", "송곳니 목걸이", "ACCESSORY", "RARE", "DROP_ONLY", 17, 1, "ui/items/rare/item_fang_necklace.png", 0, 1, 0, 0, 0, 0, "조건 없이 모든 무기 공격력 +1", "ATTACK_PLUS_1", .0, null, 111),
                arrayOf("bloody_hook", "피 묻은 갈고리검", "AUXILIARY", "RARE", "DROP_ONLY", 20, 1, "ui/items/rare/item_bloody_hook.png", 0, 1, 2, 0, 0, 0, "검 공격 시 추가 피해 2와 중첩 가능한 2턴 출혈", "SWORD_BLEED", .0, null, 112),
                arrayOf("alpha_fang", "무리 우두머리의 엄니", "AUXILIARY", "UNIQUE", "DROP_ONLY", 45, 1, "ui/items/rare/item_alpha_fang.png", 0, 1, 0, 0, 0, 0, "2칸 이상 이동 후 공격력 +2", "MOVE2_ATTACK_PLUS_2", .0, null, 113),
                arrayOf("thief_coin_pouch", "도적의 동전 주머니", "ACCESSORY", "HIGH", "DROP_ONLY", 10, 1, "ui/items/rare/item_thief_coin_pouch.png", 0, 1, 0, 0, 0, 0, "골드 드랍 성공 시 20% 확률로 1G 추가", "GOLD_BONUS_20", .0, null, 120),
                arrayOf("black_hood", "검은 두건", "HELMET", "RARE", "DROP_ONLY", 18, 1, "ui/items/rare/item_black_hood.png", 0, 1, 0, 0, 0, 0, "각 몬스터의 첫 공격을 100% 회피", "FIRST_ATTACK_DODGE", .0, null, 121),
                arrayOf("throwing_dagger", "도적의 투척 단검", "AUXILIARY", "RARE", "DROP_ONLY", 22, 1, "ui/items/rare/item_throwing_dagger.png", 0, 1, 3, 0, 0, 0, "창 공격 시 대상에게 추가 피해 3", "SPEAR_BONUS_3", .0, null, 122),
                arrayOf("greed_coin", "탐욕의 금화", "ACCESSORY", "UNIQUE", "DROP_ONLY", 50, 1, "ui/items/rare/item_greed_coin.png", 0, 1, 0, 0, 0, 0, "치명 피해 시 50G를 지불하고 HP 5로 부활, 던전당 1회", "REVIVE_50G", .0, null, 123),
                arrayOf("slime_shield", "점액 방패", "AUXILIARY", "HIGH", "DROP_ONLY", 9, 1, "ui/items/rare/item_slime_shield.png", 0, 1, 0, 0, 0, 0, "원거리 몬스터 공격을 20% 확률로 무효화", "RANGED_BLOCK_20", .0, null, 130),
                arrayOf("regen_slime_armor", "재생 점액 갑옷", "ARMOR", "RARE", "DROP_ONLY", 22, 1, "ui/items/rare/item_regen_slime_armor.png", 0, 1, 0, 0, 0, 0, "5턴 동안 피해가 없으면 HP 1 회복, 층당 최대 2회", "REGEN_5T", .0, null, 131),
                arrayOf("splitting_core", "분열하는 핵", "ACCESSORY", "UNIQUE", "DROP_ONLY", 60, 1, "ui/items/rare/item_splitting_core.png", 0, 1, 0, 0, 0, 0, "치명 피해를 HP 1로 생존하고 즉시 파괴", "LETHAL_SURVIVE", .0, null, 132)
            ).forEach { db.execSQL(itemSql, it) }

            val dropSql = "INSERT OR REPLACE INTO monster_drop VALUES (?,?,?)"
            listOf<Array<Any?>>(
                arrayOf("spider", "web_glove", .05), arrayOf("spider", "venom_dagger", .02),
                arrayOf("spider", "spider_eye_helmet", .02), arrayOf("spider", "spider_queen_heart", .005),
                arrayOf("wild_dog", "wild_dog_boots", .05), arrayOf("wild_dog", "fang_necklace", .02),
                arrayOf("wild_dog", "bloody_hook", .02), arrayOf("wild_dog", "alpha_fang", .005),
                arrayOf("bandit", "thief_coin_pouch", .05), arrayOf("bandit", "black_hood", .02),
                arrayOf("bandit", "throwing_dagger", .02), arrayOf("bandit", "greed_coin", .005),
                arrayOf("slime", "slime_shield", .05), arrayOf("slime", "regen_slime_armor", .02),
                arrayOf("slime", "splitting_core", .005)
            ).forEach { db.execSQL(dropSql, it) }
        }

        private fun seedFloorsSixToTen(db: SupportSQLiteDatabase) {
            val monsterSql = "INSERT OR REPLACE INTO monster_definition VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)"
            listOf<Array<Any?>>(
                arrayOf("plague_rat", "역병쥐", 7, 2, 1, 2, 2, 1, 2, .70, "ui/dungeon/monsters/floors_06_10/plague_rat_animation_sheet.png", 6, 20),
                arrayOf("drowned_dead", "익사한 망자", 13, 3, 1, 1, 1, 1, 2, .70, "ui/dungeon/monsters/floors_06_10/drowned_dead_animation_sheet.png", 3, 21),
                arrayOf("spore_body", "균사 포자체", 8, 2, 3, 3, 1, 1, 2, .70, "ui/dungeon/monsters/floors_06_10/spore_body_animation_sheet.png", 5, 22),
                arrayOf("hook_jailer", "갈고리 간수", 11, 4, 2, 2, 1, 1, 3, .70, "ui/dungeon/monsters/floors_06_10/hook_jailer_animation_sheet.png", 5, 23),
                arrayOf("plague_bell_keeper", "역병의 종지기", 30, 4, 4, 4, 1, 1, 8, 1.0, "ui/dungeon/monsters/floors_06_10/plague_bell_keeper_animation_sheet.png", 8, 24)
            ).forEach { db.execSQL(monsterSql, it) }

            val itemSql = "INSERT OR REPLACE INTO item_definition VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
            listOf<Array<Any?>>(
                arrayOf("flooded_star_map", "침수된 별지도", "RELIC", "RARE", "DROP_ONLY", 30, 1, "ui/items/relics/item_flooded_star_map.png", 0, 1, 0, 0, 0, 0, "계단 방향을 항상 표시하고 5칸 내 전리품을 감지", "RELIC_EXPLORE_SENSE", .0, null, 200),
                arrayOf("plague_doctor_censer", "역병 의사의 향로", "RELIC", "EPIC", "DROP_ONLY", 40, 1, "ui/items/relics/item_plague_doctor_censer.png", 0, 1, 0, 0, 0, 0, "독·화상·출혈 지속시간 1턴 감소", "RELIC_AILMENT_REDUCE_1", .0, null, 201),
                arrayOf("gravekeeper_chain", "묘지기의 쇠사슬", "RELIC", "EPIC", "DROP_ONLY", 45, 1, "ui/items/relics/item_gravekeeper_chain.png", 0, 1, 0, 0, 0, 0, "원거리 적중 시 30% 확률로 적을 1칸 끌어당김", "RELIC_RANGED_PULL_30", .0, null, 202),
                arrayOf("sleeping_saint_chalice", "잠든 성자의 성배", "RELIC", "UNIQUE", "DROP_ONLY", 60, 1, "ui/items/relics/item_sleeping_saint_chalice.png", 0, 1, 0, 0, 0, 0, "층마다 최초 HP 3 이하 진입 시 HP 4 회복", "RELIC_LOW_HP_HEAL_4", .0, null, 203),
                arrayOf("funeral_bell_heart", "장례종의 심장", "RELIC", "UNIQUE", "DROP_ONLY", 70, 1, "ui/items/relics/item_funeral_bell_heart.png", 0, 1, 0, 0, 0, 0, "처치 후 다음 공격력 +2, 반경 6칸의 적이 즉시 감지", "RELIC_KILL_POWER_ALERT", .0, null, 204)
            ).forEach { db.execSQL(itemSql, it) }

            val dropSql = "INSERT OR REPLACE INTO monster_drop VALUES (?,?,?)"
            listOf<Array<Any?>>(
                arrayOf("plague_rat", "flooded_star_map", .01),
                arrayOf("spore_body", "plague_doctor_censer", .02),
                arrayOf("hook_jailer", "gravekeeper_chain", .02),
                arrayOf("drowned_dead", "sleeping_saint_chalice", .01),
                arrayOf("plague_bell_keeper", "funeral_bell_heart", .20)
            ).forEach { db.execSQL(dropSql, it) }

            val spawnSql = "INSERT OR REPLACE INTO monster_floor_spawn VALUES (?,?,?,?,?)"
            val early = listOf("spider" to (11 to 3), "wild_dog" to (15 to 8), "bandit" to (18 to 4), "slime" to (8 to 9))
            (1..5).forEach { floor -> early.forEachIndexed { index, entry -> db.execSQL(spawnSql, arrayOf<Any?>(floor, index, entry.first, entry.second.first, entry.second.second)) } }
            val floors = mapOf(
                6 to listOf("plague_rat" to (9 to 3), "plague_rat" to (14 to 8), "drowned_dead" to (18 to 4)),
                7 to listOf("plague_rat" to (10 to 8), "spore_body" to (15 to 3), "spore_body" to (19 to 8)),
                8 to listOf("drowned_dead" to (9 to 3), "hook_jailer" to (15 to 8), "hook_jailer" to (20 to 4)),
                9 to listOf("plague_rat" to (8 to 8), "drowned_dead" to (12 to 3), "spore_body" to (17 to 8), "hook_jailer" to (20 to 3)),
                10 to listOf("drowned_dead" to (12 to 3), "plague_bell_keeper" to (18 to 6), "drowned_dead" to (21 to 9))
            )
            floors.forEach { (floor, spawns) -> spawns.forEachIndexed { index, entry -> db.execSQL(spawnSql, arrayOf<Any?>(floor, index, entry.first, entry.second.first, entry.second.second)) } }
        }

        private fun seedFloorsElevenToFifteen(db: SupportSQLiteDatabase) {
            val monsterSql = "INSERT OR REPLACE INTO monster_definition VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)"
            listOf<Array<Any?>>(
                arrayOf("ash_arbalist", "잿빛 쇠뇌병", 10, 3, 5, 5, 1, 1, 3, .70, "ui/dungeon/monsters/floors_11_15/ash_arbalist_animation_sheet.png", 7, 30),
                arrayOf("cinder_gargoyle", "불씨 가고일", 12, 3, 3, 3, 2, 1, 3, .70, "ui/dungeon/monsters/floors_11_15/cinder_gargoyle_animation_sheet.png", 6, 31),
                arrayOf("ember_deacon", "잿불 부제", 11, 2, 4, 4, 1, 1, 3, .70, "ui/dungeon/monsters/floors_11_15/ember_deacon_animation_sheet.png", 8, 32),
                arrayOf("molten_bombardier", "용융 포격수", 15, 4, 4, 4, 1, 1, 4, .70, "ui/dungeon/monsters/floors_11_15/molten_bombardier_animation_sheet.png", 7, 33),
                arrayOf("furnace_saint", "타락한 용광로 성자", 38, 5, 5, 5, 1, 1, 10, 1.0, "ui/dungeon/monsters/floors_11_15/furnace_saint_animation_sheet.png", 10, 34)
            ).forEach { db.execSQL(monsterSql, it) }

            val itemSql = "INSERT OR REPLACE INTO item_definition VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
            listOf<Array<Any?>>(
                arrayOf("ash_compass", "재의 나침반", "RELIC", "RARE", "DROP_ONLY", 35, 1, "ui/items/relics/floors_11_15/item_ash_compass.png", 0, 1, 0, 0, 0, 0, "가장 가까운 생존 몬스터의 방향과 거리를 표시", "RELIC_NEAREST_ENEMY_SENSE", .0, null, 220),
                arrayOf("cold_iron_rosary", "냉철 묵주", "RELIC", "EPIC", "DROP_ONLY", 45, 1, "ui/items/relics/floors_11_15/item_cold_iron_rosary.png", 0, 1, 0, 0, 0, 0, "층마다 처음 받는 피해를 3 감소", "RELIC_FIRST_HIT_MINUS_3", .0, null, 221),
                arrayOf("ember_eater_medal", "불씨 포식 훈장", "RELIC", "EPIC", "DROP_ONLY", 50, 1, "ui/items/relics/floors_11_15/item_ember_eater_medal.png", 0, 1, 0, 0, 0, 0, "몬스터 3마리 처치마다 HP 1 회복", "RELIC_THIRD_KILL_HEAL_1", .0, null, 222),
                arrayOf("forgemaster_tongs", "대장장이장의 집게", "RELIC", "UNIQUE", "DROP_ONLY", 65, 1, "ui/items/relics/floors_11_15/item_forgemaster_tongs.png", 0, 1, 0, 0, 0, 0, "화염병 사거리 4, 화염 지대 3턴으로 강화", "RELIC_FIRE_BOMB_ENHANCE", .0, null, 223),
                arrayOf("furnace_core", "성자의 용광로 핵", "RELIC", "UNIQUE", "DROP_ONLY", 80, 1, "ui/items/relics/floors_11_15/item_furnace_core.png", 0, 1, 0, 0, 0, 0, "모든 무기 공격력 +1, 5회 공격마다 자신에게 피해 1", "RELIC_ATTACK_PLUS_1_RECOIL", .0, null, 224)
            ).forEach { db.execSQL(itemSql, it) }

            val dropSql = "INSERT OR REPLACE INTO monster_drop VALUES (?,?,?)"
            listOf<Array<Any?>>(
                arrayOf("ash_arbalist", "ash_compass", .02),
                arrayOf("cinder_gargoyle", "cold_iron_rosary", .02),
                arrayOf("ember_deacon", "ember_eater_medal", .02),
                arrayOf("molten_bombardier", "forgemaster_tongs", .01),
                arrayOf("furnace_saint", "furnace_core", .20)
            ).forEach { db.execSQL(dropSql, it) }

            val spawnSql = "INSERT OR REPLACE INTO monster_floor_spawn VALUES (?,?,?,?,?)"
            val floors = mapOf(
                11 to listOf("ash_arbalist" to (10 to 3), "ash_arbalist" to (16 to 8), "ash_arbalist" to (21 to 4)),
                12 to listOf("cinder_gargoyle" to (10 to 8), "ash_arbalist" to (16 to 3), "cinder_gargoyle" to (21 to 8)),
                13 to listOf("ember_deacon" to (10 to 3), "molten_bombardier" to (16 to 8), "ember_deacon" to (21 to 4)),
                14 to listOf("ash_arbalist" to (8 to 8), "cinder_gargoyle" to (13 to 3), "ember_deacon" to (18 to 8), "molten_bombardier" to (21 to 3)),
                15 to listOf("ash_arbalist" to (10 to 3), "ember_deacon" to (14 to 9), "furnace_saint" to (19 to 6), "ash_arbalist" to (22 to 2))
            )
            floors.forEach { (floor, spawns) -> spawns.forEachIndexed { index, entry -> db.execSQL(spawnSql, arrayOf<Any?>(floor, index, entry.first, entry.second.first, entry.second.second)) } }
        }

        private fun seedFloorsSixteenToTwentyFive(db: SupportSQLiteDatabase) {
            val monsterSql = "INSERT OR REPLACE INTO monster_definition VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)"
            listOf<Array<Any?>>(
                arrayOf("rift_hound", "균열 들개", 20, 5, 1, 1, 2, 1, 5, .70, "ui/dungeon/monsters/floors_21_25/void_hound_animation_sheet.png", 7, 50),
                arrayOf("infernal_lancer", "지옥 창병", 24, 6, 3, 3, 1, 1, 6, .70, "ui/dungeon/monsters/floors_21_25/abyss_lancer_animation_sheet.png", 7, 51),
                arrayOf("void_oracle", "공허 예언자", 20, 5, 5, 5, 1, 1, 5, .70, "ui/dungeon/monsters/floors_21_25/starved_oracle_animation_sheet.png", 9, 52),
                arrayOf("hellshot_apostle", "화약 사도", 22, 7, 5, 5, 1, 1, 6, .70, "ui/dungeon/monsters/floors_21_25/blackpowder_apostle_animation_sheet.png", 8, 53),
                arrayOf("eclipse_archon_20", "일식의 집정관", 55, 8, 4, 4, 1, 1, 25, 1.0, "ui/dungeon/monsters/floors_21_25/eclipse_archon_animation_sheet.png", 11, 54),
                arrayOf("abyss_maw_20", "심연의 아귀", 65, 9, 2, 2, 2, 1, 25, 1.0, "ui/dungeon/monsters/floors_21_25/abyss_maw_animation_sheet.png", 10, 55),
                arrayOf("void_hound", "공허 사냥개", 28, 7, 1, 1, 2, 1, 8, .70, "ui/dungeon/monsters/floors_21_25/void_hound_animation_sheet.png", 8, 60),
                arrayOf("abyss_lancer", "심연 창기병", 32, 8, 3, 3, 1, 1, 9, .70, "ui/dungeon/monsters/floors_21_25/abyss_lancer_animation_sheet.png", 8, 61),
                arrayOf("starved_oracle", "굶주린 신탁", 26, 7, 6, 6, 1, 1, 8, .70, "ui/dungeon/monsters/floors_21_25/starved_oracle_animation_sheet.png", 10, 62),
                arrayOf("blackpowder_apostle", "흑화약 사도", 28, 9, 6, 6, 1, 1, 9, .70, "ui/dungeon/monsters/floors_21_25/blackpowder_apostle_animation_sheet.png", 9, 63),
                arrayOf("eclipse_archon_25", "검은 태양의 집정관", 80, 10, 5, 5, 1, 1, 50, 1.0, "ui/dungeon/monsters/floors_21_25/eclipse_archon_animation_sheet.png", 12, 64),
                arrayOf("abyss_maw_25", "별을 삼키는 아귀", 95, 11, 2, 2, 2, 1, 50, 1.0, "ui/dungeon/monsters/floors_21_25/abyss_maw_animation_sheet.png", 11, 65),
                arrayOf("mimic_21_25", "성소의 미믹", 42, 9, 2, 2, 2, 1, 40, 1.0, "ui/dungeon/monsters/mimic/mimic_animation_sheet.png", 9, 66)
            ).forEach { db.execSQL(monsterSql, it) }

            val spawnSql = "INSERT OR REPLACE INTO monster_floor_spawn VALUES (?,?,?,?,?)"
            val lowerPool = listOf("rift_hound", "infernal_lancer", "void_oracle", "hellshot_apostle")
            val upperPool = listOf("void_hound", "abyss_lancer", "starved_oracle", "blackpowder_apostle")
            val positions = listOf(7 to 3, 12 to 8, 17 to 4, 21 to 8)
            fun seedFloor(floor: Int, pool: List<String>, bosses: List<String> = emptyList()) {
                (pool + bosses).forEachIndexed { index, code ->
                    val position = positions[index % positions.size]
                    db.execSQL(spawnSql, arrayOf<Any?>(floor, index, code, position.first, position.second))
                }
            }
            (16..19).forEach { seedFloor(it, lowerPool) }
            seedFloor(20, lowerPool, listOf("eclipse_archon_20", "abyss_maw_20"))
            (21..24).forEach { seedFloor(it, upperPool) }
            seedFloor(25, upperPool, listOf("eclipse_archon_25", "abyss_maw_25"))

            val dropSql = "INSERT OR REPLACE INTO monster_drop VALUES (?,?,?)"
            listOf<Array<Any?>>(
                arrayOf("rift_hound", "exp_epic_sword_01", .08), arrayOf("infernal_lancer", "exp_epic_spear_02", .08),
                arrayOf("void_oracle", "exp_epic_bow_03", .08), arrayOf("hellshot_apostle", "exp_epic_gun_00", .08),
                arrayOf("void_hound", "exp_unique_sword_00", .08), arrayOf("abyss_lancer", "exp_unique_spear_01", .08),
                arrayOf("starved_oracle", "exp_unique_bow_02", .08), arrayOf("blackpowder_apostle", "exp_unique_gun_03", .08),
                arrayOf("eclipse_archon_20", "exp_legendary_spear_00", .15), arrayOf("eclipse_archon_20", "exp_mythic_bow_00", .10),
                arrayOf("abyss_maw_20", "exp_legendary_sword_03", .15), arrayOf("abyss_maw_20", "exp_mythic_gun_01", .10),
                arrayOf("eclipse_archon_25", "exp_legendary_bow_01", .18), arrayOf("eclipse_archon_25", "exp_mythic_spear_03", .12),
                arrayOf("abyss_maw_25", "exp_legendary_gun_02", .18), arrayOf("abyss_maw_25", "exp_mythic_sword_02", .12)
            ).forEach { db.execSQL(dropSql, it) }

        }

        private fun seedDungeonChestRules(db: SupportSQLiteDatabase) {
            db.execSQL("INSERT OR REPLACE INTO game_config VALUES ('dungeon_chest_spawn_percent',25,NULL,NULL)")
            db.execSQL("INSERT OR REPLACE INTO game_config VALUES ('dungeon_chest_mimic_percent',25,NULL,NULL)")
            val sql = "INSERT OR REPLACE INTO monster_definition (code,name,maxHp,attackPower,attackRange,openingAttackRange,moveDistance,moveEveryTurns,goldDrop,goldDropRate,spritePath,sensitivity,sortOrder) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)"
            listOf<Array<Any?>>(
                arrayOf("mimic_01_05", "굶주린 미믹", 8, 2, 1, 1, 1, 1, 4, 1.0, "ui/dungeon/monsters/mimic/mimic_animation_sheet.png", 5, 40),
                arrayOf("mimic_06_10", "침수된 미믹", 16, 4, 1, 1, 1, 1, 10, 1.0, "ui/dungeon/monsters/mimic/mimic_animation_sheet.png", 6, 41),
                arrayOf("mimic_11_15", "화로의 미믹", 22, 5, 1, 1, 1, 1, 18, 1.0, "ui/dungeon/monsters/mimic/mimic_animation_sheet.png", 7, 42),
                arrayOf("mimic_16_20", "심연의 미믹", 30, 7, 1, 1, 2, 1, 30, 1.0, "ui/dungeon/monsters/mimic/mimic_animation_sheet.png", 8, 43)
            ).forEach { db.execSQL(sql, it) }
        }

        @Volatile
        private var instance: GameDatabase? = null

        fun getInstance(context: Context): GameDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    GameDatabase::class.java,
                    "crpg_game.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33, MIGRATION_33_34, MIGRATION_34_35, MIGRATION_35_36, MIGRATION_36_37, MIGRATION_37_38, MIGRATION_38_39, MIGRATION_39_40, MIGRATION_40_41, MIGRATION_41_42).addCallback(CREATE_AND_SEED).build().also { instance = it }
            }
        }
    }
}
