package dev.blazelight.p4oc.domain.workspace

sealed interface AttachmentRef {
    val path: RelativePath

    data class File(
        override val path: RelativePath,
        val mimeType: String? = null,
    ) : AttachmentRef {
        init {
            require(mimeType == null || mimeType.isNotBlank()) { "MIME type must not be blank" }
        }
    }

    data class Directory(override val path: RelativePath) : AttachmentRef
}
