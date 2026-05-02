package dev.blazelight.p4oc.data.session

import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.data.remote.dto.CreateSessionRequest
import dev.blazelight.p4oc.data.remote.dto.ProjectDto
import dev.blazelight.p4oc.data.remote.dto.UpdateSessionRequest
import dev.blazelight.p4oc.data.remote.mapper.MessageMapper
import dev.blazelight.p4oc.data.remote.mapper.SessionMapper
import dev.blazelight.p4oc.data.workspace.SessionWorkspaceClient
import dev.blazelight.p4oc.data.workspace.WorkspaceClient
import dev.blazelight.p4oc.domain.model.Message
import dev.blazelight.p4oc.domain.model.MessageWithParts
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.model.Session
import dev.blazelight.p4oc.domain.model.SessionStatus
import dev.blazelight.p4oc.domain.model.TokenUsage
import dev.blazelight.p4oc.domain.session.SessionId
import dev.blazelight.p4oc.domain.session.WorkspaceSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive

class SessionRepositoryImpl(
    private val client: SessionWorkspaceClient,
    private val messageMapper: MessageMapper? = null,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SessionRepository {
    data class CachedSnapshot(
        val snapshot: Snapshot,
        val fetchedAtMs: Long,
        val workspaceKey: String,
    )

    private val reducer = SessionReducer(client.workspace)
    private val hydrateBuffer = HydrationEventBuffer()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow<RepoState>(RepoState.Hydrating())
    override val state: StateFlow<RepoState> = _state.asStateFlow()

    @Volatile
    private var inFlight: Deferred<Result<CachedSnapshot>>? = null

    @Volatile
    private var lastSuccess: CachedSnapshot? = null

    private val messageStates = mutableMapOf<String, MutableStateFlow<List<MessageWithParts>>>()

    fun peek(): CachedSnapshot? {
        val cached = lastSuccess ?: return null
        return if (cached.workspaceKey == client.workspace.key.toString() && nowMs() - cached.fetchedAtMs <= FRESHNESS_MS) {
            cached
        } else {
            null
        }
    }

    fun prewarm(seedProjects: List<ProjectDto>): Deferred<Result<CachedSnapshot>> {
        peek()?.let { return CompletableDeferred(Result.success(it)) }

        synchronized(this) {
            inFlight?.let { return it }

            val created = scope.async {
                runCatching { hydrate(seedProjects) }
            }
            inFlight = created
            return created
        }
    }

    suspend fun awaitOrFetch(): Result<CachedSnapshot> {
        peek()?.let { return Result.success(it) }
        inFlight?.let { existing ->
            return try {
                existing.await()
            } catch (ce: CancellationException) {
                if (!coroutineContext.isActive) throw ce
                Result.failure(ce)
            }
        }

        val projects = runCatching { client.listProjects() }
            .getOrElse { return Result.failure(it) }
        return prewarm(projects).await()
    }

    fun invalidate() {
        val toCancel = synchronized(this) {
            val current = inFlight
            inFlight = null
            lastSuccess = null
            current
        }
        toCancel?.cancel(CancellationException("Session repository invalidated"))
    }

    override suspend fun refresh() {
        val snapshot = hydrate(client.listProjects()).snapshot
        _state.value = RepoState.Live(snapshot)
    }

    override suspend fun getSession(id: SessionId): WorkspaceSession? {
        val current = state.value.snapshot.sessions[id.value]
        if (current != null) return current

        val session = SessionMapper.mapToDomain(client.getSession(id.value))
        return WorkspaceSession(id, client.workspace, session)
    }

    override fun acceptEvent(event: OpenCodeEvent) {
        _state.value = when (val current = _state.value) {
            is RepoState.Hydrating -> hydrateBuffer.buffer(event).copy(snapshot = current.snapshot)
            is RepoState.Live -> RepoState.Live(reducer.reduce(current.snapshot, event))
            is RepoState.Stale -> current.copy(snapshot = reducer.reduce(current.snapshot, event))
        }

        when (event) {
            is OpenCodeEvent.MessageUpdated -> upsertMessage(event.message)
            is OpenCodeEvent.MessagePartUpdated -> upsertPart(event.part, event.delta)
            is OpenCodeEvent.MessageRemoved -> removeMessage(event.sessionID, event.messageID)
            is OpenCodeEvent.PartRemoved -> removePart(event.sessionID, event.messageID, event.partID)
            else -> Unit
        }
    }

    override fun messages(sessionId: SessionId): StateFlow<List<MessageWithParts>> = messageState(sessionId.value).asStateFlow()

    override suspend fun loadMessages(sessionId: SessionId, limit: Int?) {
        val workspaceClient = client as? WorkspaceClient
            ?: error("Message loading requires WorkspaceClient")
        val mapper = messageMapper ?: error("Message loading requires MessageMapper")
        val messages = workspaceClient.getMessages(sessionId.value, limit).map { dto -> mapper.mapWrapperToDomain(dto) }
        messageState(sessionId.value).value = messages.sortedBy { it.message.createdAt }
    }

    override suspend fun clearStreamingFlags(sessionId: SessionId) {
        val state = messageState(sessionId.value)
        state.value = state.value.map { msgWithParts ->
            msgWithParts.copy(
                parts = msgWithParts.parts.map { part ->
                    if (part is Part.Text && part.isStreaming) part.copy(isStreaming = false) else part
                },
            )
        }
    }

    suspend fun createSession(title: String?, directory: String?): WorkspaceSession {
        val dto = client.createSession(CreateSessionRequest(title = title), directory)
        val session = SessionMapper.mapToDomain(dto)
        val workspaceSession = workspaceSession(session)
        upsert(workspaceSession)
        return workspaceSession
    }

    suspend fun deleteSession(id: SessionId, directory: String?) {
        val before = state.value.snapshot
        _state.value = RepoState.Live(before.copy(sessions = before.sessions - id.value))
        try {
            client.deleteSession(id.value, directory)
        } catch (e: Exception) {
            runCatching { refresh() }
            _state.value = RepoState.Stale(state.value.snapshot, reason = e.message)
            throw e
        }
    }

    suspend fun renameSession(id: SessionId, title: String, directory: String?): WorkspaceSession {
        val dto = client.updateSession(id.value, UpdateSessionRequest(title = title), directory)
        val session = workspaceSession(SessionMapper.mapToDomain(dto))
        upsert(session)
        return session
    }

    suspend fun shareSession(id: SessionId, directory: String?): WorkspaceSession {
        val session = workspaceSession(SessionMapper.mapToDomain(client.shareSession(id.value, directory)))
        upsert(session)
        return session
    }

    suspend fun unshareSession(id: SessionId, directory: String?): WorkspaceSession {
        val session = workspaceSession(SessionMapper.mapToDomain(client.unshareSession(id.value, directory)))
        upsert(session)
        return session
    }

    suspend fun summarizeSession(id: SessionId, directory: String?) {
        client.summarizeSession(id.value, directory)
        refresh()
    }

    private suspend fun hydrate(seedProjects: List<ProjectDto>): CachedSnapshot {
        _state.value = RepoState.Hydrating(snapshot = state.value.snapshot, bufferedEvents = hydrateBuffer.size)
        val workspaceKey = client.workspace.key.toString()

        try {
            val projects = seedProjects.sortedByDescending { it.worktree }
            val semaphore = Semaphore(MAX_CONCURRENT)

            val (sessions, statuses) = coroutineScope {
                val sessionsDeferred = async {
                    val globalDeferred = async {
                        semaphore.withPermit {
                            client.listSessions(directory = null, roots = true, limit = 100)
                        }
                    }
                    val projectDeferreds = projects.map { project ->
                        async {
                            semaphore.withPermit {
                                client.listSessions(directory = project.worktree, roots = true, limit = 100)
                            } to project
                        }
                    }

                    val globalSessions = runCatching { globalDeferred.await() }
                        .getOrElse { error ->
                            AppLog.e(TAG, "Failed to load global sessions: ${error.message}")
                            emptyList()
                        }
                        .map { dto -> workspaceSession(SessionMapper.mapToDomain(dto)) }

                    val projectSessions = projectDeferreds.awaitAll().flatMap { (result, project) ->
                        runCatching { result }
                            .getOrElse { error ->
                                AppLog.e(TAG, "Failed to load sessions for ${project.worktree}: ${error.message}")
                                emptyList()
                            }
                            .map { dto -> workspaceSession(SessionMapper.mapToDomain(dto)) }
                    }

                    val projectSessionIds = projectSessions.map { it.id.value }.toSet()
                    val uniqueGlobalSessions = globalSessions.filter { it.id.value !in projectSessionIds }
                    (uniqueGlobalSessions + projectSessions).associateBy { it.id.value }
                }

                val statusesDeferred = async {
                    val directories = listOf<String?>(null) + projects.map { it.worktree }
                    directories.map { directory ->
                        async {
                            semaphore.withPermit {
                                directory to runCatching { client.getSessionStatuses(directory) }
                            }
                        }
                    }.awaitAll().fold(mutableMapOf<String, SessionStatus>()) { acc, (_, result) ->
                        result.onSuccess { statusDtos ->
                            statusDtos.forEach { (sessionId, dto) ->
                                acc[sessionId] = SessionMapper.mapStatusToDomain(dto)
                            }
                        }.onFailure { error ->
                            AppLog.e(TAG, "Failed to load session statuses: ${error.message}")
                        }
                        acc
                    }
                }

                sessionsDeferred.await() to statusesDeferred.await()
            }

            check(client.workspace.key.toString() == workspaceKey) { "Workspace switched during session hydrate" }

            val hydrated = Snapshot(
                sessions = sessions,
                projects = projects,
                statuses = statuses,
            )
            val liveSnapshot = hydrateBuffer.replayOver(hydrated, reducer)
            hydrateBuffer.clear()
            val cached = CachedSnapshot(
                snapshot = liveSnapshot,
                fetchedAtMs = nowMs(),
                workspaceKey = workspaceKey,
            )
            lastSuccess = cached
            _state.value = RepoState.Live(liveSnapshot)
            return cached
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = RepoState.Stale(state.value.snapshot, reason = e.message)
            throw e
        } finally {
            synchronized(this) {
                if (inFlight?.isCompleted == true || inFlight?.isCancelled == true) {
                    inFlight = null
                }
            }
        }
    }

    private fun workspaceSession(session: Session): WorkspaceSession = WorkspaceSession(
        id = SessionId(session.id),
        workspace = client.workspace,
        session = session,
    )

    private fun upsert(session: WorkspaceSession) {
        val snapshot = state.value.snapshot
        _state.value = RepoState.Live(snapshot.copy(sessions = snapshot.sessions + (session.id.value to session)))
    }

    private fun messageState(sessionId: String): MutableStateFlow<List<MessageWithParts>> = synchronized(messageStates) {
        messageStates.getOrPut(sessionId) { MutableStateFlow(emptyList()) }
    }

    private fun upsertMessage(message: Message) {
        val state = messageState(message.sessionID)
        val existing = state.value.firstOrNull { it.message.id == message.id }
        val updated = if (existing != null) {
            state.value.map { if (it.message.id == message.id) it.copy(message = message) else it }
        } else {
            state.value + MessageWithParts(message, emptyList())
        }
        state.value = updated.sortedBy { it.message.createdAt }
    }

    private fun upsertPart(part: Part, delta: String?) {
        val state = messageState(part.sessionID)
        val existingMessage = state.value.firstOrNull { it.message.id == part.messageID }
            ?: createPlaceholderMessage(part.sessionID, part.messageID)
        val partIndex = existingMessage.parts.indexOfFirst { it.id == part.id }
        val updatedParts = if (partIndex >= 0) {
            existingMessage.parts.toMutableList().apply {
                this[partIndex] = applyDelta(this[partIndex], part, delta)
            }
        } else {
            existingMessage.parts + part
        }
        val updatedMessage = existingMessage.copy(parts = updatedParts)
        state.value = (state.value.filterNot { it.message.id == part.messageID } + updatedMessage)
            .sortedBy { it.message.createdAt }
    }

    private fun removeMessage(sessionId: String, messageId: String) {
        val state = messageState(sessionId)
        state.value = state.value.filterNot { it.message.id == messageId }
    }

    private fun removePart(sessionId: String, messageId: String, partId: String) {
        val state = messageState(sessionId)
        state.value = state.value.map { msgWithParts ->
            if (msgWithParts.message.id == messageId) {
                msgWithParts.copy(parts = msgWithParts.parts.filterNot { it.id == partId })
            } else {
                msgWithParts
            }
        }
    }

    private fun applyDelta(existing: Part, incoming: Part, delta: String?): Part =
        if (delta != null && incoming is Part.Text && existing is Part.Text) {
            incoming.copy(text = existing.text + delta, isStreaming = true)
        } else {
            incoming
        }

    private fun createPlaceholderMessage(sessionId: String, messageId: String): MessageWithParts = MessageWithParts(
        message = Message.Assistant(
            id = messageId,
            sessionID = sessionId,
            createdAt = System.currentTimeMillis(),
            parentID = "",
            providerID = "",
            modelID = "",
            mode = "",
            agent = "",
            cost = 0.0,
            tokens = TokenUsage(input = 0, output = 0),
        ),
        parts = emptyList(),
    )

    private companion object {
        const val FRESHNESS_MS = 30_000L
        const val MAX_CONCURRENT = 10
        const val TAG = "SessionRepository"
    }
}
