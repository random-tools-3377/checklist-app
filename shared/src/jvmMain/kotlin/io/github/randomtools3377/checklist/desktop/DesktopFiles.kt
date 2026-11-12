package io.github.randomtools3377.checklist.desktop

import io.github.randomtools3377.checklist.core.Celebration
import io.github.randomtools3377.checklist.data.AutoBackupFiles
import io.github.randomtools3377.checklist.data.AutoBackupResult
import io.github.randomtools3377.checklist.data.GifStore
import io.github.randomtools3377.checklist.data.PhotoStore
import io.github.randomtools3377.checklist.data.nowMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/** Writes [bytes] to [file] through a temporary file beside it, so a half-written file never replaces a whole one. */
internal fun writeWhole(file: File, bytes: ByteArray) {
    file.parentFile?.mkdirs()
    val temporary = File(file.parentFile, file.name + ".part")
    temporary.writeBytes(bytes)
    try {
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

/** The GIFs and sounds the user picked for celebrations, in the app's folder under celebrations. */
internal class DesktopGifs(private val folder: File) : GifStore {
    private fun file(name: String): File? = if (Celebration.isStoredName(name)) File(folder, name) else null

    override fun read(name: String): ByteArray? = file(name)?.takeIf { it.isFile }?.readBytes()

    override fun write(name: String, bytes: ByteArray) {
        file(name)?.let { writeWhole(it, bytes) }
    }

    override fun keepOnly(names: Set<String>) {
        folder.listFiles().orEmpty().filter { it.isFile && it.name !in names }.forEach { it.delete() }
    }
}

/** The photo behind Today, in the app's folder as background.jpg. */
internal class DesktopPhoto(private val file: File) : PhotoStore {
    override fun read(): ByteArray? = file.takeIf { it.isFile }?.readBytes()

    override fun write(bytes: ByteArray) = writeWhole(file, bytes)

    override fun delete() {
        file.delete()
    }
}

/** Small values kept on this computer only, like the emoji picked lately, in one properties file. */
internal class LocalValues(private val file: File) {
    private val values = Properties().apply {
        if (file.isFile) runCatching { file.reader(Charsets.UTF_8).use { load(it) } }
    }

    @Synchronized
    fun string(key: String): String? = values.getProperty(key)

    @Synchronized
    fun setString(key: String, value: String?) {
        if (value == null) values.remove(key) else values.setProperty(key, value)
        val text = java.io.StringWriter().also { values.store(it, "Checklist: small values kept on this computer only") }.toString()
        runCatching { writeWhole(file, text.toByteArray(Charsets.UTF_8)) }
    }

    fun long(key: String): Long? = string(key)?.toLongOrNull()

    fun setLong(key: String, value: Long?) = setString(key, value?.toString())
}

/**
 * The automatic backup on a computer: a folder the user chose, where a dated file is saved once a
 * day the app is used and the newest few of the app's own are kept (see [AutoBackupFiles]). The
 * folder is kept in the local values, on this computer only: it isn't in the backup file.
 */
internal class DesktopBackups(private val local: LocalValues) {
    /** One save at a time: the app can look twice at once, like when its window comes back on the hour. */
    private val saving = Mutex()

    /** The chosen folder's full path, or null while the automatic backup is off. */
    fun folder(): String? = local.string(FOLDER)?.takeIf { it.isNotBlank() }

    /** Sets the folder, so the next look saves there straight away; null turns the automatic backup off. */
    fun setFolder(path: String?) {
        local.setString(FOLDER, path?.takeIf { it.isNotBlank() })
        local.setString(LAST_DAY, null)
    }

    /**
     * Once a day: writes [today]'s dated backup into the folder, then deletes the app's own
     * backups there beyond the newest few. [text] makes the file's contents, and is only asked
     * once the folder is found. A file that couldn't be written whole isn't left behind.
     */
    suspend fun saveIfDue(today: LocalDate, text: suspend () -> String): AutoBackupResult = saving.withLock {
        val folder = File(folder() ?: return@withLock AutoBackupResult.NotDue)
        if (local.string(LAST_DAY) == today.toString()) return@withLock AutoBackupResult.NotDue
        if (!folder.isDirectory) return@withLock AutoBackupResult.FolderGone
        val contents = try {
            text().toByteArray(Charsets.UTF_8)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withLock AutoBackupResult.Failed
        }
        val file = File(folder, AutoBackupFiles.name(today))
        if (runCatching { writeWhole(file, contents) }.isFailure) {
            runCatching { File(folder, file.name + ".part").delete() }
            return@withLock if (folder.isDirectory) AutoBackupResult.Failed else AutoBackupResult.FolderGone
        }
        runCatching {
            val files = folder.listFiles().orEmpty().filter { it.isFile }
            AutoBackupFiles.toDelete(files) { it.name }.forEach { it.delete() }
        }
        local.setString(LAST_DAY, today.toString())
        local.setLong(LAST, nowMillis())
        AutoBackupResult.Saved
    }

    /**
     * The backups in the folder, newest first, by name and full path, to restore one: the app's
     * own, and exports saved there. Empty files are left out, as they can't be restored.
     */
    fun list(): List<Pair<String, String>> {
        val folder = File(folder() ?: return emptyList())
        val found = folder.listFiles().orEmpty()
            .filter { it.isFile && it.length() > 0 && AutoBackupFiles.isListed(it.name) }
            .map { it.name to it.absolutePath }
        return AutoBackupFiles.newestFirst(found) { it.first }
    }

    companion object {
        /** When the last export or automatic backup was made (also read for the backup reminder). */
        const val LAST = "backup.last"
        private const val FOLDER = "backup.auto_folder"
        private const val LAST_DAY = "backup.last_auto_day"
    }
}

/**
 * Starting with Windows: a shortcut to the app in the user's own Startup folder, which Windows
 * opens at sign-in. No registry and no admin rights; taking the shortcut away turns it off.
 * [appData] is %APPDATA% and [exe] the installed app; without either (not Windows, or not
 * installed) there is nothing to switch.
 */
class StartupShortcut(
    private val appData: String? = System.getenv("APPDATA"),
    private val exe: String? = System.getProperty("jpackage.app-path"),
    private val windows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true),
    /** Makes the shortcut [file] to [exe] with [arguments]; Windows' own script host writes it. */
    private val make: (file: File, exe: String, arguments: String) -> Unit = ::makeShortcut,
) {
    /** Where the shortcut goes: Startup, in the user's own Start menu. Null where there is no such folder. */
    val file: File? = appData?.takeIf { windows && it.isNotBlank() }?.let { File(startupFolder(it), NAME) }

    /** Whether the switch can be offered: on Windows, with the app installed. */
    val available: Boolean get() = file != null && !exe.isNullOrBlank()

    fun isOn(): Boolean = file?.isFile == true

    /** Adds or removes the shortcut; whether it is now as asked. */
    fun set(on: Boolean): Boolean {
        val shortcut = file ?: return !on
        if (!on) {
            runCatching { shortcut.delete() }
            return !shortcut.exists()
        }
        val target = exe?.takeIf { it.isNotBlank() } ?: return false
        runCatching {
            shortcut.parentFile?.mkdirs()
            make(shortcut, target, HIDDEN)
        }
        return shortcut.isFile
    }

    companion object {
        const val NAME = "Checklist.lnk"

        /** Given to the app when Windows starts it at sign-in: it opens in the notification area, with no window. */
        const val HIDDEN = "--hidden"

        /** The user's own Startup folder, under [appData] (%APPDATA%). */
        fun startupFolder(appData: String): File =
            File(File(File(File(File(appData, "Microsoft"), "Windows"), "Start Menu"), "Programs"), "Startup")

        /**
         * Writes the shortcut with PowerShell and Windows' script host, which both come with
         * Windows. The paths go in through the environment, so nothing in them is read as a command.
         */
        private fun makeShortcut(file: File, exe: String, arguments: String) {
            val script = "\$s = (New-Object -ComObject WScript.Shell).CreateShortcut(\$env:CHECKLIST_SHORTCUT); " +
                "\$s.TargetPath = \$env:CHECKLIST_EXE; \$s.Arguments = \$env:CHECKLIST_ARGUMENTS; " +
                "\$s.WorkingDirectory = \$env:CHECKLIST_FOLDER; \$s.Description = 'Checklist'; \$s.Save()"
            val builder = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script)
            builder.environment()["CHECKLIST_SHORTCUT"] = file.absolutePath
            builder.environment()["CHECKLIST_EXE"] = exe
            builder.environment()["CHECKLIST_ARGUMENTS"] = arguments
            builder.environment()["CHECKLIST_FOLDER"] = File(exe).parent.orEmpty()
            builder.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD)
            val process = builder.start()
            process.outputStream.close()
            if (!process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly()
        }
    }
}
