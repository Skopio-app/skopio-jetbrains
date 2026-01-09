package com.samwahome.skopiojetbrains.skopiojetbrains.listeners

import com.intellij.compiler.server.BuildManagerListener
import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.util.messages.MessageBusConnection
import com.samwahome.skopiojetbrains.skopiojetbrains.classify.ActivityClassifier
import com.samwahome.skopiojetbrains.skopiojetbrains.model.ActivityCategory
import java.util.UUID

class ExecutionModeListener(
    private val project: Project,
    private val classifier: ActivityClassifier,
) {
    private var connection: MessageBusConnection? = null

    fun start() {
        connection = project.messageBus.connect().apply {
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
                }
            })

            subscribe(BuildManagerListener.TOPIC, object : BuildManagerListener {
                override fun buildStarted(project: Project, sessionId: UUID, isAutomake: Boolean) {
                    if  (project != this@ExecutionModeListener.project) return
                    classifier.setMode(ActivityCategory.COMPILING)
                }

                override fun buildFinished(project: Project, sessionId: UUID, isAutomake: Boolean) {
                    if (project != this@ExecutionModeListener.project) return
                    classifier.setMode(ActivityCategory.CODING)
                }
            })
        }
    }

    fun stop() {
        connection?.disconnect()
        connection = null
    }
}