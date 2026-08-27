package dev.blazelight.p4oc.data.files.ofish

import dev.blazelight.p4oc.data.remote.dto.MessageWrapperDto
import dev.blazelight.p4oc.data.remote.dto.ShellCommandRequest

private const val CAPABILITY_MARKER = "#OFISH_HELLO"

/**
 * Executes the OFISH shell-environment capability probe in an ephemeral session.
 *
 * Production repository construction wires this probe, but ephemeral OFISH shell sessions are not yet
 * registered with a permission broker. This class intentionally does not auto-approve permissions by
 * itself; if the server requires approval for the probe command, probing fails conservatively instead
 * of inventing call IDs or broadly approving shell permissions.
 */
internal class OfishCapabilityProbe(
    private val client: OfishWorkspaceClient,
    private val sessionFactory: OfishSessionFactory,
    private val shellAgent: String = DEFAULT_SHELL_AGENT,
) {
    suspend fun probe(): OfishProbeResult = runCatching {
        sessionFactory.withSession(OPERATION_NAME) { session ->
            val response = client.executeShellCommand(
                sessionId = session.id,
                request = ShellCommandRequest(
                    agent = shellAgent,
                    model = null,
                    command = OfishCapabilityProbeCommand.build(),
                ),
            )
            val output = OfishShellOutputExtractor.extractCapabilitySegment(response)
                ?: return@withSession OfishProbeResult.Failed(
                    "Malformed OFISH capability probe output: missing $CAPABILITY_MARKER output segment",
                )
            OfishCapabilityParser.parse(output)
        }
    }.getOrElse { error ->
        OfishProbeResult.Failed("OFISH capability probe failed", error)
    }

    private companion object {
        const val OPERATION_NAME = "probe"
        const val DEFAULT_SHELL_AGENT = "build"
    }
}

internal object OfishShellOutputExtractor {
    fun extractCapabilitySegment(message: MessageWrapperDto): String? =
        extractMarkerSegment(message, CAPABILITY_MARKER)

    fun extractMutationSegment(message: MessageWrapperDto, expectedMarker: String): String? =
        extractMarkerSegment(message, expectedMarker)

    private fun extractMarkerSegment(message: MessageWrapperDto, expectedMarker: String): String? {
        fun String.containsMarker(): Boolean = lineSequence().any { it == expectedMarker }

        // Shell tool state is authoritative. Text parts are only a compatibility fallback for
        // servers that return command output as assistant text rather than structured tool state.
        val stateSegments = message.parts.flatMap { part ->
            listOfNotNull(part.state?.output, part.state?.raw, part.state?.error)
        }
        return stateSegments.lastOrNull { it.containsMarker() }
            ?: message.parts.asReversed().firstNotNullOfOrNull { part ->
                part.text?.takeIf { it.containsMarker() }
            }
    }
}
