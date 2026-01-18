package com.samwahome.skopiojetbrains.skopiojetbrains.cli

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import com.samwahome.skopiojetbrains.skopiojetbrains.core.CoalescingBuffer
import com.samwahome.skopiojetbrains.skopiojetbrains.model.UsageEvent
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString

class SkopioCliBridgeImpl(
    private val installer: SkopioCliInstaller,
) : SkopioCliBridge {
    private val buffer = CoalescingBuffer()
    private val draining = AtomicBoolean(false)
    private val executor = AppExecutorUtil.getAppExecutorService()

    private val cliLocator = CliLocator(installer)

    override fun submit(event: UsageEvent) {
        buffer.add(event)
    }

    override fun flush() {
        if (!draining.compareAndSet(false, true)) return

        executor.execute {
            try {
                val batch = buffer.drain()
                if (batch.isEmpty()) return@execute

                val cli = cliLocator.resolve().pathString
                for (ev in batch) {
                    runEventCommand(cli, ev)
                }
            } finally {
                draining.set(false)
            }
        }
    }

    override fun sync() {
        executor.execute {
            val cli = cliLocator.resolve().pathString
            runCommand(cli, listOf("sync"))
        }
    }

    override fun close() {
        flush()
        executor.execute {
            try {
                TimeUnit.MILLISECONDS.sleep(200)
            } catch (_: InterruptedException) {
            }
            sync()
        }
    }

    private fun runEventCommand(cliPath: String, ev: UsageEvent) {
        val args = listOf(
            "event",
            "--timestamp", ev.timestamp.toString(),
            "--end-timestamp", ev.endTimestamp.toString(),
            "--duration", ev.durationSec.toString(),
            "--category", ev.category.toCliValue(),
            "--app", ev.app,
            "--entity", ev.entity.value,
            "--entity-type", ev.entity.type.toCliValue(),
            "--project", ev.projectPath,
            "--source", ev.source,
        )
        runCommand(cliPath, args)
    }

    private fun runCommand(cliPath: String, args: List<String>) {
        val pb = ProcessBuilder(listOf(cliPath) + args)
        val proc = pb.start()

        val exit = proc.waitFor()
        if (exit != 0) {
            val err = proc.errorStream.bufferedReader().readText()
            throw RuntimeException("Skopio CLI failed ($exit): $err")
        }
    }
}

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