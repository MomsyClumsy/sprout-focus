package com.sprout.focus.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Task::class, Event::class, Session::class, Garden::class, GrownPlant::class],
    version = 5,
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

        fun build(context: Context): SproutDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                SproutDatabase::class.java,
                "sprout.db"
            )
                // fallbackToDestructiveMigration намеренно убран.
                // Пропущенная миграция теперь роняет приложение на старте —
                // это заметно сразу, в отличие от тихо стёртых данных.
                .addMigrations(MIGRATION_4_5)
                .build()
    }
}
