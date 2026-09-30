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
        const val VERSION = 3
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
        )

        fun create(context: Context): RicordellaDatabase =
            Room.databaseBuilder(context, RicordellaDatabase::class.java, FILE_NAME)
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
