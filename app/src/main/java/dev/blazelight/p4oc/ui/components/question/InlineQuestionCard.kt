package dev.blazelight.p4oc.ui.components.question

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.domain.model.Question
import dev.blazelight.p4oc.domain.model.QuestionData
import dev.blazelight.p4oc.domain.model.QuestionOption
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.Sizing

/**
 * Inline question card that appears in the chat message list.
 * Replaces the modal dialog for a more TUI-like experience.
 */
@Composable
fun InlineQuestionCard(
    questionData: QuestionData,
    onDismiss: () -> Unit,
    onSubmit: (List<List<String>>) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    var currentQuestionIndex by remember { mutableIntStateOf(0) }
    val answers = remember { mutableStateMapOf<Int, List<String>>() }
    
    val currentQuestion = questionData.questions.getOrNull(currentQuestionIndex)
    val isLastQuestion = currentQuestionIndex == questionData.questions.lastIndex
    val hasAnswer = answers[currentQuestionIndex]?.isNotEmpty() == true
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.backgroundPanel)
            .border(1.dp, theme.border, RectangleShape)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Header row with icon and title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                contentDescription = stringResource(R.string.cd_question_icon),
                tint = theme.warning,
                modifier = Modifier.size(Sizing.iconSm)
            )
            Text(
                text = stringResource(R.string.question),
                style = MaterialTheme.typography.labelMedium,
                color = theme.warning,
                fontWeight = FontWeight.Bold
            )
            if (questionData.questions.size > 1) {
                Text(
                    text = "(${currentQuestionIndex + 1}/${questionData.questions.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textMuted
                )
            }
        }
        
        currentQuestion?.let { question ->
            // Question header (short label)
            Text(
                text = question.header,
                style = MaterialTheme.typography.titleSmall,
                color = theme.text,
                fontWeight = FontWeight.Bold
            )
            
            // Full question text
            Text(
                text = question.question,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textMuted
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Options
            InlineQuestionOptions(
                question = question,
                selectedOptions = answers[currentQuestionIndex] ?: emptyList(),
                onSelectionChange = { selected ->
                    answers[currentQuestionIndex] = selected
                }
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            if (currentQuestionIndex > 0) {
                OutlinedButton(
                    onClick = { currentQuestionIndex-- },
                    modifier = Modifier.weight(1f),
                    shape = RectangleShape
                ) {
                    Text(stringResource(R.string.previous))
                }
            } else {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RectangleShape
                ) {
                    Text(stringResource(R.string.skip))
                }
            }
            
            Button(
                onClick = {
                    if (isLastQuestion) {
                        val allAnswers = questionData.questions.indices.map { idx ->
                            answers[idx] ?: emptyList()
                        }
                        onSubmit(allAnswers)
                    } else {
                        currentQuestionIndex++
                    }
                },
                enabled = hasAnswer,
                modifier = Modifier.weight(1f),
                shape = RectangleShape
            ) {
                Text(if (isLastQuestion) stringResource(R.string.submit) else stringResource(R.string.next))
            }
        }
    }
}

@Composable
private fun InlineQuestionOptions(
    question: Question,
    selectedOptions: List<String>,
    onSelectionChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    var customAnswer by remember { mutableStateOf("") }
    var showCustomInput by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (question.multiple) Modifier else Modifier.selectableGroup()),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            question.options.forEach { option ->
            InlineOptionItem(
                option = option,
                isSelected = option.label in selectedOptions,
                isMultiple = question.multiple,
                onClick = {
                    val newSelection = if (question.multiple) {
                        if (option.label in selectedOptions) {
                            selectedOptions - option.label
                        } else {
                            selectedOptions + option.label
                        }
                    } else {
                        listOf(option.label)
                    }
                    onSelectionChange(newSelection)
                    showCustomInput = false
                    customAnswer = ""
                }
            )
        }
        
        // Custom answer option
        if (showCustomInput) {
            OutlinedTextField(
                value = customAnswer,
                onValueChange = { 
                    customAnswer = it
                    if (it.isNotBlank()) {
                        onSelectionChange(listOf(it))
                    }
                },
                placeholder = { Text(stringResource(R.string.type_your_answer)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minLines = 1,
                maxLines = 3,
                shape = RectangleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = theme.primary,
                    unfocusedBorderColor = theme.border
                )
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        showCustomInput = true
                        onSelectionChange(emptyList())
                    }
                    .background(theme.backgroundElement)
                    .border(1.dp, theme.borderSubtle, RectangleShape)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.mdLg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.other),
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.textMuted
                )
            }
        }
    }
}

@Composable
private fun InlineOptionItem(
    option: QuestionOption,
    isSelected: Boolean,
    isMultiple: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    val bgColor = if (isSelected) theme.primary.copy(alpha = 0.15f) else theme.backgroundElement
    val borderColor = if (isSelected) theme.primary else theme.borderSubtle
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = if (isMultiple) Role.Checkbox else Role.RadioButton
            )
            .background(bgColor)
            .border(1.dp, borderColor, RectangleShape)
            .padding(horizontal = Spacing.lg, vertical = Spacing.mdLg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.mdLg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isMultiple) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = null,
                modifier = Modifier.size(Sizing.iconMd)
            )
        } else {
            RadioButton(
                selected = isSelected,
                onClick = null,
                modifier = Modifier.size(Sizing.iconMd)
            )
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.text,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
            if (option.description.isNotBlank()) {
                Text(
                    text = option.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMuted
                )
            }
        }
        
        if (isSelected && !isMultiple) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = stringResource(R.string.cd_selected),
                tint = theme.primary,
                modifier = Modifier.size(Sizing.iconSm)
            )
        }
    }
}
