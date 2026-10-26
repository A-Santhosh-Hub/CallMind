package com.example.callmind.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.callmind.data.local.daos.BlockedCallDao
import com.example.callmind.data.local.daos.CallRecordingDao
import com.example.callmind.data.local.daos.NoteDao
import com.example.callmind.data.local.daos.NumberRuleDao
import com.example.callmind.data.local.daos.QuickReplyDao
import com.example.callmind.data.local.daos.ReminderDao
import com.example.callmind.data.local.entities.BlockedCallEntity
import com.example.callmind.data.local.entities.CallRecordEntity
import com.example.callmind.data.local.entities.CallRecordingEntity
import com.example.callmind.data.local.entities.ContactEntity
import com.example.callmind.data.local.entities.NoteEntity
import com.example.callmind.data.local.entities.NumberRuleEntity
import com.example.callmind.data.local.entities.QuickReplyEntity
import com.example.callmind.data.local.entities.ReminderEntity

@Database(
    entities = [
        NoteEntity::class,
        ReminderEntity::class,
        ContactEntity::class,
        CallRecordEntity::class,
        QuickReplyEntity::class,
        NumberRuleEntity::class,
        BlockedCallEntity::class,
        CallRecordingEntity::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CallMindDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun reminderDao(): ReminderDao
    abstract fun quickReplyDao(): QuickReplyDao
    abstract fun numberRuleDao(): NumberRuleDao
    abstract fun blockedCallDao(): BlockedCallDao
    abstract fun callRecordingDao(): CallRecordingDao

    companion object {
        /** Adds call recordings (phone mode) without touching existing data. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `call_recordings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `filePath` TEXT NOT NULL, `phoneNumber` TEXT NOT NULL, `contactName` TEXT, `startedAt` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `isIncoming` INTEGER NOT NULL)"
                )
            }
        }

        /** Adds call-blocking tables without touching existing data. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `number_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `phoneNumber` TEXT NOT NULL, `name` TEXT, `type` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `blocked_calls` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `phoneNumber` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `reason` TEXT NOT NULL)"
                )
            }
        }

        /** Adds quick replies without touching existing notes and reminders. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `quick_replies` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`message` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
            }
        }
    }
}
