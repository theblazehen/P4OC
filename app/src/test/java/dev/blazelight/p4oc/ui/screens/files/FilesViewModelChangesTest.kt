package dev.blazelight.p4oc.ui.screens.files

import dev.blazelight.p4oc.data.vcs.WorkspaceChangesResult
import dev.blazelight.p4oc.data.vcs.WorkspaceChangesSnapshot
import dev.blazelight.p4oc.data.vcs.WorkspacePatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
class FilesViewModelChangesTest {
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
    fun entryPublishesSnapshotAtomicallyAndPreservesNormalFilesState() = runTest(dispatcher) {
        var completeLoad: ((WorkspaceChangesResult<WorkspaceChangesSnapshot>) -> Unit)? = null
        val repository = FakeChangesRepository().apply {
            snapshotHandler = {
                suspendCoroutine { continuation ->
                    completeLoad = { result -> continuation.resume(result) }
                }
            }
        }
        val viewModel = changesViewModel(repository)
        runCurrent()
        viewModel.navigateTo("src/main")
        viewModel.setSearchActive(true)
        viewModel.updateSearchQuery("needle")
        runCurrent()
        val filesState = viewModel.uiState.value
        val editState = viewModel.editState.value

        viewModel.enterChanges()
        runCurrent()

        val loading = viewModel.changesState.value
        assertTrue(loading.isActive)
        assertTrue(loading.isLoading)
        assertFalse(loading.isRefreshing)
        assertNull(loading.snapshot)
        assertNull(loading.failure)
        assertEquals(filesState, viewModel.uiState.value)
        assertEquals(editState, viewModel.editState.value)

        val loaded = changesSnapshot(paths = listOf("z.kt", "A.kt", "deleted.txt"))
        checkNotNull(completeLoad).invoke(WorkspaceChangesResult.Success(loaded))
        runCurrent()

        val state = viewModel.changesState.value
        assertTrue(state.isActive)
        assertFalse(state.isLoading)
        assertSame(loaded, state.snapshot)
        assertEquals(listOf("z.kt", "A.kt", "deleted.txt"), state.snapshot?.changes?.map { it.file })
        assertNull(state.failure)
        assertEquals(filesState, viewModel.uiState.value)
        assertEquals(editState, viewModel.editState.value)

        viewModel.exitChanges()

        assertEquals(WorkspaceChangesUiState(), viewModel.changesState.value)
        assertEquals(filesState, viewModel.uiState.value)
        assertEquals(editState, viewModel.editState.value)
    }

    @Test
    fun initialEmptyAndEveryListFailureHaveDeterministicStates() = runTest(dispatcher) {
        val emptyRepository = FakeChangesRepository().apply {
            snapshotHandler = { WorkspaceChangesResult.Success(changesSnapshot(paths = emptyList())) }
        }
        val emptyViewModel = changesViewModel(emptyRepository)
        runCurrent()
        emptyViewModel.enterChanges()
        runCurrent()

        val emptyState = emptyViewModel.changesState.value
        assertTrue(emptyState.isActive)
        assertFalse(emptyState.isLoading)
        assertTrue(checkNotNull(emptyState.snapshot).changes.isEmpty())
        assertNull(emptyState.failure)

        val cases = listOf(
            WorkspaceChangesResult.Unsupported to WorkspaceChangesFailureKind.Unsupported,
            WorkspaceChangesResult.TooLarge to WorkspaceChangesFailureKind.TooLarge,
            WorkspaceChangesResult.Malformed to WorkspaceChangesFailureKind.Malformed,
            WorkspaceChangesResult.Stale to WorkspaceChangesFailureKind.Stale,
            WorkspaceChangesResult.AuthorizationFailure to WorkspaceChangesFailureKind.AuthorizationFailure,
            WorkspaceChangesResult.HttpFailure to WorkspaceChangesFailureKind.HttpFailure,
            WorkspaceChangesResult.NetworkFailure to WorkspaceChangesFailureKind.NetworkFailure,
            WorkspaceChangesResult.Failure to WorkspaceChangesFailureKind.Failure,
        )
        cases.forEach { (result, expectedFailure) ->
            val repository = FakeChangesRepository().apply {
                snapshotHandler = { result }
            }
            val viewModel = changesViewModel(repository)
            runCurrent()

            viewModel.enterChanges()
            assertTrue(viewModel.changesState.value.isLoading)
            runCurrent()

            val state = viewModel.changesState.value
            assertTrue(state.isActive)
            assertFalse(state.isLoading)
            assertFalse(state.isRefreshing)
            assertNull(state.snapshot)
            assertEquals(expectedFailure, state.failure)
            assertNull(state.refreshFailed)
            assertNull(state.selectedPath)
            assertEquals(WorkspaceChangesPatchState.None, state.patchState)
        }
    }

