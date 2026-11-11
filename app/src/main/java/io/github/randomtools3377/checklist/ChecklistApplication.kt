package io.github.randomtools3377.checklist

import android.app.Application
import androidx.room.InvalidationTracker
import io.github.randomtools3377.checklist.core.Book
import io.github.randomtools3377.checklist.core.Gaps
import io.github.randomtools3377.checklist.core.Interest
import io.github.randomtools3377.checklist.core.PerfectDays
import io.github.randomtools3377.checklist.core.Lapses
import io.github.randomtools3377.checklist.core.PointsName
import io.github.randomtools3377.checklist.ui.PointsLabel
import kotlinx.coroutines.withContext
import io.github.randomtools3377.checklist.reminders.TimingNotice
import io.github.randomtools3377.checklist.data.LOCK_PRIVATE
import io.github.randomtools3377.checklist.data.SETTING_STOPWATCH
import io.github.randomtools3377.checklist.data.SETTING_APP_ICON
import io.github.randomtools3377.checklist.data.SETTING_POINTS_NAME
import io.github.randomtools3377.checklist.data.BackgroundPhoto
import io.github.randomtools3377.checklist.data.AppDatabase
import io.github.randomtools3377.checklist.data.SharedParts
import io.github.randomtools3377.checklist.data.open
import io.github.randomtools3377.checklist.data.CelebrationFiles
import io.github.randomtools3377.checklist.data.Repository
import io.github.randomtools3377.checklist.data.RoomStore
import io.github.randomtools3377.checklist.reminders.Reminders
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

    /** What the shared screens ask of this phone. */
    val phone: AndroidPhone by lazy { AndroidPhone(this) }

    override fun onCreate() {
        super.onCreate()
        Reminders.createChannel(this)
    }
}

@OptIn(FlowPreview::class)
class Container(app: Application) {
    val db: AppDatabase = AppDatabase.open(app)
    val celebrationFiles = CelebrationFiles(app.filesDir)
    val backgroundPhoto = BackgroundPhoto(app.filesDir)

    /** The parts every phone shares: the database and everything built on it. */
    val parts = SharedParts(db, celebrationFiles, backgroundPhoto)
    val book get() = parts.book
    val lapses get() = parts.lapses
    val gaps get() = parts.gaps
    val bets get() = parts.bets
    val repository get() = parts.repository

    /**
     * Finds misses since the last check, then gaps. When the app opens, and when the day ends.
     * Both in one transaction, so the misses a gap covers never show on their own first.
     */
    suspend fun closeDays() = parts.closeDays()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val changes = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)

    /** Fires whenever goals, ticks, settings or the points history change, from anywhere. */
    val dataChanges: kotlinx.coroutines.flow.SharedFlow<Unit> get() = changes

    init {
        // Whenever goals, ticks (a routine's items too) or settings change, from the app, a
        // widget or a notification, set the next reminder and redraw the widgets.
        db.invalidationTracker.addObserver(
            object : InvalidationTracker.Observer(arrayOf("goals", "ticks", "settings", "steps", "lapses", "entries", "checks")) {
                override fun onInvalidated(tables: Set<String>) {
                    changes.tryEmit(Unit)
                }
            },
        )
        changes.tryEmit(Unit)
        // The home-screen icon follows the setting, after a restore from a backup too.
        scope.launch {
            db.dao().observeSetting(SETTING_APP_ICON).collect { name ->
                val icon = AppIcons.of(name)
                if (AppIcons.current(app) != icon) runCatching { AppIcons.apply(app, icon) }
            }
        }
        // While a goal is being timed, a notification shows the time running, with Stop.
        scope.launch {
            db.dao().observeSetting(SETTING_STOPWATCH).collect { text ->
                val parts = text?.split("|")?.takeIf { it.size == 2 }
                val goal = parts?.get(0)?.toLongOrNull()?.let { db.dao().goal(it) }
                val startedAt = parts?.get(1)?.toLongOrNull()
                if (goal == null || startedAt == null) {
                    TimingNotice.hide(app)
                } else {
                    val hidden = goal.isPrivate && db.dao().setting(LOCK_PRIVATE) == "1"
                    TimingNotice.show(app, goal.id, if (hidden) "A private goal" else "${goal.icon} ${goal.name}", startedAt)
                }
            }
        }
        // What points are called, for the screens, the widgets and the notifications alike.
        scope.launch {
            db.dao().observeSetting(SETTING_POINTS_NAME).collect { text ->
                val name = PointsName.decode(text)
                PointsName.current = name
                withContext(Dispatchers.Main) { PointsLabel.name = name }
            }
        }
        scope.launch {
            changes.debounce(400).collect {
                runCatching { Reminders.reschedule(app) }
                io.github.randomtools3377.checklist.widget.Widgets.refreshAll(app)
            }
        }
    }
}
