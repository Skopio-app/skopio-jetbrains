package com.samwahome.skopiojetbrains.skopiojetbrains.cli

object PlatformArch {
    fun detectMacArch(): String {
        val os = System.getProperty("os.name").lowercase()
        require(os.contains("mac")) { "Only macOS supported for now. os=$os"}

        val arch = System.getProperty("os.arch").lowercase()
        return when {
            arch.contains("aarch64") || arch.contains("arm64") -> "aarch64"
            arch.contains("x86_64") || arch.contains("amd64") -> "x86_64"
            else -> error("Unsupported macOS arch: $arch")
        }
    }

    fun latestJsonAssetKey(): String = "darwin-${detectMacArch()}"
}