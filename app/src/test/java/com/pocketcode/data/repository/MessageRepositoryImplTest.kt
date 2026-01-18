package com.pocketcode.data.repository

import com.pocketcode.core.database.dao.MessageDao
import com.pocketcode.core.database.entity.MessageEntity
import com.pocketcode.core.database.entity.PartEntity
import com.pocketcode.core.network.ApiResult
import com.pocketcode.core.network.OpenCodeApi
import com.pocketcode.data.remote.dto.MessageInfoDto
import com.pocketcode.data.remote.dto.MessageTimeDto
import com.pocketcode.data.remote.dto.MessageWrapperDto
import com.pocketcode.data.remote.dto.PartDto
import com.pocketcode.data.remote.mapper.MessageMapper
import com.pocketcode.data.remote.mapper.PartMapper
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MessageRepositoryImplTest {

    @MockK
    private lateinit var api: OpenCodeApi

    @MockK
    private lateinit var messageDao: MessageDao

    private lateinit var messageMapper: MessageMapper
    private lateinit var partMapper: PartMapper
    private lateinit var json: Json
    private lateinit var repository: MessageRepositoryImpl

    private val testTimeMillis = 1737194400000L

    private val testMessageEntity = MessageEntity(
        id = "msg-1",
        sessionID = "session-1",
        createdAt = testTimeMillis,
        role = "user",
        completedAt = null,
        parentID = null,
        providerID = null,
        modelID = null,
        agent = "default",
        cost = null,
        tokensInput = null,
        tokensOutput = null,
        tokensReasoning = null,
        tokensCacheRead = null,
        tokensCacheWrite = null,
        errorCode = null,
        errorMessage = null
    )

    private val testPartEntity = PartEntity(
        id = "part-1",
        sessionID = "session-1",
        messageID = "msg-1",
        type = "text",
        text = "Hello world",
        callID = null,
        toolName = null,
        toolStateStatus = null,
        toolStateInput = null,
        toolStateRawInput = null,
        toolStateTitle = null,
        toolStateOutput = null,
        toolStateError = null,
        toolStateStartedAt = null,
        toolStateEndedAt = null,
        toolStateMetadata = null,
        mime = null,
        filename = null,
        url = null,
        hash = null,
        files = null
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
        messageMapper = MessageMapper()
        partMapper = PartMapper()
        json = Json { ignoreUnknownKeys = true }
        repository = MessageRepositoryImpl(api, messageDao, messageMapper, partMapper, json)
    }

    @Test
    fun `getMessagesForSession returns messages with parts from database`() = runTest {
        every { messageDao.getMessagesForSession("session-1") } returns flowOf(listOf(testMessageEntity))
        every { messageDao.getPartsForSession("session-1") } returns flowOf(listOf(testPartEntity))

        val result = repository.getMessagesForSession("session-1").first()

        assertEquals(1, result.size)
        assertEquals("msg-1", result[0].message.id)
        assertEquals(1, result[0].parts.size)
        assertEquals("Hello world", (result[0].parts[0] as com.pocketcode.domain.model.Part.Text).text)
    }

    @Test
    fun `getMessagesForSession returns empty list when no messages`() = runTest {
        every { messageDao.getMessagesForSession("session-1") } returns flowOf(emptyList())
        every { messageDao.getPartsForSession("session-1") } returns flowOf(emptyList())

        val result = repository.getMessagesForSession("session-1").first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchMessages fetches from API and caches to database`() = runTest {
        val dtos = listOf(testMessageWrapperDto)
        coEvery { api.getMessages("session-1", null) } returns dtos
        coEvery { messageDao.insertMessageWithParts(any(), any()) } just Runs

        val result = repository.fetchMessages("session-1")

        assertTrue(result is ApiResult.Success)
        val messages = (result as ApiResult.Success).data
        assertEquals(1, messages.size)
        assertEquals("msg-1", messages[0].message.id)

        coVerify { messageDao.insertMessageWithParts(any(), any()) }
    }

    @Test
    fun `fetchMessages returns error when API fails`() = runTest {
        coEvery { api.getMessages("session-1", null) } throws RuntimeException("Network error")

        val result = repository.fetchMessages("session-1")

        assertTrue(result is ApiResult.Error)
        assertEquals("Network error", (result as ApiResult.Error).message)
    }

    @Test
    fun `sendMessage sends async message`() = runTest {
        coEvery { api.sendMessageAsync("session-1", any()) } just Runs

        val result = repository.sendMessage("session-1", "Hello")

        assertTrue(result is ApiResult.Success)
        coVerify { api.sendMessageAsync("session-1", match { 
            it.parts.size == 1 && it.parts[0].type == "text" && it.parts[0].text == "Hello"
        }) }
    }

    @Test
    fun `respondToPermission calls API`() = runTest {
        coEvery { api.respondToPermission("session-1", "perm-1", any()) } returns true

        val result = repository.respondToPermission("session-1", "perm-1", "allow")

        assertTrue(result is ApiResult.Success)
        assertTrue((result as ApiResult.Success).data)
    }

    @Test
    fun `respondToPermission returns error when API fails`() = runTest {
        coEvery { api.respondToPermission("session-1", "perm-1", any()) } throws RuntimeException("Failed")

        val result = repository.respondToPermission("session-1", "perm-1", "allow")

        assertTrue(result is ApiResult.Error)
    }
}
