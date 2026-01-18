package com.samwahome.skopiojetbrains.skopiojetbrains.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class ActivityCategory {
    CODING, DEBUGGING, COMPILING, WRITING_DOCS, CODE_REVIEWING, TESTING;

    fun toCliValue(): String = when (this) {
        CODING -> "Coding"
        DEBUGGING -> "Debugging"
        COMPILING -> "Compiling"
        WRITING_DOCS -> "Writing Docs"
        CODE_REVIEWING -> "Code Reviewing"
        TESTING -> "Testing"
    }
}

enum class EntityType {
    APP, FILE, URL;

    fun toCliValue(): String = when (this) {
        APP -> "App"
        FILE -> "File"
        URL -> "Url"
    }
}

data class EntityRef(
    val type: EntityType,
    val value: String,
    val displayName: String,
)

data class UsageEvent(
    val category: ActivityCategory,
    val app: String,
    val entity: EntityRef,
    val projectPath: String,
    val source: String,
    val timestamp: Long,
    val endTimestamp: Long,
) {
    init {
        require(endTimestamp >= timestamp) { "End timestamp must be greater than start timestamp" }
    }

    val durationSec: Long
        get() = (endTimestamp - timestamp).coerceAtLeast(0)
}

@Serializable
data class LatestJson(
    val version: String,
    @SerialName("released_at") val releasedAt: String? = null,
    val assets: Map<String, LatestAsset>
)

@Serializable
data class LatestAsset(
    val url: String,
    val sha256: String,
    val size: Long? = null,
)