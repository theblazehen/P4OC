# Question Tool Implementation Summary

## Overview

Complete implementation of the OpenCode question tool for the Android client. This allows the LLM to ask interactive questions during task execution, enabling the user to provide preferences, clarify requirements, or make implementation choices.

## Implementation Details

### 1. Data Models (`Question.kt`)

**Core Types:**
- `QuestionRequest` - Full question request with ID, session, and questions array
- `Question` - Individual question with header, text, options, and flags
- `QuestionOption` - Answer option with label and description
- `QuestionReply` - Response format with nested arrays of selected labels
- `QuestionData` - Wrapper for UI layer containing list of questions

**Schema Alignment:**
- Fully aligned with OpenCode server spec at https://opencode.ai/docs/server/
- Matches TypeScript SDK types from anomalyco/opencode repository
- Supports both single and multi-select questions
- Allows custom text input when `custom: true` (default)

### 2. Event System

**New Event Type:**
- Added `OpenCodeEvent.QuestionAsked(request: QuestionRequest)` to `Event.kt`
- Integrated into `EventMapper.kt` to handle `question.asked` events from server
- Maps from `QuestionRequestDto` to domain model `QuestionRequest`

### 3. API Layer

**New Endpoint:**
```kotlin
@POST("session/{sessionId}/questions/{questionId}")
suspend fun respondToQuestion(
    @Path("sessionId") sessionId: String,
    @Path("questionId") questionId: String,
    @Body request: QuestionReplyRequest
): Boolean
```

**DTOs Added:**
- `QuestionRequestDto` - Server event payload
- `QuestionDto`, `QuestionOptionDto`, `QuestionToolRefDto` - Nested types
- `QuestionReplyRequest` - Answer submission format

### 4. ViewModel Integration (`ChatViewModel.kt`)

**State Management:**
- Added `pendingQuestion: QuestionRequest?` to `ChatUiState`
- Sets state when `QuestionAsked` event received
- Clears state after response or dismissal

**Methods:**
```kotlin
fun respondToQuestion(questionId: String, answers: List<List<String>>)
fun dismissQuestion()
```

**Event Handling:**
- Detects `question.asked` events for current session
- Updates `pendingQuestion` state to trigger UI

### 5. UI Components

**QuestionDialog** (`ui/components/question/QuestionDialog.kt`):
- Full-screen modal dialog for answering questions
- Multi-question support with navigation (Previous/Next buttons)
- Progress indicator for multiple questions
- Single-select (radio buttons) and multi-select (checkboxes) modes
- Custom answer input option
- Material Design 3 styling

**Features:**
- Validates answers before allowing progression
- Stores answers per question in state map
- Formats final response as `List<List<String>>`
- Cancel/dismiss functionality

**ChatScreen Integration:**
- Shows `QuestionDialog` when `pendingQuestion` is set
- Passes answers to `viewModel.respondToQuestion()`
- Auto-dismisses on submission

### 6. Extension Functions (`ToolStateExt.kt`)

**Tool State Parsing:**
```kotlin
fun ToolState.asQuestionData(): QuestionData?
fun Part.Tool.isQuestionTool(): Boolean
fun Part.Tool.getQuestionData(): QuestionData?
```

Allows parsing question data from tool parts (for alternative detection method).

## API Flow

### Question Request Flow

1. **Server sends event:**
   ```json
   {
     "type": "question.asked",
     "properties": {
       "id": "question-123",
       "sessionID": "session-456",
       "questions": [
         {
           "header": "Action",
           "question": "What would you like to do?",
           "options": [
             { "label": "Continue", "description": "Proceed with implementation" },
             { "label": "Refactor", "description": "Improve code structure" }
           ],
           "multiple": false,
           "custom": true
         }
       ]
     }
   }
   ```

2. **Android client receives and maps event**
3. **Updates `ChatUiState.pendingQuestion`**
4. **`QuestionDialog` renders**
5. **User selects answers and submits**
6. **Client sends response:**
   ```json
   {
     "answers": [
       ["Continue"]
     ]
   }
   ```

7. **Server receives and tool completes**

## Files Modified/Created

### Created Files:
- `app/src/main/java/com/pocketcode/domain/model/Question.kt`
- `app/src/main/java/com/pocketcode/domain/model/ToolStateExt.kt`
- `app/src/main/java/com/pocketcode/ui/components/question/QuestionDialog.kt`

### Modified Files:
- `app/src/main/java/com/pocketcode/domain/model/Event.kt`
  - Added `QuestionAsked` event type

- `app/src/main/java/com/pocketcode/data/remote/dto/Dtos.kt`
  - Added `QuestionRequestDto`, `QuestionDto`, `QuestionOptionDto`
  - Added `QuestionToolRefDto`, `QuestionReplyRequest`

- `app/src/main/java/com/pocketcode/core/network/OpenCodeApi.kt`
  - Added `respondToQuestion()` endpoint

- `app/src/main/java/com/pocketcode/data/remote/mapper/Mappers.kt`
  - Added `"question.asked"` event mapping in `EventMapper`

- `app/src/main/java/com/pocketcode/ui/screens/chat/ChatViewModel.kt`
  - Added `pendingQuestion` to `ChatUiState`
  - Added `respondToQuestion()` and `dismissQuestion()` methods
  - Added `QuestionAsked` event handling

- `app/src/main/java/com/pocketcode/ui/screens/chat/ChatScreen.kt`
  - Added `QuestionDialog` import
  - Added dialog rendering when `pendingQuestion` is set

## Testing Recommendations

### Unit Tests:
1. Test question parsing from JSON in `ToolStateExt`
2. Test event mapping in `EventMapper`
3. Test ViewModel question handling and state management

### UI Tests:
1. Test QuestionDialog with single question
2. Test QuestionDialog with multiple questions
3. Test single-select vs multi-select behavior
4. Test custom answer input
5. Test navigation between questions

### Integration Tests:
1. Test end-to-end question flow from event to response
2. Test concurrent permission + question handling
3. Test question during active session

## Usage Example

```kotlin
// LLM asks a question via the question tool
// Event received: question.asked

// Android displays dialog automatically
// User selects answers and clicks Submit

// Response sent to server:
POST /session/{sessionId}/questions/{questionId}
{
  "answers": [
    ["Selected Option"],  // Question 1 answer
    ["Option A", "Option B"]  // Question 2 answers (multi-select)
  ]
}
```

## Alignment with OpenCode Spec

✅ **Fully compliant** with:
- OpenCode server API specification
- TypeScript SDK types (`types.gen.ts`)
- Question tool schema from `packages/opencode/src/question/`
- Event bus patterns (`question.asked`, `question.replied`)

## Key Design Decisions

1. **Event-driven approach**: Questions come via `question.asked` events (not tool state polling)
2. **Nested answer arrays**: Each question's answers are an array, supporting multi-select
3. **State management**: Mirror `pendingPermission` pattern for consistency
4. **UI auto-show**: Dialog appears immediately when question received
5. **Custom input**: Always available as fallback option
6. **Navigation**: Users can go back to revise earlier answers

## Future Enhancements

- [ ] Remember/cache commonly selected options
- [ ] Support for question priorities (ask critical questions first)
- [ ] Timeout handling for abandoned questions
- [ ] Analytics on question response patterns
- [ ] Voice input for answers
