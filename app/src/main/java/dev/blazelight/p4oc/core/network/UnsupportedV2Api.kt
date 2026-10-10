package dev.blazelight.p4oc.core.network

import java.lang.reflect.Proxy

/**
 * Never send a v1 route to a v2 server: its SPA can return HTML with HTTP 200.
 * Throws unchecked: a checked exception from a proxy handler is wrapped in a message-less
 * UndeclaredThrowableException because Kotlin interface methods declare no `throws`.
 */
internal fun unsupportedV2Api(): OpenCodeApi = Proxy.newProxyInstance(
    OpenCodeApi::class.java.classLoader,
    arrayOf(OpenCodeApi::class.java),
) { proxy, method, args ->
    when (method.name) {
        "toString" -> "Unsupported OpenCode v2 operations"
        "hashCode" -> System.identityHashCode(proxy)
        "equals" -> proxy === args?.firstOrNull()
        else -> throw UnsupportedOperationException("${method.name} is not available on this OpenCode v2 server")
    }
} as OpenCodeApi
