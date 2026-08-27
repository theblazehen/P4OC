package dev.blazelight.p4oc.data.session

import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.data.files.ofish.OfishSessionNames
import dev.blazelight.p4oc.data.remote.dto.CreateSessionRequest
import dev.blazelight.p4oc.data.remote.dto.ForkSessionRequest
import dev.blazelight.p4oc.data.remote.dto.InitSessionRequest
import dev.blazelight.p4oc.data.remote.dto.ProjectDto
import dev.blazelight.p4oc.data.remote.dto.QuestionRequestDto
import dev.blazelight.p4oc.data.remote.dto.SendMessageRequest
import dev.blazelight.p4oc.data.remote.dto.UpdateSessionRequest
import dev.blazelight.p4oc.data.remote.mapper.MessageMapper
import dev.blazelight.p4oc.data.remote.mapper.PermissionMapper
import dev.blazelight.p4oc.data.remote.mapper.SessionMapper
import dev.blazelight.p4oc.data.remote.mapper.mapQuestionRequestDtoToDomain
import dev.blazelight.p4oc.data.workspace.SessionWorkspaceClient
import dev.blazelight.p4oc.data.workspace.WorkspaceClient
import dev.blazelight.p4oc.domain.model.Message
import dev.blazelight.p4oc.domain.model.MessageError
import dev.blazelight.p4oc.domain.model.MessageWithParts
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.model.Permission
import dev.blazelight.p4oc.domain.model.Session
import dev.blazelight.p4oc.domain.model.SessionStatus
import dev.blazelight.p4oc.domain.model.TokenUsage
import dev.blazelight.p4oc.domain.model.ToolState
import dev.blazelight.p4oc.domain.model.isQuestionTool
import dev.blazelight.p4oc.domain.session.SessionId
import dev.blazelight.p4oc.domain.session.WorkspaceSession
import dev.blazelight.p4oc.domain.workspace.Workspace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicBoolean
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
    private val hydrationTransitionLock = Any()
    private var hydrationGeneration = 0L
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
    private val sessionConsumerCounts = mutableMapOf<String, Int>()
    private val childToParentSessionIds = mutableMapOf<String, String>()

    // Question reconciliation dedup state
    private val detectedQuestionToolCallIds = mutableSetOf<String>()
    private val recentlyResolvedQuestionIds = mutableMapOf<String, Long>()
    private var projectRefreshJob: Job? = null

    // The in-flight reconnect message-recovery job. Replaced (cancelling the prior) on each new
    // reconnect and cancelled on close so overlapping recovery storms are never spawned.
    @Volatile
    private var messageRecoveryJob: Job? = null

    // Per-session message-state revision for reconnect recovery. Every message-state mutation bumps
    // it; recovery commits a fetched authoritative window only while the revision it captured is
    // unchanged, so a newer SSE mutation can never be overwritten by a stale REST snapshot.
    private val sessionRevisions = mutableMapOf<String, Long>()

    // Largest history window successfully loaded for a session. Recovery re-fetches this bound
    // (defaulting to [DEFAULT_MESSAGE_HISTORY_LIMIT]) rather than an unbounded window.
    private val sessionLoadedLimits = mutableMapOf<String, Int>()

    // Sessions whose per-session message state was invalidated (e.g. by a delete). Recovery skips
    // these so a reconnect can never authoritatively repopulate a deleted conversation, even when a
    // consumer lease is still held by an open view.
    private val recoveryInvalidatedSessions = mutableSetOf<String>()

    // Per-session lease/state-lifetime generation. Bumped ONLY on acquire, final release, and
    // SessionDeleted (cleared on close) — never by ordinary message/part mutations — so it is the
    // ownership token for recovery-bound pending question/permission reconciliation. Ordinary SSE
    // message traffic racing a pending fetch must not invalidate pending recovery for the
    // still-current lease; only a lease/state-lifetime change may.
    private val sessionLeaseGenerations = mutableMapOf<String, Long>()

    // Shared boundary guarding per-session message state, consumer counts, revisions and loaded
    // limits. Held across capture, mutation+revision-bump, active-count checks, and replacement so
    // a lease release or SSE mutation cannot interleave and let a fetched window commit stale data.
    private val messageStateLock = Any()

    private enum class CommitOutcome { Committed, Inactive, Raced }

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
        hydrate(client.listProjects())
    }

    suspend fun searchSessionsInWorkspace(query: String, directory: String): List<WorkspaceSession> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return searchSessionsInDirectories(trimmed, listOf(directory))
    }

    suspend fun searchSessionsGlobally(query: String): List<WorkspaceSession> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val projects = runCatching { client.listProjects() }.getOrElse { emptyList() }
        return searchSessionsInDirectories(trimmed, listOf<String?>(null) + projects.map { it.worktree })
    }

    private suspend fun searchSessionsInDirectories(
        query: String,
        directories: List<String?>,
    ): List<WorkspaceSession> = coroutineScope {
        val results = directories.map { searchDirectory ->
            async {
                runCatching {
                    client.listSessions(
                        directory = searchDirectory,
                        scope = null,
                        roots = true,
                        search = query,
                        limit = SEARCH_LIMIT,
                    ).filterNot { dto -> OfishSessionNames.isOfishTitle(dto.title) }
                        .map { dto -> workspaceSession(SessionMapper.mapToDomain(dto)) }
                }.onFailure { error ->
                    AppLog.e(TAG, "Failed to search sessions: ${error.javaClass.simpleName}")
                }
            }
        }.awaitAll()
        if (results.all { it.isFailure }) {
            throw results.firstNotNullOf { it.exceptionOrNull() }
        }
        results.map { it.getOrElse { emptyList() } }
            .flatten()
            .distinctBy { it.id.value }
            .sortedByDescending { it.session.updatedAt }
    }

    override suspend fun getSession(id: SessionId): WorkspaceSession? {
        val current = state.value.snapshot.sessions[id.value]
        if (current != null) return current

        val session = SessionMapper.mapToDomain(client.getSession(id.value))
        return WorkspaceSession(id, client.workspace, session)
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    override fun acceptEvent(event: OpenCodeEvent) {
        if (event is OpenCodeEvent.ProjectUpdated || event is OpenCodeEvent.ProjectDirectoriesUpdated) {
            projectRefreshJob?.cancel()
            projectRefreshJob = scope.launch {
                delay(PROJECT_EVENT_REFRESH_DEBOUNCE_MS)
                runCatching { refresh() }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        AppLog.w(TAG, "Project event refresh failed: ${error.javaClass.simpleName}")
                    }
            }
            return
        }
        if (event is OpenCodeEvent.Connected) {
            val hydration = hydrateAfterReconnect()
            scope.launch {
                try {
                    hydration.await()
                    reconcilePendingQuestionsForOwnedSessions()
                    reconcileObservedPendingPermissions()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.w(TAG, "Error during post-reconnect reconciliation: ${e.javaClass.simpleName}")
                }
            }
            // Message recovery runs on EVERY reconnect, independent of the snapshot-hydrate
            // inFlight guard (which only fires once). Messages are a separate concern from the
            // session-list snapshot, so recover only actively leased sessions from their own
            // bounded authoritative REST windows (issue #14: had to leave and re-enter to see updates).
            reconcileMessagesForActiveSessions()
            return
        }

        synchronized(hydrationTransitionLock) {
            _state.value = when (val current = _state.value) {
                is RepoState.Hydrating -> if (isSessionEvent(event)) hydrateBuffer.buffer(event).copy(snapshot = current.snapshot) else current
                is RepoState.Live -> RepoState.Live(reducer.reduce(current.snapshot, event))
                is RepoState.Stale -> current.copy(snapshot = reducer.reduce(current.snapshot, event))
            }
        }

        when (event) {
            is OpenCodeEvent.SessionCreated -> {
                updateSessionOwnership(event.session)
            }
            is OpenCodeEvent.SessionDeleted -> {
                removeSessionOwnership(event.session.id)
                synchronized(messageStateLock) {
                    messageStates.remove(event.session.id)?.value = emptyList()
                    // Treat deletion as a revisioned invalidation: remove the state but mark the
                    // session so reconnect recovery never authoritatively repopulates it, even while
                    // a consumer lease is still held. Dropping the revision alone would let a stale
                    // recovery capture collide with the default `?: 0L` and resurrect the session.
                    recoveryInvalidatedSessions.add(event.session.id)
                    sessionRevisions[event.session.id] = (sessionRevisions[event.session.id] ?: 0L) + 1
                    // Deletion ends the state lifetime: any in-flight recovery-bound pending
                    // reconciliation loses ownership.
                    sessionLeaseGenerations[event.session.id] = (sessionLeaseGenerations[event.session.id] ?: 0L) + 1
                    sessionLoadedLimits.remove(event.session.id)
                    // UI-state removal happens inside the same critical section (nested order
                    // messageStateLock -> sessionUiStates, matching releaseSession) so a guarded
                    // recovery write can never recreate the entry between removal and invalidation.
                    synchronized(sessionUiStates) { sessionUiStates.remove(event.session.id) }
                }
            }
            is OpenCodeEvent.SessionUpdated -> {
                updateSessionOwnership(event.session)
                updateSession(event.session.id) { it.copy(session = event.session) }
            }
            is OpenCodeEvent.SessionStatusChanged -> {
                updateSession(event.sessionID) { state ->
                    val alreadyIdleWithError = state.status is SessionStatus.Idle && state.error != null
                    state.copy(
                        status = event.status,
                        error = if (event.status is SessionStatus.Busy) null else state.error,
                        // A trailing terminal status after an error already completed the run must
                        // not fire a second completion (haptic/unread) for the same failure.
                        responseCompletedToken = if (event.status.isTerminalIdle() && !alreadyIdleWithError) {
                            state.responseCompletedToken + 1
                        } else {
                            state.responseCompletedToken
                        },
                    )
                }
                if (event.status.isTerminalIdle()) clearStreamingFlags(SessionId(event.sessionID))
            }
            is OpenCodeEvent.SessionIdle -> {
                updateSession(event.sessionID) { state ->
                    val alreadyIdleWithError = state.status is SessionStatus.Idle && state.error != null
                    state.copy(
                        status = SessionStatus.Idle,
                        responseCompletedToken = if (alreadyIdleWithError) {
                            state.responseCompletedToken
                        } else {
                            state.responseCompletedToken + 1
                        },
                    )
                }
                clearStreamingFlags(SessionId(event.sessionID))
            }
            is OpenCodeEvent.SessionError -> {
                val eventSessionId = event.sessionID ?: return
                applySessionError(
                    eventSessionId,
                    event.error ?: MessageError(name = "UnknownError"),
                )
            }
            is OpenCodeEvent.PermissionRequested -> {
                updateOwnedSession(event.permission.sessionID) { state ->
                    state.copy(
                        pendingPermissionsByCallId = state.pendingPermissionsByCallId +
                            (event.permission.pendingPermissionKey() to event.permission)
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
                    when {
                        state.pendingQuestion?.id == event.request.id ||
                            state.queuedQuestions.any { it.id == event.request.id } -> state
                        state.pendingQuestion == null -> state.copy(pendingQuestion = event.request)
                        else -> state.copy(queuedQuestions = state.queuedQuestions + event.request)
                    }
                }
            }
            is OpenCodeEvent.QuestionReplied -> resolveQuestion(event.sessionID, event.requestID)
            is OpenCodeEvent.QuestionRejected -> resolveQuestion(event.sessionID, event.requestID)
            is OpenCodeEvent.TodoUpdated -> updateSession(event.sessionID) { it.copy(todos = event.todos) }
            is OpenCodeEvent.MessageUpdated -> {
                upsertMessage(event.message)
                val assistant = event.message as? Message.Assistant
                assistant?.error?.let { error -> applySessionError(assistant.sessionID, error) }
            }
            is OpenCodeEvent.MessagePartUpdated -> upsertPart(event.part, event.delta)
            is OpenCodeEvent.MessagePartDelta -> applyPartDelta(event)
            is OpenCodeEvent.MessageRemoved -> removeMessage(event.sessionID, event.messageID)
            is OpenCodeEvent.PartRemoved -> removePart(event.sessionID, event.messageID, event.partID)
            else -> Unit
        }
    }

    private suspend fun fetchPendingQuestions(sessionId: String): List<QuestionRequestDto> =
        questionFetcher?.invoke()?.filter { it.sessionID == sessionId }
            ?: client.listSessionQuestions(sessionId)

    private suspend fun reconcilePendingQuestionsForOwnedSessions() {
        val sessionIds = synchronized(sessionUiStates) { sessionUiStates.keys.toList() }
        sessionIds.forEach { reconcilePendingQuestions(it) }
    }

    /**
     * Reconcile pending questions from the server.
     * Called when a question tool part is detected or on reconnect.
     * Fetches the list of pending questions from GET /question and sets
     * pendingQuestion on owned sessions that don't already have one.
     * Skips questions that were recently resolved (anti-resurrection).
     *
     * A non-null [leaseGeneration] marks a recovery-bound call: the fetch is skipped when
     * ownership was already lost, and every UI-state write is re-checked atomically against the
     * lease generation so a lease released (or released and reacquired) mid-fetch is never
     * repopulated with stale state. Ordinary message traffic does not invalidate ownership.
     */
    private suspend fun reconcilePendingQuestions(sessionId: String, leaseGeneration: Long? = null) {
        if (leaseGeneration != null && !ownsRecoveryLease(sessionId, leaseGeneration)) return
        AppLog.d(TAG, "reconcilePendingQuestions: fetching pending questions")
        val questionsToCheck = runCatching { fetchPendingQuestions(sessionId) }
            .getOrElse { error ->
                if (error is CancellationException) throw error
                AppLog.w(TAG, "Failed to fetch pending questions: ${error.javaClass.simpleName}")
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

            // Mirror live question.asked handling: first pending question is shown,
            // additional recovered questions are queued behind it.
            withRecoveryOwnership(sessionId, leaseGeneration) {
                updateOwnedSession(questionDto.sessionID) { state ->
                    val question = mapQuestionRequestDtoToDomain(questionDto)
                    when {
                        state.pendingQuestion?.id == question.id ||
                            state.queuedQuestions.any { it.id == question.id } -> state
                        state.pendingQuestion == null -> state.copy(pendingQuestion = question)
                        else -> state.copy(queuedQuestions = state.queuedQuestions + question)
                    }
                }
            }
        }
    }

    private fun hydrateAfterReconnect(): Deferred<Result<CachedSnapshot>> {
        return synchronized(this) {
            inFlight?.let { return@synchronized it }
            synchronized(hydrationTransitionLock) {
                _state.value = RepoState.Hydrating(snapshot = state.value.snapshot, bufferedEvents = hydrateBuffer.size)
            }
            scope.async {
                runCatching { hydrate(client.listProjects()) }
            }.also { inFlight = it }
        }
    }

    /**
     * Recover messages for every actively-leased session after an SSE reconnect.
     * Called directly from the [OpenCodeEvent.Connected] path (NOT gated by the snapshot-hydrate
     * inFlight guard, which only permits one hydration) so an open conversation self-heals on every
     * reconnect (issue #14: had to leave and re-enter to see updates). Only sessions with a positive
     * active consumer count are fetched; each is recovered from its own bounded authoritative REST
     * window, committed only when its per-session revision is unchanged and it remains active, with a
     * bounded number of retries if an SSE mutation races the fetch.
     */
    private fun reconcileMessagesForActiveSessions() {
        messageRecoveryJob?.cancel(CancellationException("Previous reconnect recovery superseded"))
        messageRecoveryJob = scope.launch {
            val active = synchronized(messageStateLock) {
                sessionConsumerCounts.keys.filterNot { it in recoveryInvalidatedSessions }
            }
            for (sessionId in active) {
                try {
                    reconcileMessages(SessionId(sessionId))
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    AppLog.w(TAG, "Post-reconnect message recovery failed for $sessionId: ${e.javaClass.simpleName}")
                }
            }
        }
    }

    /**
     * Reconciles the cached message state for a single session against the server's authoritative
     * REST window. This is the shared primitive used both by reconnect recovery (for every actively
     * leased session) and by post-send callers recovering a missed terminal SSE update. It reuses
     * the same active-lease, revision-safe, remembered-window algorithm, so a second independent
     * polling path is never introduced.
     */
    override suspend fun reconcileMessages(sessionId: SessionId) {
        recoverMessagesForSession(sessionId.value)
    }

    private suspend fun recoverMessagesForSession(sessionId: String) {
        val workspaceClient = client as? WorkspaceClient
            ?: error("Message recovery requires WorkspaceClient")
        val mapper = messageMapper ?: error("Message recovery requires MessageMapper")

        var racedLoaded: List<MessageWithParts> = emptyList()
        var racedLimit: Int = DEFAULT_MESSAGE_HISTORY_LIMIT
        repeat(MESSAGE_RECOVERY_MAX_ATTEMPTS) {
            val result = recoverAttempt(sessionId, workspaceClient, mapper)
            if (result.outcome != CommitOutcome.Raced) return
            racedLoaded = result.loaded
            racedLimit = result.limit
            delay(MESSAGE_RECOVERY_RETRY_DELAY_MS)
        }
        // The authoritative window raced a newer SSE mutation on every attempt. Do not overwrite the
        // live state; instead merge the freshly fetched REST messages around what SSE already
        // applied, bump the revision, and reconcile pending state so nothing is lost.
        mergeRacedRecovery(sessionId, racedLoaded, racedLimit)
    }

    private fun mergeRacedRecovery(sessionId: String, loaded: List<MessageWithParts>, limit: Int) {
        val leaseGeneration = synchronized(messageStateLock) {
            // Recheck under the lock: a lease may have been released (count removed, state cleared)
            // between the recovery loop and here, so this merge must never recreate an inactive
            // session that a subsequent lease would then inherit as stale data. The current lease
            // generation is captured as the ownership token for the follow-up pending
            // reconciliation; the revision bump stays purely message-window race detection.
            if (!canRecover(sessionId) || loaded.isEmpty()) {
                null
            } else {
                mergeLoadedMessages(sessionId, loaded)
                sessionLoadedLimits[sessionId] = maxOf(sessionLoadedLimits[sessionId] ?: 0, limit)
                sessionRevisions[sessionId] = (sessionRevisions[sessionId] ?: 0L) + 1
                sessionLeaseGenerations[sessionId] ?: 0L
            }
        } ?: return
        AppLog.w(TAG, "Post-reconnect recovery raced SSE repeatedly; merged fetched state for $sessionId")
        scope.launch {
            try {
                reconcileLoadedPendingState(sessionId, loaded, leaseGeneration)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                AppLog.w(TAG, "Post-reconnect pending reconciliation failed for $sessionId: ${e.javaClass.simpleName}")
            }
        }
    }

    private suspend fun recoverAttempt(
        sessionId: String,
        workspaceClient: WorkspaceClient,
        mapper: MessageMapper,
    ): RecoveryResult {
        val attempt = synchronized(messageStateLock) { recoveryAttemptFor(sessionId) }
            ?: return RecoveryResult(CommitOutcome.Inactive, emptyList(), DEFAULT_MESSAGE_HISTORY_LIMIT)

        val loaded = workspaceClient.getMessages(sessionId, attempt.limit)
            .map { dto -> mapper.mapWrapperToDomain(dto) }
        val outcome = commitReplacementIfUnchanged(sessionId, attempt, loaded)
        if (outcome == CommitOutcome.Committed) {
            // The lease generation captured with the attempt is the ownership token: pending
            // reconciliation may only touch UI state while that lease lifetime is still current.
            reconcileLoadedPendingState(sessionId, loaded, attempt.leaseGeneration)
        }
        return RecoveryResult(outcome, loaded, attempt.limit)
    }

    private data class RecoveryResult(
        val outcome: CommitOutcome,
        val loaded: List<MessageWithParts>,
        val limit: Int,
    )

    private fun recoveryAttemptFor(sessionId: String): RecoveryAttempt? {
        if (!canRecover(sessionId)) return null
        return RecoveryAttempt(
            revision = sessionRevisions[sessionId] ?: 0L,
            limit = sessionLoadedLimits[sessionId] ?: DEFAULT_MESSAGE_HISTORY_LIMIT,
            leaseGeneration = sessionLeaseGenerations[sessionId] ?: 0L,
        )
    }

    private fun canRecover(sessionId: String): Boolean =
        sessionId !in recoveryInvalidatedSessions && (sessionConsumerCounts[sessionId] ?: 0) > 0

    /**
     * True while [leaseGeneration] still owns the session's recovery: the session is actively
     * leased, not invalidated, and its lease/state-lifetime generation is exactly the one the
     * recovery captured. Acquire, final release, deletion, and release+reacquire each bump the
     * generation, so stale recovery work can never pass this check again — while ordinary SSE
     * message/part mutations (which bump only [sessionRevisions]) never invalidate pending
     * recovery for the still-current lease.
     */
    private fun ownsRecoveryLease(sessionId: String, leaseGeneration: Long): Boolean =
        synchronized(messageStateLock) {
            canRecover(sessionId) && (sessionLeaseGenerations[sessionId] ?: 0L) == leaseGeneration
        }

    /**
     * Runs [block] atomically under [messageStateLock] only while [leaseGeneration] still owns the
     * session's recovery, so a lease release cannot interleave between the ownership check and a
     * recovery-bound UI-state read/create/write. A null token means the caller is not
     * recovery-bound and runs unguarded, preserving pre-existing behavior. Never call with a
     * suspending or slow [block]; fetches must happen outside the lock.
     */
    private fun <T> withRecoveryOwnership(sessionId: String, leaseGeneration: Long?, block: () -> T): T? {
        if (leaseGeneration == null) return block()
        return synchronized(messageStateLock) {
            if (ownsRecoveryLease(sessionId, leaseGeneration)) block() else null
        }
    }

    private fun commitReplacement(sessionId: String, attempt: RecoveryAttempt, loaded: List<MessageWithParts>) {
        replaceMessagesAuthoritatively(sessionId, loaded)
        // A fetched window only becomes the new "largest loaded" bound after it is actually
        // committed, so an aborted race never inflates the recovery window.
        sessionLoadedLimits[sessionId] = maxOf(sessionLoadedLimits[sessionId] ?: 0, attempt.limit)
        // Bump the revision so any concurrent recovery job that captured the same prior revision
        // sees the race and refuses to overwrite this freshly committed window.
        sessionRevisions[sessionId] = attempt.revision + 1
    }

    private fun commitReplacementIfUnchanged(
        sessionId: String,
        attempt: RecoveryAttempt,
        loaded: List<MessageWithParts>,
    ): CommitOutcome = synchronized(messageStateLock) {
        when {
            !canRecover(sessionId) -> CommitOutcome.Inactive
            (sessionRevisions[sessionId] ?: 0L) != attempt.revision -> CommitOutcome.Raced
            else -> {
                commitReplacement(sessionId, attempt, loaded)
                CommitOutcome.Committed
            }
        }
    }

    /**
     * Replace the session's message state authoritatively with [loaded]. Unlike the merge-based
     * pagination path, this repairs missed deletions and stale part content: the REST window is the
     * source of truth once the caller has confirmed the revision is unchanged and the session is
     * still active. Mutations that were concurrently applied for the same message/part are not
     * distinguishable here because a committed replacement implies no revision changed.
     */
    private fun replaceMessagesAuthoritatively(sessionId: String, loaded: List<MessageWithParts>) {
        messageState(sessionId).value = loaded
    }

    private suspend fun reconcileLoadedPendingState(
        sessionId: String,
        loaded: List<MessageWithParts>,
        leaseGeneration: Long,
    ) {
        val hasRunningQuestion = loaded.any { mwp ->
            mwp.parts.any { it is Part.Tool && it.isQuestionTool() && it.state is ToolState.Running }
        }
        if (hasRunningQuestion) {
            reconcilePendingQuestions(sessionId, leaseGeneration)
        }
        reconcilePendingPermissions(sessionId, leaseGeneration)
    }

    private data class RecoveryAttempt(
        val revision: Long,
        val limit: Int,
        val leaseGeneration: Long,
    )

    override fun messages(sessionId: SessionId): StateFlow<List<MessageWithParts>> = messageState(
        sessionId.value
    ).asStateFlow()

    override fun sessionUiState(sessionId: SessionId): StateFlow<SessionUiState> = sessionUiStateFor(
        sessionId.value
    ).asStateFlow()

    override fun acquireSession(sessionId: SessionId): AutoCloseable {
        synchronized(messageStateLock) {
            // A fresh lease means state starts empty and may be recovered again on reconnect.
            recoveryInvalidatedSessions.remove(sessionId.value)
            // Bump the revision so a fetch still in flight from a previous lease can never commit
            // into this new lease.
            sessionRevisions[sessionId.value] = (sessionRevisions[sessionId.value] ?: 0L) + 1
            // A new lease lifetime begins: pending reconciliation captured under a previous lease
            // loses ownership and can never write into this one.
            sessionLeaseGenerations[sessionId.value] = (sessionLeaseGenerations[sessionId.value] ?: 0L) + 1
            sessionConsumerCounts[sessionId.value] = sessionConsumerCounts.getOrDefault(sessionId.value, 0) + 1
        }
        val released = AtomicBoolean(false)
        return AutoCloseable {
            if (released.compareAndSet(false, true)) releaseSession(sessionId.value)
        }
    }

    private fun releaseSession(sessionId: String) {
        synchronized(messageStateLock) {
            val remaining = (sessionConsumerCounts[sessionId] ?: return) - 1
            if (remaining > 0) {
                sessionConsumerCounts[sessionId] = remaining
                return
            } else {
                sessionConsumerCounts.remove(sessionId)
            }

            messageStates.remove(sessionId)?.value = emptyList()
            // Bump rather than delete the revision so a fetch that captured the pre-release revision
            // cannot commit after the lease is gone.
            sessionRevisions[sessionId] = (sessionRevisions[sessionId] ?: 0L) + 1
            // The state lifetime ends here: in-flight recovery-bound pending work loses ownership.
            sessionLeaseGenerations[sessionId] = (sessionLeaseGenerations[sessionId] ?: 0L) + 1
            sessionLoadedLimits.remove(sessionId)
            recoveryInvalidatedSessions.remove(sessionId)
            synchronized(sessionUiStates) {
                sessionUiStates.remove(sessionId)?.value = SessionUiState()
            }
        }
    }

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

    override suspend fun loadMessages(sessionId: SessionId, limit: Int): Int {
        require(limit > 0) { "Message history limit must be positive" }
        val workspaceClient = client as? WorkspaceClient
            ?: error("Message loading requires WorkspaceClient")
        val mapper = messageMapper ?: error("Message loading requires MessageMapper")
        val messages = workspaceClient.getMessages(sessionId.value, limit).map { dto -> mapper.mapWrapperToDomain(dto) }
        synchronized(messageStateLock) {
            mergeLoadedMessages(sessionId.value, messages)
            // Record the requested bound, not the count returned: the server may supply fewer
            // messages than requested, and recovery should re-fetch the same bounded window that
            // was actually loaded (largest successfully loaded history limit).
            sessionLoadedLimits[sessionId.value] = maxOf(sessionLoadedLimits[sessionId.value] ?: 0, limit)
            sessionRevisions[sessionId.value] = (sessionRevisions[sessionId.value] ?: 0L) + 1
        }
        // A question tool part loaded via REST (state=running) means a question is
        // pending that the live question.asked SSE event was missed for. Reconcile so
        // the interactive card renders. (upsertPart only fires for live SSE updates,
        // not REST history load, so this is the deterministic open-a-session trigger.)
        val hasRunningQuestion = messages.any { mwp ->
            mwp.parts.any { it is Part.Tool && it.isQuestionTool() && it.state is ToolState.Running }
        }
        if (hasRunningQuestion) {
            reconcilePendingQuestions(sessionId.value)
        }
        reconcilePendingPermissions(sessionId.value)
        return messages.size
    }

    override fun sendMessageAsync(sessionId: SessionId, request: SendMessageRequest): Deferred<Result<Unit>> = scope.async {
        runMutationCatching { client.sendMessageAsync(sessionId.value, request) }
    }

    override fun abortSession(sessionId: SessionId): Deferred<Result<Boolean>> = scope.async {
        runMutationCatching { client.abortSession(sessionId.value) }
    }

    private fun applySessionError(sessionId: String, error: MessageError) {
        updateSession(sessionId) { state ->
            val alreadyApplied = state.status is SessionStatus.Idle && state.error == error
            state.copy(
                status = SessionStatus.Idle,
                error = error,
                responseCompletedToken = if (alreadyApplied) {
                    state.responseCompletedToken
                } else {
                    state.responseCompletedToken + 1
                },
            )
        }
        clearStreamingFlags(SessionId(sessionId))
    }

    override fun clearStreamingFlags(sessionId: SessionId) {
        updateMessageState(sessionId.value) { messages ->
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
        projectRefreshJob?.cancel()
        messageRecoveryJob?.cancel(CancellationException("Session repository closed"))
        messageRecoveryJob = null
        invalidate()
        job.cancel("SessionRepository closed")
        synchronized(messageStateLock) {
            messageStates.clear()
            sessionRevisions.clear()
            sessionLoadedLimits.clear()
            sessionConsumerCounts.clear()
            recoveryInvalidatedSessions.clear()
            sessionLeaseGenerations.clear()
        }
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

    suspend fun forkSession(id: SessionId): WorkspaceSession {
        val dto = client.forkSession(id.value, ForkSessionRequest(messageID = null))
        val session = workspaceSession(SessionMapper.mapToDomain(dto))
        upsert(session)
        return session
    }

    suspend fun initSession(id: SessionId, request: InitSessionRequest): Boolean =
        client.initSession(id.value, request)

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

    @Suppress(
        "CyclomaticComplexMethod",
        "LongMethod", // Atomic generation and buffer ownership branches must remain co-located.
    )
    private suspend fun hydrate(seedProjects: List<ProjectDto>): CachedSnapshot {
        val generation = synchronized(hydrationTransitionLock) {
            val nextGeneration = ++hydrationGeneration
            _state.value = RepoState.Hydrating(snapshot = state.value.snapshot, bufferedEvents = hydrateBuffer.size)
            nextGeneration
        }
        val workspaceKey = client.workspace.key.toString()

        try {
            val projects = seedProjects.sortedByDescending { it.worktree }
            val semaphore = Semaphore(MAX_CONCURRENT)
            val totalSteps = (projects.size + 1) * 2
            val completedSteps = AtomicInteger(0)

            fun updateHydrationState(currentStep: String? = null, completed: Int = completedSteps.get()) {
                synchronized(hydrationTransitionLock) {
                    if (generation != hydrationGeneration) return
                    val current = _state.value as? RepoState.Hydrating
                    _state.value = RepoState.Hydrating(
                        snapshot = state.value.snapshot,
                        bufferedEvents = current?.bufferedEvents ?: hydrateBuffer.size,
                        completedSteps = completed,
                        totalSteps = totalSteps,
                        currentStep = currentStep,
                    )
                }
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
                                client.listSessions(directory = null, roots = true, limit = SESSION_HISTORY_LIMIT)
                            }
                        }
                    }
                    val projectDeferreds = projects.map { project ->
                        async {
                            trackedStep("Loading sessions for ${project.worktree.substringAfterLast('/')}") {
                                semaphore.withPermit {
                                    client.listSessions(
                                        directory = project.worktree,
                                        roots = true,
                                        limit = SESSION_HISTORY_LIMIT,
                                        scope = "project",
                                    )
                                }
                            } to project
                        }
                    }

                    val globalSessions = runCatching { globalDeferred.await() }
                        .getOrElse { error ->
                            AppLog.e(TAG, "Failed to load global sessions: ${error.javaClass.simpleName}")
                            emptyList()
                        }
                        .filterNot { dto -> OfishSessionNames.isOfishTitle(dto.title) }
                        .map { dto -> workspaceSession(SessionMapper.mapToDomain(dto)) }

                    val projectSessions = projectDeferreds.awaitAll().flatMap { (result, project) ->
                        runCatching { result }
                            .getOrElse { error ->
                                AppLog.e(TAG, "Failed to load project sessions: ${error.javaClass.simpleName}")
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
                            AppLog.e(TAG, "Failed to load session statuses: ${error.javaClass.simpleName}")
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
            var liveSnapshot = hydrated
            var ownershipSeeded = false
            while (true) {
                val drainedEvents = synchronized(hydrationTransitionLock) {
                    if (generation != hydrationGeneration) {
                        return CachedSnapshot(
                            snapshot = liveSnapshot,
                            fetchedAtMs = nowMs(),
                            workspaceKey = workspaceKey,
                        )
                    }
                    if (!ownershipSeeded) {
                        replaceSessionOwnership(hydrated.sessions.values)
                        ownershipSeeded = true
                    }
                    val events = hydrateBuffer.drain()
                    if (events.isEmpty()) {
                        val cached = CachedSnapshot(
                            snapshot = liveSnapshot,
                            fetchedAtMs = nowMs(),
                            workspaceKey = workspaceKey,
                        )
                        lastSuccess = cached
                        _state.value = RepoState.Live(liveSnapshot)
                        return cached
                    }
                    applySessionOwnershipEvents(events)
                    events
                }
                liveSnapshot = drainedEvents.fold(liveSnapshot) { snapshot, event ->
                    reducer.reduce(snapshot, event)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            synchronized(hydrationTransitionLock) {
                if (generation == hydrationGeneration) {
                    _state.value = RepoState.Stale(state.value.snapshot, reason = e.message)
                }
            }
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
        workspace = Workspace(
            server = client.workspace.server,
            directory = session.directory.takeIf { it.isNotBlank() },
        ),
        session = session,
    )

    private fun upsert(session: WorkspaceSession) {
        val snapshot = state.value.snapshot
        _state.value = RepoState.Live(snapshot.copy(sessions = snapshot.sessions + (session.id.value to session)))
    }

    private fun messageState(sessionId: String): MutableStateFlow<List<MessageWithParts>> =
        synchronized(messageStateLock) {
            messageStates.getOrPut(sessionId) { MutableStateFlow(emptyList()) }
        }

    /**
     * Apply [transform] to the session's message state and bump its revision so any in-flight
     * reconnect recovery that captured the prior revision will detect the race and refuse to
     * overwrite this newer mutation.
     */
    private fun updateMessageState(
        sessionId: String,
        transform: (List<MessageWithParts>) -> List<MessageWithParts>,
    ) {
        synchronized(messageStateLock) {
            messageState(sessionId).update(transform)
            sessionRevisions[sessionId] = (sessionRevisions[sessionId] ?: 0L) + 1
        }
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

    private fun replaceSessionOwnership(sessions: Collection<WorkspaceSession>) {
        synchronized(childToParentSessionIds) {
            childToParentSessionIds.clear()
            sessions.forEach { workspaceSession ->
                workspaceSession.session.parentID?.let { parentId ->
                    childToParentSessionIds[workspaceSession.id.value] = parentId
                }
            }
        }
    }

    private fun updateSessionOwnership(session: Session) {
        synchronized(childToParentSessionIds) {
            val parentId = session.parentID
            if (parentId == null) {
                childToParentSessionIds.remove(session.id)
            } else {
                childToParentSessionIds[session.id] = parentId
            }
        }
    }

    private fun removeSessionOwnership(sessionId: String) {
        synchronized(childToParentSessionIds) {
            childToParentSessionIds.entries.removeAll { (childId, parentId) ->
                childId == sessionId || parentId == sessionId
            }
        }
    }

    private fun applySessionOwnershipEvents(events: List<OpenCodeEvent>) {
        events.forEach { event ->
            when (event) {
                is OpenCodeEvent.SessionCreated -> updateSessionOwnership(event.session)
                is OpenCodeEvent.SessionUpdated -> updateSessionOwnership(event.session)
                is OpenCodeEvent.SessionDeleted -> removeSessionOwnership(event.session.id)
                else -> Unit
            }
        }
    }

    private suspend fun reconcileObservedPendingPermissions() {
        val sessionIds = synchronized(sessionUiStates) { sessionUiStates.keys.toList() }
        sessionIds.forEach { sessionId -> reconcilePendingPermissions(sessionId) }
    }

    /**
     * A non-null [leaseGeneration] marks a recovery-bound call; see [reconcilePendingQuestions].
     * The initial snapshot read is also guarded because [sessionUiStateFor] creates state on
     * demand, and a recovery that lost ownership must not recreate an evicted session's state.
     */
    private suspend fun reconcilePendingPermissions(sessionId: String, leaseGeneration: Long? = null) {
        val pendingBeforeReconciliation = withRecoveryOwnership(sessionId, leaseGeneration) {
            sessionUiStateFor(sessionId).value.pendingPermissionsByCallId
        } ?: return
        val legacyPermissions = runCatching {
            client.listPermissions()
                .filter { permission -> permission.sessionID == sessionId }
                .map(PermissionMapper::mapToDomain)
        }.onFailure { if (it is CancellationException) throw it }.getOrNull()
        val permissions = if (!legacyPermissions.isNullOrEmpty()) {
            legacyPermissions
        } else {
            runCatching { client.listSessionPermissionsV2(sessionId).map(PermissionMapper::mapV2ToDomain) }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull()
                ?: legacyPermissions
                ?: return
        }
        withRecoveryOwnership(sessionId, leaseGeneration) {
            updateSession(sessionId) { state ->
                val recovered = permissions.associateBy { permission -> permission.pendingPermissionKey() }
                val concurrentlyRemovedKeys = pendingBeforeReconciliation.keys - state.pendingPermissionsByCallId.keys
                val concurrentlyArrived = state.pendingPermissionsByCallId.filter { (key, permission) ->
                    pendingBeforeReconciliation[key] != permission
                }
                state.copy(
                    pendingPermissionsByCallId = (recovered - concurrentlyRemovedKeys) + concurrentlyArrived
                )
            }
        }
    }

    private fun mergeLoadedMessages(
        sessionId: String,
        loaded: List<MessageWithParts>,
    ) {
        val state = messageState(sessionId)
        state.update { current ->
            val currentById = current.associateBy { it.message.id }
            loaded.map { loadedMessage ->
                val currentMessage = currentById[loadedMessage.message.id]
                if (currentMessage == null) {
                    loadedMessage
                } else {
                    // Existing state may contain a newer SSE update than the bounded REST
                    // snapshot. Keep it authoritative while merging older history around it.
                    MessageWithParts(
                        currentMessage.message,
                        mergeParts(loadedMessage.parts, currentMessage.parts),
                    )
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
        updateMessageState(message.sessionID) { messages ->
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
                                reconcilePendingQuestions(part.sessionID)
                            } catch (e: Exception) {
                                AppLog.w(
                                    TAG,
                                    "Question reconciliation failed: ${e.javaClass.simpleName}",
                                )
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

        updateMessageState(part.sessionID) { messages ->
            val existingMessage = messages.firstOrNull { it.message.id == part.messageID }
                ?: createPlaceholderMessage(part.sessionID, part.messageID)
            val partIndex = existingMessage.parts.indexOfFirst { it.id == part.id }
            val updatedParts = if (partIndex >= 0) {
                existingMessage.parts.toMutableList().apply {
                    this[partIndex] = mergePartSnapshot(this[partIndex], part, delta)
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
        updateMessageState(sessionId) { messages ->
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

    private fun findSessionIdForPart(messageId: String, partId: String): String? = synchronized(messageStateLock) {
        messageStates.entries.firstOrNull { (_, flow) ->
            flow.value.any { message -> message.message.id == messageId && message.parts.any { it.id == partId } }
        }?.key
    }

    private fun appendDeltaToPart(part: Part, field: String, delta: String): Part = when (part) {
        is Part.Text -> if (field == "text") part.copy(text = part.text + delta, isStreaming = true) else part
        is Part.Reasoning -> if (field == "text") part.copy(text = part.text + delta) else part
        else -> part
    }

    private fun removeMessage(sessionId: String, messageId: String) {
        updateMessageState(sessionId) { messages -> messages.filterNot { it.message.id == messageId } }
    }

    private fun removePart(sessionId: String, messageId: String, partId: String) {
        updateMessageState(sessionId) { messages ->
            messages.map { msgWithParts ->
                if (msgWithParts.message.id == messageId) {
                    msgWithParts.copy(parts = msgWithParts.parts.filterNot { it.id == partId })
                } else {
                    msgWithParts
                }
            }
        }
    }

    private fun mergePartSnapshot(existing: Part, incoming: Part, delta: String?): Part =
        when {
            incoming is Part.Text && existing is Part.Text -> {
                incoming.copy(
                    isStreaming = incoming.isStreaming || delta != null,
                    time = incoming.time ?: existing.time,
                    metadata = incoming.metadata ?: existing.metadata,
                )
            }
            incoming is Part.Reasoning && existing is Part.Reasoning -> {
                incoming.copy(
                    time = incoming.time ?: existing.time,
                    metadata = incoming.metadata ?: existing.metadata,
                )
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

    private fun Permission.pendingPermissionKey(): String =
        callID?.takeIf { it.isNotBlank() } ?: "permission:$id"

    private companion object {
        const val FRESHNESS_MS = 30_000L
        const val PROJECT_EVENT_REFRESH_DEBOUNCE_MS = 150L
        const val MAX_CONCURRENT = 10
        const val SEARCH_LIMIT = 100
        const val SESSION_HISTORY_LIMIT = Int.MAX_VALUE
        const val TAG = "SessionRepository"
        const val RESOLVED_QUESTION_TTL_MS = 30_000L
        const val DEFAULT_MESSAGE_HISTORY_LIMIT = 100
        const val MESSAGE_RECOVERY_MAX_ATTEMPTS = 3
        const val MESSAGE_RECOVERY_RETRY_DELAY_MS = 200L
    }
}
