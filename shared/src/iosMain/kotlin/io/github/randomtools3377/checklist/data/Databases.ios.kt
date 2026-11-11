package io.github.randomtools3377.checklist.data

import androidx.room.Room
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** The iPhone's database, in the app's own Documents folder, with SQLite that comes with the app. */
@OptIn(ExperimentalForeignApi::class)
fun openDatabase(): AppDatabase {
    val documents = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    val path = requireNotNull(documents?.path) { "No Documents folder" } + "/" + AppDatabase.FILE_NAME
    return Room.databaseBuilder<AppDatabase>(name = path)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}

actual suspend fun <R> AppDatabase.withTransaction(block: suspend () -> R): R =
    useWriterConnection { it.immediateTransaction { block() } }
