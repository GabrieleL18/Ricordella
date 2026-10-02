package com.ricordella.app.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ricordella.app.data.local.converter.RoomConverters
import com.ricordella.app.data.local.dao.AttachmentDao
import com.ricordella.app.data.local.dao.BackupDao
import com.ricordella.app.data.local.dao.ItemDao
import com.ricordella.app.data.local.dao.MaintenanceDao
import com.ricordella.app.data.local.dao.PersonDao
import com.ricordella.app.data.local.dao.ReminderDao
import com.ricordella.app.domain.model.Attachment
import com.ricordella.app.domain.model.Category
import com.ricordella.app.domain.model.Item
import com.ricordella.app.domain.model.MaintenanceRecord
import com.ricordella.app.domain.model.Person
import com.ricordella.app.domain.model.PersonItemCrossRef
import com.ricordella.app.domain.model.RecurrenceRule
import com.ricordella.app.domain.model.Reminder
import com.ricordella.app.domain.model.ReminderCompletion
import com.ricordella.app.domain.model.ReminderItemCrossRef
import com.ricordella.app.domain.model.ReminderPersonCrossRef

@Database(
    entities = [
        Person::class,
        Category::class,
        Item::class,
        RecurrenceRule::class,
        Reminder::class,
        ReminderCompletion::class,
        MaintenanceRecord::class,
        Attachment::class,
        ReminderPersonCrossRef::class,
        ReminderItemCrossRef::class,
        PersonItemCrossRef::class,
    ],
    version = RicordellaDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class)
abstract class RicordellaDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
    abstract fun personDao(): PersonDao
    abstract fun itemDao(): ItemDao
    abstract fun maintenanceDao(): MaintenanceDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val VERSION = 8
        private const val FILE_NAME = "ricordella.db"

        /**
         * Migrazioni dello schema. Ogni nuova versione deve aggiungere qui la sua Migration:
         * non si usa mai fallbackToDestructiveMigration, i dati dell'utente non devono sparire.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(
            // 1 → 2: eventi di più giorni.
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE reminder ADD COLUMN endDate INTEGER")
                }
            },
            // 2 → 3: dettagli di viaggio delle vacanze.
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE reminder ADD COLUMN trip TEXT")
                }
            },
            // 4 → 5: registro dei costi (tipo di spesa e litri dei rifornimenti).
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE maintenance_record ADD COLUMN kind TEXT NOT NULL DEFAULT 'SERVICE'")
                    db.execSQL("ALTER TABLE maintenance_record ADD COLUMN liters REAL")
                }
            },
            // 5 → 6: ricorrenze contate dall'ultima volta.
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE recurrence_rule ADD COLUMN fromLastDone INTEGER NOT NULL DEFAULT 0")
                }
            },
            // 6 → 7: sveglie in pausa fino a una data.
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE reminder ADD COLUMN pausedUntil INTEGER")
                }
            },
            // 7 → 8: pagamenti a rate.
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE reminder ADD COLUMN plan TEXT")
                }
            },
            // 3 → 4: anno di nascita dei compleanni.
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE reminder ADD COLUMN birthYear INTEGER")
                }
            },
        )

        fun create(context: Context, fileName: String = FILE_NAME): RicordellaDatabase =
            Room.databaseBuilder(context, RicordellaDatabase::class.java, fileName)
                .addMigrations(*MIGRATIONS)
                .addCallback(SeedCategoriesCallback)
                .build()
    }
}

/** Inserisce le categorie predefinite alla creazione del database. */
private object SeedCategoriesCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        BuiltInCategories.all.forEach { category ->
            db.execSQL(
                "INSERT INTO category (id, name, itemGroup, kind, isBuiltIn, sortOrder) VALUES (?, ?, ?, ?, 1, ?)",
                arrayOf<Any?>(category.id, category.name, category.itemGroup.name, category.kind?.name, category.sortOrder),
            )
        }
    }
}
