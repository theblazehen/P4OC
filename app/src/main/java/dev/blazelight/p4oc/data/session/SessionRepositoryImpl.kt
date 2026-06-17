package dev.blazelight.p4oc.data.session

import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.data.files.ofish.OfishSessionNames
import dev.blazelight.p4oc.data.remote.dto.CreateSessionRequest
import dev.blazelight.p4oc.data.remote.dto.ProjectDto
import dev.blazelight.p4oc.data.remote.dto.QuestionRequestDto
import dev.blazelight.p4oc.data.remote.dto.SendMessageRequest
import dev.blazelight.p4oc.data.remote.dto.UpdateSessionRequest
import dev.blazelight.p4oc.data.remote.mapper.MessageMapper
import dev.blazelight.p4oc.data.remote.mapper.SessionMapper
import dev.blazelight.p4oc.data.remote.mapper.mapQuestionRequestDtoToDomain
import dev.blazelight.p4oc.data.workspace.SessionWorkspaceClient
import dev.blazelight.p4oc.data.workspace.WorkspaceClient
import dev.blazelight.p4oc.domain.model.Message
import dev.blazelight.p4oc.domain.model.MessageWithParts
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.model.Session
import dev.blazelight.p4oc.domain.model.SessionStatus
import dev.blazelight.p4oc.domain.model.TokenUsage
import dev.blazelight.p4oc.domain.model.ToolState
import dev.blazelight.p4oc.domain.model.isQuestionTool
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

