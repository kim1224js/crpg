package es.kim.crpg.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [LoginProfileEntity::class, OwnedItemEntity::class],
    version = 1,
    exportSchema = true
)
abstract class GameDatabase : RoomDatabase() {
    abstract fun loginProfileDao(): LoginProfileDao
    abstract fun ownedItemDao(): OwnedItemDao

    companion object {
        @Volatile
        private var instance: GameDatabase? = null

        fun getInstance(context: Context): GameDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    GameDatabase::class.java,
                    "crpg_game.db"
                ).build().also { instance = it }
            }
        }
    }
}
