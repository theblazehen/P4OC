package dev.blazelight.p4oc.ui.components.form

import android.content.Context
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.core.network.V2FormField
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * One pass over a form's fields in declaration order, mirroring the server's `validateAnswer`/`isActive`.
 *
 * A field is active when every `when` condition matches the answer of an earlier active field; inactive
 * fields contribute nothing, so hiding a controller cascades to its dependents. External fields carry no
 * conditions, are always active, and are never condition targets. Hidden fields stay active (they are
 * answered with their default) but get no control. Drafts of inactive fields are left untouched so they
 * reappear when the field becomes active again.
 */
internal data class V2FormResolution(
    val activeFields: List<V2FormField>,
    val renderedFields: List<V2FormField>,
    val answer: JsonObject,
)

internal data class V2FormValidation(
    val resolution: V2FormResolution,
    val unsupportedFields: List<V2FormField>,
    val missingChoices: Boolean,
    val answerErrors: Map<String, String>,
    val hiddenFieldErrors: List<String>,
) {
    val canSubmit: Boolean
        get() = answerErrors.isEmpty() && hiddenFieldErrors.isEmpty() &&
            unsupportedFields.isEmpty() && !missingChoices
}

/** Drafts a freshly shown form starts from: every field's declared default, in draft shape. */
internal fun initialDrafts(fields: List<V2FormField>): Map<String, JsonElement> =
    fields.mapNotNull { field -> initialDraft(field)?.let { field.key to it } }.toMap()

internal fun resolveForm(fields: List<V2FormField>, drafts: Map<String, JsonElement>): V2FormResolution {
    val activeFields = mutableListOf<V2FormField>()
    val answer = linkedMapOf<String, JsonElement>()
    val conditionValues = mutableMapOf<String, JsonElement>()
    fields.forEach { field ->
        if (!isActiveField(field, conditionValues)) return@forEach
        activeFields.add(field)
        val value = draftAnswerValue(field, effectiveDraft(field, drafts)) ?: return@forEach
        answer[field.key] = value
        if (field.type != EXTERNAL) conditionValues[field.key] = value
    }
    return V2FormResolution(
        activeFields = activeFields,
        renderedFields = activeFields.filterNot(::isHiddenField),
        answer = JsonObject(answer),
    )
}

internal fun validateForm(
    fields: List<V2FormField>,
    drafts: Map<String, JsonElement>,
    context: Context,
): V2FormValidation {
    val resolution = resolveForm(fields, drafts)
    val missingChoices = resolution.renderedFields.any { field ->
        field.type == "multiselect" && field.required == true &&
            field.options.isNullOrEmpty() && field.custom != true
    }
    val answerErrors = resolution.renderedFields.associateNotNull { field ->
        validateField(field, drafts[field.key], context)?.let { field.key to it }
    }
    val hiddenFieldErrors = resolution.activeFields.filter(::isHiddenField).mapNotNull { field ->
        validateField(field, effectiveDraft(field, drafts), context)?.let {
            context.getString(R.string.v2_form_hidden_field_error, field.title?.ifBlank { null } ?: field.key)
        }
    }
    return V2FormValidation(
        resolution = resolution,
        unsupportedFields = resolution.activeFields.filter { it.type !in SUPPORTED_FIELD_TYPES },
        missingChoices = missingChoices,
        answerErrors = answerErrors,
        hiddenFieldErrors = hiddenFieldErrors,
    )
}

private fun initialDraft(field: V2FormField): JsonElement? = when (val default = field.default) {
    null, JsonNull -> null
    is JsonPrimitive -> if (field.type == "multiselect" && default.isString) JsonArray(listOf(default)) else default
    else -> default
}

/** Hidden fields have no control, so they always answer with their default. */
private fun effectiveDraft(field: V2FormField, drafts: Map<String, JsonElement>): JsonElement? =
    drafts[field.key] ?: if (isHiddenField(field)) initialDraft(field) else null

