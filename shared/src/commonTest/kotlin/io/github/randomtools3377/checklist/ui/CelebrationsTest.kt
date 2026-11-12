package io.github.randomtools3377.checklist.ui

import androidx.compose.ui.geometry.Offset
import io.github.randomtools3377.checklist.core.CelebrationSounds
import io.github.randomtools3377.checklist.core.CelebrationSounds.Kind
import io.github.randomtools3377.checklist.core.Goal
import io.github.randomtools3377.checklist.core.GoalKind
import io.github.randomtools3377.checklist.core.Repeat
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which sound a celebration plays, and the settings behind the glow round the edges. */
class CelebrationsTest {
    private val chime = "own-0000000000000001.sound"
    private val fanfare = "own-0000000000000002.sound"
    private val purr = "own-0000000000000003.sound"

    private class Played : Sounds {
        val log = mutableListOf<String>()
        override fun tick() {
            log += "tick"
        }
        override fun big() {
            log += "big"
        }
        override fun own(file: String, big: Boolean) {
            log += if (big) "$file (big)" else file
        }
        override fun release() {}
    }

    private val played = Played()
    private val read = Goal(id = 4, kind = GoalKind.HABIT, name = "Read", icon = "📖", points = 5, repeat = Repeat.everyDay(LocalDate(2026, 11, 1)), createdAt = 1)

    private fun celebrations(sound: Boolean = true, extras: CelebrationExtras = CelebrationExtras(), goal: Goal? = null) = Celebrations().apply {
        style = CelebrationStyle(sound = sound)
        this.extras = extras
        sounds = played
        this.goal = { goal }
    }

    @Test
    fun theAppsSoundsPlayUntilOneIsPicked() {
        val c = celebrations()
        c.burst(Offset.Zero, "+5")
        c.show(BigCelebration.ProjectFinished("🏗", "Shed", 20))
        c.show(BigCelebration.Bought("🍦", "Ice cream"))
        assertEquals(listOf("tick", "big", "big"), played.log)
    }

    @Test
    fun aPickedSoundPlaysInPlaceOfTheAppsForItsKindOnly() {
        val c = celebrations(extras = CelebrationExtras(sounds = mapOf(Kind.TICK to chime, Kind.REWARD to fanfare)))
        c.burst(Offset.Zero, "+5")
        c.show(BigCelebration.ProjectFinished("🏗", "Shed", 20))
        c.show(BigCelebration.Bought("🍦", "Ice cream"))
        assertEquals(listOf(chime, "big", "$fanfare (big)"), played.log)
    }

    @Test
    fun aGoalsOwnSoundPlaysForItsTicksButNotForAReward() {
        val extras = CelebrationExtras(sounds = mapOf(Kind.TICK to chime), goalSounds = mapOf(4L to purr))
        val c = celebrations(extras = extras, goal = read)
        c.burst(Offset.Zero, "+5")
        c.show(BigCelebration.GoalFinished("📖", "Read", 5, "star"))
        c.show(BigCelebration.Bought("🍦", "Ice cream"))
        assertEquals(listOf(purr, "$purr (big)", "big"), played.log)

        // Another goal, with no sound of its own, gets the one picked for ticks.
        played.log.clear()
        celebrations(extras = extras, goal = read.copy(id = 5)).burst(Offset.Zero, "+5")
        assertEquals(listOf(chime), played.log)
    }

    @Test
    fun nothingPlaysWithSoundOff() {
        val c = celebrations(sound = false, extras = CelebrationExtras(sounds = mapOf(Kind.TICK to chime)))
        c.burst(Offset.Zero, "+5")
        c.show(BigCelebration.Bought("🍦", "Ice cream"))
        assertTrue(played.log.isEmpty())
    }

    @Test
    fun theGlowIsOnUntilTurnedOffAndTheRestIsOffUntilTurnedOn() {
        val fresh = CelebrationExtras.of(glow = null, glowAway = null, sounds = emptyMap(), goalSounds = null)
        assertTrue(fresh.glow)
        assertFalse(fresh.glowAway)
        assertTrue(fresh.sounds.isEmpty() && fresh.goalSounds.isEmpty())

        val set = CelebrationExtras.of(
            glow = "0", glowAway = "1",
            sounds = mapOf(Kind.TICK to chime, Kind.PROJECT to "", Kind.REWARD to "../x.sound"),
            goalSounds = CelebrationSounds.withGoal(null, 4, purr),
        )
        assertFalse(set.glow)
        assertTrue(set.glowAway)
        assertEquals(mapOf(Kind.TICK to chime), set.sounds)
        assertEquals(mapOf(4L to purr), set.goalSounds)
    }
}