    @Test
    fun initialFailureCanBeRetriedWithoutReentering() = runTest(dispatcher) {
        val loaded = changesSnapshot(paths = listOf("fixed.kt"))
        val repository = FakeChangesRepository().apply {
            snapshotHandler = {
                if (snapshotCalls == 1) {
                    WorkspaceChangesResult.Failure
                } else {
                    WorkspaceChangesResult.Success(loaded)
                }
            }
        }
        val viewModel = changesViewModel(repository)
        runCurrent()
        viewModel.enterChanges()
        runCurrent()
        assertEquals(WorkspaceChangesFailureKind.Failure, viewModel.changesState.value.failure)

        viewModel.refreshChanges()

        assertTrue(viewModel.changesState.value.isLoading)
        assertNull(viewModel.changesState.value.failure)
        runCurrent()
        assertSame(loaded, viewModel.changesState.value.snapshot)
        assertEquals(2, repository.snapshotCalls)
    }

    @Test
    fun everyRetryableInitialFailureRerunsTheSnapshotOperation() = runTest(dispatcher) {
        val results = mutableListOf<WorkspaceChangesResult<WorkspaceChangesSnapshot>>(
            WorkspaceChangesResult.Malformed,
            WorkspaceChangesResult.Stale,
            WorkspaceChangesResult.AuthorizationFailure,
            WorkspaceChangesResult.HttpFailure,
            WorkspaceChangesResult.NetworkFailure,
            WorkspaceChangesResult.Failure,
            WorkspaceChangesResult.Success(changesSnapshot(paths = listOf("recovered.kt"))),
        )
        val expectedFailures = listOf(
            WorkspaceChangesFailureKind.Malformed,
            WorkspaceChangesFailureKind.Stale,
            WorkspaceChangesFailureKind.AuthorizationFailure,
            WorkspaceChangesFailureKind.HttpFailure,
            WorkspaceChangesFailureKind.NetworkFailure,
            WorkspaceChangesFailureKind.Failure,
        )
        val repository = FakeChangesRepository().apply {
            snapshotHandler = { results.removeAt(0) }
        }
        val viewModel = changesViewModel(repository)
        runCurrent()
        viewModel.enterChanges()
        runCurrent()
        assertEquals(expectedFailures.first(), viewModel.changesState.value.failure)

        expectedFailures.drop(1).forEach { expectedFailure ->
            viewModel.refreshChanges()
            assertTrue(viewModel.changesState.value.isLoading)
            runCurrent()
            assertEquals(expectedFailure, viewModel.changesState.value.failure)
            assertEquals(0, repository.diffCalls)
        }

        viewModel.refreshChanges()
        runCurrent()
        assertEquals(listOf("recovered.kt"), viewModel.changesState.value.snapshot?.changes?.map { it.file })
        assertEquals(7, repository.snapshotCalls)
        assertEquals(0, repository.diffCalls)
    }

