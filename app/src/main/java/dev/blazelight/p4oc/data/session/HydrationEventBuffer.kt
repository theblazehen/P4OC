package dev.blazelight.p4oc.data.session

import dev.blazelight.p4oc.domain.model.OpenCodeEvent

class HydrationEventBuffer(
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private val events = ArrayDeque<OpenCodeEvent>()

    init {
        require(capacity > 0) { "Buffer capacity must be positive" }
    }

    val size: Int get() = events.size

    fun buffer(event: OpenCodeEvent): RepoState.Hydrating {
        if (events.size == capacity) {
            events.removeFirst()
        }
        events.addLast(event)
        return RepoState.Hydrating(bufferedEvents = events.size)
    }

    fun replayOver(snapshot: Snapshot, reducer: SessionReducer): Snapshot = events.fold(snapshot) { current, event ->
        reducer.reduce(current, event)
    }

    fun clear() {
        events.clear()
    }

    companion object {
        const val DEFAULT_CAPACITY: Int = 512
    }
}
