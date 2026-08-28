package dev.blazelight.p4oc.ui.workspace

import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.data.files.FileRepository
import dev.blazelight.p4oc.data.session.SessionRepositoryImpl
import dev.blazelight.p4oc.data.session.SessionRepositoryProvider
import dev.blazelight.p4oc.data.vcs.VcsDiffMode
import dev.blazelight.p4oc.data.vcs.WorkspaceChangesRepository
import dev.blazelight.p4oc.data.vcs.WorkspaceChangesRepositoryImpl
import dev.blazelight.p4oc.data.vcs.WorkspaceChangesResult
import dev.blazelight.p4oc.data.workspace.WorkspaceClient
import dev.blazelight.p4oc.di.viewModelModule
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.domain.workspace.Workspace
import dev.blazelight.p4oc.ui.screens.files.upload.UploadCoordinator
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication

class WorkspaceChangesWiringTest {
    @Test
    fun `owner constructs and exposes one changes repository backed by its leased client`() = runTest {
        val workspace = Workspace(
            server = ServerRef.fromEndpointKey("http://test.local"),
            directory = "/repo",
        )
        val generation = ServerGeneration(7L)
        val workspaceClient = mockk<WorkspaceClient>()
        every { workspaceClient.workspace } returns workspace
        val sessionRepository = mockk<SessionRepositoryImpl>(relaxed = true)
        val provider = mockk<SessionRepositoryProvider>()
        every { provider.acquire(workspace, generation) } returns
            SessionRepositoryProvider.Lease(workspaceClient, sessionRepository)
        every { provider.release(workspace, generation) } just Runs
        coEvery {
            workspaceClient.loadWorkspaceVcsDiff(VcsDiffMode.Git, context = 3)
        } returns emptyList()
        mockkObject(AppLog)
        try {
            every { AppLog.i(any(), any<String>()) } just Runs
            val owner = WorkspaceRepositoryOwner(
                tabId = "tab-1",
                workspace = workspace,
                generation = generation,
                sessionRepositoryProvider = provider,
            )
            try {
                val viewModel = WorkspaceViewModel(owner)
                val changesRepository = owner.workspaceChangesRepository

                assertTrue(changesRepository is WorkspaceChangesRepositoryImpl)
                assertSame(changesRepository, owner.workspaceChangesRepository)
                assertSame(changesRepository, viewModel.workspaceChangesRepository)
                assertSame(workspaceClient, owner.workspaceClient)
                assertEquals(generation, viewModel.generation)
                assertTrue(changesRepository.loadDiff() is WorkspaceChangesResult.Success<*>)
                coVerify(exactly = 1) {
                    workspaceClient.loadWorkspaceVcsDiff(VcsDiffMode.Git, context = 3)
                }
            } finally {
                owner.close()
            }
        } finally {
            unmockkObject(AppLog)
        }
    }

    @Test
    fun `changes repository remains route supplied rather than globally bound`() {
        val application = koinApplication { modules(viewModelModule) }
        try {
            assertNull(application.koin.getOrNull<WorkspaceChangesRepository>())
        } finally {
            application.close()
        }

        val fileRepository = mockk<FileRepository>()
        val changesRepository = mockk<WorkspaceChangesRepository>()
        val uploadCoordinator = mockk<UploadCoordinator>()
        val routeParameters = parametersOf(
            fileRepository,
            changesRepository,
            uploadCoordinator,
        )

        assertSame(fileRepository, routeParameters.get<FileRepository>())
        assertSame(changesRepository, routeParameters.get<WorkspaceChangesRepository>())
        assertSame(uploadCoordinator, routeParameters.get<UploadCoordinator>())
    }

    @Test
    fun `replacement generation owns a distinct client owner and changes repository`() {
        val workspace = Workspace(
            server = ServerRef.fromEndpointKey("http://replacement.test"),
            directory = "/repo",
        )
        val firstGeneration = ServerGeneration(1L)
        val replacementGeneration = ServerGeneration(2L)
        val firstClient = mockk<WorkspaceClient> {
            every { this@mockk.workspace } returns workspace
        }
        val replacementClient = mockk<WorkspaceClient> {
            every { this@mockk.workspace } returns workspace
        }
        val firstSessions = mockk<SessionRepositoryImpl>(relaxed = true)
        val replacementSessions = mockk<SessionRepositoryImpl>(relaxed = true)
        val provider = mockk<SessionRepositoryProvider>()
        every { provider.acquire(workspace, firstGeneration) } returns
            SessionRepositoryProvider.Lease(firstClient, firstSessions)
        every { provider.acquire(workspace, replacementGeneration) } returns
            SessionRepositoryProvider.Lease(replacementClient, replacementSessions)
        every { provider.release(workspace, firstGeneration) } just Runs
        every { provider.release(workspace, replacementGeneration) } just Runs
        mockkObject(AppLog)
        try {
            every { AppLog.i(any(), any<String>()) } just Runs
            val firstOwner = WorkspaceRepositoryOwner("tab-1", workspace, firstGeneration, provider)
            try {
                val replacementOwner = WorkspaceRepositoryOwner("tab-1", workspace, replacementGeneration, provider)
                try {
                    assertNotSame(firstOwner, replacementOwner)
                    assertNotSame(firstOwner.workspaceClient, replacementOwner.workspaceClient)
                    assertNotSame(firstOwner.fileRepository, replacementOwner.fileRepository)
                    assertNotSame(
                        firstOwner.workspaceChangesRepository,
                        replacementOwner.workspaceChangesRepository,
                    )
                } finally {
                    replacementOwner.close()
                }
            } finally {
                firstOwner.close()
            }
        } finally {
            unmockkObject(AppLog)
        }
    }
}
