package com.samwahome.skopiojetbrains.skopiojetbrains.core

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.samwahome.skopiojetbrains.skopiojetbrains.classify.ActivityClassifier
import com.samwahome.skopiojetbrains.skopiojetbrains.cli.SkopioCliBridge
import com.samwahome.skopiojetbrains.skopiojetbrains.model.*
import java.time.Instant
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class UsageTracker(
    private val project: Project,
    private val classifier: ActivityClassifier,
    private val cli: SkopioCliBridge,
    private val sourceName: String = "skopio-jetbrains"
) : Disposable {
    private val scheduler = AppExecutorUtil.getAppScheduledExecutorService()
    private var pendingSwitch: ScheduledFuture<*>? = null

    private val active = AtomicReference<ActiveState?>(null)

    fun start() {
        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    scheduleSwitch(event.newFile)
                }
            }
        )

        scheduler.scheduleWithFixedDelay({ cli.flush() }, 10, 10, TimeUnit.SECONDS)
        scheduler.scheduleWithFixedDelay({ cli.sync() }, 180, 180, TimeUnit.SECONDS)

        val initial = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        switchToFile(initial)
    }

    private fun scheduleSwitch(file: VirtualFile?) {
        pendingSwitch?.cancel(false)
        pendingSwitch = scheduler.schedule({ switchToFile(file) }, 150, TimeUnit.MILLISECONDS)
    }

    private fun switchToFile(file: VirtualFile?) {
        val nowSec = Instant.now().epochSecond

        val prev = active.getAndSet(null)
        if (prev != null) finalize(prev, nowSec)

        val entity = file?.let { fileEntity(it) } ?: appEntity()
        val category = classifier.currentCategoryFor(file)

        active.set(
            ActiveState(
                startedAtSec = nowSec,
                entity = entity,
                category = category,
            )
        )
    }

    private fun finalize(prev: ActiveState, endedAtSec: Long) {
        if (endedAtSec <= prev.startedAtSec) return

        val durationSec = endedAtSec - prev.startedAtSec
        if (durationSec <= 0) return

        val appName = ApplicationInfo.getInstance().fullApplicationName
        val projectPath = project.basePath ?: project.name

        cli.submit(
            UsageEvent(
                category = prev.category,
                app = appName,
                entity = prev.entity,
                projectPath = projectPath,
                source = sourceName,
                timestamp = prev.startedAtSec,
                endTimestamp = endedAtSec,
            )
        )
    }

    private fun fileEntity(file: VirtualFile): EntityRef =
        EntityRef(EntityType.FILE, file.path, displayName = file.name)

    private fun appEntity(): EntityRef {
        val name = ApplicationInfo.getInstance().fullApplicationName
        return EntityRef(EntityType.APP, value = name, displayName = name)
    }

    fun flushActiveNow() {
        val nowSec = Instant.now().epochSecond
        active.getAndSet(null)?.let { finalize(it, nowSec)}
        cli.flush()
    }

    override fun dispose() {
        pendingSwitch?.cancel(false)
        flushActiveNow()
        cli.close()
    }

    private data class ActiveState(
        val startedAtSec: Long,
        val entity: EntityRef,
        val category: ActivityCategory,
    )
}