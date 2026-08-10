package com.sprout.focus.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Task::class, Event::class, Session::class, Garden::class, GrownPlant::class,
        BlockedApp::class,
    ],
    version = 6,
    exportSchema = false
)
abstract class SproutDatabase : RoomDatabase() {

    abstract fun dao(): SproutDao

    companion object {

        /**
         * Поля напоминания.
         *
         * Первая настоящая миграция: до неё база просто пересоздавалась
         * при каждой смене схемы. Дальше так нельзя — на телефоне уже
         * накоплены серия, рост растения и события, а восстановить их
         * неоткуда: всё живёт локально и никуда не выгружается.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN remindMinuteOfDay INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN remindDaysMask INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN remindNextAt INTEGER")
            }
        }

        /**
         * Список отвлекающих приложений.
         *
         * Схема таблицы должна совпасть с тем, что Room ждёт от [BlockedApp],
         * вплоть до NOT NULL: иначе приложение упадёт на старте с жалобой
         * на расхождение — что и правильно, тихое расхождение хуже.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS blocked_apps (" +
                        "packageName TEXT NOT NULL PRIMARY KEY, " +
                        "label TEXT NOT NULL, " +
                        "addedAt INTEGER NOT NULL)"
                )
            }
        }

        fun build(context: Context): SproutDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                SproutDatabase::class.java,
                "sprout.db"
            )
                // fallbackToDestructiveMigration намеренно убран.
                // Пропущенная миграция теперь роняет приложение на старте —
                // это заметно сразу, в отличие от тихо стёртых данных.
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
                .build()
    }
}
