package io.github.randomtools3377.checklist.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig

/**
 * The AI engine, loaded once and kept while this app's process lives. Loading the file takes a
 * few seconds the first time; after that answers start sooner. One question at a time.
 */
object Brain {
    private var engine: Engine? = null

    /**
     * Answers [prompt] with [system] as its instructions. With a [schema], the answer is held to
     * that JSON shape while it is written. Low temperature: steady answers, not creative ones.
     */
    @Synchronized
    fun answer(context: Context, system: String, prompt: String, schema: String?, maxTokens: Int): String {
        check(AiFileStore.ready(context)) { "The AI file isn't added yet." }
        val loaded = engine ?: Engine(
            EngineConfig(
                modelPath = AiFileStore.file(context).path,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.path,
            ),
        ).also {
            it.initialize()
            engine = it
        }
        val config = ConversationConfig(
            systemInstruction = Contents.of(system),
            samplerConfig = SamplerConfig(topK = 20, topP = 0.8, temperature = 0.2),
            // Qwen3 can think out loud before answering; that is slow and not needed here.
            thinkingConfig = ThinkingConfig(enableThinking = false),
            maxOutputToken = maxTokens,
            enableResponseFormat = schema != null,
        )
        return loaded.createConversation(config).use { conversation ->
            conversation.sendMessage(
                Contents.of(prompt),
                responseFormat = schema?.let { ResponseFormat.json(it) },
            ).toString()
        }
    }

    /** Lets the engine go, to free memory or before the AI file is removed. */
    @Synchronized
    fun close() {
        engine?.close()
        engine = null
    }
}