    @Test
    fun exitCancelsOwnedSnapshotAndSuppressesNonCooperativeCompletion() = runTest(dispatcher) {
        var cooperativeCancelled = false
        val cooperativeRepository = FakeChangesRepository().apply {
            snapshotHandler = {
                try {
                    awaitCancellation()
                } finally {
                    cooperativeCancelled = true
                }
            }
        }
        val cooperativeViewModel = changesViewModel(cooperativeRepository)
        runCurrent()
        cooperativeViewModel.enterChanges()
        runCurrent()

        cooperativeViewModel.exitChanges()
        runCurrent()

        assertTrue(cooperativeCancelled)
        assertEquals(WorkspaceChangesUiState(), cooperativeViewModel.changesState.value)

        var completeStale: ((WorkspaceChangesResult<WorkspaceChangesSnapshot>) -> Unit)? = null
        val nonCooperativeRepository = FakeChangesRepository().apply {
            snapshotHandler = {
                suspendCoroutine { continuation ->
                    completeStale = { result -> continuation.resume(result) }
                }
            }
        }
        val nonCooperativeViewModel = changesViewModel(nonCooperativeRepository)
        runCurrent()
        nonCooperativeViewModel.enterChanges()
        runCurrent()
        nonCooperativeViewModel.exitChanges()

        checkNotNull(completeStale).invoke(
            WorkspaceChangesResult.Success(changesSnapshot(paths = listOf("stale.kt")))
        )
        runCurrent()

        assertEquals(WorkspaceChangesUiState(), nonCooperativeViewModel.changesState.value)
    }

    @Test
    fun refreshRetainsOldContentThenAtomicallyReplacesAndInvalidatesDiff() = runTest(dispatcher) {
        val original = changesSnapshot(paths = listOf("a.kt", "b.kt"), branch = "old")
        val replacement = changesSnapshot(paths = listOf("new.kt"), branch = "new")
        var completeRefresh: ((WorkspaceChangesResult<WorkspaceChangesSnapshot>) -> Unit)? = null
        val repository = FakeChangesRepository().apply {
            snapshotHandler = { WorkspaceChangesResult.Success(original) }
            diffHandler = {
                WorkspaceChangesResult.Success(
                    mapOf(
                        "a.kt" to WorkspacePatch.Content("a patch"),
                        "b.kt" to WorkspacePatch.Content("b patch"),
                        "new.kt" to WorkspacePatch.Content("new patch"),
                    )
                )
            }
        }
        val viewModel = loadedChangesViewModel(repository)
        viewModel.toggleChange("a.kt")
        runCurrent()
        assertEquals(WorkspaceChangesPatchState.Content("a.kt", "a patch"), viewModel.changesState.value.patchState)
        assertEquals(1, repository.diffCalls)

        repository.snapshotHandler = {
            suspendCoroutine { continuation ->
                completeRefresh = { result -> continuation.resume(result) }
            }
        }
        viewModel.refreshChanges()
        runCurrent()

        val refreshing = viewModel.changesState.value
        assertTrue(refreshing.isRefreshing)
        assertFalse(refreshing.isLoading)
        assertSame(original, refreshing.snapshot)
        assertEquals("a.kt", refreshing.selectedPath)
        assertEquals(WorkspaceChangesPatchState.Content("a.kt", "a patch"), refreshing.patchState)
        assertNull(refreshing.refreshFailed)

        checkNotNull(completeRefresh).invoke(WorkspaceChangesResult.Success(replacement))
        runCurrent()

        val refreshed = viewModel.changesState.value
        assertFalse(refreshed.isRefreshing)
        assertSame(replacement, refreshed.snapshot)
        assertNull(refreshed.selectedPath)
        assertEquals(WorkspaceChangesPatchState.None, refreshed.patchState)

        viewModel.toggleChange("new.kt")
        runCurrent()
        assertEquals(2, repository.diffCalls)
        assertEquals(WorkspaceChangesPatchState.Content("new.kt", "new patch"), viewModel.changesState.value.patchState)
    }

