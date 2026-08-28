package dev.blazelight.p4oc.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import dev.blazelight.p4oc.core.datastore.ChatSettings
import dev.blazelight.p4oc.core.datastore.NotificationSettings
import dev.blazelight.p4oc.core.datastore.SessionComposerSelection
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.core.datastore.VisualSettings
import dev.blazelight.p4oc.core.haptic.HapticFeedback
import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.core.network.ConnectionState
import dev.blazelight.p4oc.core.network.OpenCodeApi
import dev.blazelight.p4oc.data.files.FileRepository
import dev.blazelight.p4oc.data.files.FileRepositoryFactory
import dev.blazelight.p4oc.data.remote.dto.CommandDto
import dev.blazelight.p4oc.data.remote.dto.ExecuteCommandRequest
import dev.blazelight.p4oc.data.remote.dto.FileNodeDto
import dev.blazelight.p4oc.data.remote.dto.InitSessionRequest
import dev.blazelight.p4oc.data.remote.dto.MessageInfoDto
import dev.blazelight.p4oc.data.remote.dto.MessageTimeDto
import dev.blazelight.p4oc.data.remote.dto.MessageWrapperDto
import dev.blazelight.p4oc.data.remote.dto.ModelRefDto
import dev.blazelight.p4oc.data.remote.dto.PartDto
import dev.blazelight.p4oc.data.remote.dto.ProvidersResponseDto
import dev.blazelight.p4oc.data.remote.dto.RevertSessionRequest
import dev.blazelight.p4oc.data.remote.dto.SendMessageRequest
import dev.blazelight.p4oc.data.remote.dto.SessionDto
import dev.blazelight.p4oc.data.remote.dto.SessionModelDto
import dev.blazelight.p4oc.data.remote.dto.SessionRevertDto
import dev.blazelight.p4oc.data.remote.dto.SessionStatusDto
import dev.blazelight.p4oc.data.remote.dto.TimeDto
import dev.blazelight.p4oc.data.remote.mapper.MessageMapper
import dev.blazelight.p4oc.data.server.ActiveServerApiProvider
import dev.blazelight.p4oc.data.session.SessionRepositoryImpl
import dev.blazelight.p4oc.data.workspace.WorkspaceClient
import dev.blazelight.p4oc.domain.model.CommandSource
import dev.blazelight.p4oc.domain.model.Message
import dev.blazelight.p4oc.domain.model.MessageError
import dev.blazelight.p4oc.domain.model.MessageWithParts
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.model.Permission
import dev.blazelight.p4oc.domain.model.Question
import dev.blazelight.p4oc.domain.model.QuestionRequest
import dev.blazelight.p4oc.domain.model.Session
import dev.blazelight.p4oc.domain.model.SessionPresence
import dev.blazelight.p4oc.domain.model.SessionStatus
import dev.blazelight.p4oc.domain.model.Todo
import dev.blazelight.p4oc.domain.model.TokenUsage
import dev.blazelight.p4oc.domain.server.ScopedEvent
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.domain.session.SessionId
import dev.blazelight.p4oc.domain.workspace.Workspace
import dev.blazelight.p4oc.ui.components.chat.SelectedFile
import dev.blazelight.p4oc.ui.navigation.Screen
import dev.blazelight.p4oc.ui.screens.files.upload.UploadCoordinator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass")
class ChatViewModelTest {

    @get:Rule
    val mainDispatcherRule = ChatViewModelMainDispatcherRule()

    private lateinit var messageMapper: MessageMapper
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var events: MutableSharedFlow<ScopedEvent>
    private lateinit var api: OpenCodeApi
    private lateinit var workspaceClient: WorkspaceClient
    private lateinit var sessionRepository: SessionRepositoryImpl
    private lateinit var hapticFeedback: HapticFeedback

    @Before
    fun setUp() {
        mockkObject(AppLog)
        every { AppLog.d(any(), any<String>()) } returns Unit
        every { AppLog.d(any(), any<() -> String>()) } returns Unit
        every { AppLog.v(any(), any<String>()) } returns Unit
        every { AppLog.v(any(), any<() -> String>()) } returns Unit
        every { AppLog.i(any(), any<String>()) } returns Unit
        every { AppLog.i(any(), any<() -> String>()) } returns Unit
        every { AppLog.w(any(), any<String>()) } returns Unit
        every { AppLog.w(any(), any<String>(), any()) } returns Unit
        every { AppLog.e(any(), any<String>()) } returns Unit
        every { AppLog.e(any(), any<String>(), any()) } returns Unit

        messageMapper = MessageMapper()
        settingsDataStore = mockk()
        events = MutableSharedFlow(extraBufferCapacity = 32)
        api = mockk(relaxed = true)
        workspaceClient = WorkspaceClient(
            workspace = Workspace(
                server = ServerRef.fromEndpointKey("http://test.local"),
                directory = "/test",
            ),
            generation = ServerGeneration(0L),
            apiProvider = ActiveServerApiProvider { _, _ -> api },
            connectionState = MutableStateFlow(ConnectionState.Disconnected),
        )
        every { settingsDataStore.favoriteModels } returns flowOf(emptySet())
        every { settingsDataStore.recentModels } returns flowOf(emptyList())
        every { settingsDataStore.chatSettings } returns flowOf(ChatSettings())
        every { settingsDataStore.notificationSettings } returns flowOf(NotificationSettings())
        every { settingsDataStore.visualSettings } returns flowOf(VisualSettings())
        coEvery { settingsDataStore.getComposerSelectionForSession(any(), any()) } returns null
        coEvery { settingsDataStore.setComposerSelectionForSession(any(), any(), any()) } returns Unit
        coEvery { settingsDataStore.addRecentModel(any()) } returns Unit
        coEvery { settingsDataStore.getSelectedAgentForSession(any()) } returns null
        coEvery { settingsDataStore.setSelectedAgentForSession(any(), any()) } returns Unit

        hapticFeedback = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkObject(AppLog)
    }

    @Test
    fun handleEvent_routesMessageUpdated_toRepositoryMessages() = runTest {
        val vm = createViewModel()
        val message = assistantMessage(id = "m1", sessionId = "session-1", createdAt = 10)

        emitEvent(OpenCodeEvent.MessageUpdated(message))
        flushMessages()

        assertEquals(listOf("m1"), vm.currentMessages().map { it.message.id })
    }

    @Test
    fun initialHistoryLoad_isBounded() = runTest {
        coEvery { api.getMessages("session-1", 100, null, "/test", null) } returns emptyList()

        createViewModel()

        coVerify(exactly = 1) { api.getMessages("session-1", 100, null, "/test", null) }
        coVerify(exactly = 0) { api.getMessages("session-1", null, null, any(), null) }
    }

    @Test
    fun loadOlderMessages_increasesBoundAndPreservesChronologicalHistory() = runTest {
        val newest = (101L..200L).map { assistantMessageDto("m$it", createdAt = it) }
        val expanded = (1L..200L).map { assistantMessageDto("m$it", createdAt = it) }
        coEvery { api.getMessages("session-1", 100, null, "/test", null) } returns newest
        coEvery { api.getMessages("session-1", 200, null, "/test", null) } returns expanded
        val vm = createViewModel()

        assertTrue(vm.uiState.value.hasOlderMessages)
        vm.loadOlderMessages()
        advanceUntilIdle()

        coVerify(exactly = 1) { api.getMessages("session-1", 200, null, "/test", null) }
        assertEquals((1L..200L).map { "m$it" }, vm.currentMessages().map { it.message.id })
        assertTrue(vm.uiState.value.hasOlderMessages)
    }

    @Test
    fun loadOlderMessages_doesNotOverwriteMessageDeliveredDuringHistoryWindow() = runTest {
        val newest = (101L..200L).map { assistantMessageDto("m$it", createdAt = it) }
        val expanded = (1L..200L).map { assistantMessageDto("m$it", createdAt = it) }
        coEvery { api.getMessages("session-1", 100, null, "/test", null) } returns newest
        coEvery { api.getMessages("session-1", 200, null, "/test", null) } returns expanded
        val vm = createViewModel()
        sessionRepository.acceptEvent(
            OpenCodeEvent.MessageUpdated(assistantMessage("m200", "session-1", createdAt = 999))
        )

        vm.loadOlderMessages()
        advanceUntilIdle()

        val raced = vm.currentMessages().filter { it.message.id == "m200" }
        assertEquals(1, raced.size)
        assertEquals(999, raced.single().message.createdAt)
    }

    @Test
    fun handleEvent_routesPermissionRequested_toDialogQueueManager() = runTest {
        val vm = createViewModel()
        val permission = permission(id = "perm-1", sessionId = "session-1")

        emitEvent(OpenCodeEvent.PermissionRequested(permission))
        advanceUntilIdle()

        assertEquals(permission, vm.dialogManager.pendingPermissionsByCallId.value[permission.callID])
    }

    @Test
    fun handleEvent_filtersEventsBySessionId() = runTest {
        val vm = createViewModel()

        emitEvent(OpenCodeEvent.MessageUpdated(assistantMessage(id = "m-x", sessionId = "other", createdAt = 10)))
        emitEvent(OpenCodeEvent.PermissionRequested(permission(id = "perm-x", sessionId = "other")))
        flushMessages()

        assertTrue(vm.currentMessages().isEmpty())
        assertTrue(vm.dialogManager.pendingPermissionsByCallId.value.isEmpty())
    }

    @Test
    fun handleEvent_routesSubagentPermission_afterSessionCreated() = runTest {
        val vm = createViewModel()

        // Register a child session via SessionCreated
        val childSession = testSession(id = "child-1", parentID = "session-1")
        emitEvent(OpenCodeEvent.SessionCreated(childSession))
        advanceUntilIdle()

        // Emit permission from the child session
        val perm = permission(id = "perm-child", sessionId = "child-1")
        emitEvent(OpenCodeEvent.PermissionRequested(perm))
        advanceUntilIdle()

        assertEquals(perm, vm.dialogManager.pendingPermissionsByCallId.value[perm.callID])
    }

