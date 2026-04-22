package dev.blazelight.p4oc.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.core.datastore.VisualSettings
import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.core.network.ConnectionState
import dev.blazelight.p4oc.core.network.Connection
import dev.blazelight.p4oc.core.network.ServerConfig
import dev.blazelight.p4oc.core.network.DirectoryManager
import dev.blazelight.p4oc.core.network.OpenCodeApi
import dev.blazelight.p4oc.core.network.OpenCodeEventSource
import dev.blazelight.p4oc.data.remote.mapper.MessageMapper
import dev.blazelight.p4oc.domain.model.Message
import dev.blazelight.p4oc.domain.model.MessageWithParts
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.model.Permission
import dev.blazelight.p4oc.domain.model.Question
import dev.blazelight.p4oc.domain.model.QuestionRequest
import dev.blazelight.p4oc.domain.model.Session
import dev.blazelight.p4oc.domain.model.SessionStatus
import dev.blazelight.p4oc.domain.model.TokenUsage
import dev.blazelight.p4oc.ui.navigation.Screen
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
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

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @get:Rule
    val mainDispatcherRule = ChatViewModelMainDispatcherRule()

    private lateinit var connectionManager: ConnectionManager
    private lateinit var directoryManager: DirectoryManager
    private lateinit var messageMapper: MessageMapper
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var eventSource: OpenCodeEventSource
    private lateinit var events: MutableSharedFlow<OpenCodeEvent>

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
        directoryManager = mockk()
        messageMapper = mockk(relaxed = true)
        settingsDataStore = mockk()
        eventSource = mockk()
        events = MutableSharedFlow(extraBufferCapacity = 32)

        every { connectionManager.connectionState } returns MutableStateFlow(ConnectionState.Disconnected)
        every { connectionManager.getApi() } returns null
        every { connectionManager.getEventSource() } returns eventSource
        every { eventSource.events } returns events

        // Mock connectionManager.connection with a Connection wrapping the test eventSource
        // so that observeEvents() can flatMapLatest into the events flow
        val mockApi = mockk<OpenCodeApi>()
        val testConnection = Connection(
            config = ServerConfig.LOCAL_DEFAULT,
            api = mockApi,
            eventSource = eventSource
        )
        every { connectionManager.connection } returns MutableStateFlow(testConnection)

        every { directoryManager.getDirectory() } returns null
        every { directoryManager.setDirectory(any()) } just runs

        every { settingsDataStore.favoriteModels } returns flowOf(emptySet())
        every { settingsDataStore.recentModels } returns flowOf(emptyList())
        every { settingsDataStore.visualSettings } returns flowOf(VisualSettings())
    }

    @After
    fun tearDown() {
        unmockkObject(AppLog)
    }

    @Test
    fun handleEvent_routesMessageUpdated_toMessageStore() = runTest {
        val vm = createViewModel()
        val message = assistantMessage(id = "m1", sessionId = "session-1", createdAt = 10)

        events.emit(OpenCodeEvent.MessageUpdated(message))
        flushMessages()

        assertEquals(listOf("m1"), vm.currentMessages().map { it.message.id })
    }

    @Test
    fun handleEvent_routesPermissionRequested_toDialogQueueManager() = runTest {
        val vm = createViewModel()
        val permission = permission(id = "perm-1", sessionId = "session-1")

        events.emit(OpenCodeEvent.PermissionRequested(permission))
        advanceUntilIdle()

        assertEquals(permission, vm.dialogManager.pendingPermission.value)
    }

    @Test
    fun handleEvent_filtersEventsBySessionId() = runTest {
        val vm = createViewModel()

        events.emit(OpenCodeEvent.MessageUpdated(assistantMessage(id = "m-x", sessionId = "other", createdAt = 10)))
        events.emit(OpenCodeEvent.PermissionRequested(permission(id = "perm-x", sessionId = "other")))
        flushMessages()

        assertTrue(vm.currentMessages().isEmpty())
        assertNull(vm.dialogManager.pendingPermission.value)
    }

    @Test
    fun handleEvent_routesSubagentPermission_afterSessionCreated() = runTest {
        val vm = createViewModel()

        // Register a child session via SessionCreated
        val childSession = testSession(id = "child-1", parentID = "session-1")
        events.emit(OpenCodeEvent.SessionCreated(childSession))
        advanceUntilIdle()

        // Emit permission from the child session
        val perm = permission(id = "perm-child", sessionId = "child-1")
        events.emit(OpenCodeEvent.PermissionRequested(perm))
        advanceUntilIdle()

        assertEquals(perm, vm.dialogManager.pendingPermission.value)
    }

    @Test
    fun handleEvent_routesSubagentQuestion_afterSessionCreated() = runTest {
        val vm = createViewModel()

        // Register a child session via SessionCreated
        val childSession = testSession(id = "child-2", parentID = "session-1")
        events.emit(OpenCodeEvent.SessionCreated(childSession))
        advanceUntilIdle()

        // Emit question from the child session
        val question = questionRequest(id = "q-child", sessionId = "child-2")
        events.emit(OpenCodeEvent.QuestionAsked(question))
        advanceUntilIdle()

        assertEquals(question, vm.dialogManager.pendingQuestion.value)
    }

    @Test
    fun handleEvent_doesNotRoutePermission_forUnrelatedSession() = runTest {
        val vm = createViewModel()

        // Emit permission from an unrelated session (not parent, not child)
        val perm = permission(id = "perm-rando", sessionId = "random-session-999")
        events.emit(OpenCodeEvent.PermissionRequested(perm))
        advanceUntilIdle()

        assertNull(vm.dialogManager.pendingPermission.value)
    }

    @Test
    fun sessionStatusChanged_busy_setsIsBusyTrue() = runTest {
        val vm = createViewModel()

        events.emit(OpenCodeEvent.SessionStatusChanged("session-1", SessionStatus.Busy))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isBusy)
    }

    @Test
    fun sessionStatusChanged_idle_clearsStreamingFlags() = runTest {
        val vm = createViewModel()
        events.emit(OpenCodeEvent.MessageUpdated(assistantMessage(id = "m1", sessionId = "session-1", createdAt = 1)))
        events.emit(OpenCodeEvent.MessagePartUpdated(textPart(id = "p1", messageId = "m1", sessionId = "session-1", text = "Hello"), delta = null))
        events.emit(OpenCodeEvent.MessagePartUpdated(textPart(id = "p1", messageId = "m1", sessionId = "session-1", text = "ignored"), delta = " world"))
        events.emit(OpenCodeEvent.SessionStatusChanged("session-1", SessionStatus.Busy))
        flushMessages()

        events.emit(OpenCodeEvent.SessionStatusChanged("session-1", SessionStatus.Idle))
        flushMessages()

        val text = vm.currentMessages().single().parts.single() as Part.Text
        assertFalse(text.isStreaming)
    }

    @Test
    fun sendMessage_clearsInput_andSetsIsSending() = runTest {
        val vm = createViewModel()
        vm.updateInput("hello")

        vm.sendMessage()

        assertEquals("", vm.uiState.value.inputText)
        // isSending is set to true synchronously before the coroutine launches
        assertTrue(vm.uiState.value.isSending)

        // After coroutine runs and getApi() returns null, input is restored
        advanceUntilIdle()
        assertEquals("hello", vm.uiState.value.inputText)
        assertFalse(vm.uiState.value.isSending)
    }

    @Test
    fun sendMessage_restoresInput_onApiError() = runTest {
        val vm = createViewModel()

        // Override getApi() after init to avoid hitting unstubbed mocks during init
        val api = mockk<OpenCodeApi>()
        every { connectionManager.getApi() } returns api
        coEvery { api.sendMessageAsync(any(), any(), any()) } throws RuntimeException("boom")

        vm.updateInput("hello")
        vm.sendMessage()
        advanceUntilIdle()

        assertEquals("hello", vm.uiState.value.inputText)
        assertFalse(vm.uiState.value.isSending)
        assertTrue(vm.uiState.value.error?.contains("boom") == true)
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

        // Override getApi() after init to avoid hitting unstubbed mocks during init
        val api = mockk<OpenCodeApi>()
        every { connectionManager.getApi() } returns api
        coEvery { api.abortSession(any(), any()) } returns true
        events.emit(OpenCodeEvent.MessageUpdated(assistantMessage(id = "m1", sessionId = "session-1", createdAt = 1)))
        events.emit(OpenCodeEvent.MessagePartUpdated(textPart(id = "p1", messageId = "m1", sessionId = "session-1", text = "Hi"), delta = null))
        events.emit(OpenCodeEvent.MessagePartUpdated(textPart(id = "p1", messageId = "m1", sessionId = "session-1", text = "ignored"), delta = "!"))
        events.emit(OpenCodeEvent.SessionStatusChanged("session-1", SessionStatus.Busy))
        flushMessages()

        vm.abortSession()
        flushMessages()

        val text = vm.currentMessages().single().parts.single() as Part.Text
        assertFalse(text.isStreaming)
        assertFalse(vm.uiState.value.isBusy)
        assertFalse(vm.uiState.value.isSending)
    }

    private fun TestScope.createViewModel(): ChatViewModel {
        val vm = ChatViewModel(
            savedStateHandle = SavedStateHandle(mapOf(Screen.Chat.ARG_SESSION_ID to "session-1")),
            connectionManager = connectionManager,
            directoryManager = directoryManager,
            messageMapper = messageMapper,
            settingsDataStore = settingsDataStore
        )
        advanceUntilIdle()
        return vm
    }

    private fun TestScope.flushMessages() {
        advanceUntilIdle()
        advanceUntilIdle()
    }

    private fun ChatViewModel.currentMessages(): List<MessageWithParts> =
        messageStore.currentMessagesSnapshot()

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
            title = "Allow",
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
