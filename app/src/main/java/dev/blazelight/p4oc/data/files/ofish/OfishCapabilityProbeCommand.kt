package dev.blazelight.p4oc.data.files.ofish

internal object OfishCapabilityProbeCommand {
    fun build(): String = OfishShellWrapper.wrap(
        """
        printf '#OFISH_HELLO\n'
        missing=""
        have() { command -v "${'$'}1" >/dev/null 2>&1; }

        base64_present=0
        base64_decode=""
        if have base64; then
          base64_present=1
          encoded=${'$'}(printf test | base64 2>/dev/null)
          if printf '%s' "${'$'}encoded" | base64 -d >/dev/null 2>&1; then
            base64_decode="-d"
          elif printf '%s' "${'$'}encoded" | base64 -D >/dev/null 2>&1; then
            base64_decode="-D"
          else
            missing="${'$'}missing base64_decode"
          fi
        else
          missing="${'$'}missing base64"
        fi

        hash=""
        if have sha256sum; then
          hash="sha256sum"
        elif have shasum && printf test | shasum -a 256 >/dev/null 2>&1; then
          hash="shasum -a 256"
        elif have md5sum; then
          hash="md5sum"
        else
          missing="${'$'}missing hash"
        fi

        has_mv=0; have mv && has_mv=1 || missing="${'$'}missing mv"
        has_mkdir=0; have mkdir && has_mkdir=1 || missing="${'$'}missing mkdir"
        has_rm=0; have rm && has_rm=1 || missing="${'$'}missing rm"
        has_awk=0; have awk && has_awk=1 || missing="${'$'}missing awk"
        has_mktemp=0; have mktemp && has_mktemp=1 || missing="${'$'}missing mktemp"

        printf 'caps base64=%s base64_decode=%s hash=%s mv=%s mkdir=%s rm=%s awk=%s mktemp=%s\n' \
          "${'$'}base64_present" "${'$'}base64_decode" "${'$'}hash" "${'$'}has_mv" "${'$'}has_mkdir" "${'$'}has_rm" "${'$'}has_awk" "${'$'}has_mktemp"

        if [ -n "${'$'}missing" ]; then
          printf '### 501 caps_missing%s\n' "${'$'}missing"
        else
          printf '### 200 ok\n'
        fi
        """.trimIndent(),
    )
}
