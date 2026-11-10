package io.github.randomtools3377.checklist.core

/**
 * What was said to the 🎙 button on Today, worked out on the phone: tick a goal ("done
 * reading", "I did the gym"), add a to-do ("add buy milk", "remind me to call the bank"), or
 * neither. Nothing happens until the user confirms it.
 */
sealed interface VoiceCommand {
    data class Tick(val goalId: Long, val name: String) : VoiceCommand
    data class Add(val todo: String) : VoiceCommand
    data class NotUnderstood(val heard: String) : VoiceCommand

    companion object {
        private val tickWords = listOf(
            "i have done", "i've done", "i have finished", "i've finished", "i finished", "i did", "i've",
            "done with", "finished with", "done", "did", "finished", "completed", "tick off", "tick",
        )
        private val addWords = listOf(
            "remind me to", "add a to do to", "add a todo to", "add a to do", "add a todo", "add to do", "add todo",
            "new to do", "new todo", "to do", "todo", "i need to", "add",
        )
        private val fillers = setOf("the", "a", "an", "my", "some", "of", "for", "today", "now", "please", "it", "with", "again")

        /** Works out what [heard] asks for, matching goal names among [goals] (id to name). */
        fun parse(heard: String, goals: List<Pair<Long, String>>): VoiceCommand {
            val said = heard.lowercase().replace(Regex("[^a-z0-9' ]"), " ").replace(Regex("\\s+"), " ").trim()
            if (said.isEmpty()) return NotUnderstood(heard)
            addWords.firstOrNull { said.startsWith("$it ") }?.let { word ->
                val todo = said.removePrefix(word).trim()
                if (todo.isNotEmpty()) return Add(todo.replaceFirstChar { it.uppercaseChar() }.take(80))
            }
            val rest = tickWords.firstOrNull { said.startsWith("$it ") }?.let { said.removePrefix(it).trim() }
                ?: listOf(" done", " is done", " finished").firstOrNull { said.endsWith(it) }?.let { said.removeSuffix(it).trim() }
                ?: return NotUnderstood(heard)
            return bestGoal(rest, goals)?.let { (id, name) -> Tick(id, name) } ?: NotUnderstood(heard)
        }

        /** The goal whose name shares the most words with [words], allowing "reading" for "Read". */
        private fun bestGoal(words: String, goals: List<Pair<Long, String>>): Pair<Long, String>? {
            val said = words.split(" ").filter { it.isNotBlank() && it !in fillers }.map(::stem).toSet()
            if (said.isEmpty()) return null
            return goals.map { goal ->
                val name = goal.second.lowercase().replace(Regex("[^a-z0-9 ]"), " ").split(" ").filter { it.isNotBlank() && it !in fillers }.map(::stem)
                goal to if (name.isEmpty()) 0.0 else name.count { it in said }.toDouble() / name.size + name.count { it in said }
            }.filter { it.second > 0 }.maxByOrNull { it.second }?.first
        }

        /** A word without its ending, so "reading", "reads" and "read" match. */
        private fun stem(word: String): String {
            var w = word.removeSuffix("'s")
            for (ending in listOf("ing", "ed", "es", "s")) {
                if (w.length > ending.length + 2 && w.endsWith(ending)) {
                    w = w.dropLast(ending.length)
                    break
                }
            }
            return w
        }
    }
}