    @Test
    fun handleEvent_routesSubagentQuestion_afterSessionCreated() = runTest {
        val vm = createViewModel()

        // Register a child session via SessionCreated
        val childSession = testSession(id = "child-2", parentID = "session-1")
        emitEvent(OpenCodeEvent.SessionCreated(childSession))
        advanceUntilIdle()

        // Emit question from the child session
        val question = questionRequest(id = "q-child", sessionId = "child-2")
        emitEvent(OpenCodeEvent.QuestionAsked(question))
        advanceUntilIdle()

        assertEquals(question, vm.dialogManager.pendingQuestion.value)
    }

    @Test
    fun handleEvent_doesNotRoutePermission_forUnrelatedSession() = runTest {
        val vm = createViewModel()

        // Emit permission from an unrelated session (not parent, not child)
        val perm = permission(id = "perm-rando", sessionId = "random-session-999")
        emitEvent(OpenCodeEvent.PermissionRequested(perm))
        advanceUntilIdle()

        assertTrue(vm.dialogManager.pendingPermissionsByCallId.value.isEmpty())
    }

    @Test
    fun sessionStatusChanged_busy_setsIsBusyTrue() = runTest {
        val vm = createViewModel()

        emitEvent(OpenCodeEvent.SessionStatusChanged("session-1", SessionStatus.Busy))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isBusy)
    }

    @Test
    fun sessionStatusChanged_usageLimitRetry_explainsWhyRunIsWaiting() = runTest {
        val vm = createViewModel()

        emitEvent(
            OpenCodeEvent.SessionStatusChanged(
                "session-1",
                SessionStatus.Retry(
                    attempt = 2,
                    message = "Free usage exceeded, subscribe to Go",
                    next = 0L,
                ),
            )
        )
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isBusy)
        assertEquals(
            "Model usage limit reached. OpenCode is retrying (attempt 2).",
            vm.uiState.value.runNotice,
        )
    }

    @Test
    fun sessionError_rateLimitExplainsFailure_andClearsBusyState() = runTest {
        val vm = createViewModel()
        emitEvent(OpenCodeEvent.SessionStatusChanged("session-1", SessionStatus.Busy))

        emitEvent(
            OpenCodeEvent.SessionError(
                "session-1",
                MessageError(name = "APIError", message = "Rate limit exceeded", statusCode = 429),
            )
        )
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isBusy)
        assertEquals(
            "Model usage limit reached. Try again later or choose another model.",
            vm.uiState.value.error,
        )
    }

    @Test
    fun sessionStatusChanged_idle_clearsStreamingFlags() = runTest {
        val vm = createViewModel()
        emitEvent(OpenCodeEvent.MessageUpdated(assistantMessage(id = "m1", sessionId = "session-1", createdAt = 1)))
        emitEvent(
            OpenCodeEvent.MessagePartUpdated(
                textPart(id = "p1", messageId = "m1", sessionId = "session-1", text = "Hello"),
                delta = null
            )
        )
        emitEvent(
            OpenCodeEvent.MessagePartUpdated(
                textPart(id = "p1", messageId = "m1", sessionId = "session-1", text = "ignored"),
                delta = " world"
            )
        )
        emitEvent(OpenCodeEvent.SessionStatusChanged("session-1", SessionStatus.Busy))
        flushMessages()

        emitEvent(OpenCodeEvent.SessionStatusChanged("session-1", SessionStatus.Idle))
        flushMessages()

        val text = vm.currentMessages().single().parts.single() as Part.Text
        assertFalse(text.isStreaming)
    }

    @Test
    fun responseCompletion_onActiveTab_doesNotMarkUnread() = runTest {
        val vm = createViewModel()
        vm.markAsRead()

        emitEvent(OpenCodeEvent.SessionStatusChanged("session-1", SessionStatus.Busy))
        emitEvent(OpenCodeEvent.SessionStatusChanged("session-1", SessionStatus.Idle))
        flushMessages()

        assertFalse(vm.hasUnreadResponse.value)
        assertEquals(SessionPresence.IDLE, vm.sessionConnectionState.value)
    }

    @Test
    fun responseCompletion_onInactiveComposedTab_keepsLocalUnreadFlagUntilTabAggregatorMarksUnread() = runTest {
        val vm = createViewModel()
        vm.markAsRead()
        vm.markInactive()

        emitEvent(OpenCodeEvent.SessionStatusChanged("session-1", SessionStatus.Busy))
        emitEvent(OpenCodeEvent.SessionStatusChanged("session-1", SessionStatus.Idle))
        flushMessages()

        assertTrue(vm.hasUnreadResponse.value)
        assertEquals(SessionPresence.IDLE, vm.sessionConnectionState.value)
    }

    @Test
    fun markAsRead_clearsUnreadAfterReturningToTab() = runTest {
        val vm = createViewModel()
        vm.markInactive()
        emitEvent(OpenCodeEvent.SessionStatusChanged("session-1", SessionStatus.Busy))
        emitEvent(OpenCodeEvent.SessionStatusChanged("session-1", SessionStatus.Idle))
        flushMessages()

        vm.markAsRead()
        flushMessages()

        assertFalse(vm.hasUnreadResponse.value)
        assertEquals(SessionPresence.IDLE, vm.sessionConnectionState.value)
    }

    @Test
    fun sendMessage_blankAndEmptySlashReturnFalsePreserveInputAndCallNoSubmissionEndpoints() = runTest {
        val vm = createViewModel()
        val refusedInputs = listOf("  \n  ", "/")

        refusedInputs.forEach { input ->
            vm.updateInput(input)

            assertFalse(vm.sendMessage())
            assertEquals(input, vm.uiState.value.inputText)
        }

        coVerify(exactly = 0) { api.sendMessageAsync(any(), any(), any(), null) }
        coVerify(exactly = 0) { api.executeCommand(any(), any(), any(), null) }
        coVerify(exactly = 0) { api.revertSession(any(), any(), any(), null) }
        coVerify(exactly = 0) { api.unrevertSession(any(), any(), null) }
        coVerify(exactly = 0) { api.initSession(any(), any(), any(), null) }
    }

    @Test
    fun sendMessage_undoSlashCommand_revertsToPreviousUserMessageBoundaryWithoutExecutingCommand() =
        runTest {
            coEvery { api.getSession("session-1", any(), null) } returns sessionDto(revertMessageId = "user-2")
            coEvery { api.getMessages("session-1", any(), null, any(), null) } returns listOf(
                userMessageDto("user-1", createdAt = 1),
                assistantMessageDto("assistant-1", createdAt = 2),
                userMessageDto("user-2", createdAt = 3),
                assistantMessageDto("assistant-2", createdAt = 4),
            )
            coEvery { api.revertSession(any(), any(), any(), null) } returns sessionDto(revertMessageId = "user-1")
            coEvery { api.executeCommand(any(), any(), any(), null) } returns assistantMessageDto(
                "command-response",
                createdAt = 5
            )
            val vm = createViewModel()

            vm.updateInput("/undo")
            assertTrue(vm.sendMessage())
            advanceUntilIdle()

            assertEquals("/undo", vm.uiState.value.inputText)
            coVerify(exactly = 0) { api.executeCommand(any(), any(), any(), null) }
            coVerify(exactly = 1) {
                api.revertSession("session-1", RevertSessionRequest(messageID = "user-1"), "/test", null)
            }
        }

    @Test
    fun sendMessage_redoSlashCommandWithActiveRevert_revertsToNextUserMessageBoundaryWithoutExecutingCommand() =
        runTest {
            coEvery { api.getSession("session-1", any(), null) } returns sessionDto(revertMessageId = "user-1")
            coEvery { api.getMessages("session-1", any(), null, any(), null) } returns listOf(
                userMessageDto("user-1", createdAt = 1),
                assistantMessageDto("assistant-1", createdAt = 2),
                userMessageDto("user-2", createdAt = 3),
                assistantMessageDto("assistant-2", createdAt = 4),
            )
            coEvery { api.revertSession(any(), any(), any(), null) } returns sessionDto(revertMessageId = "user-2")
            coEvery { api.unrevertSession(any(), any(), null) } returns sessionDto(revertMessageId = null)
            coEvery { api.executeCommand(any(), any(), any(), null) } returns assistantMessageDto(
                "command-response",
                createdAt = 5
            )
            val vm = createViewModel()

            vm.updateInput("/redo")
            assertTrue(vm.sendMessage())
            advanceUntilIdle()

            assertEquals("/redo", vm.uiState.value.inputText)
            coVerify(exactly = 0) { api.executeCommand(any(), any(), any(), null) }
            coVerify(exactly = 1) {
                api.revertSession("session-1", RevertSessionRequest(messageID = "user-2"), "/test", null)
            }
            coVerify(exactly = 0) { api.unrevertSession(any(), any(), null) }
        }

    @Test
    fun sendMessage_busySlashCommandsPreserveExactInputShowErrorAndCallNoCommandEndpoints() = runTest {
        val vm = createViewModel()
        emitEvent(OpenCodeEvent.SessionStatusChanged("session-1", SessionStatus.Busy))
        flushMessages()

        val refusedInputs = listOf(
            "/undo",
            "/redo   keep  these arguments ",
            "/init   keep init arguments ",
            "/custom keep  custom arguments ",
        )
        refusedInputs.forEach { input ->
            vm.updateInput(input)

            assertFalse(vm.sendMessage())

            assertEquals(input, vm.uiState.value.inputText)
            assertEquals(
                "Wait for the current run to finish or stop it first.",
                vm.uiState.value.error,
            )
        }

        coVerify(exactly = 0) { api.sendMessageAsync(any(), any(), any(), null) }
        coVerify(exactly = 0) { api.executeCommand(any(), any(), any(), null) }
        coVerify(exactly = 0) { api.revertSession(any(), any(), any(), null) }
        coVerify(exactly = 0) { api.unrevertSession(any(), any(), null) }
        coVerify(exactly = 0) { api.initSession(any(), any(), any(), null) }
    }

    @Test
    fun sendMessage_idleCustomSlashCommandReturnsAcceptedAndDispatchesParsedArguments() = runTest {
        val request = slot<ExecuteCommandRequest>()
        coEvery {
            api.executeCommand("session-1", capture(request), "/test", null)
        } returns assistantMessageDto("command-response", createdAt = 5)
        val vm = createViewModel()
        vm.updateInput("/custom exact  arguments")

        assertTrue(vm.sendMessage())
        runCurrent()

        assertEquals("/custom exact  arguments", vm.uiState.value.inputText)
        assertEquals("custom", request.captured.command)
        assertEquals("exact  arguments", request.captured.arguments)
        coVerify(exactly = 1) { api.executeCommand("session-1", any(), "/test", null) }
    }

    @Test
    fun sendMessage_gatedGenericSlashFailureRestoresExactRawDraftAndSendsTrimmedPayload() = runTest {
        val failureGate = CompletableDeferred<Unit>()
        val request = slot<ExecuteCommandRequest>()
        coEvery { api.executeCommand("session-1", capture(request), "/test", null) } coAnswers {
            failureGate.await()
            throw IOException("boom")
        }
        val vm = createViewModel()
        val submittedDraft = " \t/custom exact  arguments  \n"

        vm.updateInput(submittedDraft)
        assertTrue(vm.sendMessage())
        runCurrent()
        vm.updateInput("")

        failureGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(submittedDraft, vm.uiState.value.inputText)
        assertEquals("custom", request.captured.command)
        assertEquals("exact  arguments", request.captured.arguments)
        assertEquals("Could not execute the command. Try again.", vm.uiState.value.error)
    }

    @Test
    fun sendMessage_missingInitModelReturnsFalseWithoutClearingDraftAndReleasesCommandOwner() = runTest {
        coEvery { api.getSession("session-1", any(), null) } returns sessionDto()
        coEvery { api.getProviders(any(), null) } returns ProvidersResponseDto(
            all = emptyList(),
            default = emptyMap(),
            connected = emptyList(),
        )
        coEvery { api.executeCommand("session-1", any(), "/test", null) } returns assistantMessageDto(
            "command-response",
            createdAt = 5,
        )
        val vm = createViewModel()
        val refusedDraft = "  /init keep exact spacing  \n"

        vm.updateInput(refusedDraft)
        assertFalse(vm.sendMessage())

        assertEquals(refusedDraft, vm.uiState.value.inputText)
        assertFalse(vm.uiState.value.isSending)
        assertEquals("Select a model before initializing the session.", vm.uiState.value.error)
        coVerify(exactly = 0) { api.initSession(any(), any(), any(), null) }

        assertTrue(vm.executeCommand("custom", "after refusal"))
        runCurrent()
        coVerify(exactly = 1) { api.executeCommand("session-1", any(), "/test", null) }
    }

    @Test
    fun sendMessage_noUndoOrRedoBoundaryReturnsFalseWithoutClearingExactDraft() = runTest {
        coEvery { api.getMessages("session-1", any(), null, any(), null) } returns emptyList()
        coEvery { api.executeCommand("session-1", any(), "/test", null) } returns assistantMessageDto(
            "command-response",
            createdAt = 5,
        )
        val vm = createViewModel()
        val refusedDrafts = listOf(
            " \t/undo  \n" to "Nothing to undo",
            "  /redo keep ignored args  " to "Nothing to redo",
        )

        refusedDrafts.forEach { (draft, expectedError) ->
            vm.updateInput(draft)

            assertFalse(vm.sendMessage())

            assertEquals(draft, vm.uiState.value.inputText)
            assertFalse(vm.uiState.value.isSending)
            assertEquals(expectedError, vm.uiState.value.error)
        }
        coVerify(exactly = 0) { api.revertSession(any(), any(), any(), null) }
        coVerify(exactly = 0) { api.unrevertSession(any(), any(), null) }

        assertTrue(vm.executeCommand("custom", "after redo refusal"))
        runCurrent()
        coVerify(exactly = 1) { api.executeCommand("session-1", any(), "/test", null) }
    }

    @Test
    fun sendMessage_acceptsInput_andMarksBusyUntilSseStatus() = runTest {
        val vm = createViewModel()
        coEvery { api.sendMessageAsync(any(), any(), any(), null) } returns Unit
        vm.updateInput("hello")

        assertTrue(vm.sendMessage())
        assertTrue(vm.uiState.value.isSending)

        advanceUntilIdle()
        assertEquals("hello", vm.uiState.value.inputText)
        assertFalse(vm.uiState.value.isSending)
        assertTrue(vm.uiState.value.isBusy)
    }

    @Test
    fun sendMessage_reconcilesEmptyCompletedResponse_whenTerminalSseIsMissing() = runTest {
        coEvery { api.getMessages("session-1", 100, null, "/test", null) } returnsMany listOf(
            emptyList(),
            listOf(
                userMessageDto("user-new", createdAt = 10),
                assistantMessageDto("assistant-empty", createdAt = 11, completedAt = 12),
            ),
        )
        coEvery { api.getSessionStatuses("/test", null) } returns mapOf(
            "session-1" to SessionStatusDto(type = "idle")
        )
        coEvery { api.sendMessageAsync(any(), any(), any(), null) } returns Unit
        val vm = createViewModel()
        vm.updateInput("hello")

        vm.sendMessage()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isBusy)
        assertEquals(
            "The model returned no response. The provider may be unavailable or rate-limited.",
            vm.uiState.value.error,
        )
        assertEquals(listOf("user-new", "assistant-empty"), vm.currentMessages().map { it.message.id })
    }

    @Test
    fun sendMessage_restIdleStatus_doesNotCancelItsOwnMessageRecovery() = runTest {
        // Physical failure sequence: the run's terminal SSE is missed, REST status already says
        // Idle, and the completed-but-empty assistant exists only behind the canonical message
        // fetch. Gate that fetch so the poll is suspended inside it with the Idle status in hand.
        val messagesGate = CompletableDeferred<Unit>()
        var messageCalls = 0
        coEvery { api.getSessionStatuses("/test", null) } returns mapOf(
            "session-1" to SessionStatusDto(type = "idle")
        )
        coEvery { api.getMessages("session-1", 100, null, "/test", null) } coAnswers {
            messageCalls += 1
            if (messageCalls == 1) {
                // The ViewModel's initial history load: nothing on the server yet.
                emptyList()
            } else {
                // The poll's canonical reconciliation fetch: held open so the test can observe
                // whether the poll survives having already seen the REST Idle status.
                messagesGate.await()
                listOf(
                    userMessageDto("user-new", createdAt = 10),
                    assistantMessageDto("assistant-empty", createdAt = 11, completedAt = 12),
                )
            }
        }
        coEvery { api.sendMessageAsync(any(), any(), any(), null) } returns Unit
        val vm = createViewModel()
        vm.updateInput("hello")

        vm.sendMessage()
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
        // The poll must be suspended inside the gated reconciliation fetch with the run still
        // busy. On the pre-fix order the REST Idle was published before this fetch, so by now the
        // responseCompletedToken collector has applied Idle (isBusy=false) and cancelled the poll
        // while it sits at the gate — completing the gate would import nothing.
        assertEquals(2, messageCalls)
        assertTrue(vm.uiState.value.isBusy)
        messagesGate.complete(Unit)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isBusy)
        assertEquals(
            "The model returned no response. The provider may be unavailable or rate-limited.",
            vm.uiState.value.error,
        )
        assertEquals(listOf("user-new", "assistant-empty"), vm.currentMessages().map { it.message.id })
    }

    @Test
    fun sendMessage_cancelsReconciliation_whenRunCompletesViaSse() = runTest {
        coEvery { api.getMessages("session-1", 100, null, "/test", null) } returns emptyList()
        coEvery { api.getSessionStatuses("/test", null) } returns mapOf(
            "session-1" to SessionStatusDto(type = "busy")
        )
        coEvery { api.sendMessageAsync(any(), any(), any(), null) } returns Unit
        val vm = createViewModel()
        vm.updateInput("hello")

        vm.sendMessage()
        // Let the send coroutine start the bounded poll, then advance past the first delay so the
        // first reconciliation iteration runs.
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
        coVerify(exactly = 1) { api.getSessionStatuses("/test", null) }

        // A terminal SSE event completes the run and must cancel the in-flight poll.
        emitEvent(OpenCodeEvent.SessionStatusChanged("session-1", SessionStatus.Idle))
        advanceUntilIdle()

        // No further reconciliation iterations run after the run completes.
        coVerify(exactly = 1) { api.getSessionStatuses("/test", null) }
        assertFalse(vm.uiState.value.isBusy)
    }

    @Test
    fun sendMessage_replacementSend_cancelsPriorReconciliation() = runTest {
        val gate = CompletableDeferred<Unit>()
        var statusCalls = 0
        coEvery { api.getSessionStatuses("/test", null) } coAnswers {
            statusCalls += 1
            if (statusCalls == 1) gate.await()
            mapOf("session-1" to SessionStatusDto(type = "busy"))
        }
        coEvery { api.getMessages("session-1", 100, null, "/test", null) } returns emptyList()
        coEvery { api.sendMessageAsync(any(), any(), any(), null) } returns Unit
        val vm = createViewModel()
        vm.updateInput("hello")

        // First send starts a poll whose first status lookup blocks on the gate.
        vm.sendMessage()
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(1, statusCalls)

        // A replacement send must cancel the in-flight poll at the send boundary.
        vm.updateInput("hello")
        vm.sendMessage()
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        // The old poll was cancelled: it never resumes to a second status lookup. Only the
        // replacement poll's four bounded iterations run (plus the one blocked call).
        assertEquals(5, statusCalls)
    }

    @Test
    fun sendMessage_pollInvokesRepositoryReconcileMessages() = runTest {
        val repo = spyk(
            SessionRepositoryImpl(
                workspaceClient,
                messageMapper,
                dispatcher = StandardTestDispatcher(testScheduler),
            )
        )
        coEvery { repo.reconcileMessages(SessionId("session-1")) } returns Unit
        coEvery { api.getSessionStatuses("/test", null) } returns mapOf(
            "session-1" to SessionStatusDto(type = "busy")
        )
        coEvery { api.getMessages("session-1", 100, null, "/test", null) } returns emptyList()
        coEvery { api.sendMessageAsync(any(), any(), any(), null) } returns Unit
        val vm = createViewModel(repository = repo)
        vm.updateInput("hello")

        vm.sendMessage()
        advanceUntilIdle()

        // The bounded poll drives the shared repository recovery primitive directly, not a second
        // message buffer or an unsafe overwrite path.
        coVerify(atLeast = 1) { repo.reconcileMessages(SessionId("session-1")) }
    }

    @Test
    fun sendMessage_completedIntermediateAssistantWhileBusy_doesNotSynthesizeCompletion() = runTest {
        coEvery { api.getMessages("session-1", 100, null, "/test", null) } returnsMany listOf(
            emptyList(),
            listOf(
                userMessageDto("user-new", createdAt = 10),
                assistantMessageDto(
                    "assistant-step",
                    createdAt = 11,
                    completedAt = 12,
                    parts = listOf(
                        PartDto(
                            id = "p-step",
                            sessionID = "session-1",
                            messageID = "assistant-step",
                            type = "text",
                            text = "intermediate step output",
                        )
                    ),
                ),
            ),
        )
        coEvery { api.getSessionStatuses("/test", null) } returns mapOf(
            "session-1" to SessionStatusDto(type = "busy")
        )
        coEvery { api.sendMessageAsync(any(), any(), any(), null) } returns Unit
        val vm = createViewModel()
        vm.updateInput("hello")

        vm.sendMessage()
        advanceUntilIdle()

        // The authoritative REST status is still Busy, so the completed assistant is an
        // intermediate step of a multi-step run: no synthesized terminal Idle, no completion
        // haptic, and the bounded poll runs its full four-iteration window instead of
        // cancelling itself mid-run.
        assertTrue(vm.uiState.value.isBusy)
        assertNull(vm.uiState.value.error)
        verify(exactly = 0) { hapticFeedback.vibrate(any()) }
        coVerify(exactly = 4) { api.getSessionStatuses("/test", null) }
    }

    @Test
    fun runStalledWarning_survivesUnrelatedRepositoryEmissions_untilTerminalBoundary() = runTest {
        coEvery { api.getMessages("session-1", 100, null, "/test", null) } returns emptyList()
        coEvery { api.getSessionStatuses("/test", null) } returns mapOf(
            "session-1" to SessionStatusDto(type = "busy")
        )
        coEvery { api.sendMessageAsync(any(), any(), any(), null) } returns Unit
        val vm = createViewModel()
        vm.updateInput("hello")

        vm.sendMessage()
        advanceUntilIdle()

        val warning = "No completion update was received. The run may still be active; stop it before retrying."
        assertEquals(warning, vm.uiState.value.runNotice)

        // An unrelated repository emission while the run is still busy (todo progress) must not
        // erase the stalled-run warning.
        emitEvent(
            OpenCodeEvent.TodoUpdated(
                "session-1",
                listOf(Todo(id = "t1", content = "step", status = "pending", priority = "medium")),
            )
        )
        flushMessages()
        assertEquals(warning, vm.uiState.value.runNotice)
        assertTrue(vm.uiState.value.isBusy)

        // The real terminal event finally arrives: the boundary clears the warning.
        emitEvent(OpenCodeEvent.SessionStatusChanged("session-1", SessionStatus.Idle))
        flushMessages()
        assertNull(vm.uiState.value.runNotice)
        assertFalse(vm.uiState.value.isBusy)
    }

    @Test
    fun retryNotice_persistsAcrossUnrelatedEmissions_andClearsWhenRunResumesBusy() = runTest {
        val vm = createViewModel()

        emitEvent(
            OpenCodeEvent.SessionStatusChanged(
                "session-1",
                SessionStatus.Retry(attempt = 1, message = "Rate limit exceeded", next = 0L),
            )
        )
        flushMessages()
        assertEquals(
            "Model usage limit reached. OpenCode is retrying (attempt 1).",
            vm.uiState.value.runNotice,
        )

        // An unrelated emission while the status is still Retry recomputes the same notice —
        // the Retry status, not stickiness, is its source of truth.
        emitEvent(
            OpenCodeEvent.TodoUpdated(
                "session-1",
                listOf(Todo(id = "t1", content = "step", status = "pending", priority = "medium")),
            )
        )
        flushMessages()
        assertEquals(
            "Model usage limit reached. OpenCode is retrying (attempt 1).",
            vm.uiState.value.runNotice,
        )

        // The retry succeeds and the run resumes: a transient Retry notice must not stick.
        emitEvent(OpenCodeEvent.SessionStatusChanged("session-1", SessionStatus.Busy))
        flushMessages()
        assertNull(vm.uiState.value.runNotice)
        assertTrue(vm.uiState.value.isBusy)
    }

    @Test
    fun constructor_withImmediateRepositoryState_adoptsInitialStateWithoutCrash() = runTest {
        val repo = SessionRepositoryImpl(
            workspaceClient,
            messageMapper,
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        repo.acceptEvent(OpenCodeEvent.SessionStatusChanged("session-1", SessionStatus.Busy))
        assertTrue(repo.sessionUiState(SessionId("session-1")).value.status is SessionStatus.Busy)
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))

        val vm = createViewModel(repository = repo)

        assertTrue(vm.uiState.value.isBusy)
    }

    @Test
    fun preexistingCompletionToken_doesNotFireSpuriousCompletionOnAttach() = runTest {
        val repo = SessionRepositoryImpl(
            workspaceClient,
            messageMapper,
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        // A run in this session completed before this ViewModel attached (e.g. another tab
        // holding the same session state), so the repository token is already nonzero.
        repo.acceptEvent(OpenCodeEvent.SessionStatusChanged("session-1", SessionStatus.Busy))
        repo.acceptEvent(OpenCodeEvent.SessionIdle("session-1"))

        val vm = createViewModel(repository = repo)

        // The subscription snapshot is a baseline, not a fresh completion: no haptic, no unread.
        verify(exactly = 0) { hapticFeedback.vibrate(any()) }
        assertFalse(vm.uiState.value.isBusy)
        assertFalse(vm.hasUnreadResponse.value)
    }

    @Test
    fun sendMessage_staleRunError_doesNotFlickerBackDuringGatedSend() = runTest {
        coEvery { api.getMessages("session-1", 100, null, "/test", null) } returns emptyList()
        coEvery { api.getSessionStatuses("/test", null) } returns mapOf(
            "session-1" to SessionStatusDto(type = "busy")
        )
        val sendGate = CompletableDeferred<Unit>()
        coEvery { api.sendMessageAsync(any(), any(), any(), null) } coAnswers { sendGate.await() }
        val vm = createViewModel()
        emitEvent(
            OpenCodeEvent.SessionError(
                "session-1",
                MessageError(name = "APIError", message = "Rate limit exceeded", statusCode = 429),
            )
        )
        flushMessages()
        assertEquals(
            "Model usage limit reached. Try again later or choose another model.",
            vm.uiState.value.error,
        )

        vm.updateInput("hello")
        vm.sendMessage()
        flushMessages()
        assertNull(vm.uiState.value.error)

        // Repository emissions while the send is still in flight carry the previous run's error;
        // it must not flicker back before the synthetic Busy clears it — including after the
        // first emission has already reset isSending.
        emitEvent(
            OpenCodeEvent.TodoUpdated(
                "session-1",
                listOf(Todo(id = "t1", content = "first", status = "pending", priority = "medium")),
            )
        )
        flushMessages()
        assertNull(vm.uiState.value.error)
        emitEvent(
            OpenCodeEvent.TodoUpdated(
                "session-1",
                listOf(Todo(id = "t2", content = "second", status = "pending", priority = "medium")),
            )
        )
        flushMessages()
        assertNull(vm.uiState.value.error)

        // The gated send completes: the synthetic Busy clears the repository error for real.
        sendGate.complete(Unit)
        advanceUntilIdle()
        assertNull(vm.uiState.value.error)
        assertTrue(vm.uiState.value.isBusy)
    }

    @Test
    fun sendMessage_immediateApiErrorRestoresExactDraftAfterAcceptedComposerClear() = runTest {
        val request = slot<SendMessageRequest>()
        coEvery { api.sendMessageAsync(any(), capture(request), any(), null) } throws RuntimeException("boom")
        val vm = createViewModel()
        val submittedDraft = "  hello from composer  \n"

        vm.updateInput(submittedDraft)
        val syncGenerationBeforeSend = vm.uiState.value.inputSyncGeneration
        assertTrue(vm.sendMessage())
        runCurrent()

        // The endpoint can fail before ChatInputBar's accepted clear is hoisted to the ViewModel.
        // That later clear atomically acknowledges the submission and forces the exact failed
        // draft back into the owned field, without relying on a transient empty state or a frame.
        assertEquals(syncGenerationBeforeSend, vm.uiState.value.inputSyncGeneration)
        vm.updateInput("")

        assertEquals(submittedDraft, vm.uiState.value.inputText)
        assertTrue(vm.uiState.value.inputSyncGeneration > syncGenerationBeforeSend)
        assertEquals("hello from composer", request.captured.parts.single().text)
        assertFalse(vm.uiState.value.isSending)
        assertEquals(
            "Could not send the message. Check the connection and try again.",
            vm.uiState.value.error,
        )
    }

    @Test
    fun sendMessage_gatedApiErrorRestoresExactDraftClearedWhileRequestIsPending() = runTest {
        val failureGate = CompletableDeferred<Unit>()
        val request = slot<SendMessageRequest>()
        coEvery { api.sendMessageAsync(any(), capture(request), any(), null) } coAnswers {
            failureGate.await()
            throw IOException("boom")
        }
        val vm = createViewModel()
        val submittedDraft = "\t message with exact whitespace \n"

        vm.updateInput(submittedDraft)
        val syncGenerationBeforeSend = vm.uiState.value.inputSyncGeneration
        assertTrue(vm.sendMessage())
        runCurrent()
        vm.updateInput("")
        assertEquals("", vm.uiState.value.inputText)
        assertEquals(syncGenerationBeforeSend, vm.uiState.value.inputSyncGeneration)

        failureGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(submittedDraft, vm.uiState.value.inputText)
        assertTrue(vm.uiState.value.inputSyncGeneration > syncGenerationBeforeSend)
        assertEquals("message with exact whitespace", request.captured.parts.single().text)
    }

    @Test
    fun sendMessage_gatedApiErrorDoesNotOverwriteNewerNonblankDraft() = runTest {
        val failureGate = CompletableDeferred<Unit>()
        coEvery { api.sendMessageAsync(any(), any(), any(), null) } coAnswers {
            failureGate.await()
            throw IOException("boom")
        }
        val vm = createViewModel()

        vm.updateInput("  original draft  ")
        val syncGenerationBeforeSend = vm.uiState.value.inputSyncGeneration
        assertTrue(vm.sendMessage())
        runCurrent()
        vm.updateInput("")
        vm.updateInput("newer typed draft")

        failureGate.complete(Unit)
        advanceUntilIdle()

        assertEquals("newer typed draft", vm.uiState.value.inputText)
        assertEquals(syncGenerationBeforeSend, vm.uiState.value.inputSyncGeneration)
        assertEquals(
            "Could not send the message. Check the connection and try again.",
            vm.uiState.value.error,
        )
    }

    @Test
    fun sendMessage_gatedApiErrorDoesNotOverwriteWhitespaceDraftTypedAfterClear() = runTest {
        val failureGate = CompletableDeferred<Unit>()
        coEvery { api.sendMessageAsync(any(), any(), any(), null) } coAnswers {
            failureGate.await()
            throw IOException("boom")
        }
        val vm = createViewModel()
        val newerWhitespaceDraft = " \t\n"

        vm.updateInput("original draft")
        val syncGenerationBeforeSend = vm.uiState.value.inputSyncGeneration
        assertTrue(vm.sendMessage())
        runCurrent()
        vm.updateInput("")
        vm.updateInput(newerWhitespaceDraft)

        failureGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(newerWhitespaceDraft, vm.uiState.value.inputText)
        assertEquals(syncGenerationBeforeSend, vm.uiState.value.inputSyncGeneration)
    }

    @Test
    fun sendMessage_immediateApiErrorDoesNotOverwriteNewerWhitespaceDraft() = runTest {
        coEvery { api.sendMessageAsync(any(), any(), any(), null) } throws RuntimeException("boom")
        val vm = createViewModel()
        val newerWhitespaceDraft = "\t \n"

        vm.updateInput("original draft")
        val syncGenerationBeforeSend = vm.uiState.value.inputSyncGeneration
        assertTrue(vm.sendMessage())
        runCurrent()

        // Failure has already completed, but a nonempty whitespace edit is user input rather
        // than the composer's accepted empty-string clear acknowledgment.
        vm.updateInput(newerWhitespaceDraft)

        assertEquals(newerWhitespaceDraft, vm.uiState.value.inputText)
        assertEquals(syncGenerationBeforeSend, vm.uiState.value.inputSyncGeneration)
    }

    @Test
    fun sendMessage_sendsBackendFileUrls_forWorkspaceAttachmentsWithSpecialCharacters() = runTest {
        val vm = createViewModel()
        val request = slot<SendMessageRequest>()
        coEvery { api.sendMessageAsync(any(), capture(request), any(), null) } returns Unit
        coEvery { api.listFiles("src/My File %/ümlaut/こんにちは", "/test", null) } returns listOf(
            FileNodeDto(
                name = "hash#query?.kt",
                path = "src/My File %/ümlaut/こんにちは/hash#query?.kt",
                absolute = "/test/src/My File %/ümlaut/こんにちは/hash#query?.kt",
                type = "file",
            )
        )
        vm.filePickerManager.restoreAttachedFiles(
            listOf(
                SelectedFile(
                    name = "hash#query?.kt",
                    path = "src/My File %/ümlaut/こんにちは/hash#query?.kt",
                    mimeType = "text/plain",
                )
            )
        )

        vm.sendMessage()
        advanceUntilIdle()

        coVerify { api.sendMessageAsync("session-1", any(), "/test", null) }
        assertEquals(
            "file:/test/src/My%20File%20%25/%C3%BCmlaut/%E3%81%93%E3%82%93%E3%81%AB%E3%81%A1%E3%81%AF/hash%23query%3F.kt",
            request.captured.parts.single().url,
        )
    }

    @Test
    fun sendMessage_sendsModelAndVariantRestoredFromServerSession() = runTest {
        val model = dev.blazelight.p4oc.data.remote.dto.ModelInput("openai", "gpt-5")
        coEvery { api.getSession("session-1", any(), null) } returns sessionDto(
            model = SessionModelDto(id = "gpt-5", providerID = "openai", variant = "high")
        )
        coEvery { api.getProviders(any(), null) } returns reasoningProviders()
        val request = slot<SendMessageRequest>()
        coEvery { api.sendMessageAsync(any(), capture(request), any(), null) } returns Unit
        val vm = createViewModel()

        vm.updateInput("hello")
        vm.sendMessage()
        advanceUntilIdle()

        assertEquals(model, request.captured.model)
        assertEquals("high", request.captured.variant)
    }

    @Test
    fun sendMessage_acknowledgesCapturedModelVariant_notMutableStateAtSuccess() = runTest {
        val sentModel = dev.blazelight.p4oc.data.remote.dto.ModelInput("openai", "gpt-5")
        coEvery { api.getSession("session-1", any(), null) } returns sessionDto(
            model = SessionModelDto(id = "gpt-5", providerID = "openai", variant = "high")
        )
        coEvery { api.getProviders(any(), null) } returns reasoningProviders()
        val sendGate = CompletableDeferred<Unit>()
        coEvery { api.sendMessageAsync(any(), any(), any(), null) } coAnswers {
            sendGate.await()
            Unit
        }
        val vm = createViewModel()

        vm.updateInput("hello")
        vm.sendMessage()
        runCurrent()

        // While the request is suspended in flight, the user changes the selection. The success
        // acknowledgment carries the model/variant captured when the request was sent (high); the
        // current pending record is now (gpt-5, low), so the ack is stale and must NOT flush the
        // in-flight selection — and must not re-read mutable state to flush "low" as if it had
        // been sent.
        vm.modelAgentManager.selectReasoningEffort("low")
        runCurrent()
        sendGate.complete(Unit)
        advanceUntilIdle()

        // The newer pending (low) selection survives as pending; the stale ack flushes nothing.
        assertEquals("low", vm.modelAgentManager.currentReasoningEffort())
        coVerify(exactly = 1) {
            settingsDataStore.setComposerSelectionForSession(
                workspaceClient.workspace,
                "session-1",
                SessionComposerSelection(model = sentModel, variant = "low", pendingServerSync = true),
            )
        }
        coVerify(exactly = 0) {
            settingsDataStore.setComposerSelectionForSession(
                workspaceClient.workspace,
                "session-1",
                SessionComposerSelection(model = sentModel, variant = "low", pendingServerSync = false),
            )
        }
    }

    @Test
    fun abortSession_clearsStreamingFlags_andBusyState() = runTest {
        val vm = createViewModel()

        coEvery { api.abortSession(any(), any(), null) } returns Response.success(Unit)
        emitEvent(OpenCodeEvent.MessageUpdated(assistantMessage(id = "m1", sessionId = "session-1", createdAt = 1)))
        emitEvent(
            OpenCodeEvent.MessagePartUpdated(
                textPart(id = "p1", messageId = "m1", sessionId = "session-1", text = "Hi"),
                delta = null
            )
        )
        emitEvent(
            OpenCodeEvent.MessagePartUpdated(
                textPart(id = "p1", messageId = "m1", sessionId = "session-1", text = "ignored"),
                delta = "!"
            )
        )
        emitEvent(OpenCodeEvent.SessionStatusChanged("session-1", SessionStatus.Busy))
        flushMessages()

        vm.abortSession()
        flushMessages()

        val text = vm.currentMessages().single().parts.single() as Part.Text
        assertFalse(text.isStreaming)
        assertFalse(vm.uiState.value.isBusy)
        assertFalse(vm.uiState.value.isSending)
    }

    @Test
    fun abortSession_sanitizesUnexpectedJsonErrors() = runTest {
        val vm = createViewModel()

        coEvery { api.abortSession(any(), any(), null) } throws RuntimeException("{\"error\":\"boom\"}")

        vm.abortSession()
        flushMessages()

        assertEquals("Could not stop the run. Try again.", vm.uiState.value.error)
    }

    @Test
    fun sessionError_messageAborted_doesNotShowSnackbarError() = runTest {
        val vm = createViewModel()

        emitEvent(
            OpenCodeEvent.SessionError(
                sessionID = "session-1",
                error = dev.blazelight.p4oc.domain.model.MessageError(
                    name = "MessageAbortedError",
                    message = "Aborted",
                ),
            ),
        )
        flushMessages()

        assertNull(vm.uiState.value.error)
    }

    @Test
    fun loadSession_notFound_emitsSessionMissing() = runTest {
        coEvery { api.getSession("session-1", any(), null) } throws httpNotFound()

        val vm = createViewModel()
        advanceUntilIdle()

        assertTrue(vm.sessionMissing.replayCache.isNotEmpty())
        assertNull(vm.uiState.value.error)
    }


    @Test
    fun loadCommands_failureKeepsBuiltIns_andAllowsRetryForWorkspaceCommands() = runTest {
        val vm = createViewModel()
        coEvery { api.listCommands(any(), null) } throws RuntimeException("network down")

        vm.loadCommands()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.commands.any { it.name == "help" })
        assertFalse(vm.uiState.value.hasLoadedWorkspaceCommands)
        assertEquals("Could not load workspace commands. Try again.", vm.uiState.value.commandLoadError)

        coEvery { api.listCommands(any(), null) } returns listOf(
            CommandDto(name = "workspace", description = "Workspace command")
        )

        vm.refreshCommandsIfNeeded()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.commands.any { it.name == "workspace" })
        assertTrue(vm.uiState.value.hasLoadedWorkspaceCommands)
        assertNull(vm.uiState.value.commandLoadError)
    }

    @Test
    fun loadCommands_serverInitOverridesBuiltinMetadata_withoutRemovingOtherBuiltIns() = runTest {
        coEvery { api.listCommands("/test", null) } returns listOf(
            CommandDto(
                name = "init",
                description = "Initialize from the live server",
                agent = "server-agent",
                model = "server-model",
                template = JsonPrimitive("server-template"),
                source = "command",
            ),
        )
        val vm = createViewModel()

        vm.loadCommands()
        advanceUntilIdle()

        val commands = vm.uiState.value.commands
        val initCommands = commands.filter { it.name == "init" }
        assertEquals(1, initCommands.size)
        with(initCommands.single()) {
            assertEquals("Initialize from the live server", description)
            assertEquals("server-agent", agent)
            assertEquals("server-model", model)
            assertEquals("server-template", template)
            assertEquals(CommandSource.Skill, source)
        }
        assertTrue(commands.any { it.name == "help" && it.source == CommandSource.BuiltIn })
    }

    @Test
    fun executeCommand_initWithSelectedModel_usesDedicatedInitEndpointWithGeneratedMessageId() = runTest {
        val selectedModel = dev.blazelight.p4oc.data.remote.dto.ModelInput("openai", "gpt-5")
        val request = slot<InitSessionRequest>()
        coEvery { api.initSession("session-1", capture(request), "/test", null) } returns true
        val vm = createViewModel()
        vm.modelAgentManager.selectModel(selectedModel)
        runCurrent()

        vm.executeCommand("init", "ignored arguments")
        runCurrent()

        coVerify(exactly = 1) { api.initSession("session-1", any(), "/test", null) }
        assertTrue(request.captured.messageID.startsWith("msg_"))
        assertTrue(request.captured.messageID.removePrefix("msg_").isNotBlank())
        assertEquals(selectedModel.providerID, request.captured.providerID)
        assertEquals(selectedModel.modelID, request.captured.modelID)
        assertTrue(vm.uiState.value.isBusy)

        emitEvent(OpenCodeEvent.SessionStatusChanged("session-1", SessionStatus.Idle))
        runCurrent()

        coVerify(exactly = 0) { api.executeCommand(any(), any(), any(), null) }
    }

    @Test
    fun sendMessage_gatedInitFailureRestoresExactRawSlashDraft() = runTest {
        val initResponse = CompletableDeferred<Boolean>()
        coEvery { api.initSession("session-1", any(), "/test", null) } coAnswers {
            initResponse.await()
        }
        val vm = createViewModel()
        vm.modelAgentManager.selectModel(
            dev.blazelight.p4oc.data.remote.dto.ModelInput("openai", "gpt-5")
        )
        runCurrent()
        val submittedDraft = " \t/init ignored  arguments \n"

        vm.updateInput(submittedDraft)
        assertTrue(vm.sendMessage())
        runCurrent()
        vm.updateInput("")

        initResponse.complete(false)
        advanceUntilIdle()

        assertEquals(submittedDraft, vm.uiState.value.inputText)
        assertFalse(vm.uiState.value.isSending)
        assertEquals("Could not initialize the session. Try again.", vm.uiState.value.error)
        coVerify(exactly = 1) { api.initSession("session-1", any(), "/test", null) }
    }

    @Test
    fun sendMessage_gatedUndoFailureRestoresExactRawSlashDraft() = runTest {
        val revertGate = CompletableDeferred<Unit>()
        coEvery { api.getSession("session-1", any(), null) } returns sessionDto(revertMessageId = "user-2")
        coEvery { api.getMessages("session-1", any(), null, any(), null) } returns listOf(
            userMessageDto("user-1", createdAt = 1),
            assistantMessageDto("assistant-1", createdAt = 2),
            userMessageDto("user-2", createdAt = 3),
        )
        coEvery { api.revertSession(any(), any(), any(), null) } coAnswers {
            revertGate.await()
            throw IOException("boom")
        }
        val vm = createViewModel()
        val submittedDraft = "  /undo keep exact whitespace \n"

        vm.updateInput(submittedDraft)
        assertTrue(vm.sendMessage())
        runCurrent()
        vm.updateInput("")

        revertGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(submittedDraft, vm.uiState.value.inputText)
        assertFalse(vm.uiState.value.isSending)
        assertEquals("Could not undo. Try again.", vm.uiState.value.error)
        coVerify(exactly = 1) {
            api.revertSession("session-1", RevertSessionRequest(messageID = "user-1"), "/test", null)
        }
    }

    @Test
    fun sendMessage_gatedRedoFailureRestoresExactRawSlashDraft() = runTest {
        val revertGate = CompletableDeferred<Unit>()
        coEvery { api.getSession("session-1", any(), null) } returns sessionDto(revertMessageId = "user-1")
        coEvery { api.getMessages("session-1", any(), null, any(), null) } returns listOf(
            userMessageDto("user-1", createdAt = 1),
            assistantMessageDto("assistant-1", createdAt = 2),
            userMessageDto("user-2", createdAt = 3),
        )
        coEvery { api.revertSession(any(), any(), any(), null) } coAnswers {
            revertGate.await()
            throw IOException("boom")
        }
        val vm = createViewModel()
        val submittedDraft = "\t/redo keep exact whitespace  \n"

        vm.updateInput(submittedDraft)
        assertTrue(vm.sendMessage())
        runCurrent()
        vm.updateInput("")

        revertGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(submittedDraft, vm.uiState.value.inputText)
        assertFalse(vm.uiState.value.isSending)
        assertEquals("Could not redo. Try again.", vm.uiState.value.error)
        coVerify(exactly = 1) {
            api.revertSession("session-1", RevertSessionRequest(messageID = "user-2"), "/test", null)
        }
    }

    @Test
    fun executeCommand_genericCommandDispatchesOnlyWhileIdle() = runTest {
        val commandResponse = CompletableDeferred<MessageWrapperDto>()
        coEvery { api.executeCommand("session-1", any(), "/test", null) } coAnswers {
            commandResponse.await()
        }
        val vm = createViewModel()

        assertTrue(vm.executeCommand("custom", "first"))
        assertFalse(vm.executeCommand("custom", "while sending"))
        assertEquals(
            "Wait for the current run to finish or stop it first.",
            vm.uiState.value.error,
        )
        runCurrent()
        assertTrue(vm.uiState.value.isSending)
        coVerify(exactly = 1) { api.executeCommand("session-1", any(), "/test", null) }

        commandResponse.complete(assistantMessageDto("command-response", createdAt = 10))
        runCurrent()
        assertTrue(vm.uiState.value.isBusy)

        assertFalse(vm.executeCommand("custom", "while busy"))
        runCurrent()
        coVerify(exactly = 1) { api.executeCommand("session-1", any(), "/test", null) }

        emitEvent(OpenCodeEvent.SessionStatusChanged("session-1", SessionStatus.Idle))
        advanceUntilIdle()
    }

    @Test
    fun executeCommand_paletteFailureLeavesExistingComposerDraftUnchanged() = runTest {
        coEvery { api.executeCommand("session-1", any(), "/test", null) } throws RuntimeException("boom")
        val vm = createViewModel()
        val existingDraft = "keep palette composer draft"
        vm.updateInput(existingDraft)

        assertTrue(vm.executeCommand("custom", "palette arguments"))
        advanceUntilIdle()

        assertEquals(existingDraft, vm.uiState.value.inputText)
        assertFalse(vm.uiState.value.isSending)
        assertEquals("Could not execute the command. Try again.", vm.uiState.value.error)
    }

    @Test
    fun executeCommand_inFlightGenericRetainsOwnerAcrossUnrelatedIdleTodo_andReleasesAfterCompletion() =
        runTest {
            val commandResponse = CompletableDeferred<MessageWrapperDto>()
            coEvery { api.executeCommand("session-1", any(), "/test", null) } coAnswers {
                commandResponse.await()
            }
            val vm = createViewModel()
            emitEvent(OpenCodeEvent.SessionStatusChanged("session-1", SessionStatus.Idle))
            runCurrent()

            assertTrue(vm.executeCommand("custom", "first"))
            runCurrent()
            assertTrue(vm.uiState.value.isSending)

            emitEvent(
                OpenCodeEvent.TodoUpdated(
                    "session-1",
                    listOf(Todo(id = "t1", content = "step", status = "pending", priority = "medium")),
                )
            )
            runCurrent()

            val refusedInput = "/custom keep this input"
            vm.updateInput(refusedInput)
            assertFalse(vm.executeCommand("custom", "second"))
            assertEquals(refusedInput, vm.uiState.value.inputText)
            assertTrue(vm.uiState.value.isSending)
            assertEquals(
                "Wait for the current run to finish or stop it first.",
                vm.uiState.value.error,
            )
            coVerify(exactly = 1) { api.executeCommand("session-1", any(), "/test", null) }

            commandResponse.complete(assistantMessageDto("command-response", createdAt = 10))
            runCurrent()
            assertFalse(vm.uiState.value.isSending)
            assertTrue(vm.uiState.value.isBusy)

            emitEvent(OpenCodeEvent.SessionStatusChanged("session-1", SessionStatus.Idle))
            runCurrent()
            assertFalse(vm.uiState.value.isBusy)
            assertTrue(vm.executeCommand("custom", "after completion"))
            runCurrent()
            coVerify(exactly = 2) { api.executeCommand("session-1", any(), "/test", null) }
        }

    @Test
    fun executeCommand_inFlightUndoRetainsOwnerAcrossPriorErrorTodo_andReleasesAfterCompletion() =
        runTest {
            val revertResponse = CompletableDeferred<SessionDto>()
            coEvery { api.getSession("session-1", any(), null) } returns sessionDto(
                revertMessageId = "user-2",
            )
            coEvery { api.getMessages("session-1", any(), null, any(), null) } returns listOf(
                userMessageDto("user-1", createdAt = 1),
                assistantMessageDto("assistant-1", createdAt = 2),
                userMessageDto("user-2", createdAt = 3),
            )
            coEvery { api.revertSession(any(), any(), any(), null) } coAnswers {
                revertResponse.await()
            }
            val vm = createViewModel()
            emitEvent(
                OpenCodeEvent.SessionError(
                    "session-1",
                    MessageError(name = "APIError", message = "prior failure"),
                )
            )
            runCurrent()
            assertEquals("The run failed. Try again.", vm.uiState.value.error)

            assertTrue(vm.executeCommand("undo", ""))
            runCurrent()
            assertTrue(vm.uiState.value.isSending)

            emitEvent(
                OpenCodeEvent.TodoUpdated(
                    "session-1",
                    listOf(Todo(id = "t1", content = "step", status = "pending", priority = "medium")),
                )
            )
            runCurrent()

            val refusedInput = "/redo keep this input"
            vm.updateInput(refusedInput)
            assertFalse(vm.executeCommand("redo", ""))
            assertEquals(refusedInput, vm.uiState.value.inputText)
            assertTrue(vm.uiState.value.isSending)
            assertEquals(
                "Wait for the current run to finish or stop it first.",
                vm.uiState.value.error,
            )
            coVerify(exactly = 1) {
                api.revertSession("session-1", RevertSessionRequest(messageID = "user-1"), "/test", null)
            }
            coVerify(exactly = 0) { api.executeCommand(any(), any(), any(), null) }

            revertResponse.complete(sessionDto(revertMessageId = "user-1"))
            runCurrent()
            assertFalse(vm.uiState.value.isSending)
        }

    @Test
    fun executeCommand_undoWithoutBoundaryReleasesOwnerForNextIdleCommand() = runTest {
        coEvery { api.getMessages("session-1", any(), null, any(), null) } returns emptyList()
        coEvery { api.executeCommand("session-1", any(), "/test", null) } returns assistantMessageDto(
            "command-response",
            createdAt = 10,
        )
        val vm = createViewModel()

        assertFalse(vm.executeCommand("undo", ""))
        assertFalse(vm.uiState.value.isSending)
        assertEquals("Nothing to undo", vm.uiState.value.error)
        coVerify(exactly = 0) { api.revertSession(any(), any(), any(), null) }

        assertTrue(vm.executeCommand("custom", "after no boundary"))
        runCurrent()
        coVerify(exactly = 1) { api.executeCommand("session-1", any(), "/test", null) }
    }

    @Test
    fun executeCommand_concurrentCallersAcceptAndDispatchExactlyOneCommand() = runTest {
        val commandResponse = CompletableDeferred<MessageWrapperDto>()
        coEvery { api.executeCommand("session-1", any(), "/test", null) } coAnswers {
            commandResponse.await()
        }
        val vm = createViewModel()
        val start = CompletableDeferred<Unit>()
        val callers = List(32) { attempt ->
            async(Dispatchers.Default) {
                start.await()
                vm.executeCommand("custom", "attempt-$attempt")
            }
        }

        start.complete(Unit)
        val results = callers.map { it.await() }
        runCurrent()

        assertEquals(1, results.count { it })
        assertEquals(31, results.count { !it })
        assertEquals(
            "Wait for the current run to finish or stop it first.",
            vm.uiState.value.error,
        )
        coVerify(exactly = 1) { api.executeCommand("session-1", any(), "/test", null) }

        commandResponse.complete(assistantMessageDto("command-response", createdAt = 10))
        runCurrent()
    }

    @Test
    fun executeCommand_duplicateInitWhileSendingOrBusy_launchesOnlyOnce() = runTest {
        val initResponse = CompletableDeferred<Boolean>()
        coEvery { api.initSession("session-1", any(), "/test", null) } coAnswers {
            initResponse.await()
        }
        val vm = createViewModel()
        vm.modelAgentManager.selectModel(
            dev.blazelight.p4oc.data.remote.dto.ModelInput("openai", "gpt-5")
        )
        runCurrent()

        vm.executeCommand("init", "")
        vm.executeCommand("init", "")
        runCurrent()

        coVerify(exactly = 1) { api.initSession("session-1", any(), "/test", null) }
        assertTrue(vm.uiState.value.isSending)

        initResponse.complete(true)
        runCurrent()
        assertTrue(vm.uiState.value.isBusy)

        vm.executeCommand("init", "")
        runCurrent()
        coVerify(exactly = 1) { api.initSession("session-1", any(), "/test", null) }

        emitEvent(OpenCodeEvent.SessionStatusChanged("session-1", SessionStatus.Idle))
        advanceUntilIdle()
    }

    @Test
    fun executeCommand_pendingInitIgnoresUnrelatedTerminalToken() = runTest {
        val initResponse = CompletableDeferred<Boolean>()
        coEvery { api.initSession("session-1", any(), "/test", null) } coAnswers {
            initResponse.await()
        }
        val vm = createViewModel()
        vm.modelAgentManager.selectModel(
            dev.blazelight.p4oc.data.remote.dto.ModelInput("openai", "gpt-5")
        )
        runCurrent()

        vm.executeCommand("init", "")
        runCurrent()

        emitEvent(OpenCodeEvent.SessionStatusChanged("session-1", SessionStatus.Idle))
        runCurrent()
        initResponse.complete(true)
        runCurrent()

        assertTrue(vm.uiState.value.isBusy)
        assertNull(vm.uiState.value.error)

        emitEvent(OpenCodeEvent.SessionStatusChanged("session-1", SessionStatus.Idle))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isBusy)
    }

    @Test
    fun abortSession_invalidatesPendingSuccessfulInit_beforeBusyOrReconciliation() = runTest {
        val initResponse = CompletableDeferred<Boolean>()
        val abortResponse = CompletableDeferred<Response<Unit>>()
        coEvery { api.initSession("session-1", any(), "/test", null) } coAnswers {
            withContext(NonCancellable) { initResponse.await() }
        }
        coEvery { api.abortSession("session-1", "/test", null) } coAnswers {
            abortResponse.await()
        }
        val vm = createViewModel()
        vm.modelAgentManager.selectModel(
            dev.blazelight.p4oc.data.remote.dto.ModelInput("openai", "gpt-5")
        )
        runCurrent()

        vm.executeCommand("init", "")
        runCurrent()
        assertTrue(vm.uiState.value.isSending)

        vm.abortSession()
        runCurrent()

        initResponse.complete(true)
        runCurrent()

        assertFalse(vm.uiState.value.isBusy)
        assertNull(vm.uiState.value.error)
        advanceTimeBy(2_000)
        runCurrent()
        coVerify(exactly = 0) { api.getSessionStatuses("/test", null) }

        abortResponse.complete(Response.success(Unit))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isSending)
    }

    @Test
    fun abortSession_invalidatesPendingFailedInit_beforeError() = runTest {
        val initResponse = CompletableDeferred<Boolean>()
        val abortResponse = CompletableDeferred<Response<Unit>>()
        coEvery { api.initSession("session-1", any(), "/test", null) } coAnswers {
            withContext(NonCancellable) { initResponse.await() }
        }
        coEvery { api.abortSession("session-1", "/test", null) } coAnswers {
            abortResponse.await()
        }
        val vm = createViewModel()
        vm.modelAgentManager.selectModel(
            dev.blazelight.p4oc.data.remote.dto.ModelInput("openai", "gpt-5")
        )
        runCurrent()

        vm.executeCommand("init", "")
        runCurrent()
        vm.abortSession()
        runCurrent()

        initResponse.complete(false)
        runCurrent()

        assertFalse(vm.uiState.value.isBusy)
        assertNull(vm.uiState.value.error)
        coVerify(exactly = 0) { api.getSessionStatuses("/test", null) }

        abortResponse.complete(Response.success(Unit))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isSending)
    }

    @Test
    fun sendMessage_replacementInvalidatesPendingInitCompletion() = runTest {
        val initResponse = CompletableDeferred<Boolean>()
        coEvery { api.initSession("session-1", any(), "/test", null) } coAnswers {
            withContext(NonCancellable) { initResponse.await() }
        }
        coEvery { api.sendMessageAsync(any(), any(), any(), null) } returns Unit
        val vm = createViewModel()
        vm.modelAgentManager.selectModel(
            dev.blazelight.p4oc.data.remote.dto.ModelInput("openai", "gpt-5")
        )
        runCurrent()

        vm.executeCommand("init", "")
        runCurrent()
        vm.updateInput("replacement")
        vm.sendMessage()
        runCurrent()
        assertTrue(vm.uiState.value.isBusy)

        initResponse.complete(false)
        runCurrent()

        assertTrue(vm.uiState.value.isBusy)
        assertNull(vm.uiState.value.error)

        emitEvent(OpenCodeEvent.SessionStatusChanged("session-1", SessionStatus.Idle))
        advanceUntilIdle()
    }

    @Test
    fun executeCommand_initReconcilesAssistant_whenTerminalSseIsMissing() = runTest {
        val existingUser = userMessageDto("user-existing", createdAt = 10)
        val initAssistant = assistantMessageDto(
            id = "assistant-init",
            createdAt = 11,
            completedAt = 12,
            parts = listOf(
                PartDto(
                    id = "part-init",
                    sessionID = "session-1",
                    messageID = "assistant-init",
                    type = "text",
                    text = "Initialized",
                )
            ),
        )
        coEvery { api.getMessages("session-1", 100, null, "/test", null) } returnsMany listOf(
            listOf(existingUser),
            listOf(existingUser, initAssistant),
        )
        coEvery { api.getSessionStatuses("/test", null) } returns mapOf(
            "session-1" to SessionStatusDto(type = "idle")
        )
        coEvery { api.initSession("session-1", any(), "/test", null) } returns true
        val vm = createViewModel()
        vm.modelAgentManager.selectModel(
            dev.blazelight.p4oc.data.remote.dto.ModelInput("openai", "gpt-5")
        )
        runCurrent()

        vm.executeCommand("init", "")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isBusy)
        assertNull(vm.uiState.value.error)
        assertEquals(
            listOf("user-existing", "assistant-init"),
            vm.currentMessages().map { it.message.id },
        )
        assertEquals("Initialized", (vm.currentMessages().last().parts.single() as Part.Text).text)
        coVerify(exactly = 1) { api.getSessionStatuses("/test", null) }
        coVerify(exactly = 2) { api.getMessages("session-1", 100, null, "/test", null) }
    }

    @Test
    fun executeCommand_undoPaletteSelection_revertsToPreviousUserMessageBoundaryWithoutExecutingCommandEndpoint() =
        runTest {
            coEvery { api.getSession("session-1", any(), null) } returns sessionDto(revertMessageId = "user-2")
            coEvery { api.getMessages("session-1", any(), null, any(), null) } returns listOf(
                userMessageDto("user-1", createdAt = 1),
                assistantMessageDto("assistant-1", createdAt = 2),
                userMessageDto("user-2", createdAt = 3),
                assistantMessageDto("assistant-2", createdAt = 4),
            )
            coEvery { api.revertSession(any(), any(), any(), null) } returns sessionDto(revertMessageId = "user-1")
            coEvery { api.executeCommand(any(), any(), any(), null) } returns assistantMessageDto(
                "command-response",
                createdAt = 5
            )
            val vm = createViewModel()

            vm.executeCommand("undo", "")
            advanceUntilIdle()

            coVerify(exactly = 0) { api.executeCommand(any(), any(), any(), null) }
            coVerify(exactly = 1) {
                api.revertSession("session-1", RevertSessionRequest(messageID = "user-1"), "/test", null)
            }
        }

    @Test
    fun executeCommand_redoPaletteSelectionWithActiveRevert_usesRevertBoundaryNotCommandEndpoint() =
        runTest {
            coEvery { api.getSession("session-1", any(), null) } returns sessionDto(revertMessageId = "user-1")
            coEvery { api.getMessages("session-1", any(), null, any(), null) } returns listOf(
                userMessageDto("user-1", createdAt = 1),
                assistantMessageDto("assistant-1", createdAt = 2),
                userMessageDto("user-2", createdAt = 3),
                assistantMessageDto("assistant-2", createdAt = 4),
            )
            coEvery { api.revertSession(any(), any(), any(), null) } returns sessionDto(revertMessageId = "user-2")
            coEvery { api.unrevertSession(any(), any(), null) } returns sessionDto(revertMessageId = null)
            coEvery { api.executeCommand(any(), any(), any(), null) } returns assistantMessageDto(
                "command-response",
                createdAt = 5
            )
            val vm = createViewModel()

            vm.executeCommand("redo", "")
            advanceUntilIdle()

            coVerify(exactly = 0) { api.executeCommand(any(), any(), any(), null) }
            coVerify(exactly = 1) {
                api.revertSession("session-1", RevertSessionRequest(messageID = "user-2"), "/test", null)
            }
            coVerify(exactly = 0) { api.unrevertSession(any(), any(), null) }
        }

    private fun TestScope.createViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(mapOf(Screen.Chat.ARG_SESSION_ID to "session-1")),
        repository: SessionRepositoryImpl = SessionRepositoryImpl(
            workspaceClient,
            messageMapper,
            dispatcher = StandardTestDispatcher(testScheduler),
        ),
    ): ChatViewModel {
        sessionRepository = repository
        val fileRepository = testFileRepository()
        val vm = ChatViewModel(
            savedStateHandle = savedStateHandle,
            workspaceClient = workspaceClient,
            sessionRepository = sessionRepository,
            uploadCoordinator = testUploadCoordinator(fileRepository),
            settingsDataStore = settingsDataStore,
            hapticFeedback = hapticFeedback,
        )
        advanceUntilIdle()
        return vm
    }

    private fun testFileRepository(): FileRepository = FileRepositoryFactory.create(workspaceClient)

    private fun testUploadCoordinator(repo: FileRepository) = UploadCoordinator(
        scope = CoroutineScope(Dispatchers.Main),
        repositoryFactory = { repo },
    )

    private fun TestScope.flushMessages() {
        advanceUntilIdle()
        advanceUntilIdle()
    }

    private suspend fun emitEvent(event: OpenCodeEvent) {
        sessionRepository.acceptEvent(event)
        events.emit(
            ScopedEvent(
                serverRef = workspaceClient.workspace.server,
                generation = workspaceClient.generation,
                workspaceKey = workspaceClient.workspace.key,
                event = event,
            ),
        )
    }

    private fun ChatViewModel.currentMessages(): List<MessageWithParts> =
        messages.value

    private fun sessionDto(
        revertMessageId: String? = null,
        model: SessionModelDto? = null,
    ): SessionDto {
        return SessionDto(
            id = "session-1",
            projectID = "project-1",
            directory = "/test",
            title = "Test Session",
            version = "1.0",
            time = TimeDto(created = 1, updated = 2),
            revert = revertMessageId?.let { SessionRevertDto(messageID = it) },
            model = model,
        )
    }

    private fun reasoningProviders() = dev.blazelight.p4oc.data.remote.dto.ProvidersResponseDto(
        all = listOf(
            dev.blazelight.p4oc.data.remote.dto.ProviderDto(
                id = "openai",
                name = "OpenAI",
                source = "env",
                models = mapOf(
                    "gpt-5" to dev.blazelight.p4oc.data.remote.dto.ModelDto(
                        id = "gpt-5",
                        providerId = "openai",
                        name = "GPT-5",
                        variants = buildJsonObject {
                            put("low", buildJsonObject {})
                            put("high", buildJsonObject {})
                        },
                    )
                ),
            )
        ),
        default = mapOf("openai" to "gpt-5"),
        connected = listOf("openai"),
    )

    private fun userMessageDto(id: String, createdAt: Long): MessageWrapperDto {
        return MessageWrapperDto(
            info = MessageInfoDto(
                id = id,
                sessionID = "session-1",
                time = MessageTimeDto(created = createdAt),
                role = "user",
                agent = "build",
                model = ModelRefDto(providerID = "provider", modelID = "model"),
            ),
            parts = emptyList(),
        )
    }

    private fun assistantMessageDto(
        id: String,
        createdAt: Long,
        completedAt: Long? = null,
        parts: List<PartDto> = emptyList(),
    ): MessageWrapperDto {
        return MessageWrapperDto(
            info = MessageInfoDto(
                id = id,
                sessionID = "session-1",
                time = MessageTimeDto(created = createdAt, completed = completedAt),
                role = "assistant",
                parentID = "",
                providerID = "provider",
                modelID = "model",
                agent = "assistant",
                mode = "chat",
            ),
            parts = parts,
        )
    }

    private fun assistantMessage(id: String, sessionId: String, createdAt: Long): Message.Assistant {
        return Message.Assistant(
            id = id,
            sessionID = sessionId,
            createdAt = createdAt,
            parentID = "",
            providerID = "provider",
            modelID = "model",
            mode = "chat",
            agent = "assistant",
            cost = 0.0,
            tokens = TokenUsage(input = 0, output = 0)
        )
    }

    private fun textPart(id: String, messageId: String, sessionId: String, text: String): Part.Text {
        return Part.Text(
            id = id,
            sessionID = sessionId,
            messageID = messageId,
            text = text
        )
    }

    private fun permission(id: String, sessionId: String): Permission {
        return Permission(
            id = id,
            type = "read",
            patterns = listOf("*.kt"),
            sessionID = sessionId,
            messageID = "m1",
            callID = "call-$id",
            metadata = buildJsonObject { },
            always = emptyList()
        )
    }

    private fun testSession(id: String, parentID: String? = null): Session {
        return Session(
            id = id,
            projectID = "project-1",
            directory = "/test",
            parentID = parentID,
            title = "Test Session",
            version = "1.0",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun questionRequest(id: String, sessionId: String): QuestionRequest {
        return QuestionRequest(
            id = id,
            sessionID = sessionId,
            questions = listOf(
                Question(
                    header = "Test",
                    question = "Do you approve?",
                    options = emptyList()
                )
            )
        )
    }

    private fun httpNotFound(): HttpException = HttpException(
        Response.error<Unit>(404, "".toResponseBody(null))
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelMainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
