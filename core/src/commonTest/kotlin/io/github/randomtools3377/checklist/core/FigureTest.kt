package io.github.randomtools3377.checklist.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FigureTest {
    private fun sheet(vararg levels: Pair<SkillArea, Int>): CharacterSheet {
        val skills = levels.associate { (area, level) -> area to Levels.needed(level, Levels.SKILL_STEP) }
        val xp = skills.values.sum()
        return CharacterSheet(xp, Levels.of(xp, Levels.CHARACTER_STEP), skills)
    }

    @Test
    fun aNewCharacterWearsNothingSpecial() {
        val outfit = Outfit.of(sheet())
        assertTrue(outfit.gear.isEmpty())
        assertNull(outfit.shirt)
        assertEquals(SkillArea.entries.size, Outfit.next(sheet()).size)
    }

    @Test
    fun gearComesAtLevelsFiveTenAndTwenty() {
        val outfit = Outfit.of(sheet(SkillArea.FITNESS to 10, SkillArea.MIND to 5))
        assertEquals(listOf(Gear.TRAINERS, Gear.HEADBAND, Gear.GLASSES), outfit.gear)
        assertEquals(SkillArea.FITNESS, outfit.shirt, "the leading skill colours the shirt")
        assertEquals(Gear.MEDAL, Outfit.next(sheet(SkillArea.FITNESS to 10)).first { it.area == SkillArea.FITNESS })
    }

    @Test
    fun theStrongerSkillGetsTheSpot() {
        // Mind's cap and Creative's beret both go on the head: the stronger skill's shows.
        val mind = Outfit.of(sheet(SkillArea.MIND to 22, SkillArea.CREATIVE to 6))
        assertTrue(Gear.CAP in mind.gear && Gear.BERET !in mind.gear)
        val creative = Outfit.of(sheet(SkillArea.MIND to 20, SkillArea.CREATIVE to 25))
        assertTrue(Gear.BERET in creative.gear && Gear.CAP !in creative.gear)
        assertEquals(1, creative.gear.count { it.slot == GearSlot.HEAD })
    }

    @Test
    fun theLookIsKeptAndReadBack() {
        val look = Look(skin = 4, hair = HairStyle.CURLY, hairColour = 3, eyes = Eyes.WINK)
        assertEquals(look, Looks.decode(Looks.encode(look)))
        assertEquals(Look(), Looks.decode(null))
        assertEquals(Look(), Looks.decode("99,NOPE,-1,X,Y"))
        assertEquals(Look(skin = 1, hair = HairStyle.SHORT, hairColour = 1, eyes = Eyes.DOTS), Looks.decode("99,NOPE,-1,X"))
    }

    @Test
    fun theDrawingHasTheGearInIt() {
        val plain = Figure.marks(Look(), Outfit.of(sheet()))
        val geared = Figure.marks(Look(), Outfit.of(sheet(SkillArea.MIND to 20, SkillArea.HOME to 20)))
        assertTrue(geared.size > plain.size)
        val svg = Figure.svg(geared)
        assertTrue(svg.startsWith("<svg") && svg.endsWith("</svg>"))
    }
}
