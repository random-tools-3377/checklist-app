package io.github.randomtools3377.checklist.data

import androidx.room.Room
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import java.io.File

/**
 * The computer app's own folder for everything it keeps: the database, picked GIFs, the photo
 * behind Today and small values. On Windows it is %LOCALAPPDATA%\Checklist (kept on this computer, never roamed to a server), elsewhere ~/.checklist.
 * It belongs to the user signed in to the computer and is never sent anywhere; the backup is the
 * export file the user makes.
 */
fun appDataFolder(): File {
    val appData = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
    val folder = if (appData != null) File(appData, "Checklist") else File(System.getProperty("user.home"), ".checklist")
    folder.mkdirs()
    check(folder.isDirectory) { "Couldn't make the app's folder $folder" }
    return folder
}

/** The computer's database, in [folder], with SQLite that comes with the app, as on the iPhone. */
fun openDatabase(folder: File = appDataFolder()): AppDatabase =
    Room.databaseBuilder<AppDatabase>(name = File(folder, AppDatabase.FILE_NAME).absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

actual suspend fun <R> AppDatabase.withTransaction(block: suspend () -> R): R =
    useWriterConnection { it.immediateTransaction { block() } }
