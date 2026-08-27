package dev.blazelight.p4oc.ui.screens.sessions

import androidx.lifecycle.SavedStateHandle
import dev.blazelight.p4oc.data.remote.dto.ForkSessionRequest
import dev.blazelight.p4oc.data.remote.mapper.MessageMapper
import dev.blazelight.p4oc.data.session.SessionRepositoryImpl
import dev.blazelight.p4oc.fakes.FakeWorkspaceClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionListViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun createSession_cancellationDoesNotSetError() = runTest(dispatcher) {
        val client = FakeWorkspaceClient().apply {
            createSessionFailure = CancellationException("leaving screen")
        }
        val repository = repository(client)
        val viewModel = SessionListViewModel(repository)

        viewModel.createSession(title = "new", directory = null)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        repository.close()
    }

    @Test
    fun forkSession_usesUnboundedRequestAndNavigatesToReturnedSession() = runTest(dispatcher) {
        val returned = FakeWorkspaceClient.sessionDto(
            id = "forked-session",
            directory = "/returned/worktree",
            parentID = "source-session",
        )
        val client = FakeWorkspaceClient().apply {
            setSessions(FakeWorkspaceClient.sessionDto("source-session", directory = "/source/worktree"))
            forkSessionResult = returned
        }
        val repository = repository(client)
        val viewModel = SessionListViewModel(repository)
        advanceUntilIdle()

        viewModel.forkSession("source-session")
        advanceUntilIdle()

        assertEquals(
            listOf(
                FakeWorkspaceClient.ForkSessionCall(
                    sourceSessionId = "source-session",
                    request = ForkSessionRequest(messageID = null),
                ),
            ),
            client.forkSessionCallsLog,
        )
        assertTrue(viewModel.uiState.value.sessions.any { it.session.id == "forked-session" })
        assertEquals("forked-session", viewModel.uiState.value.newSessionId)
        assertEquals("/returned/worktree", viewModel.uiState.value.newSessionDirectory)
        assertFalse("source-session" in viewModel.uiState.value.forkingSessionIds)
        repository.close()
    }

    @Test
    fun forkSession_failureShowsReadableErrorAndClearsRowBusyState() = runTest(dispatcher) {
        val client = FakeWorkspaceClient().apply {
            setSessions(FakeWorkspaceClient.sessionDto("source-session"))
            forkSessionFailure = IllegalStateException("raw server failure")
        }
        val repository = repository(client)
        val viewModel = SessionListViewModel(repository)
        advanceUntilIdle()

        viewModel.forkSession("source-session")
        advanceUntilIdle()

        assertEquals("Could not fork the session. Try again.", viewModel.uiState.value.error)
        assertFalse("source-session" in viewModel.uiState.value.forkingSessionIds)
        repository.close()
    }

    @Test
    fun updateSearchQuery_searchesServerAfterDebounce() = runTest(dispatcher) {
        val client = FakeWorkspaceClient().apply {
            projects = listOf(FakeWorkspaceClient.projectDto("p1", "/project"))
            sessionsByDirectoryAndSearch = mapOf(
                Pair(null, "apple") to listOf(FakeWorkspaceClient.sessionDto("global", title = "apple global", directory = "/global")),
                Pair("/project", "apple") to listOf(FakeWorkspaceClient.sessionDto("project", title = "apple project", directory = "/project")),
            )
        }
        val repository = repository(client)
        val viewModel = SessionListViewModel(repository)
        advanceUntilIdle()

        viewModel.updateSearchQuery("apple", directory = null)
        advanceTimeBy(299)
        assertTrue(client.listSessionsCallsLog.none { it.search == "apple" })

        advanceTimeBy(1)
        advanceUntilIdle()

        val searchCalls = client.listSessionsCallsLog.filter { it.search == "apple" }
        assertEquals(listOf(null, "/project"), searchCalls.map { it.directory })
        assertTrue(searchCalls.all { it.scope == null && it.roots == true && it.limit == 100 })
        assertEquals(listOf("global", "project"), viewModel.uiState.value.searchResults.map { it.session.id })
        assertEquals(SessionSearchStatus.Current, viewModel.uiState.value.searchStatus)
        repository.close()
    }

    @Test
    fun updateSearchQuery_refinesPreviousServerResultsWhileNextSearchPending() = runTest(dispatcher) {
        val client = FakeWorkspaceClient().apply {
            sessionsByDirectoryAndSearch = mapOf(
                Pair(null, "app") to listOf(
                    FakeWorkspaceClient.sessionDto("apple", title = "apple migration"),
                    FakeWorkspaceClient.sessionDto("apricot", title = "apricot cleanup"),
                ),
                Pair(null, "apple") to listOf(FakeWorkspaceClient.sessionDto("apple", title = "apple migration")),
            )
        }
        val repository = repository(client)
        val viewModel = SessionListViewModel(repository)
        advanceUntilIdle()

        viewModel.updateSearchQuery("app", directory = null)
        advanceTimeBy(300)
        advanceUntilIdle()
        assertEquals(listOf("apple", "apricot"), viewModel.uiState.value.displayedSearchResults.map { it.session.id })

        viewModel.updateSearchQuery("apple", directory = null)
        assertEquals(listOf("apple"), viewModel.uiState.value.displayedSearchResults.map { it.session.id })
        assertEquals(SessionSearchStatus.Refining, viewModel.uiState.value.searchStatus)

        advanceTimeBy(300)
        advanceUntilIdle()
        assertEquals("apple", viewModel.uiState.value.serverSearchQuery)
        assertEquals(listOf("apple"), viewModel.uiState.value.displayedSearchResults.map { it.session.id })
        assertEquals(SessionSearchStatus.Current, viewModel.uiState.value.searchStatus)
        repository.close()
    }

    @Test
    fun updateSearchQuery_setsFailedStatusWhenServerSearchFails() = runTest(dispatcher) {
        val client = FakeWorkspaceClient().apply {
            listSessionsFailure = IllegalStateException("network down")
        }
        val repository = repository(client)
        val viewModel = SessionListViewModel(repository)
        advanceUntilIdle()

        viewModel.updateSearchQuery("apple", directory = "/project")
        advanceTimeBy(300)
        advanceUntilIdle()

        assertEquals(SessionSearchStatus.Failed, viewModel.uiState.value.searchStatus)
        assertEquals(
            "Could not search sessions. Check the connection and try again.",
            viewModel.uiState.value.searchError,
        )
        repository.close()
    }

    @Test
    fun updateSearchQuery_clearingQueryRestoresNormalList() = runTest(dispatcher) {
        val client = FakeWorkspaceClient().apply {
            setSessions(FakeWorkspaceClient.sessionDto("normal", title = "normal"))
            sessionsByDirectoryAndSearch = mapOf(
                Pair(null, "apple") to listOf(FakeWorkspaceClient.sessionDto("apple", title = "apple migration")),
            )
        }
        val repository = repository(client)
        val viewModel = SessionListViewModel(repository)
        advanceUntilIdle()

        viewModel.updateSearchQuery("apple", directory = null)
        advanceTimeBy(300)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSearchActive)

        viewModel.updateSearchQuery("", directory = null)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSearchActive)
        assertTrue(viewModel.uiState.value.searchResults.isEmpty())
        assertNull(viewModel.uiState.value.searchStatus)
        repository.close()
    }

    @Test
    fun recreateWithSameSavedStateHandle_restoresGlobalSearchQueryAndExpandedSessions() = runTest(dispatcher) {
        val client = FakeWorkspaceClient().apply {
            sessionsByDirectoryAndSearch = mapOf(
                Pair(null, "apple") to listOf(FakeWorkspaceClient.sessionDto("global", title = "apple global")),
            )
        }
        val repository = repository(client)
        val savedStateHandle = SavedStateHandle()
        val original = SessionListViewModel(repository, savedStateHandle)
        advanceUntilIdle()

        original.updateSearchQuery("apple", directory = null)
        original.toggleSessionExpanded("global")
        advanceTimeBy(300)
        advanceUntilIdle()

        val recreated = SessionListViewModel(repository, savedStateHandle)
        advanceUntilIdle()
        recreated.updateSearchDirectory(null)
        advanceUntilIdle()

        assertEquals("apple", recreated.uiState.value.searchQuery)
        assertNull(recreated.uiState.value.searchDirectory)
        assertEquals(setOf("global"), recreated.uiState.value.expandedSessionIds)
        assertEquals(listOf("global"), recreated.uiState.value.displayedSearchResults.map { it.session.id })
        assertEquals(SessionSearchStatus.Current, recreated.uiState.value.searchStatus)
        repository.close()
    }

    @Test
    fun searchQueryAndExpandedSessions_areIsolatedByDirectoryContext() = runTest(dispatcher) {
        val client = FakeWorkspaceClient().apply {
            sessionsByDirectoryAndSearch = mapOf(
                Pair("/project-a", "apple") to listOf(
                    FakeWorkspaceClient.sessionDto("a", title = "apple work", directory = "/project-a"),
                ),
                Pair("/project-b", "banana") to listOf(
                    FakeWorkspaceClient.sessionDto("b", title = "banana work", directory = "/project-b"),
                ),
            )
        }
        val repository = repository(client)
        val viewModel = SessionListViewModel(repository, SavedStateHandle())
        advanceUntilIdle()

        viewModel.updateSearchQuery("apple", directory = "/project-a")
        viewModel.toggleSessionExpanded("a")
        advanceTimeBy(300)
        advanceUntilIdle()
        assertEquals(listOf("a"), viewModel.uiState.value.displayedSearchResults.map { it.session.id })

        viewModel.updateSearchDirectory("/project-b")
        advanceUntilIdle()
        assertEquals("", viewModel.uiState.value.searchQuery)
        assertEquals("/project-b", viewModel.uiState.value.searchDirectory)
        assertTrue(viewModel.uiState.value.expandedSessionIds.isEmpty())
        assertFalse(viewModel.uiState.value.isSearchActive)

        viewModel.updateSearchQuery("banana", directory = "/project-b")
        viewModel.toggleSessionExpanded("b")
        advanceTimeBy(300)
        advanceUntilIdle()
        assertEquals(listOf("b"), viewModel.uiState.value.displayedSearchResults.map { it.session.id })

        viewModel.updateSearchDirectory("/project-a")
        advanceUntilIdle()

        assertEquals("apple", viewModel.uiState.value.searchQuery)
        assertEquals("/project-a", viewModel.uiState.value.searchDirectory)
        assertEquals(setOf("a"), viewModel.uiState.value.expandedSessionIds)
        assertEquals(listOf("a"), viewModel.uiState.value.displayedSearchResults.map { it.session.id })
        assertEquals(SessionSearchStatus.Current, viewModel.uiState.value.searchStatus)
        repository.close()
    }

    @Test
    fun updateSearchQuery_blankClearsSearchOnlyForThatDirectoryContext() = runTest(dispatcher) {
        val client = FakeWorkspaceClient().apply {
            sessionsByDirectoryAndSearch = mapOf(
                Pair("/project-a", "apple") to listOf(
                    FakeWorkspaceClient.sessionDto("a", title = "apple work", directory = "/project-a"),
                ),
                Pair("/project-b", "banana") to listOf(
                    FakeWorkspaceClient.sessionDto("b", title = "banana work", directory = "/project-b"),
                ),
            )
        }
        val repository = repository(client)
        val viewModel = SessionListViewModel(repository, SavedStateHandle())
        advanceUntilIdle()

        viewModel.updateSearchQuery("apple", directory = "/project-a")
        advanceTimeBy(300)
        advanceUntilIdle()
        viewModel.updateSearchQuery("banana", directory = "/project-b")
        advanceTimeBy(300)
        advanceUntilIdle()

        viewModel.updateSearchQuery("", directory = "/project-b")
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.searchQuery)
        assertEquals("/project-b", viewModel.uiState.value.searchDirectory)
        assertFalse(viewModel.uiState.value.isSearchActive)
        assertTrue(viewModel.uiState.value.searchResults.isEmpty())
        assertNull(viewModel.uiState.value.searchStatus)

        viewModel.updateSearchDirectory("/project-a")
        advanceUntilIdle()

        assertEquals("apple", viewModel.uiState.value.searchQuery)
        assertEquals(listOf("a"), viewModel.uiState.value.displayedSearchResults.map { it.session.id })
        assertEquals(SessionSearchStatus.Current, viewModel.uiState.value.searchStatus)
        repository.close()
    }

    @Test
    fun restoredSearchWithNoMatches_keepsQueryAndCurrentNoResultsState() = runTest(dispatcher) {
        val client = FakeWorkspaceClient().apply {
            sessionsByDirectoryAndSearch = mapOf(
                Pair("/project", "missing") to emptyList(),
            )
        }
        val repository = repository(client)
        val savedStateHandle = SavedStateHandle()
        val original = SessionListViewModel(repository, savedStateHandle)
        advanceUntilIdle()

        original.updateSearchQuery("missing", directory = "/project")
        advanceTimeBy(300)
        advanceUntilIdle()
        assertEquals(SessionSearchStatus.Current, original.uiState.value.searchStatus)
        assertTrue(original.uiState.value.displayedSearchResults.isEmpty())

        val recreated = SessionListViewModel(repository, savedStateHandle)
        advanceUntilIdle()
        recreated.updateSearchDirectory("/project")
        advanceUntilIdle()

        assertEquals("missing", recreated.uiState.value.searchQuery)
        assertEquals("/project", recreated.uiState.value.searchDirectory)
        assertTrue(recreated.uiState.value.isSearchActive)
        assertTrue(recreated.uiState.value.searchResults.isEmpty())
        assertTrue(recreated.uiState.value.displayedSearchResults.isEmpty())
        assertEquals("missing", recreated.uiState.value.serverSearchQuery)
        assertEquals(SessionSearchStatus.Current, recreated.uiState.value.searchStatus)
        repository.close()
    }

    @Test
    fun oversizedRestoredState_isBoundedAtSemanticBoundaries() = runTest(dispatcher) {
        val queries = HashMap<String, String>()
        val expanded = HashMap<String, ArrayList<String>>()
        val recency = ArrayList<String>()
        repeat(SessionListViewModel.MAX_SAVED_CONTEXTS + 4) { index ->
            val context = "/project-$index"
            queries[context] = "q".repeat(SessionListViewModel.MAX_SEARCH_QUERY_CHARS + 20)
            expanded[context] = ArrayList(
                List(SessionListViewModel.MAX_EXPANDED_SESSION_IDS_PER_CONTEXT + 5) { "id-$index-$it" },
            )
            recency += context
        }
        val handle = SavedStateHandle(
            mapOf(
                "session_list_search_queries" to queries,
                "session_list_expanded_sessions" to expanded,
                "session_list_context_recency" to recency,
            ),
        )
        val repository = repository(FakeWorkspaceClient())
        val viewModel = SessionListViewModel(repository, handle)
        advanceUntilIdle()

        viewModel.updateSearchDirectory("/project-19")
        advanceUntilIdle()

        assertEquals(SessionListViewModel.MAX_SEARCH_QUERY_CHARS, viewModel.uiState.value.searchQuery.length)
        assertEquals(
            (5 until 69).map { "id-19-$it" }.toSet(),
            viewModel.uiState.value.expandedSessionIds,
        )
        assertEquals(
            SessionListViewModel.MAX_SAVED_CONTEXTS,
            handle.get<HashMap<String, String>>("session_list_search_queries")?.size,
        )
        assertEquals(
            SessionListViewModel.MAX_SAVED_CONTEXTS,
            handle.get<HashMap<String, ArrayList<String>>>("session_list_expanded_sessions")?.size,
        )
        repository.close()
    }

    @Test
    fun runtimeState_capsQueryContextsAndMostRecentExpandedIds() = runTest(dispatcher) {
        val client = FakeWorkspaceClient()
        val repository = repository(client)
        val handle = SavedStateHandle()
        val viewModel = SessionListViewModel(repository, handle)
        advanceUntilIdle()

        repeat(SessionListViewModel.MAX_SAVED_CONTEXTS + 2) { index ->
            viewModel.updateSearchQuery("query-$index", "/project-$index")
        }
        val longQuery = "x".repeat(SessionListViewModel.MAX_SEARCH_QUERY_CHARS + 50)
        viewModel.updateSearchQuery(longQuery, "/project-17")
        advanceTimeBy(300)
        advanceUntilIdle()
        repeat(SessionListViewModel.MAX_EXPANDED_SESSION_IDS_PER_CONTEXT + 3) { index ->
            viewModel.toggleSessionExpanded("session-$index")
        }

        val persistedQueries = handle
            .get<HashMap<String, String>>("session_list_search_queries")
            .orEmpty()
        val persistedExpanded = handle
            .get<HashMap<String, ArrayList<String>>>("session_list_expanded_sessions")
            .orEmpty()
        assertEquals(SessionListViewModel.MAX_SAVED_CONTEXTS, persistedQueries.size)
        assertFalse("/project-0" in persistedQueries)
        assertFalse("/project-1" in persistedQueries)
        assertEquals(SessionListViewModel.MAX_SEARCH_QUERY_CHARS, viewModel.uiState.value.searchQuery.length)
        assertTrue(
            client.listSessionsCallsLog
                .filter { it.search != null }
                .all { it.search!!.length <= SessionListViewModel.MAX_SEARCH_QUERY_CHARS },
        )
        assertEquals(
            (3 until 67).map { "session-$it" },
            persistedExpanded.getValue("/project-17"),
        )
        assertEquals(persistedExpanded.getValue("/project-17").toSet(), viewModel.uiState.value.expandedSessionIds)
        repository.close()
    }

    private fun repository(client: FakeWorkspaceClient): SessionRepositoryImpl = SessionRepositoryImpl(
        client = client,
        messageMapper = MessageMapper(Json { ignoreUnknownKeys = true }),
        dispatcher = dispatcher,
    )
}
