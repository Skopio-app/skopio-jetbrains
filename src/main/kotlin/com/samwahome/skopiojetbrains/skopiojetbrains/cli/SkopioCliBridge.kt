package com.samwahome.skopiojetbrains.skopiojetbrains.cli

import com.samwahome.skopiojetbrains.skopiojetbrains.model.UsageEvent

interface SkopioCliBridge {
    fun submit(event: UsageEvent)
    fun flush()
    fun sync()
    fun close()
}