package io.github.randomtools3377.checklist.core

import io.github.randomtools3377.checklist.core.CelebrationSounds.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CelebrationSoundsTest {
    private val a = "own-0011223344556677.sound"
    private val b = "own-8899aabbccddeeff.sound"

    @Test
    fun aNewNameIsAPlainFileName() {
        val name = CelebrationSounds.newName(byteArrayOf(0, 1, -1, 16, 32, 64, 127, -128))
        assertEquals("own-0001ff102040" + "7f80.sound", name)
        assertTrue(CelebrationSounds.isSafeName(name))
        assertTrue(Celebration.isStoredName(name))
        assertTrue(Celebration.isStoredName("own-1.gif"))
    }

    @Test
    fun aNameThatLeavesTheFolderIsRefused() {
        assertFalse(CelebrationSounds.isSafeName("../x.sound"))
        assertFalse(CelebrationSounds.isSafeName("a/b.sound"))
        assertFalse(CelebrationSounds.isSafeName("song.mp3"))
        assertFalse(Celebration.isStoredName("../x.sound"))
        assertNull(CelebrationSounds.file("../x.sound"))
        assertNull(CelebrationSounds.file(""))
        assertNull(CelebrationSounds.file(null))
    }

    @Test
    fun withNothingPickedTheAppsSoundPlays() {
        assertNull(CelebrationSounds.choose(Kind.TICK, null, emptyMap()))
        assertNull(CelebrationSounds.choose(Kind.REWARD, null, mapOf(Kind.TICK to a)))
    }

    @Test
    fun theSoundPickedForAKindPlaysForThatKind() {
        val picked = mapOf(Kind.TICK to a, Kind.PROJECT to b)
        assertEquals(a, CelebrationSounds.choose(Kind.TICK, null, picked))
        assertEquals(b, CelebrationSounds.choose(Kind.PROJECT, null, picked))
    }

    @Test
    fun aGoalsOwnSoundComesFirst() {
        assertEquals(b, CelebrationSounds.choose(Kind.TICK, b, mapOf(Kind.TICK to a)))
        assertEquals(b, CelebrationSounds.choose(Kind.PROJECT, b, emptyMap()))
        // Something that isn't a sound file is as good as none.
        assertEquals(a, CelebrationSounds.choose(Kind.TICK, "nope", mapOf(Kind.TICK to a)))
    }

    @Test
    fun goalsSoundsAreKeptAsOneSetting() {
        var setting = CelebrationSounds.withGoal(null, 7, a)
        setting = CelebrationSounds.withGoal(setting, 3, b)
        assertEquals("3:$b,7:$a", setting)
        assertEquals(mapOf(3L to b, 7L to a), CelebrationSounds.goals(setting))
        setting = CelebrationSounds.withGoal(setting, 7, null)
        assertEquals(mapOf(3L to b), CelebrationSounds.goals(setting))
        assertEquals("", CelebrationSounds.withGoal(setting, 3, null))
        assertEquals(emptyMap(), CelebrationSounds.goals("x:y,4:../bad.sound,,5"))
    }

    @Test
    fun onlySoundsStillInUseAreKept() {
        val settings = mapOf(
            Kind.TICK.setting to a,
            Kind.REWARD.setting to "",
            CelebrationSounds.GOALS_SETTING to "3:$b,9:own-99.sound",
        )
        assertEquals(setOf(a, b), CelebrationSounds.used(settings, goalIds = setOf(3L)))
        assertEquals(setOf(a), CelebrationSounds.used(settings, goalIds = emptySet()))
    }

    @Test
    fun theGlowScalesWithTheWin() {
        val tick = EdgeGlow.spec(GlowSize.TICK)
        val finish = EdgeGlow.spec(GlowSize.FINISH)
        val big = EdgeGlow.spec(GlowSize.BIG)
        assertEquals(1, tick.passes)
        assertTrue(tick.millis < 1000)
        assertTrue(big.passes > tick.passes)
        assertTrue(tick.millis < finish.millis && finish.millis < big.millis)
        assertTrue(tick.alpha < finish.alpha && finish.alpha < big.alpha)
        assertTrue(tick.width < finish.width && finish.width < big.width)
        assertTrue(big.alpha <= 1f)
    }

    @Test
    fun theGlowFadesInAndOut() {
        assertEquals(0f, EdgeGlow.strength(0f))
        assertEquals(0f, EdgeGlow.strength(1f))
        assertEquals(1f, EdgeGlow.strength(0.5f))
        assertTrue(EdgeGlow.strength(0.05f) in 0.01f..0.99f)
        assertTrue(EdgeGlow.strength(0.9f) in 0.01f..0.99f)
    }

    @Test
    fun theBrightPartRunsRoundOnceForATickAndTwiceForABigWin() {
        val tick = EdgeGlow.spec(GlowSize.TICK)
        val big = EdgeGlow.spec(GlowSize.BIG)
        assertEquals(0.5f, EdgeGlow.head(tick, 0.5f))
        assertEquals(0.5f, EdgeGlow.head(big, 0.25f))
        assertEquals(0.5f, EdgeGlow.head(big, 0.75f))
        // Brightest where the bright part is, with a tail behind it and a faint glow elsewhere.
        assertEquals(1f, EdgeGlow.brightness(0.5f, head = 0.5f))
        assertTrue(EdgeGlow.brightness(0.4f, head = 0.5f) > EdgeGlow.brightness(0.2f, head = 0.5f))
        assertEquals(EdgeGlow.brightness(0f, head = 0.5f), EdgeGlow.brightness(0.9f, head = 0.5f))
        assertTrue(EdgeGlow.brightness(0f, head = 0.5f) > 0f)
        // It wraps round the corner where the edge starts and ends.
        assertTrue(EdgeGlow.brightness(0.95f, head = 0.05f) > EdgeGlow.brightness(0.5f, head = 0.05f))
    }
}
