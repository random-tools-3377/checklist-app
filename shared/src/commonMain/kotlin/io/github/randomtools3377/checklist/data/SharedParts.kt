package io.github.randomtools3377.checklist.data

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
     * show on their own first.
     */
    suspend fun closeDays() {
        db.withTransaction {
            lapses.closeDays()
            gaps.find()
            interest.pay()
            perfectDays.pay()
            combos.pay()
            bets.settle()
        }
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

/**
 * The automatic backup's file names. Its own files are "checklist-auto-<day>.json", so it never
 * deletes an export the user saved in the same folder ("checklist-backup-<day>.json").
 */
object AutoBackupFiles {
    private val OWN = Regex("""checklist-auto-\d{4}-\d{2}-\d{2}.*\.json""")

    /** Offered to restore: its own files, exports, and automatic backups from before 0.62.0. */
    private val LISTED = Regex("""checklist-(auto|backup)-\d{4}-\d{2}-\d{2}.*\.json""")
    private val DAY = Regex("""\d{4}-\d{2}-\d{2}""")

    fun name(day: LocalDate): String = "checklist-auto-$day.json"

    fun isOwn(name: String): Boolean = OWN.matches(name)

    fun isListed(name: String): Boolean = LISTED.matches(name)

    /** Newest day first, whatever the file's prefix. */
    fun <T> newestFirst(files: List<T>, name: (T) -> String): List<T> =
        files.sortedWith(compareByDescending<T> { DAY.find(name(it))?.value }.thenByDescending { name(it) })

    /** Its own files beyond the newest [AUTO_BACKUPS_KEPT]: these are deleted. Nothing else is. */
    fun <T> toDelete(files: List<T>, name: (T) -> String): List<T> =
        newestFirst(files.filter { isOwn(name(it)) }, name).drop(AUTO_BACKUPS_KEPT)
}
