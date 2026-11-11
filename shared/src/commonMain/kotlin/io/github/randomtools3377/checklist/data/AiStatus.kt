package io.github.randomtools3377.checklist.data

/**
 * What this app knows about Checklist AI, the separate app that runs the AI. [version] is its
 * version when it answered; [ready] whether its AI file is added; [problem] why it didn't answer.
 */
data class AiStatus(
    val installed: Boolean,
    val version: String? = null,
    val ready: Boolean = false,
    val problem: String? = null,
    /** Whether Checklist AI's speech file is added, for voice. */
    val speechReady: Boolean = false,
    /** This app's own version, to compare. */
    val appVersion: String = "",
) {
    /** Whether the AI can be asked now. */
    val usable: Boolean get() = installed && ready && problem == null

    /** Whether the two apps are different versions, which they shouldn't be. */
    val outOfStep: Boolean get() = version != null && version != appVersion
}
