package dev.huecoder.md3agent.data.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.huecoder.md3agent.system.paths.AppPaths
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [ChatEntity::class, MessageEntity::class, FolderEntity::class, MemoryEntity::class, ReminderEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun folderDao(): FolderDao
    abstract fun reminderDao(): ReminderDao
    abstract fun statsDao(): StatsDao

    companion object {
        fun build(paths: AppPaths): AppDatabase = build(paths.database.apply { parentFile?.mkdirs() }.path)

        fun build(path: String): AppDatabase =
            Room.databaseBuilder<AppDatabase>(name = path)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
    }
}
