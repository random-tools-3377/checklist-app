package io.github.randomtools3377.checklist.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate

/**
 * When the last backup was made, and the folder for the automatic backup. Kept on this phone
 * only, not in the backup file: a folder belongs to one phone.
 */
object Backups {
    private const val FILE = "backups"
    private const val LAST = "last_backup"
    private const val FOLDER = "auto_folder"
    private const val LAST_AUTO_DAY = "last_auto_day"
    private const val REMINDER_OFF = "reminder_off"
    private const val LATER_UNTIL = "reminder_later_until"
    private const val FIRST_SEEN = "first_seen"

    /** The automatic backup keeps this many dated files in its folder. */
    const val KEEP = io.github.randomtools3377.checklist.data.AUTO_BACKUPS_KEPT

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** When the last export or automatic backup was made, in epoch milliseconds. */
    fun lastBackup(context: Context): Long? = prefs(context).getLong(LAST, 0L).takeIf { it > 0 }

    fun markBackedUp(context: Context, at: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(LAST, at).apply()
    }

    /** When the app was first opened with this, so a new install isn't reminded on day one. */
    fun firstSeen(context: Context): Long {
        val seen = prefs(context).getLong(FIRST_SEEN, 0L)
        if (seen > 0) return seen
        val now = System.currentTimeMillis()
        prefs(context).edit().putLong(FIRST_SEEN, now).apply()
        return now
    }

    fun reminderOn(context: Context): Boolean = !prefs(context).getBoolean(REMINDER_OFF, false)

    fun setReminderOn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(REMINDER_OFF, !on).apply()
    }

    /** "Later" on the reminder: it stays away until [until]. */
    fun later(context: Context, until: Long) {
        prefs(context).edit().putLong(LATER_UNTIL, until).apply()
    }

    fun laterUntil(context: Context): Long = prefs(context).getLong(LATER_UNTIL, 0L)

    fun folder(context: Context): Uri? = prefs(context).getString(FOLDER, null)?.let { Uri.parse(it) }

    /** Sets the automatic backup's folder, keeping access to it across restarts; null turns it off. */
    fun setFolder(context: Context, uri: Uri?) {
        val resolver = context.contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        folder(context)?.let { old -> runCatching { resolver.releasePersistableUriPermission(old, flags) } }
        if (uri != null) resolver.takePersistableUriPermission(uri, flags)
        prefs(context).edit().putString(FOLDER, uri?.toString()).remove(LAST_AUTO_DAY).apply()
    }

    /** The folder's name as the phone shows it, like "Backups", or null if it can't be reached. */
    fun folderName(context: Context): String? {
        val tree = folder(context) ?: return null
        return runCatching {
            val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }

    /**
     * Once a day the app is used: writes today's dated backup into the chosen folder, then keeps
     * only the newest [KEEP] of its own backups there. [text] makes the backup file's contents.
     * The contents are made first, and a file that couldn't be written is deleted again, so a
     * failed backup leaves nothing behind.
     */
    suspend fun autoBackupIfDue(context: Context, today: LocalDate, text: suspend () -> String): AutoBackupResult {
        val tree = folder(context) ?: return AutoBackupResult.NotDue
        if (prefs(context).getString(LAST_AUTO_DAY, null) == today.toString()) return AutoBackupResult.NotDue
        val resolver = context.contentResolver
        // A folder that's gone is found first, so it doesn't cost a whole export each time the app opens.
        val parentId: String? = try {
            val id = DocumentsContract.getTreeDocumentId(tree)
            val found = resolver.query(
                DocumentsContract.buildDocumentUriUsingTree(tree, id),
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null, null, null,
            )?.use { true } ?: false
            id.takeIf { found }
        } catch (e: Exception) {
            null
        }
        if (parentId == null) return AutoBackupResult.FolderGone
        val contents = try {
            text().toByteArray(Charsets.UTF_8)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return AutoBackupResult.Failed
        }
        val doc: Uri? = try {
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, parentId)
            DocumentsContract.createDocument(resolver, parent, "application/json", AutoBackupFiles.name(today))
        } catch (e: Exception) {
            null
        }
        if (doc == null) return AutoBackupResult.FolderGone
        val written = runCatching {
            (resolver.openOutputStream(doc, "wt") ?: error("No stream")).use { it.write(contents) }
        }.isSuccess
        if (!written) {
            runCatching { DocumentsContract.deleteDocument(resolver, doc) }
            return AutoBackupResult.Failed
        }
        runCatching { prune(context, tree, parentId) }
        prefs(context).edit().putString(LAST_AUTO_DAY, today.toString()).apply()
        markBackedUp(context)
        return AutoBackupResult.Saved
    }

    /**
     * Before an import replaces everything: writes [text] as [name] into the chosen folder, then
     * keeps only the newest few "before import" copies there (see AutoBackupFiles). The daily
     * clean-up never deletes these. True when it was saved; a file that couldn't be written is
     * deleted again, so a failed copy leaves nothing behind.
     */
    fun saveBeforeImport(context: Context, name: String, text: String): Boolean {
        val tree = folder(context) ?: return false
        val resolver = context.contentResolver
        val doc: Uri = try {
            val parentId = DocumentsContract.getTreeDocumentId(tree)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, parentId)
            DocumentsContract.createDocument(resolver, parent, "application/json", name)
        } catch (e: Exception) {
            null
        } ?: return false
        val written = runCatching {
            (resolver.openOutputStream(doc, "wt") ?: error("No stream")).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }.isSuccess
        if (!written) {
            runCatching { DocumentsContract.deleteDocument(resolver, doc) }
            return false
        }
        runCatching { pruneBeforeImports(context, tree) }
        return true
    }

    /** Deletes "before import" copies in the folder beyond the newest few. Nothing else is touched. */
    private fun pruneBeforeImports(context: Context, tree: Uri) {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val files = mutableListOf<Pair<String, String>>()
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                files += id to name
            }
        }
        AutoBackupFiles.beforeImportsToDelete(files) { it.second }.forEach { (id, _) ->
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
    }

    /**
     * The backups in the chosen folder, newest first, by name and address, to restore one: the
     * app's own, and exports saved there. Empty files are left out, as they can't be restored.
     */
    fun autoBackups(context: Context): List<Pair<String, Uri>> {
        val tree = folder(context) ?: return emptyList()
        return try {
            val parentId = DocumentsContract.getTreeDocumentId(tree)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
            val found = mutableListOf<Pair<String, Uri>>()
            context.contentResolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_SIZE,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    if (!c.isNull(2) && c.getLong(2) == 0L) continue
                    if (AutoBackupFiles.isListed(name)) found += name to DocumentsContract.buildDocumentUriUsingTree(tree, id)
                }
            }
            AutoBackupFiles.newestFirst(found) { it.first }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Deletes the app's own backups in the folder beyond the newest [KEEP]. Nothing else is touched. */
    private fun prune(context: Context, tree: Uri, parentId: String) {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val ours = mutableListOf<Pair<String, String>>()
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                ours += id to name
            }
        }
        AutoBackupFiles.toDelete(ours) { it.second }.forEach { (id, _) ->
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
    }
}
