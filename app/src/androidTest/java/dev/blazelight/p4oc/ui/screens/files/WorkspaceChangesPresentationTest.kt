package dev.blazelight.p4oc.ui.screens.files

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.data.files.FileCapabilities
import dev.blazelight.p4oc.data.files.FileList
import dev.blazelight.p4oc.data.files.FileOperationResult
import dev.blazelight.p4oc.data.files.FileRepository
import dev.blazelight.p4oc.data.files.FileUploadRequest
import dev.blazelight.p4oc.data.files.FileUploadResult
import dev.blazelight.p4oc.data.files.FileWriteRequest
import dev.blazelight.p4oc.data.files.FileWriteResult
import dev.blazelight.p4oc.data.vcs.WorkspaceChange
import dev.blazelight.p4oc.data.vcs.WorkspaceChangeStatus
import dev.blazelight.p4oc.data.vcs.WorkspaceChangesRepository
import dev.blazelight.p4oc.data.vcs.WorkspaceChangesResult
import dev.blazelight.p4oc.data.vcs.WorkspaceChangesSnapshot
import dev.blazelight.p4oc.data.vcs.WorkspacePatch
import dev.blazelight.p4oc.domain.model.FileContent
import dev.blazelight.p4oc.domain.model.FileNode
import dev.blazelight.p4oc.domain.model.Symbol
import dev.blazelight.p4oc.domain.server.WorkspaceKey
import dev.blazelight.p4oc.ui.screens.files.upload.UploadCoordinator
import dev.blazelight.p4oc.ui.theme.PocketCodeTheme
import dev.blazelight.p4oc.ui.theme.Sizing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class WorkspaceChangesPresentationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun contextualEntryExitAndSystemBackRestoreThePriorFilesFolder() {
        val viewModel = filesViewModel(snapshot())

        composeRule.setContent {
            PocketCodeTheme {
                FileExplorerScreen(
                    viewModel = viewModel,
                    workspaceKey = WorkspaceKey.Directory("/work/project"),
                    onFileClick = {},
                    onNavigateBack = {},
                )
            }
        }

        waitForDescription("src, Folder, path src")
        composeRule.onNodeWithContentDescription("src, Folder, path src").performClick()
        waitForDescription("Keep.kt, File, path src/Keep.kt")

        composeRule.onNodeWithTag("files_changes_action").performClick()
        waitForTag("files_changes_identity")
        composeRule.onNodeWithText("Changes").assertIsDisplayed()
        composeRule.onNodeWithTag("files_create_action").assertDoesNotExist()
        composeRule.onNodeWithTag("files_upload_action").assertDoesNotExist()

        composeRule.onNodeWithTag("files_changes_exit").performClick()
        waitForDescription("Keep.kt, File, path src/Keep.kt")
        composeRule.onNodeWithTag("files_breadcrumb_segment_0").assertIsDisplayed()

        composeRule.onNodeWithTag("files_changes_action").performClick()
        waitForTag("files_changes_exit")
        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        waitForDescription("Keep.kt, File, path src/Keep.kt")
        composeRule.onNodeWithTag("files_breadcrumb_segment_0").assertIsDisplayed()
    }

    @Test
    fun exitRestoresActiveFileAndSymbolSearchWithoutResettingTheirQueries() {
        val viewModel = filesViewModel(snapshot())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            PocketCodeTheme {
                FileExplorerScreen(
                    viewModel = viewModel,
                    workspaceKey = WorkspaceKey.Directory("/work/project"),
                    onFileClick = {},
                    onNavigateBack = {},
                )
            }
        }

        waitForTag("files_changes_action")
        composeRule.onNodeWithContentDescription(context.getString(R.string.cd_search)).performClick()
        composeRule.onNodeWithTag("files_search_field").performTextInput("Keep")
        composeRule.onNodeWithTag("files_changes_action").performClick()
        waitForTag("files_changes_exit")
        composeRule.onNodeWithTag("files_changes_exit").performClick()
        composeRule.onNodeWithTag("files_search_field").assertTextContains("Keep")

        composeRule.runOnIdle { viewModel.clearFilters() }
        composeRule.onNodeWithContentDescription(context.getString(R.string.cd_symbol_search)).performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("Thing")
        composeRule.onNodeWithTag("files_changes_action").performClick()
        waitForTag("files_changes_exit")
        composeRule.onNodeWithTag("files_changes_exit").performClick()
        composeRule.onNode(hasSetTextAction()).assertTextContains("Thing")
    }

    @Test
    fun controlsRowsAndPatchExposeStableAccessibleSemantics() {
        val viewModel = filesViewModel(snapshot())
        composeRule.setContent {
            PocketCodeTheme {
                FileExplorerScreen(
                    viewModel = viewModel,
                    workspaceKey = WorkspaceKey.Directory("/work/project"),
                    onFileClick = {},
                    onNavigateBack = {},
                )
            }
        }

        enterChangesAndAssertAccessibleControls()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rowTag = "files_changes_row_src/test/AppTest.kt"
        composeRule.onNodeWithTag("files_changes_list")
            .performScrollToNode(hasTestTag(rowTag))
        composeRule.onNodeWithTag(rowTag)
            .assert(
                hasContentDescription(
                    "src/test/AppTest.kt, Modified, 5 additions, 2 deletions",
                ),
            )
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    context.getString(R.string.files_changes_state_collapsed),
                ),
            )
            .performClick()

        waitForTag("files_changes_patch_src/test/AppTest.kt")
        composeRule.onNodeWithTag("files_changes_patch_src/test/AppTest.kt").performScrollTo()
        composeRule.onNodeWithTag(rowTag)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    context.getString(R.string.files_changes_state_expanded),
                ),
            )
        composeRule.onNodeWithTag("files_changes_patch_heading_src/test/AppTest.kt")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun filesChangesRefreshExpandSwitchCollapseAndBackIsReadOnly() {
        val snapshot = snapshot()
        val fileRepository = FakeFileRepository()
        val changesRepository = FakeWorkspaceChangesRepository(snapshot)
        val viewModel = filesViewModel(snapshot, fileRepository, changesRepository)
        composeRule.setContent {
            PocketCodeTheme {
                FileExplorerScreen(
                    viewModel = viewModel,
                    workspaceKey = WorkspaceKey.Directory("/work/project"),
                    onFileClick = {},
                    onNavigateBack = {},
                )
            }
        }

        waitForDescription("src, Folder, path src")
        composeRule.onNodeWithContentDescription("src, Folder, path src").performClick()
        waitForDescription("Keep.kt, File, path src/Keep.kt")
        enterChangesAndAssertAccessibleControls()
        composeRule.runOnIdle {
            assertEquals(1, changesRepository.snapshotCallCount)
            assertEquals(
                listOf(WorkspaceChangeStatus.Modified, WorkspaceChangeStatus.Added, WorkspaceChangeStatus.Deleted),
                viewModel.changesState.value.snapshot?.changes?.map(WorkspaceChange::status),
            )
            assertEquals(
                listOf("src/test/AppTest.kt", "README.md", "old/config.json"),
                viewModel.changesState.value.snapshot?.changes?.map(WorkspaceChange::file),
            )
        }
        refreshExpandSwitchAndCollapse(viewModel, changesRepository)

        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        waitForDescription("Keep.kt, File, path src/Keep.kt")
        composeRule.onNodeWithTag("files_breadcrumb_segment_0").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(2, changesRepository.snapshotCallCount)
            assertEquals(1, changesRepository.diffCallCount)
            assertEquals(0, fileRepository.mutationCallCount)
        }
    }

    private fun refreshExpandSwitchAndCollapse(
        viewModel: FilesViewModel,
        changesRepository: FakeWorkspaceChangesRepository,
    ) {
        composeRule.onNodeWithTag("files_changes_refresh").performClick()
        composeRule.waitUntil(timeoutMillis = ASYNC_TIMEOUT_MILLIS) {
            changesRepository.snapshotCallCount == 2 && !viewModel.changesState.value.isRefreshing
        }
        listOf(
            "src/test/AppTest.kt" to "src/test/AppTest.kt, Modified, 5 additions, 2 deletions",
            "README.md" to "README.md, Added, 3 additions, 0 deletions",
            "old/config.json" to "old/config.json, Deleted, 0 additions, 1 deletions",
        ).forEach { (path, description) ->
            val rowTag = "files_changes_row_$path"
            composeRule.onNodeWithTag("files_changes_list")
                .performScrollToNode(hasTestTag(rowTag))
            composeRule.onNodeWithTag(rowTag).assert(hasContentDescription(description))
        }
        val firstPath = "src/test/AppTest.kt"
        composeRule.onNodeWithTag("files_changes_list")
            .performScrollToNode(hasTestTag("files_changes_row_$firstPath"))
        composeRule.onNodeWithTag("files_changes_row_$firstPath").performClick()
        composeRule.waitUntil(timeoutMillis = ASYNC_TIMEOUT_MILLIS) {
            changesRepository.diffCallCount == 1 &&
                viewModel.changesState.value.patchState is WorkspaceChangesPatchState.Content
        }
        composeRule.onNodeWithTag("files_changes_patch_$firstPath").performScrollTo()
        composeRule.onNodeWithText("patch for $firstPath").assertIsDisplayed()

        val secondPath = "README.md"
        composeRule.onNodeWithTag("files_changes_list")
            .performScrollToNode(hasTestTag("files_changes_row_$secondPath"))
        composeRule.onNodeWithTag("files_changes_row_$secondPath").performClick()
        waitForTag("files_changes_patch_$secondPath")
        composeRule.onNodeWithTag("files_changes_patch_$firstPath").assertDoesNotExist()
        composeRule.onNodeWithTag("files_changes_patch_$secondPath").performScrollTo()
        composeRule.onNodeWithText("patch for $secondPath").assertIsDisplayed()
        composeRule.onNodeWithTag("files_changes_list")
            .performScrollToNode(hasTestTag("files_changes_row_$secondPath"))
        composeRule.onNodeWithTag("files_changes_row_$secondPath").performClick()
        composeRule.onNodeWithTag("files_changes_patch_$secondPath").assertDoesNotExist()
    }

    private fun enterChangesAndAssertAccessibleControls() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        waitForTag("files_changes_action")
        composeRule.onNodeWithContentDescription(context.getString(R.string.files_changes_action))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("files_changes_action")
            .assertHeightIsAtLeast(Sizing.minTouchTarget)
            .assertWidthIsAtLeast(Sizing.minTouchTarget)
            .performClick()
        waitForTag("files_changes_refresh")
        composeRule.onNodeWithContentDescription(context.getString(R.string.files_changes_refresh))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("files_changes_refresh")
            .assertHeightIsAtLeast(Sizing.minTouchTarget)
            .assertWidthIsAtLeast(Sizing.minTouchTarget)
        composeRule.onNodeWithContentDescription(context.getString(R.string.files_changes_exit))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("files_changes_exit")
            .assertHeightIsAtLeast(Sizing.minTouchTarget)
            .assertWidthIsAtLeast(Sizing.minTouchTarget)
    }

    private fun filesViewModel(
        snapshot: WorkspaceChangesSnapshot,
        fileRepository: FakeFileRepository = FakeFileRepository(),
        changesRepository: FakeWorkspaceChangesRepository = FakeWorkspaceChangesRepository(snapshot),
    ): FilesViewModel {
        return FilesViewModel(
            fileRepository = fileRepository,
            workspaceChangesRepository = changesRepository,
            uploadCoordinator = UploadCoordinator(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
                repositoryFactory = { fileRepository },
            ),
            savedStateHandle = SavedStateHandle(),
        )
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

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = ASYNC_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun waitForDescription(description: String) {
        composeRule.waitUntil(timeoutMillis = ASYNC_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithContentDescription(description, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private class FakeWorkspaceChangesRepository(
        private val snapshot: WorkspaceChangesSnapshot,
    ) : WorkspaceChangesRepository {
        private val snapshotCalls = AtomicInteger()
        private val diffCalls = AtomicInteger()
        val snapshotCallCount: Int get() = snapshotCalls.get()
        val diffCallCount: Int get() = diffCalls.get()

        override suspend fun loadSnapshot(): WorkspaceChangesResult<WorkspaceChangesSnapshot> {
            snapshotCalls.incrementAndGet()
            return WorkspaceChangesResult.Success(snapshot)
        }

        override suspend fun loadDiff(): WorkspaceChangesResult<Map<String, WorkspacePatch>> {
            diffCalls.incrementAndGet()
            return WorkspaceChangesResult.Success(
                snapshot.changes.associate { change ->
                    change.file to WorkspacePatch.Content("patch for ${change.file}")
                },
            )
        }
    }

    private class FakeFileRepository : FileRepository {
        var mutationCallCount = 0
            private set

        override suspend fun listFiles(path: String): FileOperationResult<FileList> =
            FileOperationResult.Ok(
                FileList(
                    path = path,
                    files = when (path) {
                        "" -> listOf(FileNode(name = "src", path = "src", type = "directory"))
                        "src" -> listOf(FileNode(name = "Keep.kt", path = "src/Keep.kt"))
                        else -> emptyList()
                    },
                ),
            )

        override suspend fun readFile(path: String): FileOperationResult<FileContent> =
            FileOperationResult.Failed("not used")

        override suspend fun searchFiles(query: String): FileOperationResult<List<FileNode>> =
            FileOperationResult.Ok(emptyList())

        override suspend fun searchSymbols(query: String): FileOperationResult<List<Symbol>> =
            FileOperationResult.Ok(emptyList())

        override suspend fun writeFile(request: FileWriteRequest): FileOperationResult<FileWriteResult> {
            mutationCallCount += 1
            return FileOperationResult.Failed("not used")
        }

        override suspend fun createDirectory(path: String): FileOperationResult<Unit> {
            mutationCallCount += 1
            return FileOperationResult.Failed("not used")
        }

        override suspend fun renameFile(fromPath: String, toPath: String): FileOperationResult<Unit> {
            mutationCallCount += 1
            return FileOperationResult.Failed("not used")
        }

        override suspend fun deleteFile(path: String): FileOperationResult<Unit> {
            mutationCallCount += 1
            return FileOperationResult.Failed("not used")
        }

        override suspend fun uploadFile(request: FileUploadRequest): FileOperationResult<FileUploadResult> {
            mutationCallCount += 1
            return FileOperationResult.Failed("not used")
        }

        override suspend fun capabilities(): FileCapabilities = FileCapabilities()
    }

    private companion object {
        const val ASYNC_TIMEOUT_MILLIS = 5_000L
    }
}