class SessionRepositoryImpl(
    private val client: SessionWorkspaceClient,
    private val messageMapper: MessageMapper? = null,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val questionFetcher: (suspend () -> List<QuestionRequestDto>)? = null,
) : SessionRepository {
    data class CachedSnapshot(
        val snapshot: Snapshot,
        val fetchedAtMs: Long,
        val workspaceKey: String,
    )

    val workspace = client.workspace

    private val reducer = SessionReducer(client.workspace)
    private val hydrateBuffer = HydrationEventBuffer()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + dispatcher)

    private val _state = MutableStateFlow<RepoState>(RepoState.Hydrating())
    override val state: StateFlow<RepoState> = _state.asStateFlow()

    @Volatile
    private var inFlight: Deferred<Result<CachedSnapshot>>? = null

    @Volatile
    private var lastSuccess: CachedSnapshot? = null

    private val messageStates = mutableMapOf<String, MutableStateFlow<List<MessageWithParts>>>()
    private val sessionUiStates = mutableMapOf<String, MutableStateFlow<SessionUiState>>()
    private val childToParentSessionIds = mutableMapOf<String, String>()

    // Question reconciliation dedup state
    private val detectedQuestionToolCallIds = mutableSetOf<String>()
    private val recentlyResolvedQuestionIds = mutableMapOf<String, Long>()

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
        if (event is OpenCodeEvent.Connected) {
            hydrateAfterReconnect()
            return
        }

        _state.value = when (val current = _state.value) {
            is RepoState.Hydrating -> if (isSessionEvent(event)) hydrateBuffer.buffer(event).copy(snapshot = current.snapshot) else current
            is RepoState.Live -> RepoState.Live(reducer.reduce(current.snapshot, event))
            is RepoState.Stale -> current.copy(snapshot = reducer.reduce(current.snapshot, event))
        }

        when (event) {
            is OpenCodeEvent.SessionCreated -> {
                event.session.parentID?.let { parentId ->
                    synchronized(childToParentSessionIds) { childToParentSessionIds[event.session.id] = parentId }
                }
            }
            is OpenCodeEvent.SessionDeleted -> {
                synchronized(childToParentSessionIds) { childToParentSessionIds.remove(event.session.id) }
                sessionUiStates.remove(event.session.id)
            }
            is OpenCodeEvent.SessionUpdated -> updateSession(event.session.id) { it.copy(session = event.session) }
            is OpenCodeEvent.SessionStatusChanged -> {
                updateSession(event.sessionID) { state ->
                    state.copy(
                        status = event.status,
                        error = null,
                        responseCompletedToken = if (event.status.isTerminalIdle()) state.responseCompletedToken + 1 else state.responseCompletedToken,
                    )
                }
                if (event.status.isTerminalIdle()) clearStreamingFlags(SessionId(event.sessionID))
            }
            is OpenCodeEvent.SessionIdle -> {
                updateSession(event.sessionID) { state ->
                    state.copy(
                        status = SessionStatus.Idle,
                        error = null,
                        responseCompletedToken = state.responseCompletedToken + 1,
                    )
                }
                clearStreamingFlags(SessionId(event.sessionID))
            }
            is OpenCodeEvent.SessionError -> {
                val eventSessionId = event.sessionID ?: return
                updateSession(eventSessionId) { state ->
                    state.copy(
                        status = SessionStatus.Idle,
                        error = event.error,
                        responseCompletedToken = state.responseCompletedToken + 1,
                    )
                }
                clearStreamingFlags(SessionId(eventSessionId))
            }
            is OpenCodeEvent.PermissionRequested -> {
                updateOwnedSession(event.permission.sessionID) { state ->
                    val callId = event.permission.callID ?: return@updateOwnedSession state
                    state.copy(
                        pendingPermissionsByCallId = state.pendingPermissionsByCallId + (callId to event.permission)
                    )
                }
            }
            is OpenCodeEvent.PermissionReplied -> {
                updateOwnedSession(event.sessionID) { state ->
                    state.copy(
                        pendingPermissionsByCallId = state.pendingPermissionsByCallId.filterValues { it.id != event.requestID }
                    )
                }
            }
            is OpenCodeEvent.QuestionAsked -> {
                updateOwnedSession(event.request.sessionID) { state ->
                    if (state.pendingQuestion == null) {
                        state.copy(pendingQuestion = event.request)
                    } else {
                        state.copy(queuedQuestions = state.queuedQuestions + event.request)
                    }
                }
            }
            is OpenCodeEvent.QuestionReplied -> resolveQuestion(event.sessionID, event.requestID)
            is OpenCodeEvent.QuestionRejected -> resolveQuestion(event.sessionID, event.requestID)
            is OpenCodeEvent.TodoUpdated -> updateSession(event.sessionID) { it.copy(todos = event.todos) }
            is OpenCodeEvent.MessageUpdated -> upsertMessage(event.message)
            is OpenCodeEvent.MessagePartUpdated -> upsertPart(event.part, event.delta)
            is OpenCodeEvent.MessagePartDelta -> applyPartDelta(event)
            is OpenCodeEvent.MessageRemoved -> removeMessage(event.sessionID, event.messageID)
            is OpenCodeEvent.PartRemoved -> removePart(event.sessionID, event.messageID, event.partID)
            else -> Unit
        }
    }

    private suspend fun fetchPendingQuestions(): List<QuestionRequestDto> =
        questionFetcher?.invoke() ?: (client as? WorkspaceClient)?.listPendingQuestions() ?: emptyList()

    /**
     * Reconcile pending questions from the server.
     * Called when a question tool part is detected or on reconnect.
     * Fetches the list of pending questions from GET /question and sets
     * pendingQuestion on owned sessions that don't already have one.
     * Skips questions that were recently resolved (anti-resurrection).
     */
    private suspend fun reconcilePendingQuestions() {
        AppLog.d(TAG, "reconcilePendingQuestions: fetching pending questions")
        val questionsToCheck = runCatching { fetchPendingQuestions() }
            .getOrElse { error ->
                AppLog.w(TAG, "Failed to fetch pending questions: ${error.message}")
                return
            }
        AppLog.d(TAG, "reconcilePendingQuestions: fetched ${questionsToCheck.size} pending question(s)")

        // Expire stale entries from recentlyResolvedQuestionIds
        val now = nowMs()
        synchronized(recentlyResolvedQuestionIds) {
            recentlyResolvedQuestionIds.entries.removeAll { (_, resolvedAtMs) ->
                now - resolvedAtMs > RESOLVED_QUESTION_TTL_MS
            }
        }

        for (questionDto in questionsToCheck) {
            // Skip if this question was recently resolved
            val isRecentlyResolved = synchronized(recentlyResolvedQuestionIds) {
                recentlyResolvedQuestionIds.containsKey(questionDto.id)
            }
            if (isRecentlyResolved) continue

            // Try to set pendingQuestion on the owned session
            updateOwnedSession(questionDto.sessionID) { state ->
                if (state.pendingQuestion == null && state.queuedQuestions.none { it.id == questionDto.id }) {
                    state.copy(pendingQuestion = mapQuestionRequestDtoToDomain(questionDto))
                } else {
                    state
                }
            }
        }
    }

    private fun hydrateAfterReconnect() {
        synchronized(this) {
            if (inFlight != null) return
            _state.value = RepoState.Hydrating(snapshot = state.value.snapshot, bufferedEvents = hydrateBuffer.size)
            inFlight = scope.async {
                runCatching { hydrate(client.listProjects()) }
            }
        }
        // Trigger question reconciliation after hydration (don't block event path)
        scope.launch {
            try {
                inFlight?.await()
                reconcilePendingQuestions()
            } catch (e: Exception) {
                AppLog.w(TAG, "Error during post-reconnect question reconciliation: ${e.message}")
            }
        }
    }

    override fun messages(sessionId: SessionId): StateFlow<List<MessageWithParts>> = messageState(
        sessionId.value
    ).asStateFlow()

    override fun sessionUiState(sessionId: SessionId): StateFlow<SessionUiState> = sessionUiStateFor(
        sessionId.value
    ).asStateFlow()

    override fun clearPermission(sessionId: SessionId, permissionId: String) {
        updateSession(sessionId.value) { state ->
            state.copy(
                pendingPermissionsByCallId = state.pendingPermissionsByCallId.filterValues { it.id != permissionId }
            )
        }
    }

    override fun clearPermissionByRequestId(sessionId: SessionId, requestId: String) {
        updateSession(sessionId.value) { state ->
            state.copy(
                pendingPermissionsByCallId = state.pendingPermissionsByCallId.filterValues { it.id != requestId }
            )
        }
    }

    override fun clearQuestion(sessionId: SessionId, requestId: String?) {
        clearQuestionInternal(sessionId.value, requestId)
    }

    /**
     * Internal implementation: clear the current pending question and promote the next queued question.
     * If [requestId] is provided, record it as recently resolved for dedup.
     */
    private fun clearQuestionInternal(sessionId: String, requestId: String?) {
        // Record the request ID if provided (for dismissQuestion path)
        if (requestId != null) {
            synchronized(recentlyResolvedQuestionIds) {
                recentlyResolvedQuestionIds[requestId] = nowMs()
            }
        }

        updateSession(sessionId) { state ->
            val nextQuestion = state.queuedQuestions.firstOrNull()
            state.copy(
                pendingQuestion = nextQuestion,
                queuedQuestions = if (nextQuestion == null) emptyList() else state.queuedQuestions.drop(1),
            )
        }
    }

    /**
     * Resolve a question that was answered or rejected (by this client or any other,
     * e.g. the desktop TUI). Clears it from the owning session's UI state by matching
     * [requestID]: if it is the current pending question, the next queued question is
     * promoted; if it is sitting in the queue, it is removed in place. Unknown
     * requestIDs are a no-op (idempotent — a resolution we never tracked, or one
     * already handled locally).
     * Records the resolved ID for dedup to prevent re-surfacing within the TTL.
     */
    private fun resolveQuestion(eventSessionId: String, requestID: String) {
        // Record this ID as recently resolved to prevent re-surfacing on reconcile
        synchronized(recentlyResolvedQuestionIds) {
            recentlyResolvedQuestionIds[requestID] = nowMs()
        }

        updateOwnedSession(eventSessionId) { state ->
            when {
                state.pendingQuestion?.id == requestID -> {
                    val nextQuestion = state.queuedQuestions.firstOrNull()
                    state.copy(
                        pendingQuestion = nextQuestion,
                        queuedQuestions = if (nextQuestion == null) emptyList() else state.queuedQuestions.drop(1),
                    )
                }
                state.queuedQuestions.any { it.id == requestID } -> {
                    state.copy(queuedQuestions = state.queuedQuestions.filterNot { it.id == requestID })
                }
                else -> state
            }
        }
    }

    override suspend fun loadMessages(sessionId: SessionId, limit: Int?) {
        val workspaceClient = client as? WorkspaceClient
            ?: error("Message loading requires WorkspaceClient")
        val mapper = messageMapper ?: error("Message loading requires MessageMapper")
        val messages = workspaceClient.getMessages(sessionId.value, limit).map { dto -> mapper.mapWrapperToDomain(dto) }
        mergeLoadedMessages(sessionId.value, messages)
        // A question tool part loaded via REST (state=running) means a question is
        // pending that the live question.asked SSE event was missed for. Reconcile so
        // the interactive card renders. (upsertPart only fires for live SSE updates,
        // not REST history load, so this is the deterministic open-a-session trigger.)
        val hasRunningQuestion = messages.any { mwp ->
            mwp.parts.any { it is Part.Tool && it.isQuestionTool() && it.state is ToolState.Running }
        }
        if (hasRunningQuestion) {
            reconcilePendingQuestions()
        }
    }

    override fun sendMessageAsync(sessionId: SessionId, request: SendMessageRequest): Deferred<Result<Unit>> = scope.async {
        runMutationCatching { client.sendMessageAsync(sessionId.value, request) }
    }

    override fun abortSession(sessionId: SessionId): Deferred<Result<Boolean>> = scope.async {
        runMutationCatching { client.abortSession(sessionId.value) }
    }

    override fun clearStreamingFlags(sessionId: SessionId) {
        messageState(sessionId.value).update { messages ->
            messages.map { msgWithParts ->
                msgWithParts.copy(
                    parts = msgWithParts.parts.map { part ->
                        if (part is Part.Text && part.isStreaming) part.copy(isStreaming = false) else part
                    },
                )
            }
        }
    }

    override fun close() {
        invalidate()
        job.cancel("SessionRepository closed")
        synchronized(messageStates) { messageStates.clear() }
        synchronized(sessionUiStates) { sessionUiStates.clear() }
        synchronized(childToParentSessionIds) { childToParentSessionIds.clear() }
        synchronized(detectedQuestionToolCallIds) { detectedQuestionToolCallIds.clear() }
        synchronized(recentlyResolvedQuestionIds) { recentlyResolvedQuestionIds.clear() }
    }

    suspend fun createSession(title: String?): WorkspaceSession {
        val dto = client.createSession(CreateSessionRequest(title = title))
        val session = SessionMapper.mapToDomain(dto)
        val workspaceSession = workspaceSession(session)
        upsert(workspaceSession)
        return workspaceSession
    }

    suspend fun deleteSession(id: SessionId) {
        val before = state.value.snapshot
        _state.value = RepoState.Live(before.copy(sessions = before.sessions - id.value))
        try {
            client.deleteSession(id.value)
        } catch (e: Exception) {
            val refetch = runCatching { hydrate(client.listProjects()).snapshot }
            _state.value = refetch.fold(
                onSuccess = { RepoState.Live(it) },
                onFailure = { RepoState.Stale(before, reason = it.message ?: e.message) },
            )
            throw e
        }
    }

    suspend fun renameSession(id: SessionId, title: String): WorkspaceSession {
        val dto = client.updateSession(id.value, UpdateSessionRequest(title = title))
        val session = workspaceSession(SessionMapper.mapToDomain(dto))
        upsert(session)
        return session
    }

    suspend fun shareSession(id: SessionId): WorkspaceSession {
        val session = workspaceSession(SessionMapper.mapToDomain(client.shareSession(id.value)))
        upsert(session)
        return session
    }

    suspend fun unshareSession(id: SessionId): WorkspaceSession {
        val session = workspaceSession(SessionMapper.mapToDomain(client.unshareSession(id.value)))
        upsert(session)
        return session
    }

    suspend fun summarizeSession(id: SessionId) {
        client.summarizeSession(id.value)
        refresh()
    }

    private suspend fun hydrate(seedProjects: List<ProjectDto>): CachedSnapshot {
        _state.value = RepoState.Hydrating(snapshot = state.value.snapshot, bufferedEvents = hydrateBuffer.size)
        val workspaceKey = client.workspace.key.toString()

        try {
            val projects = seedProjects.sortedByDescending { it.worktree }
            val semaphore = Semaphore(MAX_CONCURRENT)
            val totalSteps = (projects.size + 1) * 2
            val completedSteps = AtomicInteger(0)

            fun updateHydrationState(currentStep: String? = null, completed: Int = completedSteps.get()) {
                val current = _state.value as? RepoState.Hydrating
                _state.value = RepoState.Hydrating(
                    snapshot = state.value.snapshot,
                    bufferedEvents = current?.bufferedEvents ?: hydrateBuffer.size,
                    completedSteps = completed,
                    totalSteps = totalSteps,
                    currentStep = currentStep,
                )
            }

            suspend fun <T> trackedStep(label: String, block: suspend () -> T): T {
                updateHydrationState(currentStep = label)
                return try {
                    block()
                } finally {
                    updateHydrationState(completed = completedSteps.incrementAndGet())
                }
            }

            updateHydrationState()

            val (sessions, statuses) = coroutineScope {
                val sessionsDeferred = async {
                    val globalDeferred = async {
                        trackedStep("Loading global sessions") {
                            semaphore.withPermit {
                                client.listSessions(directory = null, roots = true, limit = 100)
                            }
                        }
                    }
                    val projectDeferreds = projects.map { project ->
                        async {
                            trackedStep("Loading sessions for ${project.worktree.substringAfterLast('/')}") {
                                semaphore.withPermit {
                                    client.listSessions(directory = project.worktree, roots = true, limit = 100)
                                }
                            } to project
                        }
                    }

                    val globalSessions = runCatching { globalDeferred.await() }
                        .getOrElse { error ->
                            AppLog.e(TAG, "Failed to load global sessions: ${error.message}")
                            emptyList()
                        }
                        .filterNot { dto -> OfishSessionNames.isOfishTitle(dto.title) }
                        .map { dto -> workspaceSession(SessionMapper.mapToDomain(dto)) }

                    val projectSessions = projectDeferreds.awaitAll().flatMap { (result, project) ->
                        runCatching { result }
                            .getOrElse { error ->
                                AppLog.e(TAG, "Failed to load sessions for ${project.worktree}: ${error.message}")
                                emptyList()
                            }
                            .filterNot { dto -> OfishSessionNames.isOfishTitle(dto.title) }
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
                            val label = directory?.substringAfterLast("/") ?: "global"
                            trackedStep("Loading status for $label") {
                                semaphore.withPermit {
                                    directory to runCatching { client.getSessionStatuses(directory) }
                                }
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

    private fun messageState(sessionId: String): MutableStateFlow<List<MessageWithParts>> = synchronized(
        messageStates
    ) {
        messageStates.getOrPut(sessionId) { MutableStateFlow(emptyList()) }
    }

    private fun sessionUiStateFor(sessionId: String): MutableStateFlow<SessionUiState> = synchronized(sessionUiStates) {
        sessionUiStates.getOrPut(sessionId) { MutableStateFlow(SessionUiState()) }
    }

    private fun updateSession(sessionId: String, transform: (SessionUiState) -> SessionUiState) {
        sessionUiStateFor(sessionId).update(transform)
    }

    private fun updateOwnedSession(eventSessionId: String, transform: (SessionUiState) -> SessionUiState) {
        val ownerSessionId = synchronized(childToParentSessionIds) { childToParentSessionIds[eventSessionId] } ?: eventSessionId
        updateSession(ownerSessionId, transform)
    }

    private fun mergeLoadedMessages(sessionId: String, loaded: List<MessageWithParts>) {
        val state = messageState(sessionId)
        state.update { current ->
            val currentById = current.associateBy { it.message.id }
            loaded.map { loadedMessage ->
                val currentMessage = currentById[loadedMessage.message.id]
                if (currentMessage == null) {
                    loadedMessage
                } else {
                    loadedMessage.copy(parts = mergeParts(loadedMessage.parts, currentMessage.parts))
                }
            }.let { mergedLoaded ->
                val loadedIds = mergedLoaded.map { it.message.id }.toSet()
                (mergedLoaded + current.filter { it.message.id !in loadedIds }).sortedBy { it.message.createdAt }
            }
        }
    }

    private fun mergeParts(loaded: List<Part>, current: List<Part>): List<Part> {
        val loadedById = loaded.associateBy { it.id }
        val currentById = current.associateBy { it.id }
        val mergedIds = loaded.map { it.id } + current.map { it.id }.filterNot { it in loadedById }
        return mergedIds.mapNotNull { id -> currentById[id] ?: loadedById[id] }
    }

    private fun upsertMessage(message: Message) {
        val state = messageState(message.sessionID)
        state.update { messages ->
            val existing = messages.firstOrNull { it.message.id == message.id }
            val updated = if (existing != null) {
                messages.map { if (it.message.id == message.id) it.copy(message = message) else it }
            } else {
                messages + MessageWithParts(message, emptyList())
            }
            updated.sortedBy { it.message.createdAt }
        }
    }

    private fun upsertPart(part: Part, delta: String?) {
        // Handle question tool detection for reconciliation (Trigger 1)
        if (part is Part.Tool && part.isQuestionTool()) {
            when (part.state) {
                is ToolState.Running -> {
                    // Question tool is running - fetch pending questions to set card
                    val isNewDetection = synchronized(detectedQuestionToolCallIds) {
                        detectedQuestionToolCallIds.add(part.callID)
                    }
                    if (isNewDetection) {
                        scope.launch {
                            try {
                                reconcilePendingQuestions()
                            } catch (e: Exception) {
                                AppLog.w(TAG, "Error reconciling questions on tool detection: ${e.message}")
                            }
                        }
                    }
                }
                is ToolState.Completed, is ToolState.Error -> {
                    // Tool finished - clean up from tracking set
                    synchronized(detectedQuestionToolCallIds) {
                        detectedQuestionToolCallIds.remove(part.callID)
                    }
                }
                else -> Unit
            }
        }

        val state = messageState(part.sessionID)
        state.update { messages ->
            val existingMessage = messages.firstOrNull { it.message.id == part.messageID }
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
            (messages.filterNot { it.message.id == part.messageID } + updatedMessage)
                .sortedBy { it.message.createdAt }
        }
    }

    private fun applyPartDelta(event: OpenCodeEvent.MessagePartDelta) {
        val sessionId = event.sessionID ?: findSessionIdForPart(event.messageID, event.partID) ?: return
        messageState(sessionId).update { messages ->
            messages.map { message ->
                if (message.message.id != event.messageID) return@map message
                message.copy(
                    parts = message.parts.map { part ->
                        if (part.id == event.partID) appendDeltaToPart(part, event.field, event.delta) else part
                    }
                )
            }
        }
    }

    private fun findSessionIdForPart(messageId: String, partId: String): String? = messageStates.entries.firstOrNull { (_, flow) ->
        flow.value.any { message -> message.message.id == messageId && message.parts.any { it.id == partId } }
    }?.key

    private fun appendDeltaToPart(part: Part, field: String, delta: String): Part = when (part) {
        is Part.Text -> if (field == "text") part.copy(text = part.text + delta, isStreaming = true) else part
        is Part.Reasoning -> if (field == "text") part.copy(text = part.text + delta) else part
        else -> part
    }

    private fun removeMessage(sessionId: String, messageId: String) {
        messageState(sessionId).update { messages -> messages.filterNot { it.message.id == messageId } }
    }

    private fun removePart(sessionId: String, messageId: String, partId: String) {
        messageState(sessionId).update { messages ->
            messages.map { msgWithParts ->
                if (msgWithParts.message.id == messageId) {
                    msgWithParts.copy(parts = msgWithParts.parts.filterNot { it.id == partId })
                } else {
                    msgWithParts
                }
            }
        }
    }

    private fun applyDelta(existing: Part, incoming: Part, delta: String?): Part =
        when {
            delta != null && incoming is Part.Text && existing is Part.Text -> {
                incoming.copy(text = existing.text + delta, isStreaming = true)
            }
            delta != null && incoming is Part.Reasoning && existing is Part.Reasoning -> {
                incoming.copy(text = existing.text + delta)
            }
            else -> incoming
        }

    private fun createPlaceholderMessage(sessionId: String, messageId: String): MessageWithParts = MessageWithParts(
        message = Message.Assistant(
            id = messageId,
            sessionID = sessionId,
            createdAt = nowMs(),
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

    private fun isSessionEvent(event: OpenCodeEvent): Boolean = when (event) {
        is OpenCodeEvent.SessionCreated,
        is OpenCodeEvent.SessionUpdated,
        is OpenCodeEvent.SessionDeleted,
        is OpenCodeEvent.SessionStatusChanged,
        is OpenCodeEvent.SessionDiff,
        is OpenCodeEvent.SessionIdle,
        is OpenCodeEvent.SessionCompacted,
        is OpenCodeEvent.SessionError,
        is OpenCodeEvent.MessagePartDelta -> true
        else -> false
    }

    private fun SessionStatus.isTerminalIdle(): Boolean = this !is SessionStatus.Busy && this !is SessionStatus.Retry

    private inline fun <T> runMutationCatching(block: () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        const val FRESHNESS_MS = 30_000L
        const val MAX_CONCURRENT = 10
        const val TAG = "SessionRepository"
        const val RESOLVED_QUESTION_TTL_MS = 30_000L
    }
}
