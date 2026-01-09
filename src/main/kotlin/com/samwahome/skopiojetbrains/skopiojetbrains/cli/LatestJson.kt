package com.samwahome.skopiojetbrains.skopiojetbrains.cli

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

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
): LatestReader {
    override fun fetch(latestJsonUrl: String): LatestJson {
        val req = HttpRequest.newBuilder().uri(URI.create(latestJsonUrl)).GET().build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() !in 200..299) {
            error("Failed to fetch latest.json ($latestJsonUrl): HTTP: ${res.statusCode()}")
        }
        return json.decodeFromString(LatestJson.serializer(), res.body())
    }
}