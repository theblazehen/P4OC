@file:Suppress("FunctionNaming") // Compose UI functions follow the framework's PascalCase convention.

package dev.blazelight.p4oc.ui.components.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.core.network.V2FormField
import dev.blazelight.p4oc.core.network.V2FormInfo
import dev.blazelight.p4oc.core.network.V2FormOption
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.net.URI

@Composable
fun InlineV2FormStatusCard(
    message: String,
    isLoading: Boolean,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOpenCodeTheme.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.backgroundPanel)
            .border(Sizing.strokeMd, theme.border, RectangleShape)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(Sizing.iconSm), strokeWidth = Spacing.hairline)
                Spacer(Modifier.width(Spacing.sm))
            }
            Text(message, color = if (isLoading) theme.textMuted else theme.error)
        }
        if (onRetry != null) {
            OutlinedButton(onClick = onRetry, shape = RectangleShape) {
                Text(stringResource(R.string.v2_form_refresh))
            }
        }
    }
}

private data class V2FormActions(
    val onSubmit: (JsonObject) -> Unit,
    val onReject: () -> Unit,
    val onRetry: () -> Unit,
)

private data class V2FormCardState(
    val isLoading: Boolean,
    val isSubmitting: Boolean,
    val error: String?,
)

private data class V2FormFieldInputState(
    val draft: JsonElement?,
    val customInput: String,
    val externalError: String?,
    val validationError: String?,
    val onDraftChange: (JsonElement?) -> Unit,
    val onCustomInputChange: (String) -> Unit,
    val onAddCustom: () -> Unit,
    val openExternal: (String) -> Unit,
)

private class V2FormDraftState {
    val drafts = mutableStateMapOf<String, JsonElement>()
    val customInputs = mutableStateMapOf<String, String>()
    val externalErrors = mutableStateMapOf<String, String>()
    var showValidation by mutableStateOf(false)

    fun inputState(
        field: V2FormField,
        validationError: String?,
        context: android.content.Context,
        uriHandler: androidx.compose.ui.platform.UriHandler,
    ) = V2FormFieldInputState(
        draft = drafts[field.key],
        customInput = customInputs[field.key].orEmpty(),
        externalError = externalErrors[field.key],
        validationError = validationError,
        onDraftChange = { value ->
            if (value == null) drafts.remove(field.key) else drafts[field.key] = value
        },
        onCustomInputChange = { customInputs[field.key] = it },
        onAddCustom = {
            val current = selectedValues(drafts[field.key]).toMutableList()
            val value = customInputs[field.key].orEmpty().trim()
            if (value.isNotEmpty() && value !in current) {
                current.add(value)
                drafts[field.key] = JsonArray(current.map(::JsonPrimitive))
            }
            customInputs.remove(field.key)
        },
        openExternal = { url ->
            try {
                uriHandler.openUri(url)
                externalErrors.remove(field.key)
            } catch (_: Exception) {
                externalErrors[field.key] = context.getString(R.string.v2_form_external_error)
            }
        },
    )
}

@Suppress("LongParameterList") // Keep the established Compose entry-point API stable.
@Composable
fun InlineV2FormCard(
    form: V2FormInfo?,
    isLoading: Boolean,
    isSubmitting: Boolean,
    error: String?,
    onSubmit: (JsonObject) -> Unit,
    onReject: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (form == null) {
        InlineV2FormStatusCard(
            message = error ?: stringResource(R.string.v2_form_loading),
            isLoading = isLoading,
            onRetry = if (isLoading) null else onRetry,
            modifier = modifier,
        )
        return
    }

    V2FormFieldsCard(
        form = form,
        state = V2FormCardState(isLoading, isSubmitting, error),
        actions = V2FormActions(onSubmit, onReject, onRetry),
        modifier = modifier,
    )
}

@Composable
private fun V2FormFieldsCard(
    form: V2FormInfo,
    state: V2FormCardState,
    actions: V2FormActions,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOpenCodeTheme.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val draftState = remember(form.id) { V2FormDraftState() }

    LaunchedEffect(form.id, form.fields) {
        initialDrafts(form.fields).forEach { (key, draft) ->
            if (key !in draftState.drafts) draftState.drafts[key] = draft
        }
    }

    val validation = validateForm(form.fields, draftState.drafts, context)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.backgroundPanel)
            .border(Sizing.strokeMd, theme.border, RectangleShape)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        V2FormHeader(form)
        if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        V2FormFieldInputs(validation, draftState, context, uriHandler)
        V2FormValidationWarnings(validation)
        V2FormServerError(state.error, state.isLoading, state.isSubmitting, actions.onRetry)
        V2FormActionRow(
            isSubmitting = state.isSubmitting,
            isLoading = state.isLoading,
            validation = validation,
            actions = actions,
            onSubmit = {
                draftState.showValidation = true
                if (validation.canSubmit) actions.onSubmit(validation.resolution.answer)
            },
        )
    }
}