private fun isHiddenField(field: V2FormField): Boolean = field.hidden == true && field.type != EXTERNAL

private fun draftAnswerValue(field: V2FormField, draft: JsonElement?): JsonElement? = when (field.type) {
    "string" -> (draft as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotEmpty() }
    "number" -> numberAnswer(draft.asText(), integer = false)
    "integer" -> numberAnswer(draft.asText(), integer = true)
    "boolean" -> (draft as? JsonPrimitive)?.booleanOrNull?.let(::JsonPrimitive)
    "multiselect" -> JsonArray(selectedValues(draft).map(::JsonPrimitive)).takeIf { it.isNotEmpty() }
    EXTERNAL -> JsonPrimitive(true).takeIf { isAcknowledged(draft) }
    else -> null
}

private fun isAcknowledged(draft: JsonElement?): Boolean = (draft as? JsonPrimitive)?.booleanOrNull == true

private fun numberAnswer(text: String, integer: Boolean): JsonElement? {
    if (text in SPECIAL_NUMBER_TEXTS) return JsonPrimitive(text)
    val value = text.toBigDecimalOrNull()
    return when {
        value == null -> null
        integer -> runCatching { value.toBigIntegerExact() }.getOrNull()?.let(::JsonPrimitive)
        else -> JsonPrimitive(value)
    }
}

// An unanswered (or inactive) referenced field makes the condition false for both ops.
private fun isActiveField(field: V2FormField, values: Map<String, JsonElement>): Boolean =
    field.type == EXTERNAL || field.conditions.all { condition ->
        val actual = values[condition.key] ?: return@all false
        val equal = conditionMatches(actual, condition.value)
        when (condition.op) {
            "eq" -> equal
            "neq" -> !equal
            else -> false
        }
    }

private fun conditionMatches(actual: JsonElement, expected: JsonElement): Boolean = when {
    actual is JsonArray -> actual.any { item -> conditionMatches(item, expected) }
    actual !is JsonPrimitive || expected !is JsonPrimitive -> actual == expected
    actual.isString || expected.isString ->
        actual.isString && expected.isString && actual.content == expected.content
    else -> primitiveConditionMatches(actual, expected)
}

private fun primitiveConditionMatches(actual: JsonPrimitive, expected: JsonPrimitive): Boolean {
    val actualBoolean = actual.booleanOrNull
    val expectedBoolean = expected.booleanOrNull
    if (actualBoolean != null || expectedBoolean != null) return actualBoolean == expectedBoolean
    val actualNumber = actual.content.toBigDecimalOrNull()
    val expectedNumber = expected.content.toBigDecimalOrNull()
    return if (actualNumber != null && expectedNumber != null) {
        actualNumber.compareTo(expectedNumber) == 0
    } else {
        actual.content == expected.content
    }
}

private fun validateField(
    field: V2FormField,
    draft: JsonElement?,
    context: Context,
): String? = when (field.type) {
    "string" -> validateStringField(field, draft.asText(), context)
    "number", "integer" -> validateNumberField(field, draft.asText(), context)
    "boolean" -> validateBooleanField(field, draft, context)
    "multiselect" -> validateMultiselectField(field, draft, context)
    EXTERNAL -> if (isAcknowledged(draft)) null else context.getString(R.string.v2_form_external_ack_error)
    else -> context.getString(R.string.v2_form_unsupported_field)
}

private fun validateStringField(
    field: V2FormField,
    value: String,
    context: Context,
): String? = when {
    field.required == true && value.isBlank() -> context.getString(R.string.v2_form_required_error)
    value.isEmpty() -> null
    field.options.orEmpty().isNotEmpty() && field.custom != true &&
        field.options.orEmpty().none { it.value == value } ->
        context.getString(R.string.v2_form_choose_option)
    field.minLength != null && value.length < field.minLength ->
        context.getString(R.string.v2_form_min_length_error, field.minLength)
    field.maxLength != null && value.length > field.maxLength ->
        context.getString(R.string.v2_form_max_length_error, field.maxLength)
    !validStringFormat(field.format, value) -> stringFormatError(field.format, context)
    field.pattern != null -> patternError(field.pattern, value, context)
    else -> null
}

