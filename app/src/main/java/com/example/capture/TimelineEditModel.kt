package com.example.capture

import java.util.Locale

/**
 * Origin tag for a cut decision. Manual cuts are created by the user in v1;
 * AI_SILENCE_GAP and AI_FILLER_WORD allow a future AI analysis pass to drop
 * suggested cuts directly onto the exact same non-destructive timeline model.
 */
enum class CutOrigin {
    MANUAL,
    AI_SILENCE_GAP,
    AI_FILLER_WORD,
}

/**
 * Contiguous slice of the source recording.
 */
data class TimelineSegment(
    val id: Long,
    val startMs: Long,
    val endMs: Long,
    val isRemoved: Boolean = false,
    val origin: CutOrigin = CutOrigin.MANUAL,
) {
    val durationMs: Long
        get() = (endMs - startMs).coerceAtLeast(0L)
}

/**
 * Pure, immutable, non-destructive cut-list state for Eyeline's Trim & Cut Editor.
 * Partitions [0, durationMs] into contiguous [TimelineSegment] items with full Undo support.
 */
data class TimelineEditState(
    val durationMs: Long,
    val segments: List<TimelineSegment>,
    val selectedSegmentId: Long?,
    val undoStack: List<List<TimelineSegment>> = emptyList(),
    val nextSegmentId: Long = 2L,
) {
    val keptSegments: List<TimelineSegment>
        get() = segments.filter { !it.isRemoved && it.durationMs > 0L }

    val removedSegments: List<TimelineSegment>
        get() = segments.filter { it.isRemoved && it.durationMs > 0L }

    val keptDurationMs: Long
        get() = keptSegments.sumOf { it.durationMs }

    val removedDurationMs: Long
        get() = (durationMs - keptDurationMs).coerceAtLeast(0L)

    val cutCount: Int
        get() = removedSegments.size

    val hasRemovals: Boolean
        get() = removedSegments.isNotEmpty()

    val hasEdits: Boolean
        get() = hasRemovals || segments.size > 1

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val trimStartMs: Long
        get() = keptSegments.firstOrNull()?.startMs ?: 0L

    val trimEndMs: Long
        get() = keptSegments.lastOrNull()?.endMs ?: durationMs

    val selectedSegment: TimelineSegment?
        get() = segments.firstOrNull { it.id == selectedSegmentId } ?: segments.firstOrNull()

    fun selectedSegmentIndex(): Int {
        val targetId = selectedSegment?.id ?: return 0
        return segments.indexOfFirst { it.id == targetId }.coerceAtLeast(0)
    }

    fun segmentAt(positionMs: Long): TimelineSegment? {
        if (segments.isEmpty()) return null
        val clamped = positionMs.coerceIn(0L, durationMs)
        return segments.firstOrNull { clamped >= it.startMs && clamped < it.endMs }
            ?: segments.lastOrNull()
    }

    fun selectSegment(segmentId: Long): TimelineEditState {
        if (segments.none { it.id == segmentId }) return this
        return copy(selectedSegmentId = segmentId)
    }

    fun selectSegmentAt(positionMs: Long): TimelineEditState {
        val seg = segmentAt(positionMs) ?: return this
        return if (seg.id == selectedSegmentId) this else copy(selectedSegmentId = seg.id)
    }

    fun canSplitAt(positionMs: Long): Boolean {
        val seg = segmentAt(positionMs) ?: return false
        return (positionMs - seg.startMs) >= MIN_SEGMENT_DURATION_MS &&
            (seg.endMs - positionMs) >= MIN_SEGMENT_DURATION_MS
    }

    /**
     * Splits the segment containing [positionMs] into two contiguous segments inheriting
     * the parent's kept/cut state, and selects the right-hand segment for immediate action.
     */
    fun splitAt(positionMs: Long): TimelineEditState {
        if (!canSplitAt(positionMs)) return this
        val target = segmentAt(positionMs) ?: return this
        val index = segments.indexOfFirst { it.id == target.id }
        if (index < 0) return this

        val leftSeg = target.copy(endMs = positionMs)
        val rightId = nextSegmentId
        val rightSeg = TimelineSegment(
            id = rightId,
            startMs = positionMs,
            endMs = target.endMs,
            isRemoved = target.isRemoved,
            origin = target.origin,
        )

        val updated = buildList(segments.size + 1) {
            addAll(segments.subList(0, index))
            add(leftSeg)
            add(rightSeg)
            if (index + 1 < segments.size) {
                addAll(segments.subList(index + 1, segments.size))
            }
        }

        return copy(
            segments = updated,
            selectedSegmentId = rightId,
            undoStack = pushUndo(segments),
            nextSegmentId = rightId + 1L,
        )
    }

    fun canToggleCut(segmentId: Long?): Boolean {
        val target = segments.firstOrNull { it.id == segmentId } ?: return false
        if (target.isRemoved) return true // Restoring a cut is always safe
        val remainingKeptMs = keptDurationMs - target.durationMs
        val remainingKeptCount = keptSegments.count { it.id != target.id }
        return remainingKeptCount >= 1 && remainingKeptMs >= MIN_KEPT_DURATION_MS
    }

    /**
     * Toggles [segmentId] between Kept and Cut while guarding against removing all video content.
     */
    fun toggleCut(segmentId: Long?, origin: CutOrigin = CutOrigin.MANUAL): TimelineEditState {
        if (!canToggleCut(segmentId)) return this
        val targetId = segmentId ?: return this
        val updated = segments.map { seg ->
            if (seg.id == targetId) {
                seg.copy(
                    isRemoved = !seg.isRemoved,
                    origin = if (!seg.isRemoved) origin else CutOrigin.MANUAL,
                )
            } else {
                seg
            }
        }
        return copy(
            segments = updated,
            selectedSegmentId = targetId,
            undoStack = pushUndo(segments),
        )
    }

    /**
     * Captures a single undo checkpoint before a continuous trim-handle drag gesture begins.
     */
    fun recordUndoCheckpoint(): TimelineEditState {
        return copy(undoStack = pushUndo(segments))
    }

    /**
     * Adjusts the leading outer trim boundary while preserving internal splits and middle cuts.
     */
    fun trimLeadingTo(targetMs: Long): TimelineEditState {
        val currentEnd = trimEndMs
        val maxStart = (currentEnd - MIN_KEPT_DURATION_MS).coerceAtLeast(0L)
        val snappedStart = targetMs.coerceIn(0L, maxStart).let { if (it < EDGE_SNAP_MS) 0L else it }
        return rebuildOuterTrim(newStartMs = snappedStart, newEndMs = currentEnd)
    }

    /**
     * Adjusts the trailing outer trim boundary while preserving internal splits and middle cuts.
     */
    fun trimTrailingTo(targetMs: Long): TimelineEditState {
        val currentStart = trimStartMs
        val minEnd = (currentStart + MIN_KEPT_DURATION_MS).coerceAtMost(durationMs)
        val snappedEnd = targetMs.coerceIn(minEnd, durationMs).let {
            if (durationMs - it < EDGE_SNAP_MS) durationMs else it
        }
        return rebuildOuterTrim(newStartMs = currentStart, newEndMs = snappedEnd)
    }

    private fun rebuildOuterTrim(newStartMs: Long, newEndMs: Long): TimelineEditState {
        if (durationMs <= 0L || newEndMs <= newStartMs) return this

        val oldStart = trimStartMs
        val oldEnd = trimEndMs

        // Preserve any interior cuts that lived strictly inside the previous kept bounds
        val interiorCuts = segments.filter { seg ->
            seg.isRemoved && seg.startMs >= oldStart && seg.endMs <= oldEnd
        }

        // Preserve interior split markers so uncut splits remain intact during outer handle drags
        val interiorBoundaries = segments
            .flatMap { listOf(it.startMs, it.endMs) }
            .filter { boundary ->
                boundary != oldStart &&
                    boundary != oldEnd &&
                    boundary > newStartMs + BOUNDARY_EPSILON_MS &&
                    boundary < newEndMs - BOUNDARY_EPSILON_MS
            }
            .distinct()
            .sorted()

        var nextId = nextSegmentId
        val rebuilt = mutableListOf<TimelineSegment>()

        if (newStartMs > 0L) {
            rebuilt += TimelineSegment(
                id = nextId++,
                startMs = 0L,
                endMs = newStartMs,
                isRemoved = true,
                origin = CutOrigin.MANUAL,
            )
        }

        val points = buildList {
            add(newStartMs)
            addAll(interiorBoundaries)
            add(newEndMs)
        }

        for (i in 0 until points.lastIndex) {
            val a = points[i]
            val b = points[i + 1]
            if (b <= a) continue
            val mid = (a + b) / 2L
            val matchingCut = interiorCuts.firstOrNull { mid >= it.startMs && mid < it.endMs }
            rebuilt += TimelineSegment(
                id = nextId++,
                startMs = a,
                endMs = b,
                isRemoved = matchingCut != null,
                origin = matchingCut?.origin ?: CutOrigin.MANUAL,
            )
        }

        if (newEndMs < durationMs) {
            rebuilt += TimelineSegment(
                id = nextId++,
                startMs = newEndMs,
                endMs = durationMs,
                isRemoved = true,
                origin = CutOrigin.MANUAL,
            )
        }

        // Collapse adjacent leading or trailing removed pieces if handle crossed an interior cut
        val normalized = normalizeOuterRemovedSegments(rebuilt)

        // Guard: never allow trimming away all kept footage
        val newKeptMs = normalized.filter { !it.isRemoved }.sumOf { it.durationMs }
        if (newKeptMs < MIN_KEPT_DURATION_MS) return this

        val preservedSelection = normalized.firstOrNull { !it.isRemoved }?.id ?: normalized.firstOrNull()?.id
        return copy(
            segments = normalized,
            selectedSegmentId = preservedSelection,
            nextSegmentId = nextId,
        )
    }

    private fun normalizeOuterRemovedSegments(list: List<TimelineSegment>): List<TimelineSegment> {
        if (list.size <= 1) return list
        val result = list.toMutableList()
        // Merge contiguous leading removed segments
        while (result.size > 1 && result[0].isRemoved && result[1].isRemoved) {
            result[0] = result[0].copy(endMs = result[1].endMs)
            result.removeAt(1)
        }
        // Merge contiguous trailing removed segments
        while (result.size > 1 && result.last().isRemoved && result[result.lastIndex - 1].isRemoved) {
            val last = result.removeAt(result.lastIndex)
            val prevIdx = result.lastIndex
            result[prevIdx] = result[prevIdx].copy(endMs = last.endMs)
        }
        return result
    }

    fun undo(): TimelineEditState {
        if (undoStack.isEmpty()) return this
        val restored = undoStack.last()
        val remaining = undoStack.dropLast(1)
        val validSelection = restored.firstOrNull { it.id == selectedSegmentId }?.id
            ?: restored.firstOrNull()?.id
        return copy(
            segments = restored,
            selectedSegmentId = validSelection,
            undoStack = remaining,
        )
    }

    fun reset(): TimelineEditState {
        if (!hasEdits) return this
        val freshId = nextSegmentId
        return copy(
            segments = listOf(
                TimelineSegment(
                    id = freshId,
                    startMs = 0L,
                    endMs = durationMs,
                    isRemoved = false,
                    origin = CutOrigin.MANUAL,
                )
            ),
            selectedSegmentId = freshId,
            undoStack = pushUndo(segments),
            nextSegmentId = freshId + 1L,
        )
    }

    /**
     * Merges adjacent kept segments into minimal continuous playback/export ranges so
     * Media3 Transformer avoids unnecessary seams at uncut split markers.
     */
    fun mergedKeptRanges(): List<LongRange> {
        val kept = keptSegments.sortedBy { it.startMs }
        if (kept.isEmpty()) return emptyList()
        val merged = mutableListOf<LongRange>()
        var currentStart = kept.first().startMs
        var currentEnd = kept.first().endMs

        for (i in 1 until kept.size) {
            val seg = kept[i]
            if (seg.startMs <= currentEnd + 2L) {
                currentEnd = maxOf(currentEnd, seg.endMs)
            } else {
                merged += currentStart..currentEnd
                currentStart = seg.startMs
                currentEnd = seg.endMs
            }
        }
        merged += currentStart..currentEnd
        return merged
    }

    /**
     * Live skip-over coordinator for ExoPlayer preview inside the Trim Editor.
     * Returns null when [currentPositionMs] is safely inside a kept range, or returns the
     * target seek position (next kept start, or looping back to the first kept start).
     */
    fun nextPlaybackPosition(currentPositionMs: Long): Long? {
        val ranges = mergedKeptRanges()
        if (ranges.isEmpty()) return null
        val pos = currentPositionMs.coerceAtLeast(0L)

        // Check if currently inside any kept range (with tiny end tolerance so we hop smoothly)
        for (range in ranges) {
            if (pos in range.first until (range.last - PLAYBACK_END_TOLERANCE_MS).coerceAtLeast(range.first + 1L)) {
                return null
            }
        }

        // Jump to next kept range strictly ahead of current position
        val upcoming = ranges.firstOrNull { it.first > pos }
        return upcoming?.first ?: ranges.first().first
    }

    private fun pushUndo(snapshot: List<TimelineSegment>): List<List<TimelineSegment>> {
        val next = undoStack + listOf(snapshot)
        return if (next.size > MAX_UNDO_DEPTH) next.takeLast(MAX_UNDO_DEPTH) else next
    }

    companion object {
        const val MIN_SEGMENT_DURATION_MS = 250L
        const val MIN_KEPT_DURATION_MS = 400L
        private const val EDGE_SNAP_MS = 80L
        private const val BOUNDARY_EPSILON_MS = 60L
        private const val PLAYBACK_END_TOLERANCE_MS = 35L
        private const val MAX_UNDO_DEPTH = 30

        fun initial(durationMs: Long): TimelineEditState {
            val safeDuration = durationMs.coerceAtLeast(1L)
            return TimelineEditState(
                durationMs = safeDuration,
                segments = listOf(
                    TimelineSegment(
                        id = 1L,
                        startMs = 0L,
                        endMs = safeDuration,
                        isRemoved = false,
                        origin = CutOrigin.MANUAL,
                    )
                ),
                selectedSegmentId = 1L,
                undoStack = emptyList(),
                nextSegmentId = 2L,
            )
        }

        /**
         * Formats milliseconds into crisp studio timecode `MM:SS.d` (e.g. `00:14.3`).
         */
        fun formatPreciseTime(ms: Long): String {
            val safe = ms.coerceAtLeast(0L)
            val totalTenths = safe / 100L
            val tenths = (totalTenths % 10L).toInt()
            val totalSeconds = totalTenths / 10L
            val minutes = (totalSeconds / 60L).toInt()
            val seconds = (totalSeconds % 60L).toInt()
            return String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, tenths)
        }
    }
}
