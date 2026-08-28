package dev.blazelight.p4oc.ui.screens.files

import dev.blazelight.p4oc.data.vcs.WorkspaceChangesResult
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
class FilesViewModelChangesPatchTest {
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
    fun diffIsLazySingleSelectionAndCacheIsReusedUntilSnapshotChanges() = runTest(dispatcher) {
        val repository = FakeChangesRepository().apply {
            snapshotHandler = {
                WorkspaceChangesResult.Success(changesSnapshot(paths = listOf("a.kt", "b.kt")))
            }
            diffHandler = {
                WorkspaceChangesResult.Success(
                    mapOf(
                        "a.kt" to WorkspacePatch.Content("a patch"),
                        "b.kt" to WorkspacePatch.Content("b patch"),
                    ),
                )
            }
        }
        val viewModel = loadedChangesViewModel(repository)
        assertEquals(0, repository.diffCalls)

        viewModel.toggleChange("a.kt")
        assertEquals("a.kt", viewModel.changesState.value.selectedPath)
        assertEquals(WorkspaceChangesPatchState.Loading, viewModel.changesState.value.patchState)
        runCurrent()
        assertEquals(1, repository.diffCalls)
        assertEquals(
            WorkspaceChangesPatchState.Content("a.kt", "a patch"),
            viewModel.changesState.value.patchState,
        )

        viewModel.toggleChange("b.kt")

        val switched = viewModel.changesState.value
        assertEquals(1, repository.diffCalls)
        assertEquals("b.kt", switched.selectedPath)
        assertEquals(WorkspaceChangesPatchState.Content("b.kt", "b patch"), switched.patchState)
        assertFalse(switched.patchState == WorkspaceChangesPatchState.Content("a.kt", "a patch"))

        viewModel.toggleChange("b.kt")
        assertNull(viewModel.changesState.value.selectedPath)
        assertEquals(WorkspaceChangesPatchState.None, viewModel.changesState.value.patchState)
        assertEquals(1, repository.diffCalls)

        viewModel.toggleChange("a.kt")
        assertEquals(
            WorkspaceChangesPatchState.Content("a.kt", "a patch"),
            viewModel.changesState.value.patchState,
        )
        assertEquals(1, repository.diffCalls)
    }

    @Test
    fun exitAndReentryReleaseTheDiffCache() = runTest(dispatcher) {
        val repository = FakeChangesRepository().apply {
            snapshotHandler = {
                WorkspaceChangesResult.Success(changesSnapshot(paths = listOf("a.kt")))
            }
            diffHandler = {
                WorkspaceChangesResult.Success(
                    mapOf("a.kt" to WorkspacePatch.Content("patch $diffCalls")),
                )
            }
        }
        val viewModel = loadedChangesViewModel(repository)
        viewModel.toggleChange("a.kt")
        runCurrent()
        assertEquals(
            WorkspaceChangesPatchState.Content("a.kt", "patch 1"),
            viewModel.changesState.value.patchState,
        )

        viewModel.exitChanges()
        viewModel.enterChanges()
        runCurrent()
        viewModel.toggleChange("a.kt")
        runCurrent()

        assertEquals(2, repository.snapshotCalls)
        assertEquals(2, repository.diffCalls)
        assertEquals(
            WorkspaceChangesPatchState.Content("a.kt", "patch 2"),
            viewModel.changesState.value.patchState,
        )
    }

    @Test
    fun invalidPathIsIgnoredAndDiffUsesExactPathOnly() = runTest(dispatcher) {
        val repository = FakeChangesRepository().apply {
            snapshotHandler = {
                WorkspaceChangesResult.Success(changesSnapshot(paths = listOf("src/a.kt")))
            }
            diffHandler = {
                WorkspaceChangesResult.Success(
                    mapOf("test/a.kt" to WorkspacePatch.Content("wrong basename patch")),
                )
            }
        }
        val viewModel = loadedChangesViewModel(repository)

        viewModel.toggleChange("not-in-status.kt")
        runCurrent()
        assertEquals(0, repository.diffCalls)
        assertNull(viewModel.changesState.value.selectedPath)

        viewModel.toggleChange("src/a.kt")
        runCurrent()

        assertEquals(1, repository.diffCalls)
        assertEquals(
            WorkspaceChangesPatchState.Stale("src/a.kt"),
            viewModel.changesState.value.patchState,
        )
    }

