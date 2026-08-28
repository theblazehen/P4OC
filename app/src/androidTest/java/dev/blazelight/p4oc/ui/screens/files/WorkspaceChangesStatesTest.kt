package dev.blazelight.p4oc.ui.screens.files

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.blazelight.p4oc.data.vcs.WorkspaceChange
import dev.blazelight.p4oc.data.vcs.WorkspaceChangeStatus
import dev.blazelight.p4oc.data.vcs.WorkspaceChangesSnapshot
import dev.blazelight.p4oc.ui.theme.PocketCodeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceChangesStatesTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val retryableInitialFailures = listOf(
        WorkspaceChangesFailureKind.Malformed to
            "The server returned changes data the app could not read.",
        WorkspaceChangesFailureKind.Stale to
            "This workspace is no longer current. Refresh Changes and try again.",
        WorkspaceChangesFailureKind.AuthorizationFailure to
            "You are not authorized to view workspace changes. Check access and try again.",
        WorkspaceChangesFailureKind.HttpFailure to
            "The server could not complete the workspace changes request. Try again.",
        WorkspaceChangesFailureKind.NetworkFailure to
            "Could not reach the server to load workspace changes. Check the connection and try again.",
        WorkspaceChangesFailureKind.Failure to
            "Something went wrong while loading workspace changes. Try again.",
    )
    private val nonRetryableRefreshFailures = listOf(
        WorkspaceChangesFailureKind.Unsupported to
            "This server no longer provides workspace changes. Showing the previously loaded changes.",
        WorkspaceChangesFailureKind.TooLarge to
            "The refreshed change set is too large to display. Showing the previously loaded changes.",
    )
    private val retryableRefreshFailures = listOf(
        WorkspaceChangesFailureKind.Malformed to
            "The refreshed changes data could not be read. Showing the previously loaded changes.",
        WorkspaceChangesFailureKind.Stale to
            "The workspace changed before refresh completed. Showing the previously loaded changes.",
        WorkspaceChangesFailureKind.AuthorizationFailure to
            "Access to workspace changes was denied. Showing the previously loaded changes.",
        WorkspaceChangesFailureKind.HttpFailure to
            "The server could not refresh workspace changes. Showing the previously loaded changes.",
        WorkspaceChangesFailureKind.NetworkFailure to
            "Could not reach the server to refresh workspace changes. Showing the previously loaded changes.",
        WorkspaceChangesFailureKind.Failure to
            "Could not refresh. Showing the previously loaded changes.",
    )
    private val patchPath = "src/test/AppTest.kt"
    private val nonRetryablePatchFailures = listOf(
        WorkspaceChangesPatchState.Unsupported(patchPath) to
            "Patch preview is not supported by this server version.",
        WorkspaceChangesPatchState.Unavailable(patchPath) to
            "Patch unavailable for this change.",
        WorkspaceChangesPatchState.TooLarge(patchPath) to
            "Patch too large to display.",
    )
    private val retryablePatchFailures = listOf(
        WorkspaceChangesPatchState.Malformed(patchPath) to
            "The server returned patch data the app could not read.",
        WorkspaceChangesPatchState.AuthorizationFailure(patchPath) to
            "You are not authorized to view this patch. Check access and try again.",
        WorkspaceChangesPatchState.HttpFailure(patchPath) to
            "The server could not load this patch. Try again.",
        WorkspaceChangesPatchState.NetworkFailure(patchPath) to
            "Could not reach the server to load this patch. Check the connection and try again.",
        WorkspaceChangesPatchState.Failure(patchPath) to
            "Something went wrong while loading this patch. Try again.",
    )

    @Test
    fun identityAggregateStatsAndServerOrderIncludeDeletedAndOutOfFolderRows() {
        val state = WorkspaceChangesUiState(
            isActive = true,
            snapshot = snapshot(),
        )
        composeRule.setContent {
            PocketCodeTheme {
                workspaceChangesContent(state, {}, {}, {})
            }
        }

        composeRule.onNodeWithTag("files_changes_identity")
            .assert(
                hasContentDescription(
                    "server.example · /work/project. Branch: feature/review · Default: main. " +
                        "Changed files: 3 · +8 −3",
                ),
            )
        val indexForKey = composeRule.onNodeWithTag("files_changes_list")
            .fetchSemanticsNode()
            .config[SemanticsProperties.IndexForKey]
        assertEquals(0, indexForKey("src/test/AppTest.kt"))
        assertEquals(1, indexForKey("README.md"))
        assertEquals(2, indexForKey("old/config.json"))

        composeRule.onNodeWithTag("files_changes_list")
            .performScrollToNode(hasTestTag("files_changes_row_README.md"))
        composeRule.onNodeWithTag("files_changes_row_README.md")
            .assert(hasContentDescription("README.md, Added, 3 additions, 0 deletions"))
        composeRule.onNodeWithTag("files_changes_list")
            .performScrollToNode(hasTestTag("files_changes_row_old/config.json"))
        composeRule.onNodeWithTag("files_changes_row_old/config.json")
            .assert(hasContentDescription("old/config.json, Deleted, 0 additions, 1 deletions"))
    }

    @Test
    fun globalWorkspaceAndMissingBranchesAreExplicitlyIdentified() {
        val state = WorkspaceChangesUiState(
            isActive = true,
            snapshot = snapshot().copy(
                workspaceDirectory = null,
                branch = null,
                defaultBranch = null,
            ),
        )
        composeRule.setContent {
            PocketCodeTheme {
                workspaceChangesContent(state, {}, {}, {})
            }
        }

        composeRule.onNodeWithTag("files_changes_identity")
            .assert(
                hasContentDescription(
                    "server.example · Global workspace. Branch: Unavailable · Default: Unavailable. " +
                        "Changed files: 3 · +8 −3",
                ),
            )
    }

    @Test
    fun rowsExpandCollapseAndSwitchWithOnlyOnePatchBodyComposed() {
        var selectedPath by mutableStateOf<String?>(null)
        var patchState by mutableStateOf<WorkspaceChangesPatchState>(WorkspaceChangesPatchState.None)
        val stateSnapshot = snapshot()
        composeRule.setContent {
            PocketCodeTheme {
                workspaceChangesContent(
                    state = WorkspaceChangesUiState(
                        isActive = true,
                        snapshot = stateSnapshot,
                        selectedPath = selectedPath,
                        patchState = patchState,
                    ),
                    onRefresh = {},
                    onToggle = { path ->
                        if (selectedPath == path) {
                            selectedPath = null
                            patchState = WorkspaceChangesPatchState.None
                        } else {
                            selectedPath = path
                            patchState = WorkspaceChangesPatchState.Content(path, "patch for $path")
                        }
                    },
                    onRetryPatch = {},
                )
            }
        }

        composeRule.onNodeWithTag("files_changes_list")
            .performScrollToNode(hasTestTag("files_changes_row_src/test/AppTest.kt"))
        composeRule.onNodeWithTag("files_changes_row_src/test/AppTest.kt").performClick()
        composeRule.onNodeWithTag("files_changes_patch_src/test/AppTest.kt")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("patch for src/test/AppTest.kt").assertIsDisplayed()
        composeRule.onNodeWithTag("files_changes_patch_README.md").assertDoesNotExist()

        composeRule.onNodeWithTag("files_changes_list")
            .performScrollToNode(hasTestTag("files_changes_row_README.md"))
        composeRule.onNodeWithTag("files_changes_row_README.md").performClick()
        composeRule.onNodeWithTag("files_changes_patch_src/test/AppTest.kt").assertDoesNotExist()
        composeRule.onNodeWithText("patch for src/test/AppTest.kt").assertDoesNotExist()
        composeRule.onNodeWithTag("files_changes_patch_README.md")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("patch for README.md").assertIsDisplayed()

        composeRule.onNodeWithTag("files_changes_list")
            .performScrollToNode(hasTestTag("files_changes_row_README.md"))
        composeRule.onNodeWithTag("files_changes_row_README.md").performClick()
        composeRule.onNodeWithTag("files_changes_patch_README.md").assertDoesNotExist()
    }

    @Test
    fun initialLoadingAndEmptyStateAreAnnouncedWithoutRows() {
        val state = renderChangesState(WorkspaceChangesUiState(isActive = true, isLoading = true))
        composeRule.onNodeWithTag("files_changes_loading")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )
        composeRule.runOnIdle {
            state.value = WorkspaceChangesUiState(
                isActive = true,
                snapshot = snapshot(changes = emptyList()),
            )
        }
        composeRule.onNodeWithTag("files_changes_empty").assertIsDisplayed()
        composeRule.onNodeWithTag("files_changes_list").assertDoesNotExist()
    }

    @Test
    fun unsupportedAndOversizeListFailuresDoNotOfferRetry() {
        val state = renderChangesState(
            WorkspaceChangesUiState(
                isActive = true,
                failure = WorkspaceChangesFailureKind.Unsupported,
            ),
        )
        composeRule.onNodeWithText("Workspace changes are not available on this server version.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("files_changes_retry").assertDoesNotExist()
        composeRule.runOnIdle {
            state.value = WorkspaceChangesUiState(
                isActive = true,
                failure = WorkspaceChangesFailureKind.TooLarge,
            )
        }
        composeRule.onNodeWithText("This workspace change set is too large to display.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("files_changes_retry").assertDoesNotExist()
    }

    @Test
    fun retryableListFailuresUseDistinctSafeCopyAndManualRetry() {
        var retryCount = 0
        val state = renderChangesState(
            initial = WorkspaceChangesUiState(
                isActive = true,
                failure = retryableInitialFailures.first().first,
            ),
            onRefresh = { retryCount += 1 },
        )
        composeRule.onNodeWithTag("files_changes_failure")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )
        retryableInitialFailures.forEach { (failure, message) ->
            composeRule.runOnIdle {
                state.value = WorkspaceChangesUiState(isActive = true, failure = failure)
            }
            composeRule.onNodeWithText(message).assertIsDisplayed()
            composeRule.onNodeWithTag("files_changes_retry").performClick()
        }
        composeRule.runOnIdle { assertEquals(retryableInitialFailures.size, retryCount) }
    }

    @Test
    fun refreshingRetainsRowsAndAnnouncesRefreshFailure() {
        var refreshCount = 0
        var state by mutableStateOf(
            WorkspaceChangesUiState(
                isActive = true,
                isRefreshing = true,
                snapshot = snapshot(),
            ),
        )
        composeRule.setContent {
            PocketCodeTheme {
                workspaceChangesContent(state, { refreshCount += 1 }, {}, {})
            }
        }

        composeRule.onNodeWithTag("files_changes_refreshing")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )
        composeRule.onNodeWithTag("files_changes_list")
            .performScrollToNode(hasTestTag("files_changes_row_README.md"))
        composeRule.onNodeWithTag("files_changes_row_README.md").assertIsDisplayed()

        nonRetryableRefreshFailures.forEach { (failure, message) ->
            composeRule.runOnIdle { state = state.copy(isRefreshing = false, refreshFailed = failure) }
            composeRule.onNodeWithText(message).assertIsDisplayed()
            composeRule.onNodeWithTag("files_changes_retry").assertDoesNotExist()
        }

        retryableRefreshFailures.forEach { (failure, message) ->
            composeRule.runOnIdle { state = state.copy(refreshFailed = failure) }
            composeRule.onNodeWithText(message).assertIsDisplayed()
            composeRule.onNodeWithTag("files_changes_retry").performClick()
        }
        composeRule.onNodeWithTag("files_changes_refresh_failed")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        composeRule.onNodeWithTag("files_changes_list")
            .performScrollToNode(hasTestTag("files_changes_row_README.md"))
        composeRule.onNodeWithTag("files_changes_row_README.md").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(retryableRefreshFailures.size, refreshCount) }
    }

    @Test
    fun everyRowPatchStateIsLocalHumanReadableAndBounded() {
        var patchState by mutableStateOf<WorkspaceChangesPatchState>(WorkspaceChangesPatchState.Loading)
        var retryCount = 0
        var refreshCount = 0
        composeRule.setContent {
            PocketCodeTheme {
                workspaceChangesContent(
                    state = WorkspaceChangesUiState(
                        isActive = true,
                        snapshot = snapshot(),
                        selectedPath = patchPath,
                        patchState = patchState,
                    ),
                    onRefresh = { refreshCount += 1 },
                    onToggle = {},
                    onRetryPatch = { retryCount += 1 },
                )
            }
        }

        composeRule.onNodeWithText("Loading patch…").assertIsDisplayed()

        composeRule.runOnIdle {
            patchState = WorkspaceChangesPatchState.Content(patchPath, "@@ -1 +1 @@\n-old\n+new")
        }
        composeRule.onNodeWithTag("files_changes_patch_$patchPath").assertIsDisplayed()

        nonRetryablePatchFailures.forEach { (failure, message) ->
            composeRule.runOnIdle { patchState = failure }
            composeRule.onNodeWithText(message).assertIsDisplayed()
            composeRule.onNodeWithTag("files_changes_retry").assertDoesNotExist()
        }

        composeRule.runOnIdle { patchState = WorkspaceChangesPatchState.Stale(patchPath) }
        composeRule.onNodeWithText("This patch is no longer available. Refresh Changes and try again.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("files_changes_retry").performClick()

        retryablePatchFailures.forEach { (failure, message) ->
            composeRule.runOnIdle { patchState = failure }
            composeRule.onNodeWithText(message).assertIsDisplayed()
            composeRule.onNodeWithTag("files_changes_retry").performClick()
        }
        composeRule.runOnIdle {
            assertEquals(1, refreshCount)
            assertEquals(retryablePatchFailures.size, retryCount)
        }
    }

    private fun renderChangesState(
        initial: WorkspaceChangesUiState,
        onRefresh: () -> Unit = {},
    ): MutableState<WorkspaceChangesUiState> {
        val state = mutableStateOf(initial)
        composeRule.setContent {
            PocketCodeTheme {
                workspaceChangesContent(
                    state = state.value,
                    onRefresh = onRefresh,
                    onToggle = {},
                    onRetryPatch = {},
                )
            }
        }
        return state
    }

    private fun snapshot(
        changes: List<WorkspaceChange> = listOf(
            WorkspaceChange("src/test/AppTest.kt", WorkspaceChangeStatus.Modified, 5, 2),
            WorkspaceChange("README.md", WorkspaceChangeStatus.Added, 3, 0),
            WorkspaceChange("old/config.json", WorkspaceChangeStatus.Deleted, 0, 1),
        ),
    ) = WorkspaceChangesSnapshot(
        serverLabel = "server.example",
        workspaceDirectory = "/work/project",
        branch = "feature/review",
        defaultBranch = "main",
        changes = changes,
        additions = changes.sumOf(WorkspaceChange::additions),
        deletions = changes.sumOf(WorkspaceChange::deletions),
    )
}
