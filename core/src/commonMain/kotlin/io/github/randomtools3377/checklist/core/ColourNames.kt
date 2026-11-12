package io.github.randomtools3377.checklist.core

/**
 * A colour's name in words, for screen readers: the colours the app offers have their own,
 * like "Ocean blue", and any other colour is described, like "Dark blue" or "Light pink".
 */
object ColourNames {
    private val NAMED: Map<Int, String> = mapOf(
        0xFF2E6B5E to "Garden green",
        0xFF2F80C0 to "Ocean blue",
        0xFF3E7B3A to "Forest green",
        0xFFD9822B to "Sunset orange",
        0xFF8E5BB5 to "Lavender",
        0xFFC2185B to "Berry pink",
        0xFFE0567A to "Rose pink",
        0xFF6B8E23 to "Olive green",
        0xFF00897B to "Teal",
        0xFF5C6BC0 to "Indigo",
        0xFF8D6E63 to "Brown",
        0xFF607D8B to "Blue grey",
        // The colour-blind friendly ones (see ColourBlind).
        0xFF0072B2 to "Blue",
        0xFFE69F00 to "Orange",
        0xFF009E73 to "Bluish green",
        0xFFCC79A7 to "Reddish purple",
        0xFF56B4E9 to "Sky blue",
        0xFFD55E00 to "Vermillion",
        0xFFF0E442 to "Yellow",
        0xFF666666 to "Grey",
    ).mapKeys { it.key.toInt() }

    /** The name of [argb] (0xFFRRGGBB), or a description of it. */
    fun of(argb: Int): String = NAMED[argb or (0xFF shl 24)] ?: describe(argb)

    private fun describe(argb: Int): String {
        val (hue, saturation, value) = Colors.toHsv(argb)
        if (saturation < 0.15f) {
            return when {
                value < 0.2f -> "Black"
                value > 0.9f -> "White"
                value < 0.45f -> "Dark grey"
                value > 0.75f -> "Light grey"
                else -> "Grey"
            }
        }
        val name = when {
            hue < 15f || hue >= 345f -> "red"
            hue < 45f -> "orange"
            hue < 70f -> "yellow"
            hue < 165f -> "green"
            hue < 195f -> "teal"
            hue < 255f -> "blue"
            hue < 290f -> "purple"
            else -> "pink"
        }
        val shade = when {
            value < 0.45f -> "Dark "
            value > 0.85f && saturation < 0.5f -> "Light "
            else -> ""
        }
        return (shade + name).replaceFirstChar { it.uppercase() }
    }
}
