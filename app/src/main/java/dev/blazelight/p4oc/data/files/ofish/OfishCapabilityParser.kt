package dev.blazelight.p4oc.data.files.ofish

internal object OfishCapabilityParser {
    private val KEY_VALUE = Regex("(\\w+)=([^\\n]+?)(?=\\s+\\w+=|$)")
    private val STATUS_LINE = Regex("^### \\d{3}(?:\\s.*)?$")

    fun parse(output: String): OfishProbeResult {
        val lines = output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val capsLineIndex = lines.indexOfLast { it.startsWith("caps ") }
        if (capsLineIndex == -1) {
            return OfishProbeResult.Failed("Malformed OFISH capability probe output: missing caps line")
        }
        val statusLineIndices = lines.indices.filter { STATUS_LINE.matches(lines[it]) }
        if (statusLineIndices.isEmpty()) {
            return OfishProbeResult.Failed("Malformed OFISH capability probe output: missing status line")
        }
        val statusLineIndex = statusLineIndices.firstOrNull { it > capsLineIndex }
            ?: return OfishProbeResult.Failed(
                "Malformed OFISH capability probe output: status line before caps line",
            )
        val capsLine = lines[capsLineIndex]
        val statusLine = lines[statusLineIndex]

        val capabilities = parseCapabilities(capsLine)
            ?: return OfishProbeResult.Failed("Malformed OFISH capability probe output: invalid caps line")

        return when {
            statusLine == "### 200 ok" -> {
                if (capabilities.supportsMutation) {
                    OfishProbeResult.Available(capabilities)
                } else {
                    OfishProbeResult.Missing(computeMissing(capabilities), capabilities)
                }
            }
            statusLine.startsWith("### 501 caps_missing") -> {
                val missing = statusLine
                    .removePrefix("### 501 caps_missing")
                    .trim()
                    .split(Regex("\\s+"))
                    .filter { it.isNotBlank() }
                    .ifEmpty { computeMissing(capabilities) }
                OfishProbeResult.Missing(missing, capabilities)
            }
            else -> OfishProbeResult.Failed("Malformed OFISH capability probe output: unsupported status line")
        }
    }

    private fun parseCapabilities(capsLine: String): OfishCapabilities? {
        val values = KEY_VALUE.findAll(capsLine.removePrefix("caps "))
            .associate { match -> match.groupValues[1] to match.groupValues[2].trim() }

        if (values.isEmpty()) return null

        return OfishCapabilities(
            hasBase64 = values["base64"].toBooleanFlag(),
            base64DecodeFlag = values["base64_decode"].blankToNull(),
            hashCommand = values["hash"].toHashCommand(),
            hasMv = values["mv"].toBooleanFlag(),
            hasMkdir = values["mkdir"].toBooleanFlag(),
            hasRm = values["rm"].toBooleanFlag(),
            hasAwk = values["awk"].toBooleanFlag(),
            hasMktemp = values["mktemp"].toBooleanFlag(),
            hasChmod = values["chmod"].toBooleanFlag(),
            modeCommand = values["mode"].toModeCommand(),
        )
    }

    private fun computeMissing(capabilities: OfishCapabilities): List<String> = buildList {
        if (!capabilities.hasBase64) add("base64")
        if (capabilities.base64DecodeFlag.isNullOrBlank()) add("base64_decode")
        if (capabilities.hashCommand == null) add("hash")
        if (!capabilities.hasMv) add("mv")
        if (!capabilities.hasMkdir) add("mkdir")
        if (!capabilities.hasRm) add("rm")
        if (!capabilities.hasAwk) add("awk")
        if (!capabilities.hasMktemp) add("mktemp")
        if (!capabilities.hasChmod) add("chmod")
        if (capabilities.modeCommand == null) add("stat_mode")
    }

    private fun String?.toBooleanFlag(): Boolean = this == "1" || equals("true", ignoreCase = true)

    private fun String?.blankToNull(): String? = this?.takeIf { it.isNotBlank() }

    private fun String?.toHashCommand(): HashCommand? = when (this?.trim()) {
        HashCommand.SHA256SUM.wireName -> HashCommand.SHA256SUM
        HashCommand.SHASUM_256.wireName -> HashCommand.SHASUM_256
        HashCommand.MD5SUM.wireName -> HashCommand.MD5SUM
        else -> null
    }

    private fun String?.toModeCommand(): ModeCommand? = when (this?.trim()) {
        ModeCommand.STAT_GNU.wireName -> ModeCommand.STAT_GNU
        ModeCommand.STAT_BSD.wireName -> ModeCommand.STAT_BSD
        else -> null
    }
}
