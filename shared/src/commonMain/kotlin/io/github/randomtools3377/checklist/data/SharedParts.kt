package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Backup
import io.github.randomtools3377.checklist.core.Bets
import io.github.randomtools3377.checklist.core.Book
import io.github.randomtools3377.checklist.core.Combos
import io.github.randomtools3377.checklist.core.Gaps
import io.github.randomtools3377.checklist.core.Interest
import io.github.randomtools3377.checklist.core.Lapses
import io.github.randomtools3377.checklist.core.PerfectDays
import kotlinx.datetime.LocalDate

/**
 * The app's parts that are the same on every phone: the database and everything built on it.
 * Each phone makes one, with its own database and its own storage for picked GIFs and photos.
 */
class SharedParts(val db: AppDatabase, val gifs: GifStore, val photo: PhotoStore?) {
    val book = Book(RoomStore(db))
    val lapses = Lapses(RoomStore(db), book)
    val gaps = Gaps(RoomStore(db), book)
    val interest = Interest(RoomStore(db), book)
    val perfectDays = PerfectDays(RoomStore(db), book)
    val combos = Combos(RoomStore(db), book)
    val bets = Bets(RoomStore(db), book)
    val repository = Repository(db, gifs, photo)

    /**
     * Finds misses since the last check, then gaps, and pays what the day's end pays. When the
     * app opens, and when the day ends. All in one transaction, so the misses a gap covers never
     * show on their own first. A bet still open from before bets were removed gives its stake
     * back here, once.
     */
    suspend fun closeDays() {
        db.withTransaction {
            lapses.closeDays()
            gaps.find()
            interest.pay()
            perfectDays.pay()
            combos.pay()
            bets.giveBackOpen()
        }
    }

    /**
     * Replaces everything with [backup] (see [Repository.restore]). A bet still open in it, from
     * a backup made before bets were removed, gives its stake back straight away.
     */
    suspend fun restore(backup: Backup) {
        repository.restore(backup)
        bets.giveBackOpen()
    }
}

/** How many automatic backups are kept in the folder: the newest ones. */
const val AUTO_BACKUPS_KEPT = 7

/** What an automatic backup did. */
sealed interface AutoBackupResult {
    data object NotDue : AutoBackupResult
    data object Saved : AutoBackupResult

    /** The folder can't be written any more, like after it was deleted. */
    data object FolderGone : AutoBackupResult

    /** The folder was there, but the backup couldn't be made or written. Nothing was left behind. */
    data object Failed : AutoBackupResult
}

/** How many "before import" copies are kept in the folder: the newest ones. */
const val BEFORE_IMPORTS_KEPT = 3

/**
 * The automatic backup's file names. Its own files are "checklist-auto-<day>.json", so it never
 * deletes an export the user saved in the same folder ("checklist-backup-<day>.json").
 *
 * The copy saved before an import replaces everything is "checklist-before-import-<day>-<time>.json".
 * The daily clean-up never deletes those: they have their own count ([BEFORE_IMPORTS_KEPT]), trimmed
 * only when a newer one is saved, so the newest is always there to undo a wrong import.
 */
object AutoBackupFiles {
    private val OWN = Regex("""checklist-auto-\d{4}-\d{2}-\d{2}.*\.json""")
    private val BEFORE_IMPORT = Regex("""checklist-before-import-\d{4}-\d{2}-\d{2}.*\.json""")

    /** Offered to restore: its own files, exports, "before import" copies, and automatic backups from before 0.62.0. */
    private val LISTED = Regex("""checklist-(auto|backup|before-import)-\d{4}-\d{2}-\d{2}.*\.json""")
    private val DAY = Regex("""\d{4}-\d{2}-\d{2}""")
    private val TIME = Regex("""-\d{4}-\d{2}-\d{2}-(\d{2})(\d{2})\d{2}""")

    fun name(day: LocalDate): String = "checklist-auto-$day.json"

    /** The copy of what is on the device at [now], saved before an import replaces it. */
    fun beforeImportName(now: kotlinx.datetime.LocalDateTime): String {
        fun two(n: Int) = n.toString().padStart(2, '0')
        return "checklist-before-import-${now.date}-${two(now.hour)}${two(now.minute)}${two(now.second)}.json"
    }

    fun isOwn(name: String): Boolean = OWN.matches(name)

    fun isBeforeImport(name: String): Boolean = BEFORE_IMPORT.matches(name)

    fun isListed(name: String): Boolean = LISTED.matches(name)

    /** The day in a file's name, and for a "before import" copy its time as hour and minute. */
    fun dayOf(name: String): LocalDate? = DAY.find(name)?.value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun timeOf(name: String): kotlinx.datetime.LocalTime? = TIME.find(name)?.let { found ->
        runCatching { kotlinx.datetime.LocalTime(found.groupValues[1].toInt(), found.groupValues[2].toInt()) }.getOrNull()
    }

    /** Newest day first, whatever the file's prefix. */
    fun <T> newestFirst(files: List<T>, name: (T) -> String): List<T> =
        files.sortedWith(compareByDescending<T> { DAY.find(name(it))?.value }.thenByDescending { name(it) })

    /** Its own files beyond the newest [AUTO_BACKUPS_KEPT]: these are deleted. Nothing else is. */
    fun <T> toDelete(files: List<T>, name: (T) -> String): List<T> =
        newestFirst(files.filter { isOwn(name(it)) }, name).drop(AUTO_BACKUPS_KEPT)

    /** "Before import" copies beyond the newest [BEFORE_IMPORTS_KEPT], deleted when a new one is saved. */
    fun <T> beforeImportsToDelete(files: List<T>, name: (T) -> String): List<T> =
        newestFirst(files.filter { isBeforeImport(name(it)) }, name).drop(BEFORE_IMPORTS_KEPT)
}
