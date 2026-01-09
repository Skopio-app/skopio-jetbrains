package com.samwahome.skopiojetbrains.skopiojetbrains.cli

import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.io.path.*

interface Unzipper {
    fun extractSingleFile(zipPath: Path, outputDir: Path): Path
}

class ZipUnzipper: Unzipper {
    override fun extractSingleFile(zipPath: Path, outputDir: Path): Path {
        zipPath.inputStream().use { fis ->
            ZipInputStream(fis).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?:break
                    if (entry.isDirectory) continue

                    val outName = entry.name.substringAfterLast('/')
                    val outPath = outputDir.resolve(outName)

                    outPath.outputStream().use { os -> zis.copyTo(os) }
                    zis.closeEntry()
                    return outPath
                }
            }
        }
        error("Zip contained no file entries: $zipPath")
    }
}