package com.pocketcode.ui.components.question

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pocketcode.domain.model.Question
import com.pocketcode.domain.model.QuestionData
import com.pocketcode.domain.model.QuestionOption

@Composable
fun QuestionDialog(
    questionData: QuestionData,
    onDismiss: () -> Unit,
    onSubmit: (List<List<String>>) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentQuestionIndex by remember { mutableIntStateOf(0) }
    val answers = remember {
        mutableStateMapOf<Int, List<String>>()
    }
    
    val currentQuestion = questionData.questions.getOrNull(currentQuestionIndex)
    val isLastQuestion = currentQuestionIndex == questionData.questions.lastIndex
    val hasAnswer = answers[currentQuestionIndex]?.isNotEmpty() == true
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                if (questionData.questions.size > 1) {
                    LinearProgressIndicator(
                        progress = { (currentQuestionIndex + 1f) / questionData.questions.size },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Question ${currentQuestionIndex + 1} of ${questionData.questions.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                currentQuestion?.let { question ->
                    Text(
                        text = question.header,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = question.question,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    QuestionOptions(
                        question = question,
                        selectedOptions = answers[currentQuestionIndex] ?: emptyList(),
                        onSelectionChange = { selected ->
                            answers[currentQuestionIndex] = selected
                        },
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (currentQuestionIndex > 0) {
                        OutlinedButton(
                            onClick = { currentQuestionIndex-- },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Previous")
                        }
                    } else {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
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
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isLastQuestion) "Submit" else "Next")
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionOptions(
    question: Question,
    selectedOptions: List<String>,
    onSelectionChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var customAnswer by remember { mutableStateOf("") }
    val showCustomInput = remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .then(
                if (question.multiple) Modifier else Modifier.selectableGroup()
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        question.options.forEach { option ->
            QuestionOptionItem(
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
                    showCustomInput.value = false
                    customAnswer = ""
                }
            )
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        
        if (showCustomInput.value) {
            OutlinedTextField(
                value = customAnswer,
                onValueChange = { 
                    customAnswer = it
                    if (it.isNotBlank()) {
                        onSelectionChange(listOf(it))
                    }
                },
                label = { Text("Custom answer") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minLines = 2,
                maxLines = 4
            )
        } else {
            OutlinedButton(
                onClick = { 
                    showCustomInput.value = true
                    onSelectionChange(emptyList())
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Other (provide custom answer)")
            }
        }
    }
}

@Composable
private fun QuestionOptionItem(
    option: QuestionOption,
    isSelected: Boolean,
    isMultiple: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = if (isMultiple) Role.Checkbox else Role.RadioButton
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (isMultiple) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null
                )
            } else {
                RadioButton(
                    selected = isSelected,
                    onClick = null
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (option.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = option.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (isSelected && !isMultiple) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
