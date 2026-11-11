package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Bets
import io.github.randomtools3377.checklist.core.Book
import io.github.randomtools3377.checklist.core.Combos
import io.github.randomtools3377.checklist.core.Gaps
import io.github.randomtools3377.checklist.core.Interest
import io.github.randomtools3377.checklist.core.Lapses
import io.github.randomtools3377.checklist.core.PerfectDays

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
}
