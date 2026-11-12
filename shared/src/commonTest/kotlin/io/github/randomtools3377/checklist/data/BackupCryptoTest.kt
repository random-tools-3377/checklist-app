package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Backup
import io.github.randomtools3377.checklist.core.BackupError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Backup files protected with a passphrase: sealed and opened again, on every kind of device. */
class BackupCryptoTest {
    /** Few rounds, so the tests are quick; the app uses [BackupCrypto.ITERATIONS]. */
    private fun key(passphrase: String) = BackupCrypto.newKey(passphrase, iterations = 1000)

    private val backup = Backup.encode(
        Backup(
            exportedAt = 1_700_000_000_000, goals = emptyList(), steps = emptyList(), ticks = emptyList(), entries = emptyList(),
            rewards = emptyList(), purchases = emptyList(), settings = mapOf("note" to "café ✓ 日本"),
        ),
    )

    @Test
    fun aProtectedFileOpensWithItsPassphrase() {
        val file = BackupCrypto.seal(backup, key("correct horse battery"))
        assertTrue(BackupCrypto.isEncrypted(file))
        // The header says in plain words what the file is; nothing of the backup shows.
        assertTrue(file.startsWith("CHECKLIST-ENCRYPTED-BACKUP 1\nThis is a Checklist backup protected with a passphrase."))
        assertFalse("café" in file)
        assertFalse("exportedAt" in file)
        val opened = BackupCrypto.open(file, "correct horse battery")
        assertEquals(backup, opened)
        assertEquals("café ✓ 日本", Backup.decode(opened).settings["note"])
    }

    @Test
    fun aWrongPassphraseSaysSoAndOpensNothing() {
        val file = BackupCrypto.seal(backup, key("correct horse battery"))
        val error = assertFailsWith<WrongPassphraseException> { BackupCrypto.open(file, "correct horse batterx") }
        assertEquals("That passphrase doesn't open this file. Check it and try again.", error.message)
        assertFailsWith<WrongPassphraseException> { BackupCrypto.open(file, "") }
    }

    @Test
    fun spacesAroundThePassphraseDoNotCount() {
        val file = BackupCrypto.seal(backup, key("  correct horse battery "))
        assertEquals(backup, BackupCrypto.open(file, "correct horse battery\n"))
    }

    @Test
    fun plainFilesStillImport() {
        assertFalse(BackupCrypto.isEncrypted(backup))
        assertEquals(1_700_000_000_000, Backup.decode(backup).exportedAt)
        // A protected file given to the plain reader is turned away in plain words, not half read.
        val file = BackupCrypto.seal(backup, key("correct horse battery"))
        assertFailsWith<BackupError> { Backup.decode(file) }
    }

    @Test
    fun aChangedFileIsNotOpened() {
        val file = BackupCrypto.seal(backup, key("correct horse battery"))
        val lines = file.trimEnd().lines().toMutableList()
        val last = lines.last()
        lines[lines.size - 1] = (if (last[0] == 'A') 'B' else 'A') + last.drop(1)
        assertFailsWith<WrongPassphraseException> { BackupCrypto.open(lines.joinToString("\n"), "correct horse battery") }
        // A header that lost its salt, or a file cut short, is damaged.
        assertFailsWith<BackupError> { BackupCrypto.open(file.lines().filterNot { it.startsWith("salt:") }.joinToString("\n"), "correct horse battery") }
        assertFailsWith<BackupError> { BackupCrypto.open("CHECKLIST-ENCRYPTED-BACKUP 1\n", "correct horse battery") }
        assertFailsWith<BackupError> { BackupCrypto.open(file.replace("BACKUP 1", "BACKUP 2"), "correct horse battery") }
    }

    @Test
    fun aFileSavedWithWindowsLineEndingsStillOpens() {
        val file = BackupCrypto.seal(backup, key("correct horse battery"))
        assertEquals(backup, BackupCrypto.open("\uFEFF" + file.replace("\n", "\r\n"), "correct horse battery"))
    }

    @Test
    fun everyFileIsSealedDifferently() {
        val key = key("correct horse battery")
        assertNotEquals(BackupCrypto.seal(backup, key), BackupCrypto.seal(backup, key))
        assertNotEquals(key.salt.toList(), key("correct horse battery").salt.toList())
    }

    @Test
    fun theKeyKeptOnTheDeviceReadsBack() {
        val key = key("correct horse battery")
        val read = assertNotNull(BackupKey.decode(key.encode()))
        assertEquals(key.iterations, read.iterations)
        assertEquals(key.salt.toList(), read.salt.toList())
        assertEquals(key.key.toList(), read.key.toList())
        assertEquals(backup, BackupCrypto.open(BackupCrypto.seal(backup, read), "correct horse battery"))
        assertNull(BackupKey.decode(null))
        assertNull(BackupKey.decode(""))
        assertNull(BackupKey.decode("1000:AAAA:AAAA"))
    }

    @Test
    fun thisDeviceOpensWhatItSeals() {
        assertTrue(BackupCrypto.works("correct horse battery"))
    }

    @Test
    fun aPassphraseNeedsEightCharactersTypedTheSameTwice() {
        assertEquals("Use at least 8 characters.", BackupCrypto.problem("short", "short"))
        assertEquals("The two don't match yet.", BackupCrypto.problem("long enough", "long enoug"))
        assertNull(BackupCrypto.problem("long enough", "long enough"))
    }

    /**
     * A file made once on a computer, with javax.crypto. Every kind of device has to open it and
     * make the very same file from the same key and nonce, so a file moves between them.
     */
    @Test
    fun everyDeviceMakesAndOpensTheSameFile() {
        val passphrase = "pässwörd ✓ twelve"
        val salt = ByteArray(16) { (it * 7 + 1).toByte() }
        val nonce = ByteArray(12) { (it * 11 + 3).toByte() }
        val text = "{\"hello\": \"checklist\", \"n\": [1, 2, 3], \"note\": \"café ✓\"}"
        val key = BackupKey(1000, salt, pbkdf2Sha256(passphrase, salt, 1000, BackupCrypto.KEY_BYTES))
        assertEquals(KNOWN_FILE, BackupCrypto.seal(text, key, nonce))
        assertEquals(text, BackupCrypto.open(KNOWN_FILE, passphrase))
        assertFailsWith<WrongPassphraseException> { BackupCrypto.open(KNOWN_FILE, "passwörd ✓ twelve") }
    }

    private companion object {
        const val KNOWN_FILE = "CHECKLIST-ENCRYPTED-BACKUP 1\n" +
            "This is a Checklist backup protected with a passphrase.\n" +
            "To open it: in the app, Settings, Backup, \"Import a backup\", then type the passphrase.\n" +
            "cipher: AES-256-GCM\n" +
            "kdf: PBKDF2-HMAC-SHA256\n" +
            "iterations: 1000\n" +
            "salt: AQgPFh0kKzI5QEdOVVxjag==\n" +
            "nonce: Aw4ZJC86RVBbZnF8\n" +
            "\n" +
            "SKLtmRlNa/8uEFC71eoHVbYxXG4QpqgvbiyhUR3LEuZDPxaDAGp8CX8veOoh8l0OYgrvMDUhmamN\n" +
            "h71uxhNIQoeEc4Tkl0LlYhZn\n"
    }
}
