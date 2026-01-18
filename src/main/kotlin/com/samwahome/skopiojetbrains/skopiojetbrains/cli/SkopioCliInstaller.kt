package com.samwahome.skopiojetbrains.skopiojetbrains.cli

import com.samwahome.skopiojetbrains.skopiojetbrains.model.*
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream
import java.security.MessageDigest
import kotlin.io.path.*

class SkopioCliInstaller(
    private val installDir: Path,
    private val latestJsonUrl: String,
    private val http: HttpClient = defaultHttpClient(),
    private val latestReader: LatestReader = HttpLatestReader(http),
    private val unzipper: Unzipper = ZipUnzipper(),
) {

    fun ensureInstalled(): Path {
        installDir.createDirectories()

        val arch = PlatformArch.detectArch()
        val binaryName = "skopio-cli-darwin-$arch"
        val targetPath = installDir.resolve(binaryName)
        val versionMarker = installDir.resolve("$binaryName.version")

        val latest = latestReader.fetch(latestJsonUrl)
        val assetKey = PlatformArch.latestJsonAssetKey()
        val asset = latest.assets[assetKey]
            ?: error("latest.json missing asset key '$assetKey'")

        if (targetPath.exists() && versionMarker.exists()) {
            val installed = versionMarker.readText().trim()
            if (installed == latest.version) return targetPath
        }

        val tmpDir = installDir.resolve(".tmp").also { it.createDirectories() }

        val zipName = asset.url.substringAfterLast('/')
        val tmpZip = tmpDir.resolve(zipName)

        downloadToFile(asset.url, tmpZip)

        // Verify sha256 against latest.json
        val actualSha = sha256Hex(tmpZip)
        if (!actualSha.equals(asset.sha256, ignoreCase = true)) {
            tmpZip.deleteIfExists()
            error("SHA-256 mismatch for $zipName. expected=${asset.sha256} actual=$actualSha")
        }

        val extracted = unzipper.extractSingleFile(tmpZip, tmpDir)
        extracted.toFile().setExecutable(true)

        atomicReplace(extracted, targetPath)
        versionMarker.writeText(latest.version)

        tmpZip.deleteIfExists()
        return targetPath
    }

    private fun downloadToFile(url: String, dest: Path) {
        val req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofByteArray())
        if (res.statusCode() !in 200..299) {
            error("Failed to download $url: HTTP ${res.statusCode()}")
        }
        dest.writeBytes(res.body())
    }

    private fun atomicReplace(from: Path, to: Path) {
        try {
            Files.move(
                from,
                to,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            Files.move(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        fun defaultHttpClient(): HttpClient =
            HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()

        fun sha256Hex(path: Path): String {
            val md = MessageDigest.getInstance("SHA-256")
            path.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}


interface Unzipper {
    fun extractSingleFile(zipPath: Path, outputDir: Path): Path
}

class ZipUnzipper : Unzipper {
    override fun extractSingleFile(zipPath: Path, outputDir: Path): Path {
        zipPath.inputStream().use { fis ->
            ZipInputStream(fis).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    if (entry.isDirectory) continue

                    val outName = entry.name.substringAfterLast('/')
                    val outPath = outputDir.resolve(outName)

                    outPath.outputStream().use { os -> zis.copyTo(os) }
                    zis.closeEntry()
                    return outPath
                }
            }
        }
        error("Zip contained no file entries: $zipPath")
    }
}

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
        require(os.contains("mac")) { "Only macOS supported for now. os=$os" }

        val arch = System.getProperty("os.arch").lowercase()
        return when {
            arch.contains("aarch64") || arch.contains("arm64") -> "aarch64"
            arch.contains("x86_64") || arch.contains("amd64") -> "x86_64"
            else -> error("Unsupported arch: $arch")
        }
    }

    fun latestJsonAssetKey(): String = "darwin-${detectArch()}"
}