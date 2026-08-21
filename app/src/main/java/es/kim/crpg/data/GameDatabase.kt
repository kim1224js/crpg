package es.kim.crpg.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [LoginProfileEntity::class, OwnedItemEntity::class],
    version = 2,
    exportSchema = true
)
abstract class GameDatabase : RoomDatabase() {
    abstract fun loginProfileDao(): LoginProfileDao
    abstract fun ownedItemDao(): OwnedItemDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE login_profile ADD COLUMN gold INTEGER NOT NULL DEFAULT 10")
            }
        }

        @Volatile
        private var instance: GameDatabase? = null

        fun getInstance(context: Context): GameDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    GameDatabase::class.java,
                    "crpg_game.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
        }
    }
}
