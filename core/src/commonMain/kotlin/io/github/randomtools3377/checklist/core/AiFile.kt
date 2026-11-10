package io.github.randomtools3377.checklist.core

/**
 * The AI file: a small open model, downloaded once in the phone's browser and picked in
 * Checklist AI, which checks it is exactly this file before using it. CI downloads it from the
 * same address and checks the same size and checksum (.github/workflows/ai-files.yml).
 */
object AiFile {
    const val NAME = "Qwen3 0.6B"
    const val LICENCE = "Apache 2.0"
    const val FILE = "Qwen3-0.6B.litertlm"
    const val SIZE = 614_236_160L
    const val SHA256 = "555579ff2f4fd13379abe69c1c3ab5200f7338bc92471557f1d6614a6e5ab0b4"
    const val URL = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/8414150f2e9dcc82449bcc9c5abc404b399a4d06/Qwen3-0.6B.litertlm?download=true"

    /** The size for people: "614 MB". */
    val sizeText: String get() = "${SIZE / 1_000_000} MB"
}
