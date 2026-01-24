package dev.blazelight.p4oc.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import dev.blazelight.p4oc.core.network.ConnectionState
import dev.blazelight.p4oc.core.network.OpenCodeApi
import dev.blazelight.p4oc.core.network.OpenCodeEventSource
import dev.blazelight.p4oc.data.remote.dto.*
import dev.blazelight.p4oc.data.remote.mapper.MessageMapper
import dev.blazelight.p4oc.data.remote.mapper.PartMapper
import dev.blazelight.p4oc.data.remote.mapper.SessionMapper
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.model.SessionStatus
import dev.blazelight.p4oc.ui.navigation.Screen
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @MockK
    private lateinit var api: OpenCodeApi

    @MockK
    private lateinit var eventSource: OpenCodeEventSource

    private lateinit var sessionMapper: SessionMapper
    private lateinit var messageMapper: MessageMapper
    private lateinit var partMapper: PartMapper

    private val testDispatcher = StandardTestDispatcher()
    private val testTimeMillis = 1737194400000L

    private val eventsFlow = MutableSharedFlow<OpenCodeEvent>()
    private val connectionStateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    private val testSessionDto = SessionDto(
        id = "session-1",
        projectID = "project-1",
        directory = "/test/dir",
        parentID = null,
        title = "Test Session",
        version = "1.0",
        time = TimeDto(created = testTimeMillis, updated = testTimeMillis),
        summary = null,
        share = null
    )

    private val testMessageInfoDto = MessageInfoDto(
        id = "msg-1",
        sessionID = "session-1",
        time = MessageTimeDto(created = testTimeMillis),
        role = "user",
        parentID = null,
        model = null,
        agent = "default",
        cost = null,
        tokens = null,
        error = null,
        path = null
    )

    private val testPartDto = PartDto(
        id = "part-1",
        sessionID = "session-1",
        messageID = "msg-1",
        type = "text",
        time = null,
        text = "Hello world",
        callID = null,
        toolName = null,
        state = null,
        mime = null,
        filename = null,
        url = null,
        hash = null,
        files = null
    )

    private val testMessageWrapperDto = MessageWrapperDto(
        info = testMessageInfoDto,
        parts = listOf(testPartDto)
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        Dispatchers.setMain(testDispatcher)

        sessionMapper = SessionMapper()
        messageMapper = MessageMapper()
        partMapper = PartMapper()

        every { eventSource.events } returns eventsFlow
        every { eventSource.connectionState } returns connectionStateFlow
        every { eventSource.connect() } just Runs
        every { eventSource.disconnect() } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ChatViewModel {
        val savedStateHandle = SavedStateHandle(mapOf(Screen.Chat.ARG_SESSION_ID to "session-1"))
        return ChatViewModel(
            savedStateHandle = savedStateHandle,
            api = api,
            eventSource = eventSource,
            sessionMapper = sessionMapper,
            messageMapper = messageMapper,
            partMapper = partMapper
        )
    }

    @Test
    fun `initial state has default values`() = runTest {
        coEvery { api.getSession("session-1") } returns testSessionDto
        coEvery { api.getMessages("session-1", null) } returns emptyList()

        val viewModel = createViewModel()

        assertNull(viewModel.uiState.value.session)
        assertEquals("", viewModel.uiState.value.inputText)
        assertFalse(viewModel.uiState.value.isSending)
    }

    @Test
    fun `loadSession populates session in state`() = runTest {
        coEvery { api.getSession("session-1") } returns testSessionDto
        coEvery { api.getMessages("session-1", null) } returns emptyList()

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("session-1", viewModel.uiState.value.session?.id)
        assertEquals("Test Session", viewModel.uiState.value.session?.title)
    }

    @Test
    fun `loadMessages populates messages list`() = runTest {
        val messagesWithParts = listOf(testMessageWrapperDto)
        coEvery { api.getSession("session-1") } returns testSessionDto
        coEvery { api.getMessages("session-1", null) } returns messagesWithParts

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(1, viewModel.messages.value.size)
        assertEquals("msg-1", viewModel.messages.value[0].message.id)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `updateInput updates inputText`() = runTest {
        coEvery { api.getSession("session-1") } returns testSessionDto
        coEvery { api.getMessages("session-1", null) } returns emptyList()

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateInput("Hello")

        assertEquals("Hello", viewModel.uiState.value.inputText)
    }

    @Test
    fun `sendMessage clears input and sets isSending`() = runTest {
        coEvery { api.getSession("session-1") } returns testSessionDto
        coEvery { api.getMessages("session-1", null) } returns emptyList()
        coEvery { api.sendMessageAsync("session-1", any()) } just Runs

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateInput("Hello world")
        viewModel.sendMessage()

        assertEquals("", viewModel.uiState.value.inputText)
        assertTrue(viewModel.uiState.value.isSending)
    }

    @Test
    fun `sendMessage with empty text does nothing`() = runTest {
        coEvery { api.getSession("session-1") } returns testSessionDto
        coEvery { api.getMessages("session-1", null) } returns emptyList()

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateInput("   ")
        viewModel.sendMessage()

        coVerify(exactly = 0) { api.sendMessageAsync(any(), any()) }
    }

    @Test
    fun `sendMessage success sets isBusy true`() = runTest {
        coEvery { api.getSession("session-1") } returns testSessionDto
        coEvery { api.getMessages("session-1", null) } returns emptyList()
        coEvery { api.sendMessageAsync("session-1", any()) } just Runs

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateInput("Test message")
        viewModel.sendMessage()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isBusy)
        assertFalse(viewModel.uiState.value.isSending)
    }

    @Test
    fun `sendMessage failure restores input and sets error`() = runTest {
        coEvery { api.getSession("session-1") } returns testSessionDto
        coEvery { api.getMessages("session-1", null) } returns emptyList()
        coEvery { api.sendMessageAsync("session-1", any()) } throws RuntimeException("Network error")

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateInput("Test message")
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals("Test message", viewModel.uiState.value.inputText)
        assertNotNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.error!!.contains("Network error"))
    }

    @Test
    fun `clearError clears error state`() = runTest {
        coEvery { api.getSession("session-1") } throws RuntimeException("Error")
        coEvery { api.getMessages("session-1", null) } returns emptyList()

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)

        viewModel.clearError()

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `abortSession calls API and clears busy state`() = runTest {
        coEvery { api.getSession("session-1") } returns testSessionDto
        coEvery { api.getMessages("session-1", null) } returns emptyList()
        coEvery { api.abortSession("session-1") } returns true

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.abortSession()
        advanceUntilIdle()

        coVerify { api.abortSession("session-1") }
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun `SessionStatusChanged event updates isBusy`() = runTest {
        coEvery { api.getSession("session-1") } returns testSessionDto
        coEvery { api.getMessages("session-1", null) } returns emptyList()

        val viewModel = createViewModel()
        advanceUntilIdle()

        eventsFlow.emit(OpenCodeEvent.SessionStatusChanged("session-1", SessionStatus.Busy))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isBusy)

        eventsFlow.emit(OpenCodeEvent.SessionStatusChanged("session-1", SessionStatus.Idle))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun `connectionState reflects eventSource state`() = runTest {
        coEvery { api.getSession("session-1") } returns testSessionDto
        coEvery { api.getMessages("session-1", null) } returns emptyList()

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(ConnectionState.Disconnected, viewModel.connectionState.value)

        connectionStateFlow.value = ConnectionState.Connected

        assertEquals(ConnectionState.Connected, viewModel.connectionState.value)
    }
}
