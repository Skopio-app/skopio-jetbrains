package com.samwahome.skopiojetbrains.skopiojetbrains

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class SkopioProjectActivity: ProjectActivity {
    override suspend fun execute(project: Project) {
        project.getService(SkopioProjectService::class.java).start()
    }
}