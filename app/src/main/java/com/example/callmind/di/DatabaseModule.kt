package com.example.callmind.di

import android.content.Context
import androidx.room.Room
import com.example.callmind.data.local.CallMindDatabase
import com.example.callmind.data.local.daos.NoteDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CallMindDatabase {
        return Room.databaseBuilder(
            context,
            CallMindDatabase::class.java,
            "callmind_db"
        )
            .addMigrations(
                CallMindDatabase.MIGRATION_3_4,
                CallMindDatabase.MIGRATION_4_5,
                CallMindDatabase.MIGRATION_5_6
            )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideQuickReplyDao(database: CallMindDatabase): com.example.callmind.data.local.daos.QuickReplyDao {
        return database.quickReplyDao()
    }

    @Provides
    fun provideNumberRuleDao(database: CallMindDatabase): com.example.callmind.data.local.daos.NumberRuleDao {
        return database.numberRuleDao()
    }

    @Provides
    fun provideBlockedCallDao(database: CallMindDatabase): com.example.callmind.data.local.daos.BlockedCallDao {
        return database.blockedCallDao()
    }

    @Provides
    fun provideCallRecordingDao(database: CallMindDatabase): com.example.callmind.data.local.daos.CallRecordingDao {
        return database.callRecordingDao()
    }

    @Provides
    fun provideNoteDao(database: CallMindDatabase): NoteDao {
        return database.noteDao()
    }

    @Provides
    fun provideReminderDao(database: CallMindDatabase): com.example.callmind.data.local.daos.ReminderDao {
        return database.reminderDao()
    }

    @Provides
    fun provideCommunicationLauncher(@ApplicationContext context: Context): com.example.callmind.core.communication.CommunicationLauncher {
        return com.example.callmind.core.communication.CommunicationLauncher(context)
    }
}