    @Test
    fun newerSelectionSuppressesLateCompletionAndCannotSeedCache() = runTest(dispatcher) {
        var completeFirst: ((WorkspaceChangesResult<Map<String, WorkspacePatch>>) -> Unit)? = null
        val repository = FakeChangesRepository().apply {
            snapshotHandler = {
                WorkspaceChangesResult.Success(changesSnapshot(paths = listOf("a.kt", "b.kt")))
            }
            diffHandler = {
                if (diffCalls == 1) {
                    suspendCoroutine { continuation ->
                        completeFirst = { result -> continuation.resume(result) }
                    }
                } else {
                    WorkspaceChangesResult.Success(
                        mapOf("b.kt" to WorkspacePatch.Content("current b patch")),
                    )
                }
            }
        }
        val viewModel = loadedChangesViewModel(repository)
        viewModel.toggleChange("a.kt")
        runCurrent()
        assertEquals(WorkspaceChangesPatchState.Loading, viewModel.changesState.value.patchState)

        viewModel.toggleChange("b.kt")
        runCurrent()
        assertEquals(2, repository.diffCalls)
        assertEquals(
            WorkspaceChangesPatchState.Content("b.kt", "current b patch"),
            viewModel.changesState.value.patchState,
        )

        checkNotNull(completeFirst).invoke(
            WorkspaceChangesResult.Success(
                mapOf("a.kt" to WorkspacePatch.Content("stale a patch")),
            ),
        )
        runCurrent()
        assertEquals(
            WorkspaceChangesPatchState.Content("b.kt", "current b patch"),
            viewModel.changesState.value.patchState,
        )

        viewModel.toggleChange("a.kt")
        assertEquals(
            WorkspaceChangesPatchState.Stale("a.kt"),
            viewModel.changesState.value.patchState,
        )
        assertEquals(2, repository.diffCalls)
    }

