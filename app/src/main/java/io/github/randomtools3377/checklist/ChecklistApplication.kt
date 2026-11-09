package io.github.randomtools3377.checklist

import android.app.Application
import androidx.room.InvalidationTracker
import androidx.room.withTransaction
import io.github.randomtools3377.checklist.core.Book
import io.github.randomtools3377.checklist.core.Gaps
import io.github.randomtools3377.checklist.core.Lapses
import io.github.randomtools3377.checklist.data.AppDatabase
import io.github.randomtools3377.checklist.data.CelebrationFiles
import io.github.randomtools3377.checklist.data.Repository
import io.github.randomtools3377.checklist.data.RoomStore
import io.github.randomtools3377.checklist.reminders.Reminders
import io.github.randomtools3377.checklist.widget.CalendarWidget
import io.github.randomtools3377.checklist.widget.StatsWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

class ChecklistApplication : Application() {
    /** Opened on first use, not at start-up. */
    val container: Container by lazy { Container(this) }

    override fun onCreate() {
        super.onCreate()
        Reminders.createChannel(this)
    }
}

@OptIn(FlowPreview::class)
class Container(app: Application) {
    val db: AppDatabase = AppDatabase.open(app)
    val book = Book(RoomStore(db))
    val lapses = Lapses(RoomStore(db), book)
    val gaps = Gaps(RoomStore(db), book)

    /**
     * Finds misses since the last check, then gaps. When the app opens, and when the day ends.
     * Both in one transaction, so the misses a gap covers never show on their own first.
     */
    suspend fun closeDays() {
        db.withTransaction {
            lapses.closeDays()
            gaps.find()
        }
    }
    val celebrationFiles = CelebrationFiles(app.filesDir)
    val repository = Repository(db, celebrationFiles)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val changes = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)

    init {
        // Whenever goals, ticks or settings change, from the app, the widget or a notification,
        // set the next reminder and redraw the widget.
        db.invalidationTracker.addObserver(
            object : InvalidationTracker.Observer(arrayOf("goals", "ticks", "settings", "steps", "lapses", "entries")) {
                override fun onInvalidated(tables: Set<String>) {
                    changes.tryEmit(Unit)
                }
            },
        )
        changes.tryEmit(Unit)
        scope.launch {
            changes.debounce(400).collect {
                runCatching { Reminders.reschedule(app) }
                runCatching { CalendarWidget.refresh(app) }
                runCatching { StatsWidget.refresh(app) }
            }
        }
    }
}
