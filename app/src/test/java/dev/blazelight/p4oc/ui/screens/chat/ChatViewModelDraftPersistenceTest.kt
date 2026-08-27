package dev.blazelight.p4oc.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import dev.blazelight.p4oc.core.datastore.ChatSettings
import dev.blazelight.p4oc.core.datastore.NotificationSettings
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.core.datastore.VisualSettings
import dev.blazelight.p4oc.core.haptic.HapticFeedback
import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.core.network.ConnectionState
import dev.blazelight.p4oc.core.network.OpenCodeApi
import dev.blazelight.p4oc.data.files.FileRepository
import dev.blazelight.p4oc.data.files.FileRepositoryFactory
import dev.blazelight.p4oc.data.remote.dto.FileNodeDto
import dev.blazelight.p4oc.data.remote.mapper.MessageMapper
import dev.blazelight.p4oc.data.server.ActiveServerApiProvider
import dev.blazelight.p4oc.data.session.SessionRepositoryImpl
import dev.blazelight.p4oc.data.workspace.WorkspaceClient
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
import kotlinx.serialization.json.Json
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
class ChatViewModelDraftPersistenceTest {

    @get:Rule
    val mainDispatcherRule = DraftPersistenceMainDispatcherRule()

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

        messageMapper = MessageMapper(Json { ignoreUnknownKeys = true })
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
        every { settingsDataStore.visualSettings } returns flowOf(VisualSettings())
        every { settingsDataStore.notificationSettings } returns flowOf(NotificationSettings())
        coEvery { settingsDataStore.getSelectedAgentForSession(any()) } returns null
        coEvery { settingsDataStore.setSelectedAgentForSession(any(), any()) } returns Unit
        coEvery { settingsDataStore.getComposerSelectionForSession(any(), any()) } returns null

