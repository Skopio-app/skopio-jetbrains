package com.samwahome.skopiojetbrains.skopiojetbrains.cli

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

class CliLocator(
    private val installer: SkopioCliInstaller,
) {
    private var log = Logger.getInstance(CliLocator::class.java)

    fun resolve(): Path {
        val devEnabled = isDevOverrideEnabled()

        val prop = System.getProperty("skopio.cli.path")
        val env = System.getenv("SKOPIO_CLI_PATH")
        val overrideRaw = prop ?: env

        log.warn(
            "Skopio CLI: devEnabled=$devEnabled, prop.skopio.cli.path=${prop ?: "<unset>"}, " +
                    "env.SKOPIO_CLI_PATH=${env ?: "<unset>"}, pluginsPath=${PathManager.getPluginsPath()}"
        )

        if (devEnabled && !overrideRaw.isNullOrBlank()) {
            val p = Path(overrideRaw)

            require(p.exists() && p.isRegularFile()) {
                "Dev CLI override path is set but invalid: $p"
            }

            log.warn("Skopio CLI: Using DEV override: $p")
            return p
        }

        val installed = installer.ensureInstalled()
        log.warn("Skopio CLI: Using installed binary: $installed")
        return installed
    }

    private fun isDevOverrideEnabled(): Boolean {
        val explicit = System.getProperty("skopio.dev")?.equals("true", ignoreCase = true) == true
        if (explicit) return true

        val pluginsPath = PathManager.getPluginsPath().lowercase()
        return pluginsPath.contains("idea-sandbox") || pluginsPath.contains("sandbox")
    }
}