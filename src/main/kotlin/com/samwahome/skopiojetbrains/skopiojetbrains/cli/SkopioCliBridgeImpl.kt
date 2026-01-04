package com.samwahome.skopiojetbrains.skopiojetbrains.cli

import com.intellij.util.concurrency.AppExecutorUtil
import com.samwahome.skopiojetbrains.skopiojetbrains.core.CoalescingBuffer
import com.samwahome.skopiojetbrains.skopiojetbrains.model.UsageEvent
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.pathString

class SkopioCliBridgeImpl(
    private val installer: SkopioCliInstaller,
): SkopioCliBridge {
    private val buffer = CoalescingBuffer()
    private val draining = AtomicBoolean(false)
    private val executor = AppExecutorUtil.getAppExecutorService()

    override fun submit(event: UsageEvent) {
        buffer.add(event)
    }

    override fun flush() {
        if (!draining.compareAndSet(false, true)) return

        executor.execute {
            try {
                val batch = buffer.drain()
                if (batch.isEmpty()) return@execute

                val cli = installer.ensureInstalled().pathString
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
            val cli = installer.ensureInstalled().pathString
            runCommand(cli, listOf("sync"))
        }
    }

    override fun close() {
        flush()
        executor.execute {
            try {
                TimeUnit.MILLISECONDS.sleep(200)
            } catch (_: InterruptedException) {}
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
