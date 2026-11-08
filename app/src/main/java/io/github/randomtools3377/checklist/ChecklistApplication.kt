package io.github.randomtools3377.checklist

import android.app.Application
import io.github.randomtools3377.checklist.core.Book
import io.github.randomtools3377.checklist.data.AppDatabase
import io.github.randomtools3377.checklist.data.Repository
import io.github.randomtools3377.checklist.data.RoomStore

class ChecklistApplication : Application() {
    /** Opened on first use, not at start-up. */
    val container: Container by lazy { Container(this) }
}

class Container(app: Application) {
    val db: AppDatabase = AppDatabase.open(app)
    val book = Book(RoomStore(db))
    val repository = Repository(db)
}
