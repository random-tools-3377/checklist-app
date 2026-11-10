package io.github.randomtools3377.checklist.core

import kotlin.test.Test
import kotlin.test.assertEquals

class VoiceCommandTest {
    private val goals = listOf(1L to "Read", 2L to "Gym", 3L to "Drink water", 4L to "Tidy desk", 5L to "Stretching")

    @Test
    fun doneTicksTheGoalItNames() {
        assertEquals(VoiceCommand.Tick(1, "Read"), VoiceCommand.parse("done reading", goals))
        assertEquals(VoiceCommand.Tick(2, "Gym"), VoiceCommand.parse("I did the gym", goals))
        assertEquals(VoiceCommand.Tick(3, "Drink water"), VoiceCommand.parse("finished drinking water", goals))
        assertEquals(VoiceCommand.Tick(4, "Tidy desk"), VoiceCommand.parse("desk done", goals))
        assertEquals(VoiceCommand.Tick(5, "Stretching"), VoiceCommand.parse("Done stretches.", goals))
    }

    @Test
    fun addMakesATodo() {
        assertEquals(VoiceCommand.Add("Buy milk"), VoiceCommand.parse("add buy milk", goals))
        assertEquals(VoiceCommand.Add("Call the bank"), VoiceCommand.parse("remind me to call the bank", goals))
        assertEquals(VoiceCommand.Add("Post the parcel"), VoiceCommand.parse("to do post the parcel", goals))
    }

    @Test
    fun anythingElseIsNotUnderstood() {
        assertEquals(VoiceCommand.NotUnderstood("hello there"), VoiceCommand.parse("hello there", goals))
        assertEquals(VoiceCommand.NotUnderstood("done swimming"), VoiceCommand.parse("done swimming", goals))
        assertEquals(VoiceCommand.NotUnderstood(""), VoiceCommand.parse("", goals))
        assertEquals(VoiceCommand.NotUnderstood("add"), VoiceCommand.parse("add", goals))
    }
}