private fun validateNumberField(
    field: V2FormField,
    valueText: String,
    context: Context,
): String? = if (valueText.isBlank()) {
    if (field.required == true) context.getString(R.string.v2_form_required_error) else null
} else {
    numericError(field, valueText, context)
}

private fun validateBooleanField(
    field: V2FormField,
    draft: JsonElement?,
    context: Context,
): String? = if (field.required == true && (draft as? JsonPrimitive)?.booleanOrNull == null) {
    context.getString(R.string.v2_form_required_error)
} else {
    null
}

private fun validateMultiselectField(
    field: V2FormField,
    draft: JsonElement?,
    context: Context,
): String? {
    val values = selectedValues(draft)
    return when {
        field.required == true && values.isEmpty() -> context.getString(R.string.v2_form_required_error)
        field.minItems != null && values.size < field.minItems ->
            context.getString(R.string.v2_form_min_items_error, field.minItems)
        field.maxItems != null && values.size > field.maxItems ->
            context.getString(R.string.v2_form_max_items_error, field.maxItems)
        else -> null
    }
}

private fun numericError(field: V2FormField, valueText: String, context: Context): String? {
    val value = valueText.toBigDecimalOrNull()
    val invalidNumber = value == null && valueText !in SPECIAL_NUMBER_TEXTS
    val invalidInteger = field.type == "integer" && value != null &&
        runCatching { value.toBigIntegerExact() }.isFailure
    val minimum = field.minimum.asText()
    val maximum = field.maximum.asText()
    return when {
        invalidNumber -> context.getString(
            if (field.type == "integer") R.string.v2_form_integer_error else R.string.v2_form_number_error,
        )
        invalidInteger -> context.getString(R.string.v2_form_integer_error)
        minimum.isNotBlank() && compareNumericValues(valueText, minimum)?.let { it < 0 } == true ->
            context.getString(R.string.v2_form_minimum_error, minimum)
        maximum.isNotBlank() && compareNumericValues(valueText, maximum)?.let { it > 0 } == true ->
            context.getString(R.string.v2_form_maximum_error, maximum)
        else -> null
    }
}

private fun compareNumericValues(left: String, right: String): Int? {
    val leftInfinity = infinityRank(left)
    val rightInfinity = infinityRank(right)
    return when {
        left == "NaN" || right == "NaN" -> null
        leftInfinity != rightInfinity -> leftInfinity.compareTo(rightInfinity)
        leftInfinity != 0 -> 0
        else -> left.toBigDecimalOrNull()?.let { leftNumber ->
            right.toBigDecimalOrNull()?.let(leftNumber::compareTo)
        }
    }
}

private fun infinityRank(value: String): Int = when (value) {
    "Infinity" -> 1
    "-Infinity" -> -1
    else -> 0
}

internal fun JsonElement?.asText(): String = (this as? JsonPrimitive)?.content.orEmpty()

internal fun selectedValues(draft: JsonElement?): List<String> = when (draft) {
    is JsonArray -> draft.mapNotNull { (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content }
    is JsonPrimitive -> if (draft.isString && draft.content.isNotEmpty()) listOf(draft.content) else emptyList()
    else -> emptyList()
}

private inline fun <T, K, V> Iterable<T>.associateNotNull(transform: (T) -> Pair<K, V>?): Map<K, V> =
    buildMap { this@associateNotNull.forEach { item -> transform(item)?.let { (key, value) -> put(key, value) } } }

private const val EXTERNAL = "external"

private val SUPPORTED_FIELD_TYPES = setOf("string", "number", "integer", "boolean", "multiselect", EXTERNAL)

private val SPECIAL_NUMBER_TEXTS = setOf("Infinity", "-Infinity", "NaN")
