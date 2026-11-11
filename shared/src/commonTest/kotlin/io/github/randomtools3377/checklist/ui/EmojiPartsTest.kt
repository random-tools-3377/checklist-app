package io.github.randomtools3377.checklist.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** The parts of an emoji a font must have, for leaving out emoji the device can't draw. */
class EmojiPartsTest {
    @Test
    fun anEmojiIsSplitIntoTheCharactersThatDrawSomething() {
        assertEquals(listOf(0x1F600), emojiParts("😀"))
        // The variation selector draws nothing alone.
        assertEquals(listOf(0x2764), emojiParts("❤️"))
        // A skin tone counts; the joiner doesn't.
        assertEquals(listOf(0x1F469, 0x1F3FD, 0x1F4BB), emojiParts("👩🏽‍💻"))
        // A keycap needs its base and the keycap.
        assertEquals(listOf(0x23, 0x20E3), emojiParts("#️⃣"))
        // England's flag: the black flag; its tag letters draw nothing alone.
        assertEquals(listOf(0x1F3F4), emojiParts("🏴󠁧󠁢󠁥󠁮󠁧󠁿"))
        // Newer emoji, like heart hands from Emoji 14, are code points beyond the first plane.
        assertEquals(listOf(0x1FAF6), emojiParts("🫶"))
        assertEquals(emptyList(), emojiParts(""))
    }
}
