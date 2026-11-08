package io.github.randomtools3377.checklist

import android.app.Application
import io.github.randomtools3377.checklist.core.Book
import io.github.randomtools3377.checklist.data.AppDatabase
import io.github.randomtools3377.checklist.data.Repository
import io.github.randomtools3377.checklist.data.RoomStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class ChecklistApplication : Application() {
    /** Opened on first use, not at start-up. */
    val container: Container by lazy { Container(this) }
}

class Container(app: Application) {
    val clock: Clock = PhoneClock
    val db: AppDatabase = AppDatabase.open(app)
    val book = Book(RoomStore(db), clock)
    val repository = Repository(db, clock)
}

/** The phone's time, in whatever time zone the phone is set to right now. */
object PhoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = Clock.system(zone)
    override fun instant(): Instant = Instant.now()
}
