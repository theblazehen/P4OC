package dev.blazelight.p4oc.data.files.ofish

import dev.blazelight.p4oc.domain.model.FileContent
import java.security.MessageDigest

internal object OfishBaselineHasher {
    fun hash(content: FileContent, hashCommand: HashCommand): String? {
        if (content.type != "text") return null
        if (content.encoding != null) return null

        val algorithm = when (hashCommand) {
            HashCommand.SHA256SUM,
            HashCommand.SHASUM_256 -> "SHA-256"
            HashCommand.MD5SUM -> "MD5"
        }
        return MessageDigest.getInstance(algorithm)
            .digest(content.content.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
