package io.github.randomtools3377.checklist.ui

import io.github.randomtools3377.checklist.core.Feature

/*
 * What the search at the top of Settings finds beyond the Settings page itself: the parts of
 * Appearance and the extras on the Features page, each opening its page at the right place.
 * The matching itself is SettingsSearch, in the rules.
 */

/** A part of Appearance a Settings search can open: the [key] of the place it opens at, its name, and the words that find it. */
internal data class LookPart(val key: String, val title: String, val words: String)

/** The places on the Appearance page a search can open it at. */
internal object LookPlace {
    const val THEME = "theme"
    const val COLOUR = "colour"
    const val COLOUR_BLIND = "colour-blind"
    const val TEXT = "text"
    const val TODAY = "today"
    const val SECTIONS = "sections"
    const val TABS = "tabs"
    const val PERSONAL = "personal"
    const val CELEBRATIONS = "celebrations"
}

/** The parts of Appearance on [phone], as a Settings search finds them: only what that page shows there. */
internal fun lookParts(phone: Phone, matchWallpaper: Boolean): List<LookPart> = listOfNotNull(
    LookPart(LookPlace.THEME, "Theme", "dark mode light black night ${phone.deviceWord}"),
    LookPart(LookPlace.COLOUR, "Colour", "colour color accent palette any picker hex" + if (matchWallpaper) " wallpaper" else ""),
    LookPart(LookPlace.COLOUR_BLIND, "Colour-blind friendly", "colour-blind color-blind colourblind colorblind accessibility"),
    LookPart(LookPlace.TEXT, "Text size", "text size bigger larger smaller accessibility"),
    LookPart(LookPlace.TODAY, "Today's look", "layout grid tiles list cards card size background photo gradient picture dim"),
    LookPart(LookPlace.SECTIONS, "Today's sections", "sections order hide show move"),
    LookPart(LookPlace.TABS, "Tabs", "tabs tab hidden opens on first bottom bar"),
    LookPart(LookPlace.PERSONAL, "Points name", "points called coins stars xp gems own words"),
    LookPart(LookPlace.PERSONAL, "Font", "font fonts typeface rounded serif easy to read"),
    LookPart(LookPlace.PERSONAL, "App icon", "app icon home screen").takeIf { phone.canChangeIcon },
    LookPart(LookPlace.PERSONAL, "Widgets", "widgets widget style see-through").takeIf { phone.hasWidgets },
    LookPart(LookPlace.CELEBRATIONS, "Celebrations", "celebrations sound sounds own sound file confetti gif gifs animation edge glow lighting" + if (phone.canBuzz) " buzz vibrate vibration" else ""),
)

/** The extras the Features page lists on [phone]: the AI helper only where there is Checklist AI to use. */
internal fun featuresOn(phone: Phone): List<Feature> = Feature.entries.filter { it != Feature.AI || phone.hasAi }

/** The words a Settings search finds [feature] by: its name, what it does, its group, and a few more. */
internal fun featureWords(feature: Feature): String = listOf(
    feature.title,
    feature.about,
    feature.group,
    when (feature) {
        Feature.TODOS -> "todos tasks"
        Feature.MOOD -> "moods feelings journal notes check-ins"
        Feature.CALENDAR -> "calendar events"
        Feature.WEEKLY_PLANNING -> "plan planning"
        Feature.SUGGESTIONS -> "suggested suggestions prices money treat"
        Feature.CHAINS -> "habit chains chain after next"
        Feature.CHARACTER -> "character avatar game levels skills"
        Feature.QUESTS -> "quest game"
        Feature.BOSSES -> "boss items game"
        Feature.BUDGET -> "budget money pay spending bills savings"
        Feature.BUDGET_GAME -> "budget money game"
        Feature.AI -> "ai artificial intelligence voice speech"
        else -> ""
    },
).joinToString(" ")
