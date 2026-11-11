package io.github.randomtools3377.checklist.data

import androidx.room.Room
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/**
 * The app's own folder for everything it keeps: the database, picked GIFs and the photo behind
 * Today. It is left out of iCloud and computer backups, as Android's data is left out of phone
 * backups, so the goals and the reasons for misses never leave the phone; the backup is the
 * export file the user makes.
 */
@OptIn(ExperimentalForeignApi::class)
fun appDataFolder(): String {
    val manager = NSFileManager.defaultManager
    val base = requireNotNull(manager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)) { "No app folder" }
    val folder = requireNotNull(base.URLByAppendingPathComponent("Checklist", isDirectory = true)) { "No app folder" }
    manager.createDirectoryAtURL(folder, withIntermediateDirectories = true, attributes = null, error = null)
    folder.setResourceValue(NSNumber(bool = true), forKey = NSURLIsExcludedFromBackupKey, error = null)
    return requireNotNull(folder.path) { "No app folder" }
}

/** The iPhone's database, in the app's own folder, with SQLite that comes with the app. */
fun openDatabase(): AppDatabase =
    Room.databaseBuilder<AppDatabase>(name = appDataFolder() + "/" + AppDatabase.FILE_NAME)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

actual suspend fun <R> AppDatabase.withTransaction(block: suspend () -> R): R =
    useWriterConnection { it.immediateTransaction { block() } }
