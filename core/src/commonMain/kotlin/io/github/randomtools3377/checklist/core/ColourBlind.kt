package io.github.randomtools3377.checklist.core

/**
 * The colour-blind friendly look (Settings → Appearance). Good things (points earned, everything
 * done, on time) are blue, and bad things (points lost, nothing done, late, over the limit) are
 * vermillion: the pair from Okabe and Ito's palette that stays apart for every common kind of
 * colour blindness, whatever colour the app is. Goals get colours from the same palette.
 */
object ColourBlind {
    /** The setting it is kept in: "1" when on. */
    const val SETTING = "colour_blind"

    /** Okabe and Ito's colours, offered for goals, with a grey in place of their black. */
    val GOAL_COLORS: List<Int> = listOf(
        0xFF0072B2, // blue
        0xFFE69F00, // orange
        0xFF009E73, // bluish green
        0xFFCC79A7, // reddish purple
        0xFF56B4E9, // sky blue
        0xFFD55E00, // vermillion
        0xFFF0E442, // yellow
        0xFF666666, // grey
    ).map { it.toInt() }

    private val blue = TonalPalette.of(0xFF0072B2.toInt())
    private val vermillion = TonalPalette.of(0xFFD55E00.toInt())

    private fun light(brightness: Brightness) = brightness == Brightness.LIGHT

    /** The colour for good things, readable on the background. */
    fun good(brightness: Brightness): Int = blue.tone(if (light(brightness)) 35 else 80)

    /** A soft background for good things, like a day with some done. */
    fun goodContainer(brightness: Brightness): Int = blue.tone(if (light(brightness)) 90 else 30)

    /** Text on [good]. */
    fun onGood(brightness: Brightness): Int = blue.tone(if (light(brightness)) 100 else 10)

    /** The colour for bad things, readable on the background. */
    fun bad(brightness: Brightness): Int = vermillion.tone(if (light(brightness)) 45 else 70)

    /** The warning roles in vermillion: the colour, text on it, a soft background, and text on that. */
    fun warnings(brightness: Brightness): List<Int> =
        if (light(brightness)) listOf(bad(brightness), vermillion.tone(100), vermillion.tone(90), vermillion.tone(10))
        else listOf(bad(brightness), vermillion.tone(10), vermillion.tone(30), vermillion.tone(90))

    /** [scheme] with its warning colours in vermillion instead of red. */
    fun scheme(scheme: ColorScheme, brightness: Brightness): ColorScheme {
        val (error, onError, container, onContainer) = warnings(brightness)
        return scheme.copy(error = error, onError = onError, errorContainer = container, onErrorContainer = onContainer)
    }

    /**
     * How [argb] looks to someone with no [vision] of one kind of colour, from Machado, Oliveira
     * and Fernandes (2009) at full strength. For checking that colours stay apart.
     */
    fun simulate(argb: Int, vision: ColourVision): Int {
        val r = Colors.linear(Colors.red(argb))
        val g = Colors.linear(Colors.green(argb))
        val b = Colors.linear(Colors.blue(argb))
        val m = vision.matrix
        return Colors.argb(
            Colors.gamma((m[0] * r + m[1] * g + m[2] * b).coerceIn(0.0, 1.0)),
            Colors.gamma((m[3] * r + m[4] * g + m[5] * b).coerceIn(0.0, 1.0)),
            Colors.gamma((m[6] * r + m[7] * g + m[8] * b).coerceIn(0.0, 1.0)),
        )
    }

    /** How far apart two colours look (CIELAB distance): about 2 is just noticeable, 20 or more is plainly different. */
    fun difference(a: Int, b: Int): Double {
        val (l1, c1, h1) = Colors.lch(a)
        val (l2, c2, h2) = Colors.lch(b)
        val r1 = h1 * kotlin.math.PI / 180
        val r2 = h2 * kotlin.math.PI / 180
        val da = c1 * kotlin.math.cos(r1) - c2 * kotlin.math.cos(r2)
        val db = c1 * kotlin.math.sin(r1) - c2 * kotlin.math.sin(r2)
        return kotlin.math.sqrt((l1 - l2) * (l1 - l2) + da * da + db * db)
    }
}

/** The common kinds of colour blindness: no red cones, no green cones, or no blue cones. */
enum class ColourVision(internal val matrix: DoubleArray) {
    PROTAN(doubleArrayOf(0.152286, 1.052583, -0.204868, 0.114503, 0.786281, 0.099216, -0.003882, -0.048116, 1.051998)),
    DEUTAN(doubleArrayOf(0.367322, 0.860646, -0.227968, 0.280085, 0.672501, 0.047413, -0.011820, 0.042940, 0.968881)),
    TRITAN(doubleArrayOf(1.255528, -0.076749, -0.178779, -0.078411, 0.930809, 0.147602, 0.004733, 0.691367, 0.303900)),
}
