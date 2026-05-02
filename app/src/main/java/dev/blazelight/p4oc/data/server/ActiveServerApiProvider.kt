package dev.blazelight.p4oc.data.server

import dev.blazelight.p4oc.core.network.OpenCodeApi
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.server.ServerRef

fun interface ActiveServerApiProvider {
    fun apiFor(serverRef: ServerRef, generation: ServerGeneration): OpenCodeApi
}
