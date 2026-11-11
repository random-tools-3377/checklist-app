package io.github.randomtools3377.checklist.core

/*
 * The character drawn: a friendly figure the user styles (skin, hair, eyes), wearing gear its
 * skills have earned. It is described here as simple shapes on a 120 by 160 grid, so Android
 * draws it, the iPhone app can later, and tests can check it.
 */

/** Hair styles to pick from. */
enum class HairStyle(val label: String) { SHORT("Short"), LONG("Long"), BUN("Bun"), CURLY("Curly"), SPIKY("Spiky"), NONE("None") }

/** Eyes to pick from. */
enum class Eyes(val label: String) { DOTS("Round"), HAPPY("Smiling"), WINK("Wink") }

/** How the user has styled the character. Colours are picked from [Looks.SKINS] and [Looks.HAIR]. */
data class Look(val skin: Int = 1, val hair: HairStyle = HairStyle.SHORT, val hairColour: Int = 1, val eyes: Eyes = Eyes.DOTS)

object Looks {
    val SKINS: List<Int> = listOf(0xFFF9DCC4, 0xFFF1C27D, 0xFFE0AC69, 0xFFC68642, 0xFF8D5524, 0xFF5C3A21).map { it.toInt() }
    val HAIR: List<Int> = listOf(0xFF1F1A17, 0xFF4A2F1E, 0xFF7B4B2A, 0xFFD8B25A, 0xFFA8432A, 0xFF9C9C9C).map { it.toInt() }

    /** The setting the look is kept in. */
    const val SETTING = "character_look"

    /** Kept as "skin,hair,hairColour,eyes", like "1,SHORT,1,DOTS". */
    fun encode(look: Look): String = "${look.skin},${look.hair.name},${look.hairColour},${look.eyes.name}"

    fun decode(text: String?): Look {
        val parts = text?.split(",") ?: return Look()
        if (parts.size != 4) return Look()
        return Look(
            skin = parts[0].toIntOrNull()?.takeIf { it in SKINS.indices } ?: 1,
            hair = HairStyle.entries.firstOrNull { it.name == parts[1] } ?: HairStyle.SHORT,
            hairColour = parts[2].toIntOrNull()?.takeIf { it in HAIR.indices } ?: 1,
            eyes = Eyes.entries.firstOrNull { it.name == parts[3] } ?: Eyes.DOTS,
        )
    }
}

/** Where on the character a piece of gear goes; one piece per place. */
enum class GearSlot { HEAD, FACE, CHEEKS, NECK, BODY, LEFT_HAND, RIGHT_HAND, FEET, BESIDE }

/** Gear a skill earns: at levels 5, 10 and 20 of its area. */
enum class Gear(val area: SkillArea, val level: Int, val slot: GearSlot, val label: String) {
    TRAINERS(SkillArea.FITNESS, 5, GearSlot.FEET, "Trainers"),
    HEADBAND(SkillArea.FITNESS, 10, GearSlot.HEAD, "Headband"),
    MEDAL(SkillArea.FITNESS, 20, GearSlot.NECK, "Gold medal"),
    BOTTLE(SkillArea.HEALTH, 5, GearSlot.LEFT_HAND, "Water bottle"),
    GLOW(SkillArea.HEALTH, 10, GearSlot.CHEEKS, "Healthy glow"),
    HEART(SkillArea.HEALTH, 20, GearSlot.BESIDE, "Heart"),
    GLASSES(SkillArea.MIND, 5, GearSlot.FACE, "Glasses"),
    BOOK(SkillArea.MIND, 10, GearSlot.RIGHT_HAND, "Book"),
    CAP(SkillArea.MIND, 20, GearSlot.HEAD, "Graduation cap"),
    APRON(SkillArea.HOME, 5, GearSlot.BODY, "Apron"),
    SPATULA(SkillArea.HOME, 10, GearSlot.LEFT_HAND, "Spatula"),
    PLANT(SkillArea.HOME, 20, GearSlot.BESIDE, "Potted plant"),
    LANYARD(SkillArea.WORK, 5, GearSlot.NECK, "Name badge"),
    BRIEFCASE(SkillArea.WORK, 10, GearSlot.RIGHT_HAND, "Briefcase"),
    BLAZER(SkillArea.WORK, 20, GearSlot.BODY, "Blazer"),
    BERET(SkillArea.CREATIVE, 5, GearSlot.HEAD, "Beret"),
    BRUSH(SkillArea.CREATIVE, 10, GearSlot.LEFT_HAND, "Paintbrush"),
    SCARF(SkillArea.CREATIVE, 20, GearSlot.NECK, "Scarf"),
}