    @Test
    fun failedRefreshRetainsSnapshotSelectionAndCurrentGenerationCache() = runTest(dispatcher) {
        val original = changesSnapshot(paths = listOf("a.kt", "b.kt"))
        val repository = FakeChangesRepository().apply {
            snapshotHandler = { WorkspaceChangesResult.Success(original) }
            diffHandler = {
                WorkspaceChangesResult.Success(
                    mapOf(
                        "a.kt" to WorkspacePatch.Content("a patch"),
                        "b.kt" to WorkspacePatch.Content("b patch"),
                    )
                )
            }
        }
        val viewModel = loadedChangesViewModel(repository)
        viewModel.toggleChange("a.kt")
        runCurrent()

        val failures = listOf(
            WorkspaceChangesResult.Unsupported to WorkspaceChangesFailureKind.Unsupported,
            WorkspaceChangesResult.TooLarge to WorkspaceChangesFailureKind.TooLarge,
            WorkspaceChangesResult.Malformed to WorkspaceChangesFailureKind.Malformed,
            WorkspaceChangesResult.Stale to WorkspaceChangesFailureKind.Stale,
            WorkspaceChangesResult.AuthorizationFailure to WorkspaceChangesFailureKind.AuthorizationFailure,
            WorkspaceChangesResult.HttpFailure to WorkspaceChangesFailureKind.HttpFailure,
            WorkspaceChangesResult.NetworkFailure to WorkspaceChangesFailureKind.NetworkFailure,
            WorkspaceChangesResult.Failure to WorkspaceChangesFailureKind.Failure,
        )
        failures.forEach { (result, expectedFailure) ->
            repository.snapshotHandler = { result }
            viewModel.refreshChanges()
            assertTrue(viewModel.changesState.value.isRefreshing)
            assertNull(viewModel.changesState.value.refreshFailed)
            runCurrent()

            val failed = viewModel.changesState.value
            assertSame(original, failed.snapshot)
            assertFalse(failed.isRefreshing)
            assertEquals(expectedFailure, failed.refreshFailed)
            assertNull(failed.failure)
            assertEquals("a.kt", failed.selectedPath)
            assertEquals(WorkspaceChangesPatchState.Content("a.kt", "a patch"), failed.patchState)
        }

        viewModel.toggleChange("b.kt")

        assertEquals(1, repository.diffCalls)
        assertEquals("b.kt", viewModel.changesState.value.selectedPath)
        assertEquals(WorkspaceChangesPatchState.Content("b.kt", "b patch"), viewModel.changesState.value.patchState)
    }

    @Test
    fun newerRefreshSuppressesLateNonCooperativeCompletion() = runTest(dispatcher) {
        val initial = changesSnapshot(paths = listOf("initial.kt"))
        val latest = changesSnapshot(paths = listOf("latest.kt"))
        var completeOldRefresh: ((WorkspaceChangesResult<WorkspaceChangesSnapshot>) -> Unit)? = null
        val repository = FakeChangesRepository().apply {
            snapshotHandler = { WorkspaceChangesResult.Success(initial) }
        }
        val viewModel = loadedChangesViewModel(repository)

        repository.snapshotHandler = {
            suspendCoroutine { continuation ->
                completeOldRefresh = { result -> continuation.resume(result) }
            }
        }
        viewModel.refreshChanges()
        runCurrent()

        repository.snapshotHandler = { WorkspaceChangesResult.Success(latest) }
        viewModel.refreshChanges()
        runCurrent()
        assertSame(latest, viewModel.changesState.value.snapshot)

        checkNotNull(completeOldRefresh).invoke(WorkspaceChangesResult.Malformed)
        runCurrent()

        val state = viewModel.changesState.value
        assertSame(latest, state.snapshot)
        assertNull(state.failure)
        assertNull(state.refreshFailed)
        assertFalse(state.isRefreshing)
    }
}
