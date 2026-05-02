package dev.blazelight.p4oc.domain.session

@JvmInline
value class SessionId(val value: String) {
    init {
        require(value.isNotBlank()) { "Session id must not be blank" }
    }

    override fun toString(): String = value
}