/** What the character wears for its skills: one piece per place, from the stronger skill when two want it. */
data class Outfit(val gear: List<Gear>, val shirt: SkillArea?) {
    companion object {
        fun of(sheet: CharacterSheet): Outfit {
            val levels = SkillArea.entries.associateWith { sheet.skillLevel(it).level }
            val earned = Gear.entries.filter { levels.getValue(it.area) >= it.level }
            val worn = earned.groupBy { it.slot }.values.map { pieces ->
                pieces.maxWith(compareBy<Gear>({ sheet.skills[it.area] ?: 0 }, { it.level }).thenByDescending { it.area.ordinal })
            }
            // The shirt is the colour of the skill that leads, once one has got going.
            val top = SkillArea.entries.maxByOrNull { sheet.skills[it] ?: 0 }?.takeIf { (levels[it] ?: 1) >= 2 }
            return Outfit(worn.sortedBy { it.ordinal }, top)
        }

        /** The next piece each skill will earn, with the level it comes at; none for a skill with them all. */
        fun next(sheet: CharacterSheet): List<Gear> = SkillArea.entries.mapNotNull { area ->
            val level = sheet.skillLevel(area).level
            Gear.entries.filter { it.area == area && it.level > level }.minByOrNull { it.level }
        }
    }
}

/** One shape of the drawing. Colours are ARGB; a path is in SVG path syntax. */
sealed interface Mark {
    data class Circle(val cx: Float, val cy: Float, val r: Float, val fill: Int?, val stroke: Int? = null, val width: Float = 0f) : Mark
    data class Oval(val cx: Float, val cy: Float, val rx: Float, val ry: Float, val fill: Int) : Mark
    data class Box(val x: Float, val y: Float, val w: Float, val h: Float, val radius: Float, val fill: Int) : Mark
    data class Path(val d: String, val fill: Int?, val stroke: Int? = null, val width: Float = 0f) : Mark
}

object Figure {
    const val WIDTH = 120f
    const val HEIGHT = 160f

    /** Each area's colour, for the shirt and gear. */
    fun colour(area: SkillArea): Int = when (area) {
        SkillArea.FITNESS -> 0xFFE4572E
        SkillArea.HEALTH -> 0xFF3BA99C
        SkillArea.MIND -> 0xFF5B6CC8
        SkillArea.HOME -> 0xFFE8A33D
        SkillArea.WORK -> 0xFF3A4E7A
        SkillArea.CREATIVE -> 0xFFB5579B
    }.toInt()

    private val INK = 0xFF2B2B2B.toInt()
    private val WHITE = 0xFFFFFFFF.toInt()
    private val GOLD = 0xFFF2C14E.toInt()

