package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.data.vcs.VcsDiffMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query
import retrofit2.http.Streaming

class OpenCodeApiVcsContractTest {
    @Test
    fun `workspace VCS raw endpoints are streaming GET reads with exact paths`() {
        assertStreamingGet("getVcsInfoRaw", "vcs")
        assertStreamingGet("getVcsStatusRaw", "vcs/status")
        assertStreamingGet("getVcsDiffRaw", "vcs/diff")
    }

    @Test
    fun `workspace VCS raw endpoints require explicit routing and closed diff arguments`() {
        assertEquals(
            listOf("directory", "workspace"),
            queryNames("getVcsInfoRaw"),
        )
        assertEquals(
            listOf("directory", "workspace"),
            queryNames("getVcsStatusRaw"),
        )
        assertEquals(
            listOf("mode", "context", "directory", "workspace"),
            queryNames("getVcsDiffRaw"),
        )

        val diffMethod = OpenCodeApi::class.java.declaredMethods.single { it.name == "getVcsDiffRaw" }
        assertEquals(VcsDiffMode::class.java, diffMethod.parameterTypes[0])
        assertEquals(setOf("git", "branch"), VcsDiffMode.entries.map { mode -> mode.toString() }.toSet())
    }

    @Test
    fun `API declares no VCS mutation route`() {
        val vcsMethods = OpenCodeApi::class.java.declaredMethods.filter { method ->
            method.annotations.any { annotation ->
                endpointPath(annotation)?.let { it == "vcs" || it.startsWith("vcs/") } == true
            }
        }

        assertTrue(vcsMethods.isNotEmpty())
        vcsMethods.forEach { method ->
            assertNotNull("${method.name} must remain a GET", method.getAnnotation(GET::class.java))
            assertEquals(null, method.getAnnotation(POST::class.java))
            assertEquals(null, method.getAnnotation(PUT::class.java))
            assertEquals(null, method.getAnnotation(PATCH::class.java))
            assertEquals(null, method.getAnnotation(DELETE::class.java))
        }
        assertTrue(
            vcsMethods.none { method ->
                MUTATION_WORDS.any { word -> word in method.name.lowercase() }
            },
        )
    }

    private fun assertStreamingGet(name: String, path: String) {
        val method = OpenCodeApi::class.java.declaredMethods.single { it.name == name }
        assertEquals(path, method.getAnnotation(GET::class.java)?.value)
        assertNotNull(method.getAnnotation(Streaming::class.java))
    }

    private fun queryNames(name: String): List<String> =
        OpenCodeApi::class.java.declaredMethods.single { it.name == name }
            .parameterAnnotations
            .mapNotNull { annotations -> annotations.filterIsInstance<Query>().singleOrNull()?.value }

    private fun endpointPath(annotation: Annotation): String? = when (annotation) {
        is GET -> annotation.value
        is POST -> annotation.value
        is PUT -> annotation.value
        is PATCH -> annotation.value
        is DELETE -> annotation.value
        else -> null
    }

    private companion object {
        val MUTATION_WORDS = setOf("apply", "stage", "discard", "restore", "commit", "checkout", "write")
    }
}
