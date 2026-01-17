package com.samwahome.skopiojetbrains.skopiojetbrains.core

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.samwahome.skopiojetbrains.skopiojetbrains.classify.ActivityClassifier
import com.samwahome.skopiojetbrains.skopiojetbrains.cli.SkopioCliBridge
import com.samwahome.skopiojetbrains.skopiojetbrains.model.*
import java.awt.KeyboardFocusManager
import java.beans.PropertyChangeListener
import java.time.Instant
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class UsageTracker(
    private val project: Project,
    private val classifier: ActivityClassifier,
    private val cli: SkopioCliBridge,
    private val sourceName: String = "skopio-jetbrains",
    private val idleTimeoutSec: Long = 60,
    private val idleCheckEverySec: Long = 5,
) : Disposable {
    private val scheduler = AppExecutorUtil.getAppScheduledExecutorService()
    private var pendingSwitch: ScheduledFuture<*>? = null
    private var idleCheckTask: ScheduledFuture<*>? = null
    private var flushTask: ScheduledFuture<*>? = null
    private var syncTask: ScheduledFuture<*>? = null

    private val active = AtomicReference<ActiveState?>(null)

    private val lastInteractionAtSec = AtomicLong(Instant.now().epochSecond)

    private val caretListener = object : CaretListener {
        override fun caretPositionChanged(event: CaretEvent) {
            touch()
        }
    }

    private val focusListener = PropertyChangeListener { evt ->
        if (evt.propertyName == "focusOwner") {
            touch()
        }
    }

    private val editorFactoryListener = object : EditorFactoryListener {
        override fun editorCreated(event: EditorFactoryEvent) {
            event.editor.caretModel.addCaretListener(caretListener)
        }

        override fun editorReleased(event: EditorFactoryEvent) {
            event.editor.caretModel.removeCaretListener(caretListener)
        }
    }

    fun start() {
        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    touch()
                    scheduleSwitch(event.newFile)
                }
            }
        )

        EditorFactory.getInstance().addEditorFactoryListener(editorFactoryListener, this)

        KeyboardFocusManager
            .getCurrentKeyboardFocusManager()
            .addPropertyChangeListener(focusListener)

        idleCheckTask = scheduler.scheduleWithFixedDelay(
            { checkIdleAndFinalize() },
            idleCheckEverySec,
            idleCheckEverySec,
            TimeUnit.SECONDS
        )

        flushTask = scheduler.scheduleWithFixedDelay({ cli.flush() }, 10, 10, TimeUnit.SECONDS)
        syncTask = scheduler.scheduleWithFixedDelay({ cli.sync() }, 180, 180, TimeUnit.SECONDS)

        val initial = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        switchToFile(initial)
    }

    private fun touch() {
        lastInteractionAtSec.set(Instant.now().epochSecond)
    }

    private fun scheduleSwitch(file: VirtualFile?) {
        pendingSwitch?.cancel(false)
        pendingSwitch = scheduler.schedule({ switchToFile(file) }, 150, TimeUnit.MILLISECONDS)
    }

    private fun switchToFile(file: VirtualFile?) {
        val nowSec = Instant.now().epochSecond
        lastInteractionAtSec.set(nowSec)

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

    private fun checkIdleAndFinalize() {
        val cur = active.get() ?: return

        val nowSec = Instant.now().epochSecond
        val lastSec = lastInteractionAtSec.get()

        if (nowSec - lastSec >= idleTimeoutSec) {
            if (active.compareAndSet(cur, null)) {
                finalize(cur, lastSec)
                cli.flush()
            }
        }
    }

    private fun finalize(prev: ActiveState, endedAtSec: Long) {
        if (endedAtSec <= prev.startedAtSec) return

        val durationSec = endedAtSec - prev.startedAtSec
        if (durationSec <= 0) return

        val appName = ApplicationInfo.getInstance().fullApplicationName.replace(Regex("""\s+\d+(\.\d+)*.*$"""), "")
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
        val name = ApplicationInfo.getInstance().fullApplicationName.replace(Regex("""\s+\d+(\.\d+)*.*$"""), "")
        return EntityRef(EntityType.APP, value = name, displayName = name)
    }

    fun flushActive() {
        val nowSec = Instant.now().epochSecond
        active.getAndSet(null)?.let { finalize(it, nowSec) }
        cli.flush()
    }

    override fun dispose() {
        pendingSwitch?.cancel(false)
        idleCheckTask?.cancel(false)
        flushTask?.cancel(false)
        syncTask?.cancel(false)

        KeyboardFocusManager
            .getCurrentKeyboardFocusManager()
            .removePropertyChangeListener(focusListener)

        flushActive()
        cli.close()
    }

    private data class ActiveState(
        val startedAtSec: Long,
        val entity: EntityRef,
        val category: ActivityCategory,
    )
}

class CoalescingBuffer(
    private val maxItems: Int = 300,
    private val mergeGapSeconds: Long = 2,
) {
    private val items = ArrayList<UsageEvent>(maxItems)

    @Synchronized
    fun add(ev: UsageEvent) {
        val last = items.lastOrNull()
        if (last != null && canMerge(last, ev)) {
            items[items.lastIndex] = merge(last, ev)
            return
        }

        items.add(ev)
        if (items.size > maxItems) {
            items.removeAt(0)
        }
    }

    @Synchronized
    fun drain(): List<UsageEvent> {
        if (items.isEmpty()) return emptyList()
        val out = items.toList()
        items.clear()
        return out
    }

    private fun canMerge(a: UsageEvent, b: UsageEvent): Boolean {
        if (a.category != b.category) return false
        if (a.app != b.app) return false
        if (a.projectPath != b.projectPath) return false
        if (a.source != b.source) return false
        if (a.entity.type != b.entity.type) return false
        if (a.entity.value != b.entity.value) return false

        val gap = b.timestamp - a.endTimestamp
        return gap in 0..mergeGapSeconds
    }

    private fun merge(a: UsageEvent, b: UsageEvent): UsageEvent {
        val started = minOf(a.timestamp, b.timestamp)
        val ended = maxOf(a.endTimestamp, b.endTimestamp)
        return a.copy(timestamp = started, endTimestamp = ended)
    }
}