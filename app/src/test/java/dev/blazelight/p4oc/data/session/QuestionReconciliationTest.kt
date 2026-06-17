package dev.blazelight.p4oc.data.session

import dev.blazelight.p4oc.data.remote.dto.QuestionDto
import dev.blazelight.p4oc.data.remote.dto.QuestionOptionDto
import dev.blazelight.p4oc.data.remote.dto.QuestionRequestDto
import dev.blazelight.p4oc.data.remote.dto.QuestionToolRefDto
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.model.QuestionRequest
import dev.blazelight.p4oc.domain.model.Session
import dev.blazelight.p4oc.domain.model.ToolState
import dev.blazelight.p4oc.domain.session.SessionId
import dev.blazelight.p4oc.fakes.FakeWorkspaceClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuestionReconciliationTest {

    @Test
    fun `running question tool part triggers reconciliation and sets pendingQuestion`() = runTest {
        val sessionId = "sess_001"
        val callId = "call_001"
        val questionId = "que_001"
        val questionDto = buildQuestionRequestDto(questionId, sessionId, callId)
        var fetchCount = 0

        val client = FakeWorkspaceClient()
        val repository = SessionRepositoryImpl(
            client,
            nowMs = { testScheduler.currentTime },
            dispatcher = StandardTestDispatcher(testScheduler),
            questionFetcher = {
                fetchCount++
                listOf(questionDto)
            }
        )

        // Initialize with a session
        val session = Session(
            id = sessionId,
            projectID = "proj_001",
            directory = "/test",
            parentID = null,
            title = "Test Session",
            version = "1",
            createdAt = 0L,
            updatedAt = 0L,
            compactingAt = null,
            summary = null,
            shareUrl = null,
            revert = null
        )
        repository.acceptEvent(OpenCodeEvent.SessionCreated(session))

        // Simulate a running question tool part
        val toolPart = Part.Tool(
            id = "part_001",
            sessionID = sessionId,
            messageID = "msg_001",
            callID = callId,
            toolName = "question",
            state = ToolState.Running(
                input = buildJsonObject {},
                title = "Asking question",
                startedAt = testScheduler.currentTime,
                metadata = null
            )
        )
        repository.acceptEvent(
            OpenCodeEvent.MessagePartUpdated(toolPart, null)
        )
        advanceUntilIdle()

        // Verify pendingQuestion was set via reconciliation
        val sessionState = repository.sessionUiState(SessionId(sessionId))
        assertNotNull("pendingQuestion should be set", sessionState.value.pendingQuestion)
        assertEquals(questionId, sessionState.value.pendingQuestion?.id)
        assertEquals(1, fetchCount)
    }

    @Test
    fun `anti_thrash same callID does not trigger multiple fetches`() = runTest {
        val sessionId = "sess_001"
        val callId = "call_001"
        val questionId = "que_001"
        val questionDto = buildQuestionRequestDto(questionId, sessionId, callId)
        var fetchCount = 0

        val client = FakeWorkspaceClient()
        val repository = SessionRepositoryImpl(
            client,
            nowMs = { testScheduler.currentTime },
            dispatcher = StandardTestDispatcher(testScheduler),
            questionFetcher = {
                fetchCount++
                listOf(questionDto)
            }
        )

        val session = Session(
            id = sessionId,
            projectID = "proj_001",
            directory = "/test",
            parentID = null,
            title = "Test Session",
            version = "1",
            createdAt = 0L,
            updatedAt = 0L,
            compactingAt = null,
            summary = null,
            shareUrl = null,
            revert = null
        )
        repository.acceptEvent(OpenCodeEvent.SessionCreated(session))

        // Simulate two Running updates with the same callID
        repeat(2) {
            val toolPart = Part.Tool(
                id = "part_00${it + 1}",
                sessionID = sessionId,
                messageID = "msg_001",
                callID = callId,
                toolName = "question",
                state = ToolState.Running(
                    input = buildJsonObject {},
                    title = "Asking question",
                    startedAt = testScheduler.currentTime,
                    metadata = null
                )
            )
            repository.acceptEvent(
                OpenCodeEvent.MessagePartUpdated(toolPart, null)
            )
        }
        advanceUntilIdle()

        // Should fetch only once despite two Running parts
        assertEquals("fetchCount should be 1", 1, fetchCount)
    }

    @Test
    fun `anti_resurrection recently resolved question is not re_surfaced on sweep`() = runTest {
        val sessionId = "sess_001"
        val questionId = "que_001"
        val questionDto = buildQuestionRequestDto(questionId, sessionId, null)
        var fetchCount = 0

        val client = FakeWorkspaceClient()
        val repository = SessionRepositoryImpl(
            client,
            nowMs = { testScheduler.currentTime },
            dispatcher = StandardTestDispatcher(testScheduler),
            questionFetcher = {
                fetchCount++
                listOf(questionDto)
            }
        )

        val session = Session(
            id = sessionId,
            projectID = "proj_001",
            directory = "/test",
            parentID = null,
            title = "Test Session",
            version = "1",
            createdAt = 0L,
            updatedAt = 0L,
            compactingAt = null,
            summary = null,
            shareUrl = null,
            revert = null
        )
        repository.acceptEvent(OpenCodeEvent.SessionCreated(session))

        // Simulate receiving a question.replied event (marks as resolved)
        repository.acceptEvent(
            OpenCodeEvent.QuestionReplied(sessionId, questionId, emptyList())
        )
        advanceUntilIdle()

        var sessionState = repository.sessionUiState(SessionId(sessionId))
        assertNull("pendingQuestion should be cleared after reply", sessionState.value.pendingQuestion)

        // Now simulate a reconnect trigger that would fetch the same question
        // The question should NOT be re-set due to anti-resurrection dedup
        repository.acceptEvent(OpenCodeEvent.Connected)
        advanceUntilIdle()

        sessionState = repository.sessionUiState(SessionId(sessionId))
        assertNull("pendingQuestion should not be re-surfaced within TTL", sessionState.value.pendingQuestion)
        // fetchCount should be 1 (from the Connected event reconciliation)
        assertEquals(1, fetchCount)
    }

    @Test
    fun `reconnect sweep hydrates pendingQuestion`() = runTest {
        val sessionId = "sess_001"
        val questionId = "que_001"
        val questionDto = buildQuestionRequestDto(questionId, sessionId, null)
        var fetchCount = 0

        val client = FakeWorkspaceClient()
        val repository = SessionRepositoryImpl(
            client,
            nowMs = { testScheduler.currentTime },
            dispatcher = StandardTestDispatcher(testScheduler),
            questionFetcher = {
                fetchCount++
                listOf(questionDto)
            }
        )

        val session = Session(
            id = sessionId,
            projectID = "proj_001",
            directory = "/test",
            parentID = null,
            title = "Test Session",
            version = "1",
            createdAt = 0L,
            updatedAt = 0L,
            compactingAt = null,
            summary = null,
            shareUrl = null,
            revert = null
        )
        repository.acceptEvent(OpenCodeEvent.SessionCreated(session))

        // Initially no pending question
        var sessionState = repository.sessionUiState(SessionId(sessionId))
        assertNull("initially no pendingQuestion", sessionState.value.pendingQuestion)

        // Simulate reconnect which triggers question reconciliation
        repository.acceptEvent(OpenCodeEvent.Connected)
        advanceUntilIdle()

        // Question should now be set
        sessionState = repository.sessionUiState(SessionId(sessionId))
        assertNotNull("pendingQuestion should be set after reconnect", sessionState.value.pendingQuestion)
        assertEquals(questionId, sessionState.value.pendingQuestion?.id)
        assertEquals(1, fetchCount)
    }

    @Test
    fun `clearQuestion with requestId records dedup id`() = runTest {
        val sessionId = "sess_001"
        val questionId = "que_001"
        val questionDto = buildQuestionRequestDto(questionId, sessionId, null)
        var fetchCount = 0

        val client = FakeWorkspaceClient()
        val repository = SessionRepositoryImpl(
            client,
            nowMs = { testScheduler.currentTime },
            dispatcher = StandardTestDispatcher(testScheduler),
            questionFetcher = {
                fetchCount++
                listOf(questionDto)
            }
        )

        val session = Session(
            id = sessionId,
            projectID = "proj_001",
            directory = "/test",
            parentID = null,
            title = "Test Session",
            version = "1",
            createdAt = 0L,
            updatedAt = 0L,
            compactingAt = null,
            summary = null,
            shareUrl = null,
            revert = null
        )
        repository.acceptEvent(OpenCodeEvent.SessionCreated(session))

        // Set a pending question via event
        repository.acceptEvent(
            OpenCodeEvent.QuestionAsked(
                QuestionRequest(
                    id = questionId,
                    sessionID = sessionId,
                    questions = emptyList(),
                    tool = null
                )
            )
        )
        advanceUntilIdle()

        var sessionState = repository.sessionUiState(SessionId(sessionId))
        assertNotNull("pendingQuestion should be set", sessionState.value.pendingQuestion)

        // Clear with requestId (dismissQuestion path)
        repository.clearQuestion(SessionId(sessionId), questionId)
        sessionState = repository.sessionUiState(SessionId(sessionId))
        assertNull("pendingQuestion should be cleared", sessionState.value.pendingQuestion)

        // Now trigger a reconciliation - the question should NOT be re-set
        // because it was recorded in the dedup map
        repository.acceptEvent(OpenCodeEvent.Connected)
        advanceUntilIdle()

        sessionState = repository.sessionUiState(SessionId(sessionId))
        assertNull(
            "pendingQuestion should not be re-surfaced after clearQuestion with id",
            sessionState.value.pendingQuestion
        )
    }

    // Helper functions
    private fun buildQuestionRequestDto(
        id: String,
        sessionId: String,
        callId: String?
    ): QuestionRequestDto {
        return QuestionRequestDto(
            id = id,
            sessionID = sessionId,
            questions = listOf(
                QuestionDto(
                    header = "Test",
                    question = "Do you want to continue?",
                    options = listOf(
                        QuestionOptionDto(label = "Yes", description = "Continue"),
                        QuestionOptionDto(label = "No", description = "Stop")
                    ),
                    multiple = false,
                    custom = true
                )
            ),
            tool = callId?.let { QuestionToolRefDto(messageID = "msg_001", callID = it) }
        )
    }
}