@Composable
private fun V2FormHeader(form: V2FormInfo) {
    val theme = LocalOpenCodeTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(R.string.v2_form_pending),
            style = MaterialTheme.typography.labelMedium,
            color = theme.warning,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = form.title.ifBlank { form.id },
            style = MaterialTheme.typography.titleSmall,
            color = theme.text,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun V2FormFieldInputs(
    validation: V2FormValidation,
    draftState: V2FormDraftState,
    context: android.content.Context,
    uriHandler: androidx.compose.ui.platform.UriHandler,
) {
    validation.resolution.renderedFields.forEach { field ->
        V2FormFieldInput(
            field = field,
            input = draftState.inputState(
                field,
                if (draftState.showValidation) validation.answerErrors[field.key] else null,
                context,
                uriHandler,
            ),
        )
    }
}

@Composable
private fun V2FormValidationWarnings(validation: V2FormValidation) {
    val theme = LocalOpenCodeTheme.current
    if (validation.unsupportedFields.isNotEmpty()) {
        Text(
            text = stringResource(R.string.v2_form_unsupported_field),
            color = theme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (validation.missingChoices) {
        Text(
            stringResource(R.string.v2_form_missing_choices),
            color = theme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    validation.hiddenFieldErrors.forEach { message ->
        Text(message, color = theme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun V2FormServerError(
    error: String?,
    isLoading: Boolean,
    isSubmitting: Boolean,
    onRetry: () -> Unit,
) {
    if (error == null) return
    val theme = LocalOpenCodeTheme.current
    Text(error, color = theme.error, style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = onRetry, enabled = !isLoading && !isSubmitting) {
        Text(stringResource(R.string.v2_form_refresh))
    }
}

@Composable
private fun V2FormActionRow(
    isSubmitting: Boolean,
    isLoading: Boolean,
    validation: V2FormValidation,
    actions: V2FormActions,
    onSubmit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        OutlinedButton(
            onClick = actions.onReject,
            enabled = !isSubmitting && !isLoading,
            modifier = Modifier.weight(1f),
            shape = RectangleShape,
        ) {
            Text(stringResource(R.string.v2_form_reject))
        }
        Button(
            onClick = onSubmit,
            enabled = !isSubmitting && !isLoading && validation.unsupportedFields.isEmpty() &&
                !validation.missingChoices && validation.hiddenFieldErrors.isEmpty(),
            modifier = Modifier.weight(1f),
            shape = RectangleShape,
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(Sizing.iconSm), strokeWidth = Spacing.hairline)
                Spacer(Modifier.width(Spacing.xs))
            }
            Text(stringResource(R.string.submit))
        }
    }
}

@Composable
private fun V2FormFieldInput(field: V2FormField, input: V2FormFieldInputState) {
    val theme = LocalOpenCodeTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = field.title?.ifBlank { null } ?: field.key,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.text,
                fontWeight = FontWeight.SemiBold,
            )
            if (field.required == true || field.type == "external") {
                Spacer(Modifier.width(Spacing.xxs))
                Text("*", color = theme.warning, style = MaterialTheme.typography.bodyMedium)
            }
        }
        field.description?.takeIf(String::isNotBlank)?.let { description ->
            Text(description, style = MaterialTheme.typography.bodySmall, color = theme.textMuted)
        }
        V2FormFieldControl(field, input)
        input.validationError?.let {
            Text(it, color = theme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun V2FormFieldControl(field: V2FormField, input: V2FormFieldInputState) {
    when (field.type) {
        "string" -> V2StringInput(field, input.draft, input.onDraftChange)
        "number", "integer" -> V2NumberInput(field, input)
        "boolean" -> V2BooleanInput(input)
        "multiselect" -> V2MultiselectInput(field, input)
        "external" -> V2ExternalInput(field, input)
    }
}

@Composable
private fun V2NumberInput(field: V2FormField, input: V2FormFieldInputState) {
    OutlinedTextField(
        value = input.draft.asText(),
        onValueChange = { input.onDraftChange(JsonPrimitive(it)) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = field.placeholder?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (field.type == "integer") KeyboardType.Number else KeyboardType.Decimal,
        ),
        singleLine = true,
    )
}

@Composable
private fun V2BooleanInput(input: V2FormFieldInputState) {
    val selected = (input.draft as? JsonPrimitive)?.booleanOrNull
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FilterChip(
            selected = selected == true,
            onClick = { input.onDraftChange(if (selected == true) null else JsonPrimitive(true)) },
            label = { Text(stringResource(R.string.v2_form_yes)) },
        )
        FilterChip(
            selected = selected == false,
            onClick = { input.onDraftChange(if (selected == false) null else JsonPrimitive(false)) },
            label = { Text(stringResource(R.string.v2_form_no)) },
        )
    }
}

@Composable
private fun V2ExternalInput(field: V2FormField, input: V2FormFieldInputState) {
    val theme = LocalOpenCodeTheme.current
    val url = field.url.orEmpty()
    val safeUrl = url.takeIf(::isWebUrl)
    if (safeUrl != null) {
        OutlinedButton(
            onClick = { input.openExternal(safeUrl) },
            modifier = Modifier.testTag("v2_form_external_open_${field.key}"),
            shape = RectangleShape,
        ) {
            Text(field.title?.ifBlank { null } ?: stringResource(R.string.v2_form_external_open))
        }
    } else {
        Text(url, style = MaterialTheme.typography.bodySmall, color = theme.textMuted)
        Text(
            stringResource(R.string.v2_form_external_error),
            color = theme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    input.externalError?.let { Text(it, color = theme.error, style = MaterialTheme.typography.bodySmall) }
    V2ExternalAcknowledgement(field, input)
}

/** Opening the link is not completion; the server only accepts an external field once the user confirms it. */
@Composable
private fun V2ExternalAcknowledgement(field: V2FormField, input: V2FormFieldInputState) {
    val acknowledged = (input.draft as? JsonPrimitive)?.booleanOrNull == true
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = acknowledged,
                role = Role.Checkbox,
                onValueChange = { checked -> input.onDraftChange(if (checked) JsonPrimitive(true) else null) },
            )
            .testTag("v2_form_external_ack_${field.key}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = acknowledged, onCheckedChange = null)
        Text(
            text = stringResource(R.string.v2_form_external_ack),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalOpenCodeTheme.current.text,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V2StringInput(
    field: V2FormField,
    draft: JsonElement?,
    onDraftChange: (JsonElement?) -> Unit,
) {
    val options = field.options.orEmpty()
    var expanded by remember(field.key) { mutableStateOf(false) }
    val lockedToOptions = options.isNotEmpty() && field.custom != true
    if (options.isEmpty()) {
        OutlinedTextField(
            value = draft.asText(),
            onValueChange = { onDraftChange(JsonPrimitive(it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = field.placeholder?.let { { Text(it) } },
        )
    } else {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = draft.asText(),
                onValueChange = { if (!lockedToOptions) onDraftChange(JsonPrimitive(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(
                        if (lockedToOptions) {
                            ExposedDropdownMenuAnchorType.PrimaryNotEditable
                        } else {
                            ExposedDropdownMenuAnchorType.PrimaryEditable
                        },
                        enabled = true,
                    ),
                readOnly = lockedToOptions,
                placeholder = field.placeholder?.let { { Text(it) } },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(option.label)
                                option.description?.takeIf(String::isNotBlank)?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        },
                        onClick = {
                            onDraftChange(JsonPrimitive(option.value))
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun V2MultiselectInput(field: V2FormField, input: V2FormFieldInputState) {
    val selected = selectedValues(input.draft)
    val options = field.options.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        options.forEach { option ->
            V2MultiselectOption(option, selected, input.onDraftChange)
        }
        val customValues = selected.filter { selectedValue ->
            options.none { it.value == selectedValue }
        }
        customValues.forEach { value ->
            V2MultiselectCustomValue(value) {
                input.onDraftChange(JsonArray((selected - value).map(::JsonPrimitive)))
            }
        }
        if (field.custom == true) V2MultiselectCustomInput(input)
    }
}

@Composable
private fun V2MultiselectOption(
    option: V2FormOption,
    selected: List<String>,
    onDraftChange: (JsonElement?) -> Unit,
) {
    val checked = option.value in selected
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val updated = if (checked) selected - option.value else selected + option.value
                onDraftChange(JsonArray(updated.map(::JsonPrimitive)))
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { isChecked ->
                val updated = if (isChecked) selected + option.value else selected - option.value
                onDraftChange(JsonArray(updated.map(::JsonPrimitive)))
            },
        )
        Column {
            Text(option.label)
            option.description?.takeIf(String::isNotBlank)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalOpenCodeTheme.current.textMuted,
                )
            }
        }
    }
}

@Composable
private fun V2MultiselectCustomValue(value: String, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(value, modifier = Modifier.weight(1f))
        TextButton(onClick = onRemove) {
            Text(stringResource(R.string.v2_form_remove_custom))
        }
    }
}

@Composable
private fun V2MultiselectCustomInput(input: V2FormFieldInputState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        OutlinedTextField(
            value = input.customInput,
            onValueChange = input.onCustomInputChange,
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.v2_form_custom_value)) },
            singleLine = true,
        )
        OutlinedButton(onClick = input.onAddCustom, shape = RectangleShape) {
            Text(stringResource(R.string.v2_form_add_custom))
        }
    }
}

private fun isWebUrl(value: String): Boolean = runCatching {
    val uri = URI(value)
    uri.isAbsolute && uri.host != null &&
        (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true))
}.getOrDefault(false)