    /** The shapes for [look] wearing [outfit], with the face for [expression], back to front. */
    fun marks(look: Look, outfit: Outfit, expression: Expression = Expression.CALM): List<Mark> {
        val skin = Looks.SKINS[look.skin]
        val hair = Looks.HAIR[look.hairColour]
        val shirt = outfit.shirt?.let(::colour) ?: 0xFF8AA8A1.toInt()
        val worn = outfit.gear.associateBy { it.slot }
        val m = mutableListOf<Mark>()

        // Behind: the ground's shadow, things beside, and long hair.
        m += Mark.Oval(60f, 152f, 32f, 5f, 0x22000000)
        when (worn[GearSlot.BESIDE]) {
            Gear.PLANT -> {
                m += Mark.Oval(104f, 118f, 6f, 12f, 0xFF4E9F3D.toInt())
                m += Mark.Oval(112f, 124f, 6f, 10f, 0xFF6BBF59.toInt())
                m += Mark.Oval(97f, 126f, 5f, 9f, 0xFF6BBF59.toInt())
                m += Mark.Path("M95,132 L113,132 L110,150 L98,150 Z", 0xFFC8623B.toInt())
            }
            Gear.HEART -> m += Mark.Path("M104,30 C104,24 112,22 112,29 C112,22 120,24 120,30 C120,36 112,40 112,44 C112,40 104,36 104,30 Z", 0xFFE8505B.toInt())
            else -> Unit
        }
        if (look.hair == HairStyle.LONG) m += Mark.Box(31f, 30f, 58f, 50f, 14f, hair)

        // Legs and shoes.
        val trousers = if (outfit.shirt == SkillArea.WORK || worn[GearSlot.BODY] == Gear.BLAZER) 0xFF33384A.toInt() else 0xFF3B4A6B.toInt()
        m += Mark.Box(46f, 112f, 11f, 36f, 4f, trousers)
        m += Mark.Box(63f, 112f, 11f, 36f, 4f, trousers)
        if (worn[GearSlot.FEET] == Gear.TRAINERS) {
            m += Mark.Oval(51f, 148f, 9f, 5f, WHITE)
            m += Mark.Oval(69f, 148f, 9f, 5f, WHITE)
            m += Mark.Box(44f, 146f, 13f, 2.2f, 1f, colour(SkillArea.FITNESS))
            m += Mark.Box(63f, 146f, 13f, 2.2f, 1f, colour(SkillArea.FITNESS))
        } else {
            m += Mark.Oval(51f, 148f, 8f, 4.5f, 0xFF5B4636.toInt())
            m += Mark.Oval(69f, 148f, 8f, 4.5f, 0xFF5B4636.toInt())
        }

        // Arms, body and neck.
        m += Mark.Box(29f, 76f, 11f, 34f, 5.5f, shirt)
        m += Mark.Box(80f, 76f, 11f, 34f, 5.5f, shirt)
        m += Mark.Box(40f, 72f, 40f, 46f, 12f, shirt)
        m += Mark.Box(55f, 62f, 10f, 12f, 3f, skin)
        when (worn[GearSlot.BODY]) {
            Gear.APRON -> {
                m += Mark.Path("M49,82 L52,72 M71,82 L68,72", null, 0xFFE2CFA0.toInt(), 2f)
                m += Mark.Box(45f, 82f, 30f, 36f, 6f, 0xFFF6E7C1.toInt())
                m += Mark.Box(54f, 96f, 12f, 8f, 2f, 0xFFE9D39E.toInt())
            }
            Gear.BLAZER -> {
                val jacket = 0xFF2E3A56.toInt()
                m += Mark.Path("M40,84 Q40,72 52,72 L58,72 L60,98 L60,118 L46,118 Q40,118 40,112 Z", jacket)
                m += Mark.Path("M80,84 Q80,72 68,72 L62,72 L60,98 L60,118 L74,118 Q80,118 80,112 Z", jacket)
                m += Mark.Path("M52,72 L58,72 L60,90 Z M68,72 L62,72 L60,90 Z", 0xFF24304A.toInt())
                m += Mark.Box(29f, 76f, 11f, 34f, 5.5f, jacket)
                m += Mark.Box(80f, 76f, 11f, 34f, 5.5f, jacket)
            }
            else -> Unit
        }
        when (worn[GearSlot.NECK]) {
            Gear.MEDAL -> {
                m += Mark.Path("M51,72 L60,88 L69,72", null, colour(SkillArea.FITNESS), 3f)
                m += Mark.Circle(60f, 92f, 6f, GOLD, 0xFFC99A2E.toInt(), 1.2f)
            }
            Gear.LANYARD -> {
                m += Mark.Path("M50,72 L60,92 L70,72", null, colour(SkillArea.WORK), 2f)
                m += Mark.Box(54f, 92f, 12f, 9f, 1.5f, WHITE)
                m += Mark.Box(54f, 92f, 12f, 2.5f, 1f, colour(SkillArea.WORK))
            }
            Gear.SCARF -> {
                m += Mark.Box(46f, 64f, 28f, 9f, 4f, colour(SkillArea.CREATIVE))
                m += Mark.Box(63f, 70f, 8f, 20f, 3f, colour(SkillArea.CREATIVE))
            }
            else -> Unit
        }

        // Things in the hands, then the hands.
        when (worn[GearSlot.LEFT_HAND]) {
            Gear.BOTTLE -> {
                m += Mark.Box(29f, 102f, 9f, 20f, 3f, 0xFF9AD4F0.toInt())
                m += Mark.Box(30.5f, 98f, 6f, 4f, 1f, colour(SkillArea.HEALTH))
            }
            Gear.SPATULA -> {
                m += Mark.Path("M34.5,114 L34.5,97", null, 0xFF8A5A3B.toInt(), 2.4f)
                m += Mark.Box(30f, 86f, 9f, 11f, 2f, 0xFF9AA3A8.toInt())
            }
            Gear.BRUSH -> {
                m += Mark.Path("M34.5,116 L34.5,96", null, 0xFF8A5A3B.toInt(), 2.4f)
                m += Mark.Path("M32,96 L37,96 L34.5,88 Z", colour(SkillArea.CREATIVE))
            }
            else -> Unit
        }
        when (worn[GearSlot.RIGHT_HAND]) {
            Gear.BOOK -> {
                m += Mark.Box(81f, 98f, 15f, 18f, 1.5f, colour(SkillArea.MIND))
                m += Mark.Box(83f, 100f, 2f, 14f, 0.5f, 0x55FFFFFF)
            }
            Gear.BRIEFCASE -> {
                m += Mark.Path("M84,114 Q89,106 94,114", null, 0xFF5B3A26.toInt(), 2f)
                m += Mark.Box(79f, 113f, 20f, 14f, 2f, 0xFF7A4E33.toInt())
            }
            else -> Unit
        }
        m += Mark.Circle(34.5f, 112f, 5.5f, skin)
        m += Mark.Circle(85.5f, 112f, 5.5f, skin)

        // Head: ears, face, then hair on top.
        m += Mark.Circle(34f, 46f, 5f, skin)
        m += Mark.Circle(86f, 46f, 5f, skin)
        m += Mark.Circle(60f, 44f, 26f, skin)
        // Proud: eyes closed in a smile; tired: eyes half shut. Otherwise the eyes picked.
        when {
            expression == Expression.PROUD -> m += Mark.Path("M47,47 Q51,42 55,47 M65,47 Q69,42 73,47", null, INK, 2.2f)
            expression == Expression.TIRED -> {
                m += Mark.Path("M47,46 Q51,50 55,46 M65,46 Q69,50 73,46", null, INK, 2.2f)
                m += Mark.Path("M47,46 L55,46 M65,46 L73,46", null, INK, 1.4f)
            }
            look.eyes == Eyes.DOTS -> {
                m += Mark.Circle(51f, 46f, 3f, INK)
                m += Mark.Circle(69f, 46f, 3f, INK)
            }
            look.eyes == Eyes.HAPPY -> m += Mark.Path("M47,47 Q51,42 55,47 M65,47 Q69,42 73,47", null, INK, 2.2f)
            else -> {
                m += Mark.Circle(51f, 46f, 3f, INK)
                m += Mark.Path("M65,47 Q69,42 73,47", null, INK, 2.2f)
            }
        }
        // Eyebrows, in the hair's colour (ink with no hair): raised in the middle when worried,
        // higher when happy or proud, lower when tired.
        val brows = when (expression) {
            Expression.WORRIED -> "M47,39 Q51,38 55,35 M65,35 Q69,38 73,39"
            Expression.HAPPY, Expression.PROUD -> "M47,37 Q51,34 55,37 M65,37 Q69,34 73,37"
            Expression.TIRED -> "M47,41 Q51,40 55,41 M65,41 Q69,40 73,41"
            Expression.CALM -> "M47,39 Q51,37 55,39 M65,39 Q69,37 73,39"
        }
        if (look.hair != HairStyle.NONE) {
            m += Mark.Path(brows, null, hair, 1.8f)
        } else if (expression != Expression.CALM) {
            m += Mark.Path(brows, null, INK, 1.4f)
        }
        if (worn[GearSlot.CHEEKS] == Gear.GLOW) {
            m += Mark.Circle(45f, 54f, 4.5f, 0x66F06A7A)
            m += Mark.Circle(75f, 54f, 4.5f, 0x66F06A7A)
        }
        when (expression) {
            Expression.CALM -> m += Mark.Path("M54,56 Q60,62 66,56", null, INK, 2f)
            // An open smile.
            Expression.HAPPY, Expression.PROUD -> {
                m += Mark.Path("M52,55 Q60,66 68,55 Z", 0xFF8C2F39.toInt(), INK, 1.6f)
                m += Mark.Path("M55,61 Q60,64 65,61", null, 0xFFE8707A.toInt(), 2f)
            }
            Expression.TIRED -> m += Mark.Path("M55,58 Q60,60 65,58", null, INK, 2f)
            // A little frown, and a drop of sweat.
            Expression.WORRIED -> {
                m += Mark.Path("M54,59 Q60,55 66,59", null, INK, 2f)
                m += Mark.Path("M81,34 Q84,40 81,42 Q78,40 81,34 Z", 0xFF9AD4F0.toInt())
            }
        }
        if (worn[GearSlot.FACE] == Gear.GLASSES) {
            m += Mark.Circle(51f, 46f, 6.5f, null, INK, 1.8f)
            m += Mark.Circle(69f, 46f, 6.5f, null, INK, 1.8f)
            m += Mark.Path("M57.5,46 L62.5,46", null, INK, 1.8f)
        }
        when (look.hair) {
            HairStyle.SHORT, HairStyle.LONG -> m += Mark.Path("M34,42 Q34,16 60,16 Q86,16 86,42 Q80,28 60,28 Q40,28 34,42 Z", hair)
            HairStyle.BUN -> {
                // A beret or a cap covers the bun; a headband doesn't.
                val hat = worn[GearSlot.HEAD]
                if (hat != Gear.BERET && hat != Gear.CAP) m += Mark.Circle(60f, 14f, 9f, hair)
                m += Mark.Path("M34,42 Q34,16 60,16 Q86,16 86,42 Q80,28 60,28 Q40,28 34,42 Z", hair)
            }
            HairStyle.CURLY -> listOf(36f to 34f, 42f to 24f, 51f to 19f, 60f to 17f, 69f to 19f, 78f to 24f, 84f to 34f)
                .forEach { (x, y) -> m += Mark.Circle(x, y, 8f, hair) }
            HairStyle.SPIKY -> m += Mark.Path("M34,40 L36,22 L44,28 L48,14 L55,24 L60,10 L65,24 L72,14 L76,28 L84,22 L86,40 Q78,28 60,28 Q42,28 34,40 Z", hair)
            HairStyle.NONE -> Unit
        }
        when (worn[GearSlot.HEAD]) {
            Gear.HEADBAND -> m += Mark.Box(34f, 30f, 52f, 6f, 3f, colour(SkillArea.FITNESS))
            Gear.CAP -> {
                m += Mark.Box(45f, 20f, 30f, 10f, 2f, 0xFF262B3A.toInt())
                m += Mark.Path("M28,20 L60,9 L92,20 L60,31 Z", 0xFF262B3A.toInt())
                m += Mark.Path("M60,20 L86,24 L86,34", null, GOLD, 1.6f)
            }
            Gear.BERET -> {
                m += Mark.Oval(55f, 21f, 27f, 9f, colour(SkillArea.CREATIVE))
                m += Mark.Circle(55f, 12f, 2.5f, colour(SkillArea.CREATIVE))
            }
            else -> Unit
        }
        return m
    }

