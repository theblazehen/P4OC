package dev.blazelight.p4oc.domain.workspace

@JvmInline
value class RelativePath(val value: String) {
    init {
        require(value.isNotBlank()) { "Relative path must not be blank" }
        require(!value.startsWith("/")) { "Relative path must not be absolute" }
        require(!value.startsWith("file://", ignoreCase = true)) { "Relative path must not use file:// scheme" }
    }

    override fun toString(): String = value
}

sealed interface WorkspacePath {
    val value: String

    data class Relative(val path: RelativePath) : WorkspacePath {
        override val value: String = path.value
    }

    data class Absolute(override val value: String) : WorkspacePath {
        init {
            require(value.isNotBlank()) { "Absolute path must not be blank" }
            require(value.startsWith("/")) { "Absolute path must start with /" }
            require(!value.startsWith("file://", ignoreCase = true)) { "Absolute path must not use file:// scheme" }
        }
    }
}