    @Test
    fun collapseCancelsPatchWithoutTurningCancellationIntoFailure() = runTest(dispatcher) {
        var cancelled = false
        val repository = FakeChangesRepository().apply {
            snapshotHandler = {
                WorkspaceChangesResult.Success(changesSnapshot(paths = listOf("a.kt")))
            }
            diffHandler = {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
        }
        val viewModel = loadedChangesViewModel(repository)
        viewModel.toggleChange("a.kt")
        runCurrent()

        viewModel.toggleChange("a.kt")
        runCurrent()

        assertTrue(cancelled)
        assertNull(viewModel.changesState.value.selectedPath)
        assertEquals(WorkspaceChangesPatchState.None, viewModel.changesState.value.patchState)
    }

    @Test
    fun cachedPatchVariantsMapToExactlyOneRowLocalState() = runTest(dispatcher) {
        val repository = FakeChangesRepository().apply {
            snapshotHandler = {
                WorkspaceChangesResult.Success(
                    changesSnapshot(
                        paths = listOf("content.kt", "missing.kt", "large.kt", "stale.kt"),
                    ),
                )
            }
            diffHandler = {
                WorkspaceChangesResult.Success(
                    mapOf(
                        "content.kt" to WorkspacePatch.Content("patch text"),
                        "missing.kt" to WorkspacePatch.Unavailable,
                        "large.kt" to WorkspacePatch.TooLarge,
                        "stale.kt" to WorkspacePatch.Stale,
                    ),
                )
            }
        }
        val viewModel = loadedChangesViewModel(repository)

        viewModel.toggleChange("content.kt")
        runCurrent()
        assertEquals(
            WorkspaceChangesPatchState.Content("content.kt", "patch text"),
            viewModel.changesState.value.patchState,
        )

        viewModel.toggleChange("missing.kt")
        assertEquals(
            WorkspaceChangesPatchState.Unavailable("missing.kt"),
            viewModel.changesState.value.patchState,
        )

        viewModel.toggleChange("large.kt")
        assertEquals(
            WorkspaceChangesPatchState.TooLarge("large.kt"),
            viewModel.changesState.value.patchState,
        )

        viewModel.toggleChange("stale.kt")
        assertEquals(
            WorkspaceChangesPatchState.Stale("stale.kt"),
            viewModel.changesState.value.patchState,
        )
        assertEquals(1, repository.diffCalls)
    }

    @Test
    fun everyRetryablePatchFailureRemainsDistinctAndRetriesOnlyTheDiff() = runTest(dispatcher) {
        val results = mutableListOf<WorkspaceChangesResult<Map<String, WorkspacePatch>>>(
            WorkspaceChangesResult.Malformed,
            WorkspaceChangesResult.Stale,
            WorkspaceChangesResult.AuthorizationFailure,
            WorkspaceChangesResult.HttpFailure,
            WorkspaceChangesResult.NetworkFailure,
            WorkspaceChangesResult.Failure,
            WorkspaceChangesResult.Success(mapOf("a.kt" to WorkspacePatch.Content("recovered"))),
        )
        val expectedFailures = listOf(
            WorkspaceChangesPatchState.Malformed("a.kt"),
            WorkspaceChangesPatchState.Stale("a.kt"),
            WorkspaceChangesPatchState.AuthorizationFailure("a.kt"),
            WorkspaceChangesPatchState.HttpFailure("a.kt"),
            WorkspaceChangesPatchState.NetworkFailure("a.kt"),
            WorkspaceChangesPatchState.Failure("a.kt"),
        )
        val repository = FakeChangesRepository().apply {
            snapshotHandler = {
                WorkspaceChangesResult.Success(changesSnapshot(paths = listOf("a.kt")))
            }
            diffHandler = { results.removeAt(0) }
        }
        val viewModel = loadedChangesViewModel(repository)
        viewModel.toggleChange("a.kt")
        runCurrent()
        assertEquals(expectedFailures.first(), viewModel.changesState.value.patchState)
        assertTrue(checkNotNull(viewModel.changesState.value.snapshot).changes.isNotEmpty())

        expectedFailures.drop(1).forEach { expectedFailure ->
            viewModel.retrySelectedPatch()
            assertEquals(WorkspaceChangesPatchState.Loading, viewModel.changesState.value.patchState)
            runCurrent()
            assertEquals(expectedFailure, viewModel.changesState.value.patchState)
            assertEquals(1, repository.snapshotCalls)
        }

        viewModel.retrySelectedPatch()
        runCurrent()
        assertEquals(
            WorkspaceChangesPatchState.Content("a.kt", "recovered"),
            viewModel.changesState.value.patchState,
        )
        assertEquals(7, repository.diffCalls)
        assertEquals(1, repository.snapshotCalls)

        viewModel.retrySelectedPatch()
        runCurrent()
        assertEquals(7, repository.diffCalls)
    }

    @Test
    fun unsupportedAndTooLargeDiffResultsRetainListAndAreNotRetried() = runTest(dispatcher) {
        val results = mutableListOf<WorkspaceChangesResult<Map<String, WorkspacePatch>>>(
            WorkspaceChangesResult.TooLarge,
            WorkspaceChangesResult.Unsupported,
        )
        val repository = FakeChangesRepository().apply {
            snapshotHandler = {
                WorkspaceChangesResult.Success(
                    changesSnapshot(paths = listOf("large.kt", "unsupported.kt")),
                )
            }
            diffHandler = { results.removeAt(0) }
        }
        val viewModel = loadedChangesViewModel(repository)

        viewModel.toggleChange("large.kt")
        runCurrent()
        assertEquals(
            WorkspaceChangesPatchState.TooLarge("large.kt"),
            viewModel.changesState.value.patchState,
        )
        assertEquals(2, checkNotNull(viewModel.changesState.value.snapshot).changes.size)
        viewModel.retrySelectedPatch()
        runCurrent()
        assertEquals(1, repository.diffCalls)

        viewModel.toggleChange("unsupported.kt")
        runCurrent()
        assertEquals(
            WorkspaceChangesPatchState.Unsupported("unsupported.kt"),
            viewModel.changesState.value.patchState,
        )
        viewModel.retrySelectedPatch()
        runCurrent()
        assertEquals(2, repository.diffCalls)
    }
}