    /** The drawing as SVG, for checking it by eye. */
    fun svg(marks: List<Mark>, scale: Float = 2f): String = buildString {
        fun colourAttr(name: String, argb: Int?): String {
            if (argb == null) return "$name=\"none\""
            val a = (argb ushr 24) and 0xFF
            val rgb = (argb and 0xFFFFFF).toString(16).padStart(6, '0')
            return "$name=\"#$rgb\" $name-opacity=\"${a / 255.0}\""
        }
        append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${WIDTH * scale}\" height=\"${HEIGHT * scale}\" viewBox=\"0 0 $WIDTH $HEIGHT\">")
        marks.forEach { mark ->
            when (mark) {
                is Mark.Circle -> append("<circle cx=\"${mark.cx}\" cy=\"${mark.cy}\" r=\"${mark.r}\" ${colourAttr("fill", mark.fill)} ${colourAttr("stroke", mark.stroke)} stroke-width=\"${mark.width}\"/>")
                is Mark.Oval -> append("<ellipse cx=\"${mark.cx}\" cy=\"${mark.cy}\" rx=\"${mark.rx}\" ry=\"${mark.ry}\" ${colourAttr("fill", mark.fill)}/>")
                is Mark.Box -> append("<rect x=\"${mark.x}\" y=\"${mark.y}\" width=\"${mark.w}\" height=\"${mark.h}\" rx=\"${mark.radius}\" ${colourAttr("fill", mark.fill)}/>")
                is Mark.Path -> append("<path d=\"${mark.d}\" ${colourAttr("fill", mark.fill)} ${colourAttr("stroke", mark.stroke)} stroke-width=\"${mark.width}\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>")
            }
        }
        append("</svg>")
    }
}
