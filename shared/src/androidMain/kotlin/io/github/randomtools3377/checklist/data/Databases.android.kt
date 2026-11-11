package io.github.randomtools3377.checklist.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction as roomTransaction

/** The phone's database, with the phone's own SQLite, as it has always been opened on Android. */
fun AppDatabase.Companion.open(context: Context): AppDatabase =
    Room.databaseBuilder(context, AppDatabase::class.java, FILE_NAME)
        .addMigrations(*MIGRATIONS)
        .build()

actual suspend fun <R> AppDatabase.withTransaction(block: suspend () -> R): R = roomTransaction(block)
