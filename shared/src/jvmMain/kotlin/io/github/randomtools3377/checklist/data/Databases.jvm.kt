package io.github.randomtools3377.checklist.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

// The desktop target is only for building and testing the shared code on any machine.

actual suspend fun <R> AppDatabase.withTransaction(block: suspend () -> R): R =
    useWriterConnection { it.immediateTransaction { block() } }
