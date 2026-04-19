package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.data.remote.dto.ProjectDto
import dev.blazelight.p4oc.data.remote.mapper.SessionMapper
import dev.blazelight.p4oc.domain.model.SessionStatus
import dev.blazelight.p4oc.ui.screens.sessions.ProjectInfo
import dev.blazelight.p4oc.ui.screens.sessions.SessionWithProject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class SessionDataCache(
    private val connectionManager: ConnectionManager,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    data class CachedSessions(
        val sessions: List<SessionWithProject>,
        val projects: List<ProjectInfo>,
        val statuses: Map<String, SessionStatus>,
        val fetchedAtMs: Long,
        val serverBaseUrl: String,
    )

    companion object {
        private const val FRESHNESS_MS = 30_000L
        private const val MAX_CONCURRENT = 10
        private const val TAG = "SessionDataCache"
    }

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    @Volatile
    private var inFlight: Deferred<Result<CachedSessions>>? = null

    @Volatile
    private var lastSuccess: CachedSessions? = null

    init {
        scope.launch {
            connectionManager.connection.collectLatest { connection ->
                if (connection == null) {
                    invalidate()
                }
            }
        }
    }

    fun prewarm(seedProjects: List<ProjectDto>): Deferred<Result<CachedSessions>> {
        val serverBaseUrl = connectionManager.currentBaseUrl
            ?: return CompletableDeferred(Result.failure(IllegalStateException("Not connected to any server")))

        peek()?.let { return CompletableDeferred(Result.success(it)) }

        synchronized(this) {
            inFlight?.let { return it }

            val created = scope.async {
                fetchSessions(serverBaseUrl, seedProjects)
            }
            inFlight = created
            return created
        }
    }

    suspend fun awaitOrFetch(): Result<CachedSessions> {
        peek()?.let { return Result.success(it) }
        inFlight?.let { existing ->
            return try {
                existing.await()
            } catch (ce: CancellationException) {
                if (!coroutineContext.isActive) throw ce
                Result.failure(ce)
            }
        }

        val api = connectionManager.getApi()
            ?: return Result.failure(IllegalStateException("Not connected to any server"))
        val projects = runCatching { api.listProjects() }
            .getOrElse { return Result.failure(it) }

        return prewarm(projects).await()
    }

    fun peek(): CachedSessions? {
        val cached = lastSuccess ?: return null
        val currentBaseUrl = connectionManager.currentBaseUrl ?: return null
        val now = nowMs()

        return if (cached.serverBaseUrl == currentBaseUrl && now - cached.fetchedAtMs <= FRESHNESS_MS) {
            cached
        } else {
            null
        }
    }

    fun invalidate() {
        val toCancel = synchronized(this) {
            val current = inFlight
            inFlight = null
            lastSuccess = null
            current
        }
        toCancel?.cancel(CancellationException("Session data cache invalidated"))
    }

    private suspend fun fetchSessions(
        serverBaseUrl: String,
        seedProjects: List<ProjectDto>
    ): Result<CachedSessions> {
        val api = connectionManager.getApi()
            ?: return Result.failure(IllegalStateException("Not connected to any server"))

        return try {
            val projects = seedProjects.map(::toProjectInfo).sortedByDescending { it.worktree }
            val semaphore = Semaphore(MAX_CONCURRENT)

            val (sessions, statuses) = kotlinx.coroutines.coroutineScope {
                val sessionsDeferred = async {
                    val globalDeferred = async {
                        semaphore.withPermit {
                            safeApiCall { api.listSessions(directory = null, roots = true, limit = 100) }
                        }
                    }
                    val projectDeferreds = projects.map { project ->
                        async {
                            semaphore.withPermit {
                                safeApiCall {
                                    api.listSessions(directory = project.worktree, roots = true, limit = 100)
                                }
                            } to project
                        }
                    }

                    val globalSessions = when (val result = globalDeferred.await()) {
                        is ApiResult.Success -> result.data.map { dto ->
                            SessionWithProject(session = SessionMapper.mapToDomain(dto))
                        }
                        is ApiResult.Error -> {
                            AppLog.e(TAG, "Failed to load global sessions: ${result.message}")
                            emptyList()
                        }
                    }

                    val projectSessions = projectDeferreds.awaitAll().flatMap { (result, project) ->
                        when (result) {
                            is ApiResult.Success -> result.data.map { dto ->
                                SessionWithProject(
                                    session = SessionMapper.mapToDomain(dto),
                                    projectId = project.id,
                                    projectName = project.name,
                                )
                            }
                            is ApiResult.Error -> {
                                AppLog.e(TAG, "Failed to load sessions for ${project.name}: ${result.message}")
                                emptyList()
                            }
                        }
                    }

                    val projectSessionIds = projectSessions.map { it.session.id }.toSet()
                    val uniqueGlobalSessions = globalSessions.filter { it.session.id !in projectSessionIds }
                    (uniqueGlobalSessions + projectSessions).sortedByDescending { it.session.updatedAt }
                }

                val statusesDeferred = async {
                    val directories = listOf<String?>(null) + projects.map { it.worktree }
                    directories.map { directory ->
                        async {
                            semaphore.withPermit {
                                directory to safeApiCall { api.getSessionStatuses(directory) }
                            }
                        }
                    }.awaitAll().fold(mutableMapOf<String, SessionStatus>()) { acc, (_, result) ->
                        when (result) {
                            is ApiResult.Success -> result.data.forEach { (sessionId, dto) ->
                                acc[sessionId] = SessionMapper.mapStatusToDomain(dto)
                            }
                            is ApiResult.Error -> {
                                AppLog.e(TAG, "Failed to load session statuses: ${result.message}")
                            }
                        }
                        acc
                    }
                }

                sessionsDeferred.await() to statusesDeferred.await()
            }

            if (connectionManager.currentBaseUrl != serverBaseUrl) {
                val error = IllegalStateException("Server switched during session prefetch")
                AppLog.w(TAG, "Discarding prefetched sessions for stale server $serverBaseUrl")
                Result.failure(error)
            } else {
                val cached = CachedSessions(
                    sessions = sessions,
                    projects = projects,
                    statuses = statuses,
                    fetchedAtMs = nowMs(),
                    serverBaseUrl = serverBaseUrl,
                )
                lastSuccess = cached
                Result.success(cached)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "Session prefetch failed: ${e.message}", e)
            Result.failure(e)
        } finally {
            synchronized(this) {
                if (inFlight?.isCompleted == true || inFlight?.isCancelled == true) {
                    inFlight = null
                }
            }
        }
    }

    private fun toProjectInfo(dto: ProjectDto): ProjectInfo = ProjectInfo(
        id = dto.id,
        worktree = dto.worktree,
        name = dto.worktree.substringAfterLast("/"),
    )
}
