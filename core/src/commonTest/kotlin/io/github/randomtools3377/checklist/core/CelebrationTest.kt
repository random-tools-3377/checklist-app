package io.github.randomtools3377.checklist.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CelebrationTest {
    @Test
    fun aBuiltInGifIsKnownByName() {
        assertEquals("star", Celebration.builtIn("star"))
        assertNull(Celebration.ownFile("star"))
        assertTrue(Celebration.isValid("confetti"))
        assertFalse(Celebration.isValid("sparkles"))
        assertFalse(Celebration.isValid(null))
    }

    @Test
    fun theUsersOwnGifIsAFileName() {
        val own = Celebration.own("own-5f3a.gif")
        assertEquals("own-5f3a.gif", Celebration.ownFile(own))
        assertNull(Celebration.builtIn(own))
        assertTrue(Celebration.isValid(own))
    }

    @Test
    fun aFileNameCannotReachOutsideTheAppsFolder() {
        assertFalse(Celebration.isSafeName("../../shared_prefs/x.gif"))
        assertFalse(Celebration.isSafeName("a/b.gif"))
        assertFalse(Celebration.isSafeName("photo.png"))
        assertFalse(Celebration.isSafeName(".gif"))
        assertNull(Celebration.ownFile("own:../x.gif"))
        assertFailsWith<IllegalArgumentException> { Celebration.own("../x.gif") }
    }
}
