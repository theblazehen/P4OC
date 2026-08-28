package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.domain.server.ServerGeneration
import java.util.concurrent.atomic.AtomicLong

/** Thread-safe sequence shared by every connection registry created in this process. */
internal object ServerGenerationSequence {
    private val counter = AtomicLong(0L)

    fun next(): ServerGeneration {
        val value = counter.updateAndGet { current ->
            check(current < Long.MAX_VALUE) { "Server connection generation space exhausted" }
            current + 1L
        }
        return ServerGeneration(value)
    }
}
