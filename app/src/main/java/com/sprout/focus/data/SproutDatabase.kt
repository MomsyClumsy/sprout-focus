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
        BlockedApp::class, Experiment::class,
    ],
    version = 8,
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

        /**
         * Эксперименты над собой.
         *
         * Единственная таблица с состоянием, которое не выводится из событий:
         * приложение обещало неделю вести себя иначе, и обещание должно
         * пережить перезапуск. Nullable-поля — те, что заполняются на финише.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS experiments (" +
                        "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                        "hypothesis TEXT NOT NULL, " +
                        "startedAt INTEGER NOT NULL, " +
                        "endsAt INTEGER NOT NULL, " +
                        "baselinePercent INTEGER NOT NULL, " +
                        "baselineCount INTEGER NOT NULL, " +
                        "endedAt INTEGER, " +
                        "outcome TEXT, " +
                        "resultPercent INTEGER, " +
                        "observations INTEGER)"
                )
            }
        }

        /**
         * Итог эксперимента: увиден ли он и закрепили ли изменение.
         *
         * Неделя кончается сама по часам, а прочитать итог человек может
         * через три дня — поэтому «когда кончилось» и «когда человек это
         * увидел» приходится хранить порознь.
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE experiments ADD COLUMN resolvedAt INTEGER")
                db.execSQL("ALTER TABLE experiments ADD COLUMN kept INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE experiments ADD COLUMN succeeded INTEGER")
            }
        }

        /**
         * Все миграции подряд.
         *
         * Отдельным списком, потому что его читает не только [build]:
         * тест на устройстве поднимает базу версии 4 и прогоняет через
         * тот же самый набор. Проверять миграции копией списка — значит
         * проверять копию, а не то, что стоит у человека на телефоне.
         */
        val MIGRATIONS: Array<Migration> =
            arrayOf(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)

        fun build(context: Context): SproutDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                SproutDatabase::class.java,
                "sprout.db"
            )
                // fallbackToDestructiveMigration намеренно убран.
                // Пропущенная миграция теперь роняет приложение на старте —
                // это заметно сразу, в отличие от тихо стёртых данных.
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
