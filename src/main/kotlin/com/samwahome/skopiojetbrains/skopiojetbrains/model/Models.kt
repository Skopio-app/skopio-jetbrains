package com.samwahome.skopiojetbrains.skopiojetbrains.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

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

interface LatestReader {
    fun fetch(latestJsonUrl: String): LatestJson
}

class HttpLatestReader(
    private val http: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : LatestReader {
    override fun fetch(latestJsonUrl: String): LatestJson {
        val req = HttpRequest.newBuilder().uri(URI.create(latestJsonUrl)).GET().build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() !in 200..299) {
            error("Failed to fetch latest.json ($latestJsonUrl): HTTP: ${res.statusCode()}")
        }
        return json.decodeFromString(LatestJson.serializer(), res.body())
    }
}

object PlatformArch {
    fun detectArch(): String {
        val os = System.getProperty("os.name").lowercase()
        require(os.contains("mac")) { "Only macOS supported for now. os=$os"}

        val arch = System.getProperty("os.arch").lowercase()
        return when {
            arch.contains("aarch64") || arch.contains("arm64") -> "aarch64"
            arch.contains("x86_64") || arch.contains("amd64") -> "x86_64"
            else -> error("Unsupported arch: $arch")
        }
    }

    fun latestJsonAssetKey(): String = "darwin-${detectArch()}"
}