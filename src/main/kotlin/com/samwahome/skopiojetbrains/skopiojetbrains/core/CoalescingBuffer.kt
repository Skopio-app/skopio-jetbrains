package com.samwahome.skopiojetbrains.skopiojetbrains.core

import com.samwahome.skopiojetbrains.skopiojetbrains.model.UsageEvent

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