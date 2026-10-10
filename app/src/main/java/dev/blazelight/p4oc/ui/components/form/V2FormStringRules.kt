package dev.blazelight.p4oc.ui.components.form

import android.content.Context
import android.util.Patterns
import dev.blazelight.p4oc.R
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

/** String `format` and `pattern` rules for v2 form fields, with human-readable errors. */
internal fun validStringFormat(format: String?, value: String): Boolean = when (format) {
    null -> true
    "email" -> Patterns.EMAIL_ADDRESS.matcher(value).matches()
    "uri" -> runCatching { URI(value).isAbsolute }.getOrDefault(false)
    "date" -> runCatching { LocalDate.parse(value) }.isSuccess
    "date-time" ->
        runCatching { OffsetDateTime.parse(value) }.isSuccess ||
            runCatching { Instant.parse(value) }.isSuccess
    else -> true
}

internal fun stringFormatError(format: String?, context: Context): String = when (format) {
    "email" -> context.getString(R.string.v2_form_email_error)
    "uri" -> context.getString(R.string.v2_form_uri_error)
    "date" -> context.getString(R.string.v2_form_date_error)
    "date-time" -> context.getString(R.string.v2_form_datetime_error)
    else -> context.getString(R.string.v2_form_pattern_error)
}

internal fun patternError(pattern: String, value: String, context: Context): String? = try {
    if (Regex(pattern).containsMatchIn(value)) null else context.getString(R.string.v2_form_pattern_error)
} catch (_: IllegalArgumentException) {
    context.getString(R.string.v2_form_invalid_pattern)
}
