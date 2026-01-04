package com.samwahome.skopiojetbrains.skopiojetbrains.classify

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.samwahome.skopiojetbrains.skopiojetbrains.model.ActivityCategory
import java.util.concurrent.atomic.AtomicReference

class ActivityClassifier(@Suppress("unused") private val project: Project) {
    private val mode = AtomicReference(ActivityCategory.CODING)

    fun setMode(category: ActivityCategory) {
        mode.set(category)
    }

    fun currentCategoryFor(file: VirtualFile?): ActivityCategory {
        val m = mode.get()
        if (m != ActivityCategory.CODING) return m

        val ext = file?.extension?.lowercase()
        if (ext in setOf("md", "rst", "txt", "adoc")) return ActivityCategory.WRITING_DOCS

        return ActivityCategory.CODING
    }
}