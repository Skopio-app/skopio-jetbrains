package com.samwahome.skopiojetbrains.skopiojetbrains.cli

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

class CliLocator(
    private val installer: SkopioCliInstaller,
) {
    fun resolve(): Path {
        resolveDevOverrideIfAllowed()?.let { return it }
        return installer.ensureInstalled()
    }

    private fun resolveDevOverrideIfAllowed(): Path? {
        if (!isDevMode()) return null

        val override = System.getProperty("skopio.cli.path")
            ?: System.getenv("SKOPIO_CLI_PATH")
            ?: return null

        val p = Path(override)
        require(p.exists() && p.isRegularFile()) {
            "Dev CLI override path is set but invalid: $p"
        }

        return p
    }

    private fun isDevMode(): Boolean {
        val app = ApplicationManager.getApplication()
        val internal = runCatching { app.isInternal }.getOrDefault(false)
        if (internal) return true

        val config = PathManager.getConfigPath().lowercase()
        val system = PathManager.getSystemPath().lowercase()
        return config.contains("sandbox") || system.contains("sandbox")
    }
}