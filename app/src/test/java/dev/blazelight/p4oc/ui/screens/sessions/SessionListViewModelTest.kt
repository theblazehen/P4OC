package dev.blazelight.p4oc.ui.screens.sessions

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

        viewModel.createSession(title = "new")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
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
        assertEquals("Search failed: network down", viewModel.uiState.value.searchError)
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

    private fun repository(client: FakeWorkspaceClient): SessionRepositoryImpl = SessionRepositoryImpl(
        client = client,
        messageMapper = MessageMapper(Json { ignoreUnknownKeys = true }),
        dispatcher = dispatcher,
    )
}