        hapticFeedback = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkObject(AppLog)
    }

    @Test
    fun updateInput_persistsDraftTextInSavedStateHandle() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf(Screen.Chat.ARG_SESSION_ID to "session-1"))
        val vm = createViewModel(savedStateHandle)

        vm.updateInput("unsent draft")

        assertEquals("unsent draft", savedStateHandle.get<String>("chat_draft_text"))
    }

    @Test
    fun updateInput_oversizedDraftRemovesPersistenceAndSmallerDraftRecovers() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf(Screen.Chat.ARG_SESSION_ID to "session-1"))
        val vm = createViewModel(savedStateHandle)

        vm.updateInput("x".repeat(70_000))

        assertEquals(70_000, vm.uiState.value.inputText.length)
        assertNull(savedStateHandle.get<String>("chat_draft_text"))

        vm.updateInput("small again")

        assertEquals("small again", savedStateHandle.get<String>("chat_draft_text"))
    }

    @Test
    fun createViewModel_restoresDraftTextFromSavedStateHandle() = runTest {
        val savedStateHandle = SavedStateHandle(
            mapOf(
                Screen.Chat.ARG_SESSION_ID to "session-1",
                "chat_draft_text" to "restored draft",
            )
        )

        val vm = createViewModel(savedStateHandle)

        assertEquals("restored draft", vm.uiState.value.inputText)
    }

    @Test
    fun createViewModel_doesNotShareDraftTextAcrossSavedStateHandles() = runTest {
        val firstHandle = SavedStateHandle(mapOf(Screen.Chat.ARG_SESSION_ID to "session-1"))
        val secondHandle = SavedStateHandle(mapOf(Screen.Chat.ARG_SESSION_ID to "session-2"))
        val first = createViewModel(firstHandle)

        first.updateInput("session one draft")
        val second = createViewModel(secondHandle)

        assertEquals("session one draft", first.uiState.value.inputText)
        assertEquals("", second.uiState.value.inputText)
        assertNull(secondHandle.get<String>("chat_draft_text"))
    }

    @Test
    fun createViewModel_restoresAttachedFilesFromSavedStateHandle() = runTest {
        val savedStateHandle = SavedStateHandle(
            mapOf(
                Screen.Chat.ARG_SESSION_ID to "session-1",
                "chat_attached_files" to """[{"path":"src/Main.kt","name":"Main.kt","mimeType":"text/x-kotlin"}]""",
            )
        )

        val vm = createViewModel(savedStateHandle)

        assertEquals(listOf("src/Main.kt"), vm.filePickerManager.attachedFiles.value.map { it.path })
    }

    @Test
    fun attachments_oversizedJsonRemovesPersistenceAndSmallerListRecovers() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf(Screen.Chat.ARG_SESSION_ID to "session-1"))
        val vm = createViewModel(savedStateHandle)
        val oversized = SelectedFile(
            path = "src/${"x".repeat(70_000)}.kt",
            name = "Large.kt",
            mimeType = "text/x-kotlin",
        )

        vm.filePickerManager.restoreAttachedFiles(listOf(oversized))
        advanceUntilIdle()

        assertEquals(oversized, vm.filePickerManager.attachedFiles.value.single())
        assertNull(savedStateHandle.get<String>("chat_attached_files"))

        vm.filePickerManager.detachFile(oversized.path)
        vm.filePickerManager.restoreAttachedFiles(
            listOf(SelectedFile(path = "src/Small.kt", name = "Small.kt", mimeType = "text/x-kotlin"))
        )
        advanceUntilIdle()

        val persisted = savedStateHandle.get<String>("chat_attached_files")
        assertEquals("src/Small.kt", Json.decodeFromString<List<SelectedFile>>(persisted!!).single().path)
    }

    @Test
    fun createViewModel_restoresAvailableAttachmentAsSendable() = runTest {
        coEvery { api.listFiles("src", "/test", null) } returns listOf(
            FileNodeDto(
                name = "Main.kt",
                path = "src/Main.kt",
                absolute = "/test/src/Main.kt",
                type = "file",
            )
        )
        coEvery { api.sendMessageAsync(any(), any(), any(), null) } returns Unit
        val savedStateHandle = SavedStateHandle(
            mapOf(
                Screen.Chat.ARG_SESSION_ID to "session-1",
                "chat_attached_files" to """[{"path":"src/Main.kt","name":"Main.kt","mimeType":"text/x-kotlin"}]""",
            )
        )

        val vm = createViewModel(savedStateHandle)
        advanceUntilIdle()

        val restored = vm.filePickerManager.attachedFiles.value.single()
        assertEquals("src/Main.kt", restored.path)
        assertTrue(restored.available)

        vm.sendMessage()
        advanceUntilIdle()

        coVerify(exactly = 1) { api.sendMessageAsync("session-1", any(), "/test", null) }
    }

    @Test
    fun createViewModel_marksRestoredMissingAttachmentUnavailableAndBlocksSend() = runTest {
        coEvery { api.listFiles("src", "/test", null) } returns emptyList()
        coEvery { api.sendMessageAsync(any(), any(), any(), null) } returns Unit
        val savedStateHandle = SavedStateHandle(
            mapOf(
                Screen.Chat.ARG_SESSION_ID to "session-1",
                "chat_attached_files" to missingAttachmentJson,
            )
        )

        val vm = createViewModel(savedStateHandle)
        advanceUntilIdle()

        val restored = vm.filePickerManager.attachedFiles.value.single()
        assertEquals("src/Missing.kt", restored.path)
        assertFalse(restored.available)

        vm.updateInput("please read this")
        assertFalse(vm.sendMessage())
        advanceUntilIdle()

        coVerify(exactly = 0) { api.sendMessageAsync(any(), any(), any(), null) }
        assertEquals("please read this", vm.uiState.value.inputText)
        assertEquals(listOf("src/Missing.kt"), vm.filePickerManager.attachedFiles.value.map { it.path })
        assertEquals("Remove unavailable attachments before sending.", vm.uiState.value.error)
    }

    @Test
    fun detachFile_removingUnavailableRestoredAttachmentClearsSendBlocker() = runTest {
        coEvery { api.listFiles("src", "/test", null) } returns emptyList()
        coEvery { api.sendMessageAsync(any(), any(), any(), null) } returns Unit
        val savedStateHandle = SavedStateHandle(
            mapOf(
                Screen.Chat.ARG_SESSION_ID to "session-1",
                "chat_attached_files" to missingAttachmentJson,
            )
        )
        val vm = createViewModel(savedStateHandle)
        advanceUntilIdle()
        assertFalse(vm.filePickerManager.attachedFiles.value.single().available)

        vm.filePickerManager.detachFile("src/Missing.kt")
        vm.updateInput("send without the missing file")
        vm.sendMessage()
        advanceUntilIdle()

        assertTrue(vm.filePickerManager.attachedFiles.value.isEmpty())
        coVerify(exactly = 1) { api.sendMessageAsync("session-1", any(), "/test", null) }
    }

    @Test
    fun createViewModel_marksRestoredAttachmentUnavailableWhenValidationFails() = runTest {
        coEvery { api.listFiles("src", "/test", null) } throws HttpException(
            Response.error<Unit>(403, "forbidden".toResponseBody(null))
        )
        val savedStateHandle = SavedStateHandle(
            mapOf(
                Screen.Chat.ARG_SESSION_ID to "session-1",
                "chat_attached_files" to privateAttachmentJson,
            )
        )

        val vm = createViewModel(savedStateHandle)
        advanceUntilIdle()

        val restored = vm.filePickerManager.attachedFiles.value.single()
        assertEquals("src/Private.kt", restored.path)
        assertFalse(restored.available)
    }

    @Test
    fun acceptedSend_composerClearClearsPersistedDraftAndAttachments() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf(Screen.Chat.ARG_SESSION_ID to "session-1"))
        val vm = createViewModel(savedStateHandle)
        coEvery { api.sendMessageAsync(any(), any(), any(), null) } returns Unit
        coEvery { api.listFiles("src", "/test", null) } returns listOf(
            FileNodeDto(
                name = "Main.kt",
                path = "src/Main.kt",
                absolute = "/test/src/Main.kt",
                type = "file",
            )
        )
        vm.updateInput("hello")
        vm.filePickerManager.restoreAttachedFiles(
            listOf(SelectedFile(path = "src/Main.kt", name = "Main.kt", mimeType = "text/x-kotlin"))
        )
        advanceUntilIdle()
        assertEquals("hello", savedStateHandle.get<String>("chat_draft_text"))
        assertTrue(savedStateHandle.get<String>("chat_attached_files")?.contains("src/Main.kt") == true)

        assertTrue(vm.sendMessage())
        assertEquals("hello", savedStateHandle.get<String>("chat_draft_text"))
        vm.updateInput("")
        advanceUntilIdle()

        assertNull(savedStateHandle.get<String>("chat_draft_text"))
        assertNull(savedStateHandle.get<String>("chat_attached_files"))
    }

    private val missingAttachmentJson =
        """[{"path":"src/Missing.kt","name":"Missing.kt","mimeType":"text/x-kotlin"}]"""

    private val privateAttachmentJson =
        """[{"path":"src/Private.kt","name":"Private.kt","mimeType":"text/x-kotlin"}]"""

    private fun TestScope.createViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(
            mapOf(Screen.Chat.ARG_SESSION_ID to "session-1")
        )
    ): ChatViewModel {
        sessionRepository = SessionRepositoryImpl(
            workspaceClient,
            messageMapper,
            dispatcher = StandardTestDispatcher(mainDispatcherRule.dispatcher.scheduler),
        )
        val fileRepository = testFileRepository()
        return ChatViewModel(
            savedStateHandle = savedStateHandle,
            workspaceClient = workspaceClient,
            sessionRepository = sessionRepository,
            uploadCoordinator = testUploadCoordinator(fileRepository),
            settingsDataStore = settingsDataStore,
            hapticFeedback = hapticFeedback,
        ).also { advanceUntilIdle() }
    }

    private fun testFileRepository(): FileRepository = FileRepositoryFactory.create(workspaceClient)

    private fun testUploadCoordinator(repo: FileRepository) = UploadCoordinator(
        scope = CoroutineScope(Dispatchers.Main),
        repositoryFactory = { repo },
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class DraftPersistenceMainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
