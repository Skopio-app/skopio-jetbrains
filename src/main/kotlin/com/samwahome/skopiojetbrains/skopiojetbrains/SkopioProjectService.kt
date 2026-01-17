package com.samwahome.skopiojetbrains.skopiojetbrains

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.samwahome.skopiojetbrains.skopiojetbrains.classify.ActivityClassifier
import com.samwahome.skopiojetbrains.skopiojetbrains.cli.*
import com.samwahome.skopiojetbrains.skopiojetbrains.core.UsageTracker
import com.samwahome.skopiojetbrains.skopiojetbrains.listeners.ExecutionModeListener
import java.nio.file.Paths

@Service(Service.Level.PROJECT)
class SkopioProjectService(private val project: Project) : Disposable {
    private val classifier = ActivityClassifier(project)

    private val installer = SkopioCliInstaller(
        installDir = Paths.get(System.getProperty("user.home"), ".skopio", "bin"),
        latestJsonUrl = "https://github.com/Skopio-app/cli-releases/releases/latest/download/latest.json"
    )

    private val cliBridge: SkopioCliBridge = SkopioCliBridgeImpl(installer)

    private val tracker = UsageTracker(
        project = project,
        classifier = classifier,
        cli = cliBridge,
    )

    private val execListener = ExecutionModeListener(project, classifier)

    fun start() {
        execListener.start()
        tracker.start()
    }

    override fun dispose() {
        execListener.stop()
        tracker.dispose()
    }
}