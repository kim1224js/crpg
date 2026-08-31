package es.kim.crpg.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface GameMasterDao {
    @Query("SELECT * FROM item_definition ORDER BY sortOrder")
    fun getItems(): List<ItemDefinitionEntity>

    @Query("SELECT * FROM item_definition WHERE code = :code LIMIT 1")
    fun getItem(code: String): ItemDefinitionEntity?

    @Query("SELECT * FROM monster_definition ORDER BY sortOrder")
    fun getMonsters(): List<MonsterDefinitionEntity>

    @Query("SELECT * FROM monster_drop ORDER BY monsterCode, itemCode")
    fun getMonsterDrops(): List<MonsterDropEntity>

    @Query("SELECT * FROM monster_floor_spawn ORDER BY floor, spawnOrder")
    fun getMonsterFloorSpawns(): List<MonsterFloorSpawnEntity>

    @Query("SELECT * FROM dungeon_interactable_definition ORDER BY code")
    fun getDungeonInteractableDefinitions(): List<DungeonInteractableDefinitionEntity>

    @Query("SELECT * FROM dungeon_interactable_spawn ORDER BY floor, spawnOrder")
    fun getDungeonInteractableSpawns(): List<DungeonInteractableSpawnEntity>

    @Query("SELECT * FROM appraisal_rule ORDER BY sortOrder")
    fun getAppraisalRules(): List<AppraisalRuleEntity>

    @Query("SELECT * FROM game_config")
    fun getGameConfigs(): List<GameConfigEntity>

    @Query("SELECT intValue FROM game_config WHERE `key` = :key LIMIT 1")
    fun getConfigInt(key: String): Int?

    @Upsert
    fun saveConfig(config: GameConfigEntity)
}
