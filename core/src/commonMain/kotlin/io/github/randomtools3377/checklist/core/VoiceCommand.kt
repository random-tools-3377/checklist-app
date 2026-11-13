package io.github.randomtools3377.checklist.core

/**
 * What was said to the 🎙 button on Today, or typed instead, worked out on the phone: tick a
 * goal or a to-do ("done reading", "I did the gym"), add a to-do ("add buy milk", "remind me to
 * call the bank"), a question ("what's next", "how am I doing", "what's left"), which the app
 * answers from its own numbers, or none of these. Nothing is ticked or added until the user
 * confirms it.
 */
sealed interface VoiceCommand {
    /** [todo] says it is a to-do rather than a goal due today. */
    data class Tick(val goalId: Long, val name: String, val todo: Boolean = false) : VoiceCommand
    data class Add(val todo: String) : VoiceCommand

    /** A question, for the app to answer (see [Asker]). */
    data class Question(val question: String) : VoiceCommand

    /**
     * Not understood. When it sounded like "done …" with no goal recognised, [closest] holds
     * the open goals and to-dos to offer as buttons (id to name), the likeliest first.
     */
    data class NotUnderstood(val heard: String, val closest: List<Pair<Long, String>> = emptyList()) : VoiceCommand

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
        private val questionWords = listOf(
            "what", "what's", "whats", "how", "when", "why", "where", "which", "who", "can", "could", "do", "does", "is", "are", "am",
            "tell me", "show me", "should",
        )

        /** Whether [heard] reads as a question for the app rather than something to do. */
        fun isQuestion(heard: String): Boolean {
            val said = heard.lowercase().replace(Regex("[^a-z0-9'? ]"), " ").replace(Regex("\\s+"), " ").trim()
            return said.endsWith("?") || questionWords.any { said == it || said.startsWith("$it ") }
        }

        /**
         * Works out what [heard] asks for, matching names among [goals] due today and open
         * [todos] (id to name).
         */
        fun parse(heard: String, goals: List<Pair<Long, String>>, todos: List<Pair<Long, String>> = emptyList()): VoiceCommand {
            val said = heard.lowercase().replace(Regex("[^a-z0-9' ]"), " ").replace(Regex("\\s+"), " ").trim()
            if (said.isEmpty()) return NotUnderstood(heard)
            if (isQuestion(heard)) return Question(heard.trim().take(Asker.MAX_QUESTION))
            addWords.firstOrNull { said.startsWith("$it ") }?.let { word ->
                val todo = said.removePrefix(word).trim()
                if (todo.isNotEmpty()) return Add(todo.replaceFirstChar { it.uppercaseChar() }.take(80))
            }
            val rest = tickWords.firstOrNull { said.startsWith("$it ") }?.let { said.removePrefix(it).trim() }
                ?: listOf(" done", " is done", " finished").firstOrNull { said.endsWith(it) }?.let { said.removeSuffix(it).trim() }
                ?: return NotUnderstood(heard)
            bestGoal(rest, goals)?.let { (id, name) -> return Tick(id, name) }
            bestGoal(rest, todos)?.let { (id, name) -> return Tick(id, name, todo = true) }
            return NotUnderstood(heard, closest(rest, goals + todos))
        }

        /** The open goals and to-dos to offer when none was recognised: those sharing letters with [words] first, at most 6. */
        fun closest(words: String, open: List<Pair<Long, String>>): List<Pair<Long, String>> {
            val said = words.split(" ").filter { it.length >= 3 && it !in fillers }.map { it.take(3) }
            return open.distinctBy { it.first }.sortedByDescending { (_, name) ->
                val lower = name.lowercase()
                said.count { it in lower }
            }.take(6)
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
