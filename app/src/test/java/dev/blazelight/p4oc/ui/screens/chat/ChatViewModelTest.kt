package dev.blazelight.p4oc.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import dev.blazelight.p4oc.core.datastore.ChatSettings
import dev.blazelight.p4oc.core.datastore.NotificationSettings
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.core.datastore.VisualSettings
import dev.blazelight.p4oc.core.haptic.HapticFeedback
import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.core.network.Connection
import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.core.network.ConnectionState
import dev.blazelight.p4oc.core.network.OpenCodeApi
import dev.blazelight.p4oc.core.network.OpenCodeEventSource
import dev.blazelight.p4oc.core.network.ServerConfig
import dev.blazelight.p4oc.data.files.FileRepository
import dev.blazelight.p4oc.data.files.FileRepositoryFactory
import dev.blazelight.p4oc.data.remote.dto.CommandDto
import dev.blazelight.p4oc.data.remote.dto.SendMessageRequest
import dev.blazelight.p4oc.data.remote.mapper.MessageMapper
import dev.blazelight.p4oc.data.server.ActiveServerApiProvider
import dev.blazelight.p4oc.data.session.SessionRepositoryImpl
import dev.blazelight.p4oc.data.workspace.WorkspaceClient
import dev.blazelight.p4oc.domain.model.Message
import dev.blazelight.p4oc.domain.model.MessageWithParts
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.model.Permission
import dev.blazelight.p4oc.domain.model.Question
import dev.blazelight.p4oc.domain.model.QuestionRequest
import dev.blazelight.p4oc.domain.model.Session
import dev.blazelight.p4oc.domain.model.SessionPresence
import dev.blazelight.p4oc.domain.model.SessionStatus
import dev.blazelight.p4oc.domain.model.TokenUsage
import dev.blazelight.p4oc.domain.server.ScopedEvent
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.server.ServerRef
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
import io.mockk.unmockkObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @get:Rule
    val mainDispatcherRule = ChatViewModelMainDispatcherRule()

    private lateinit var connectionManager: ConnectionManager
    private lateinit var messageMapper: MessageMapper
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var eventSource: OpenCodeEventSource
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

        connectionManager = mockk()
        messageMapper = mockk(relaxed = true)
        settingsDataStore = mockk()
        eventSource = mockk()
        events = MutableSharedFlow(extraBufferCapacity = 32)
        api = mockk(relaxed = true)
        workspaceClient = WorkspaceClient(
            workspace = Workspace(
                server = ServerRef.fromEndpointKey("http://test.local"),
                directory = "/test",
            ),
            generation = ServerGeneration(0L),
            apiProvider = ActiveServerApiProvider { _, _ -> api },
        )
        every { connectionManager.connectionState } returns MutableStateFlow(ConnectionState.Disconnected)
        every { connectionManager.getApi() } returns api
        every { connectionManager.getEventSource() } returns eventSource
        every { eventSource.events } returns MutableSharedFlow(extraBufferCapacity = 32)

        // Mock connectionManager.connection with a Connection wrapping the test eventSource
        // so that observeEvents() can flatMapLatest into the events flow
        val testConnection = Connection(
            config = ServerConfig.LOCAL_DEFAULT,
            generation = ServerGeneration(0L),
            api = api,
            eventSource = eventSource
        )
        every { connectionManager.connection } returns MutableStateFlow(testConnection)
        every { connectionManager.scopedEvents } returns events

        every { settingsDataStore.favoriteModels } returns flowOf(emptySet())
        every { settingsDataStore.recentModels } returns flowOf(emptyList())
        every { settingsDataStore.chatSettings } returns flowOf(ChatSettings())
        every { settingsDataStore.visualSettings } returns flowOf(VisualSettings())
        every { settingsDataStore.notificationSettings } returns flowOf(NotificationSettings())
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
    fun sendMessage_clearsInput_andMarksBusyUntilSseStatus() = runTest {
        val vm = createViewModel()
        coEvery { api.sendMessageAsync(any(), any(), any()) } returns Unit
        vm.updateInput("hello")

        vm.sendMessage()

        assertEquals("", vm.uiState.value.inputText)
        // isSending is set to true synchronously before the coroutine launches
        assertTrue(vm.uiState.value.isSending)

        advanceUntilIdle()
        assertEquals("", vm.uiState.value.inputText)
        assertFalse(vm.uiState.value.isSending)
        assertTrue(vm.uiState.value.isBusy)
    }

    @Test
    fun sendMessage_restoresInput_onApiError() = runTest {
        val vm = createViewModel()

        coEvery { api.sendMessageAsync(any(), any(), any()) } throws RuntimeException("boom")

        vm.updateInput("hello")
        vm.sendMessage()
        advanceUntilIdle()

        assertEquals("hello", vm.uiState.value.inputText)
        assertFalse(vm.uiState.value.isSending)
        assertTrue(vm.uiState.value.error?.contains("boom") == true)
    }

    @Test
    fun sendMessage_sendsBackendFileUrls_forWorkspaceAttachmentsWithSpecialCharacters() = runTest {
        val vm = createViewModel()
        val request = slot<SendMessageRequest>()
        coEvery { api.sendMessageAsync(any(), capture(request), any()) } returns Unit
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

        coVerify { api.sendMessageAsync("session-1", any(), "/test") }
        assertEquals(
            "file:/test/src/My%20File%20%25/%C3%BCmlaut/%E3%81%93%E3%82%93%E3%81%AB%E3%81%A1%E3%81%AF/hash%23query%3F.kt",
            request.captured.parts.single().url,
        )
    }

    @Test
    fun queueMessage_appendsToQueue_andClearsInput() = runTest {
        val vm = createViewModel()
        vm.updateInput("queued text")

        vm.queueMessage()

        assertEquals("", vm.uiState.value.inputText)
        assertEquals(listOf("queued text"), vm.uiState.value.queuedMessages.map { it.text })
    }

    @Test
    fun queueMessage_preservesFifoOrder() = runTest {
        val vm = createViewModel()

        vm.updateInput("first")
        vm.queueMessage()
        vm.updateInput("second")
        vm.queueMessage()
        vm.updateInput("third")
        vm.queueMessage()

        assertEquals(listOf("first", "second", "third"), vm.uiState.value.queuedMessages.map { it.text })
    }

    @Test
    fun queueMessage_capsAtTenEntries() = runTest {
        val vm = createViewModel()

        repeat(10) { index ->
            vm.updateInput("queued-$index")
            vm.queueMessage()
        }
        vm.updateInput("overflow")
        vm.queueMessage()

        assertEquals(10, vm.uiState.value.queuedMessages.size)
        assertFalse(vm.uiState.value.queuedMessages.any { it.text == "overflow" })
    }

    @Test
    fun cancelQueuedMessage_removesMatchingEntry() = runTest {
        val vm = createViewModel()

        vm.updateInput("first")
        vm.queueMessage()
        vm.updateInput("second")
        vm.queueMessage()
        val cancelId = vm.uiState.value.queuedMessages.first().id

        vm.cancelQueuedMessage(cancelId)

        assertEquals(listOf("second"), vm.uiState.value.queuedMessages.map { it.text })
    }

    @Test
    fun abortSession_clearsStreamingFlags_andBusyState() = runTest {
        val vm = createViewModel()

        coEvery { api.abortSession(any(), any()) } returns Response.success(Unit)
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

        coEvery { api.abortSession(any(), any()) } throws RuntimeException("{\"error\":\"boom\"}")

        vm.abortSession()
        flushMessages()

        assertEquals("Failed to stop run: Unable to stop run", vm.uiState.value.error)
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
        coEvery { api.getSession("session-1", any()) } throws httpNotFound()

        val vm = createViewModel()
        advanceUntilIdle()

        assertTrue(vm.sessionMissing.replayCache.isNotEmpty())
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun loadCommands_failureKeepsBuiltIns_andAllowsRetryForWorkspaceCommands() = runTest {
        val vm = createViewModel()
        coEvery { api.listCommands(any()) } throws RuntimeException("network down")

        vm.loadCommands()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.commands.any { it.name == "help" })
        assertFalse(vm.uiState.value.hasLoadedWorkspaceCommands)
        assertEquals("network down", vm.uiState.value.commandLoadError)

        coEvery { api.listCommands(any()) } returns listOf(
            CommandDto(name = "workspace", description = "Workspace command")
        )

        vm.refreshCommandsIfNeeded()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.commands.any { it.name == "workspace" })
        assertTrue(vm.uiState.value.hasLoadedWorkspaceCommands)
        assertNull(vm.uiState.value.commandLoadError)
    }

    private fun TestScope.createViewModel(): ChatViewModel {
        sessionRepository = SessionRepositoryImpl(
            workspaceClient,
            messageMapper,
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val fileRepository = testFileRepository()
        val vm = ChatViewModel(
            savedStateHandle = SavedStateHandle(mapOf(Screen.Chat.ARG_SESSION_ID to "session-1")),
            workspaceClient = workspaceClient,
            sessionRepository = sessionRepository,
            uploadCoordinator = testUploadCoordinator(fileRepository),
            connectionManager = connectionManager,
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
