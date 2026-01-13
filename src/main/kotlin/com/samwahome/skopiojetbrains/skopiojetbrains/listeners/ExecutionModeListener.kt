package com.samwahome.skopiojetbrains.skopiojetbrains.listeners

import com.intellij.diff.editor.DiffViewerVirtualFile
import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskListener
import com.intellij.task.ProjectTaskManager
import com.intellij.util.messages.MessageBusConnection
import com.samwahome.skopiojetbrains.skopiojetbrains.classify.ActivityClassifier
import com.samwahome.skopiojetbrains.skopiojetbrains.model.ActivityCategory

class ExecutionModeListener(
    private val project: Project,
    private val classifier: ActivityClassifier,
) {
    private var connection: MessageBusConnection? = null

    // review context state (diff editor or VCS/Commit toolwindow)
    private var inReviewContext: Boolean = false

    fun start() {
        connection = project.messageBus.connect().apply {
            // Run / Debug / Test execution tracking
            subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
                override fun processStartScheduled(executorId: String, env: ExecutionEnvironment) {
                    when {
                        executorId.equals("Debug", ignoreCase = true) -> classifier.setMode(ActivityCategory.DEBUGGING)
                        executorId.equals("Run", ignoreCase = true) -> classifier.setMode(ActivityCategory.TESTING)
                        else -> classifier.setMode(ActivityCategory.CODING)
                    }
                }

                override fun processTerminated(
                    executorId: String,
                    env: ExecutionEnvironment,
                    handler: ProcessHandler,
                    exitCode: Int
                ) {
                    classifier.setMode(ActivityCategory.CODING)
                    recomputeReviewContext()
                }
            })

            // Build / Compile tracking
            subscribe(ProjectTaskListener.TOPIC, object : ProjectTaskListener {
                override fun started(context: ProjectTaskContext) {
                    classifier.setMode(ActivityCategory.COMPILING)
                }

                override fun finished(result: ProjectTaskManager.Result) {
                    classifier.setMode(ActivityCategory.CODING)
                    recomputeReviewContext()
                }
            })

            // Detect diff editor focus via editor selection changes
            subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    val isDiff = event.newFile?.let(::isDiffFile) == true
                    updateReviewContext(isDiff || isVcsToolwindowActive())
                }
            })

            // Detect toolwindow focus changes
            subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
                override fun stateChanged(
                    toolWindowManager: ToolWindowManager,
                ) {
                    val review = isDiffEditorSelected() || isVcsToolwindowActive(toolWindowManager)
                    updateReviewContext(review)
                }
            })
        }

        // Initial recompute on start
        recomputeReviewContext()
    }

    fun stop() {
        connection?.disconnect()
        connection = null

        if (classifier.getMode() == ActivityCategory.CODE_REVIEWING) {
            classifier.setMode(ActivityCategory.CODING)
        }
        inReviewContext = false
    }

    private fun recomputeReviewContext() {
        val review = isDiffEditorSelected() || isVcsToolwindowActive()
        updateReviewContext(review)
    }

    private fun updateReviewContext(nowInReview: Boolean) {
        if (nowInReview == inReviewContext) return
        inReviewContext = nowInReview

        val current = classifier.getMode()
        if (nowInReview) {
            if (current == ActivityCategory.CODING || current == ActivityCategory.WRITING_DOCS) {
                classifier.setMode(ActivityCategory.CODE_REVIEWING)
            }
        } else {
            if (current == ActivityCategory.CODE_REVIEWING) {
                classifier.setMode(ActivityCategory.CODING)
            }
        }
    }

    private fun isDiffEditorSelected(): Boolean {
        val selected = FileEditorManager.getInstance(project).selectedFiles
        return selected.any(::isDiffFile)
    }

    private fun isDiffFile(file: VirtualFile): Boolean {
        if (file is DiffViewerVirtualFile) return true

        // Some diff editors expose a "diff" protocol VFS
        val protocol = runCatching { file.fileSystem.protocol }.getOrNull()
        if (protocol.equals("diff", ignoreCase = true)) return true

        val name = runCatching { file.name }.getOrNull()
        return name?.contains("diff", ignoreCase = true) == true
    }

    private fun isVcsToolwindowActive(toolWindowManager: ToolWindowManager = ToolWindowManager.getInstance(project)): Boolean {
        val id = toolWindowManager.activeToolWindowId ?: return false

        return id == ToolWindowId.VCS || id.equals("Commit", ignoreCase = true) || id.equals("VCS", ignoreCase = true)
    }
}