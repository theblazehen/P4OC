package com.pocketcode.data.repository

import com.pocketcode.core.database.dao.SessionDao
import com.pocketcode.core.database.entity.SessionEntity
import com.pocketcode.core.network.ApiResult
import com.pocketcode.core.network.OpenCodeApi
import com.pocketcode.data.remote.dto.SessionDto
import com.pocketcode.data.remote.dto.TimeDto
import com.pocketcode.data.remote.mapper.SessionMapper
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SessionRepositoryImplTest {

    @MockK
    private lateinit var api: OpenCodeApi

    @MockK
    private lateinit var sessionDao: SessionDao

    private lateinit var sessionMapper: SessionMapper
    private lateinit var repository: SessionRepositoryImpl

    private val testTimeMillis = 1737194400000L

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

    private val testSessionEntity = SessionEntity(
        id = "session-1",
        projectID = "project-1",
        directory = "/test/dir",
        parentID = null,
        title = "Test Session",
        version = "1.0",
        createdAt = testTimeMillis,
        updatedAt = testTimeMillis,
        summaryAdditions = null,
        summaryDeletions = null,
        summaryFiles = null,
        shareUrl = null
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        sessionMapper = SessionMapper()
        repository = SessionRepositoryImpl(api, sessionDao, sessionMapper)
    }

    @Test
    fun `getSessions returns sessions from database`() = runTest {
        val entities = listOf(testSessionEntity)
        every { sessionDao.getActiveSessions() } returns flowOf(entities)

        val result = repository.getSessions().first()

        assertEquals(1, result.size)
        assertEquals("session-1", result[0].id)
        assertEquals("Test Session", result[0].title)
    }

    @Test
    fun `getSession returns session by id from database`() = runTest {
        every { sessionDao.getSessionByIdFlow("session-1") } returns flowOf(testSessionEntity)

        val result = repository.getSession("session-1").first()

        assertNotNull(result)
        assertEquals("session-1", result?.id)
    }

    @Test
    fun `getSession returns null when session not found`() = runTest {
        every { sessionDao.getSessionByIdFlow("unknown") } returns flowOf(null)

        val result = repository.getSession("unknown").first()

        assertNull(result)
    }

    @Test
    fun `fetchSessions fetches from API and caches to database`() = runTest {
        val dtos = listOf(testSessionDto)
        coEvery { api.listSessions() } returns dtos
        coEvery { sessionDao.insertSessions(any()) } just Runs

        val result = repository.fetchSessions()

        assertTrue(result is ApiResult.Success)
        val sessions = (result as ApiResult.Success).data
        assertEquals(1, sessions.size)
        assertEquals("session-1", sessions[0].id)

        coVerify { sessionDao.insertSessions(any()) }
    }

    @Test
    fun `fetchSessions returns error when API fails`() = runTest {
        coEvery { api.listSessions() } throws RuntimeException("Network error")

        val result = repository.fetchSessions()

        assertTrue(result is ApiResult.Error)
        assertEquals("Network error", (result as ApiResult.Error).message)
    }

    @Test
    fun `createSession creates and caches session`() = runTest {
        coEvery { api.createSession(any()) } returns testSessionDto
        coEvery { sessionDao.insertSession(any()) } just Runs

        val result = repository.createSession(title = "New Session")

        assertTrue(result is ApiResult.Success)
        val session = (result as ApiResult.Success).data
        assertEquals("session-1", session.id)

        coVerify { api.createSession(match { it.title == "New Session" }) }
        coVerify { sessionDao.insertSession(any()) }
    }

    @Test
    fun `updateSession updates and caches session`() = runTest {
        val updatedDto = testSessionDto.copy(title = "Updated Title")
        coEvery { api.updateSession("session-1", any()) } returns updatedDto
        coEvery { sessionDao.insertSession(any()) } just Runs

        val result = repository.updateSession("session-1", title = "Updated Title")

        assertTrue(result is ApiResult.Success)
        val session = (result as ApiResult.Success).data
        assertEquals("Updated Title", session.title)

        coVerify { sessionDao.insertSession(any()) }
    }

    @Test
    fun `deleteSession deletes from API and database`() = runTest {
        coEvery { api.deleteSession("session-1") } returns true
        coEvery { sessionDao.deleteSessionById("session-1") } just Runs

        val result = repository.deleteSession("session-1")

        assertTrue(result is ApiResult.Success)
        assertTrue((result as ApiResult.Success).data)

        coVerify { sessionDao.deleteSessionById("session-1") }
    }

    @Test
    fun `deleteSession does not delete from database when API returns false`() = runTest {
        coEvery { api.deleteSession("session-1") } returns false

        val result = repository.deleteSession("session-1")

        assertTrue(result is ApiResult.Success)
        assertFalse((result as ApiResult.Success).data)

        coVerify(exactly = 0) { sessionDao.deleteSessionById(any()) }
    }

    @Test
    fun `abortSession calls API`() = runTest {
        coEvery { api.abortSession("session-1") } returns true

        val result = repository.abortSession("session-1")

        assertTrue(result is ApiResult.Success)
        assertTrue((result as ApiResult.Success).data)
    }
}
