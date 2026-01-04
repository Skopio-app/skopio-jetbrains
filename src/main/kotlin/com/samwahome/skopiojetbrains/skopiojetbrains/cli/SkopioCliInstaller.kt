package com.samwahome.skopiojetbrains.skopiojetbrains.cli

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import kotlin.io.path.*

class SkopioCliInstaller(
    private val installDir: Path,
    private val downloadUrl: String,
    private val binaryName: String,
) {
    private val http = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun cliPath(): Path = installDir.resolve(binaryName)

    fun ensureInstalled(): Path {
        installDir.createDirectories()
        val path = cliPath()
        if (path.exists()) return path

        val req = HttpRequest.newBuilder()
            .uri(URI.create(downloadUrl))
            .GET()
            .build()

        val res = http.send(req, HttpResponse.BodyHandlers.ofByteArray())
        if (res.statusCode() !in 200..299) {
            throw RuntimeException("Failed to download Skopio CLI: HTTP ${res.statusCode()}")
        }

        path.writeBytes(res.body())
        path.toFile().setExecutable(true)
        return path
    }
}