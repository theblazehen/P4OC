# Pocket Code - Complete Implementation Specification

> Native Android client for OpenCode AI agent with Termux integration

## Project Overview

**App Name:** Pocket Code  
**Package:** `com.pocketcode`  
**Min SDK:** 26 (Android 8.0)  
**Target SDK:** 35  
**Language:** Kotlin 2.0  
**UI:** Jetpack Compose + Material 3  
**Architecture:** MVVM + Clean Architecture + Repository Pattern

---

## Core Features

### 1. Server Connection Modes
- **Local (Termux):** Run OpenCode server on-device via Termux
- **Remote:** Connect to OpenCode server on LAN/internet

### 2. Chat Interface
- Session management (list, create, fork, delete)
- Message streaming with SSE
- Markdown rendering
- Code syntax highlighting
- Tool execution cards (pending/running/completed/error)
- Permission approval dialogs
- Branching conversations

### 3. Terminal Integration
- Execute commands via Termux RUN_COMMAND intent
- View command output
- Start/stop OpenCode server

### 4. File Operations
- File explorer tree view
- File content viewing with syntax highlighting
- Diff viewer (side-by-side)
- Search (text, files, symbols)

---

## Technology Stack

### Core Dependencies

```kotlin
// build.gradle.kts (app module)
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.pocketcode"
    compileSdk = 35
    
    defaultConfig {
        applicationId = "com.pocketcode"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.0")
    
    // ViewModel + Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    
    // Networking - OkHttp + Retrofit
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    
    // SSE (Server-Sent Events)
    implementation("com.launchdarkly:okhttp-eventsource:4.1.1")
    
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
    
    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    
    // DataStore Preferences
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    
    // Dependency Injection - Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    
    // Markdown Rendering
    implementation("com.github.jeziellago:compose-markdown:0.5.4")
    
    // Syntax Highlighting
    implementation("io.noties:prism4j:2.0.0")
    ksp("io.noties:prism4j-bundler:2.0.0")
    
    // Image Loading
    implementation("io.coil-kt:coil-compose:2.7.0")
    
    // Splash Screen
    implementation("androidx.core:core-splashscreen:1.0.1")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("io.mockk:mockk:1.13.11")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

---

## Project Structure

```
app/src/main/java/com/pocketcode/
├── PocketCodeApp.kt                    # Application class with Hilt
├── MainActivity.kt                      # Single activity entry point
│
├── di/                                  # Dependency Injection
│   ├── AppModule.kt                     # Application-scoped dependencies
│   ├── NetworkModule.kt                 # Retrofit, OkHttp, SSE
│   ├── DatabaseModule.kt                # Room database
│   └── RepositoryModule.kt              # Repository bindings
│
├── core/                                # Core infrastructure
│   ├── network/
│   │   ├── OpenCodeApi.kt               # Retrofit API interface
│   │   ├── OpenCodeEventSource.kt       # SSE client for real-time events
│   │   ├── ApiResult.kt                 # Result wrapper (Success/Error)
│   │   ├── NetworkMonitor.kt            # Connection state observer
│   │   └── interceptors/
│   │       ├── AuthInterceptor.kt       # Basic auth for protected servers
│   │       └── ServerUrlInterceptor.kt  # Dynamic base URL
│   │
│   ├── database/
│   │   ├── PocketCodeDatabase.kt        # Room database definition
│   │   ├── Converters.kt                # Type converters
│   │   ├── dao/
│   │   │   ├── SessionDao.kt
│   │   │   ├── MessageDao.kt
│   │   │   └── ServerConfigDao.kt
│   │   └── entity/
│   │       ├── SessionEntity.kt
│   │       ├── MessageEntity.kt
│   │       ├── PartEntity.kt
│   │       └── ServerConfigEntity.kt
│   │
│   ├── datastore/
│   │   └── SettingsDataStore.kt         # User preferences
│   │
│   ├── termux/                          # Termux integration
│   │   ├── TermuxBridge.kt              # IPC with Termux app
│   │   ├── TermuxResultService.kt       # Receive command results
│   │   └── TermuxConstants.kt           # Intent action/extra constants
│   │
│   └── util/
│       ├── Extensions.kt                # Kotlin extensions
│       ├── DateTimeUtil.kt              # Date/time formatting
│       └── JsonUtil.kt                  # JSON helpers
│
├── domain/                              # Domain layer
│   ├── model/                           # Domain models
│   │   ├── Session.kt
│   │   ├── Message.kt
│   │   ├── Part.kt
│   │   ├── ToolState.kt
│   │   ├── Permission.kt
│   │   ├── Event.kt
│   │   ├── FileNode.kt
│   │   ├── SearchResult.kt
│   │   └── ServerConfig.kt
│   │
│   └── repository/                      # Repository interfaces
│       ├── SessionRepository.kt
│       ├── MessageRepository.kt
│       ├── FileRepository.kt
│       ├── EventRepository.kt
│       └── SettingsRepository.kt
│
├── data/                                # Data layer
│   ├── remote/
│   │   ├── dto/                         # API DTOs
│   │   │   ├── SessionDto.kt
│   │   │   ├── MessageDto.kt
│   │   │   ├── PartDto.kt
│   │   │   ├── EventDto.kt
│   │   │   ├── FileDto.kt
│   │   │   └── RequestDto.kt
│   │   └── mapper/
│   │       ├── SessionMapper.kt
│   │       ├── MessageMapper.kt
│   │       └── PartMapper.kt
│   │
│   └── repository/                      # Repository implementations
│       ├── SessionRepositoryImpl.kt
│       ├── MessageRepositoryImpl.kt
│       ├── FileRepositoryImpl.kt
│       ├── EventRepositoryImpl.kt
│       └── SettingsRepositoryImpl.kt
│
└── ui/                                  # UI layer
    ├── theme/
    │   ├── Theme.kt                     # Material 3 theme
    │   ├── Color.kt                     # Color definitions
    │   ├── Typography.kt                # Text styles
    │   └── Shapes.kt                    # Shape definitions
    │
    ├── navigation/
    │   ├── NavGraph.kt                  # Navigation graph
    │   ├── Screen.kt                    # Screen route definitions
    │   └── NavAnimations.kt             # Transition animations
    │
    ├── components/                      # Reusable composables
    │   ├── chat/
    │   │   ├── ChatBubble.kt            # Message bubble
    │   │   ├── ChatInput.kt             # Message input bar
    │   │   ├── StreamingText.kt         # Animated streaming text
    │   │   ├── ToolCard.kt              # Tool execution card
    │   │   ├── PermissionCard.kt        # Permission request card
    │   │   └── MessageActions.kt        # Branch, copy, retry actions
    │   │
    │   ├── markdown/
    │   │   ├── MarkdownRenderer.kt      # Markdown to Compose
    │   │   └── CodeBlock.kt             # Syntax highlighted code
    │   │
    │   ├── terminal/
    │   │   ├── TerminalView.kt          # Terminal output display
    │   │   └── TerminalInput.kt         # Command input
    │   │
    │   ├── files/
    │   │   ├── FileTree.kt              # File explorer tree
    │   │   ├── FileItem.kt              # Single file/folder item
    │   │   └── FilePreview.kt           # File content preview
    │   │
    │   ├── diff/
    │   │   ├── DiffViewer.kt            # Side-by-side diff
    │   │   └── DiffLine.kt              # Single diff line
    │   │
    │   └── common/
    │       ├── LoadingIndicator.kt
    │       ├── ErrorView.kt
    │       ├── EmptyState.kt
    │       ├── ConnectionBanner.kt      # Connection status banner
    │       └── ConfirmDialog.kt
    │
    └── screens/
        ├── setup/                       # First-time setup
        │   ├── SetupScreen.kt
        │   ├── TermuxSetupScreen.kt
        │   └── SetupViewModel.kt
        │
        ├── server/                      # Server connection
        │   ├── ServerScreen.kt
        │   └── ServerViewModel.kt
        │
        ├── sessions/                    # Session list
        │   ├── SessionListScreen.kt
        │   └── SessionListViewModel.kt
        │
        ├── chat/                        # Chat conversation
        │   ├── ChatScreen.kt
        │   └── ChatViewModel.kt
        │
        ├── terminal/                    # Terminal/shell
        │   ├── TerminalScreen.kt
        │   └── TerminalViewModel.kt
        │
        ├── files/                       # File explorer
        │   ├── FileExplorerScreen.kt
        │   ├── FileViewerScreen.kt
        │   └── FilesViewModel.kt
        │
        └── settings/                    # App settings
            ├── SettingsScreen.kt
            └── SettingsViewModel.kt
```

---

## Data Models

### Domain Models

```kotlin
// domain/model/Session.kt
data class Session(
    val id: String,
    val slug: String,
    val projectID: String,
    val directory: String,
    val parentID: String? = null,
    val title: String,
    val version: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val archivedAt: Instant? = null,
    val summary: SessionSummary? = null,
    val shareUrl: String? = null
)

data class SessionSummary(
    val additions: Int,
    val deletions: Int,
    val files: Int
)

// domain/model/Message.kt
sealed class Message {
    abstract val id: String
    abstract val sessionID: String
    abstract val createdAt: Instant
    
    data class User(
        override val id: String,
        override val sessionID: String,
        override val createdAt: Instant,
        val agent: String,
        val model: ModelRef
    ) : Message()
    
    data class Assistant(
        override val id: String,
        override val sessionID: String,
        override val createdAt: Instant,
        val completedAt: Instant? = null,
        val parentID: String,
        val providerID: String,
        val modelID: String,
        val agent: String,
        val cost: Double,
        val tokens: TokenUsage,
        val error: MessageError? = null
    ) : Message()
}

data class ModelRef(
    val providerID: String,
    val modelID: String
)

data class TokenUsage(
    val input: Int,
    val output: Int,
    val reasoning: Int = 0,
    val cacheRead: Int = 0,
    val cacheWrite: Int = 0
)

data class MessageWithParts(
    val message: Message,
    val parts: List<Part>
)

// domain/model/Part.kt
sealed class Part {
    abstract val id: String
    abstract val sessionID: String
    abstract val messageID: String
    
    data class Text(
        override val id: String,
        override val sessionID: String,
        override val messageID: String,
        val text: String,
        val isStreaming: Boolean = false
    ) : Part()
    
    data class Reasoning(
        override val id: String,
        override val sessionID: String,
        override val messageID: String,
        val text: String
    ) : Part()
    
    data class Tool(
        override val id: String,
        override val sessionID: String,
        override val messageID: String,
        val callID: String,
        val toolName: String,
        val state: ToolState
    ) : Part()
    
    data class File(
        override val id: String,
        override val sessionID: String,
        override val messageID: String,
        val mime: String,
        val filename: String?,
        val url: String
    ) : Part()
    
    data class Patch(
        override val id: String,
        override val sessionID: String,
        override val messageID: String,
        val hash: String,
        val files: List<String>
    ) : Part()
}

// domain/model/ToolState.kt
sealed class ToolState {
    abstract val input: Map<String, Any?>
    
    data class Pending(
        override val input: Map<String, Any?>,
        val rawInput: String
    ) : ToolState()
    
    data class Running(
        override val input: Map<String, Any?>,
        val title: String?,
        val startedAt: Instant
    ) : ToolState()
    
    data class Completed(
        override val input: Map<String, Any?>,
        val output: String,
        val title: String,
        val startedAt: Instant,
        val endedAt: Instant,
        val metadata: Map<String, Any?> = emptyMap()
    ) : ToolState()
    
    data class Error(
        override val input: Map<String, Any?>,
        val error: String,
        val startedAt: Instant,
        val endedAt: Instant
    ) : ToolState()
}

// domain/model/Event.kt
sealed class OpenCodeEvent {
    data class MessageUpdated(val message: Message) : OpenCodeEvent()
    data class MessagePartUpdated(val part: Part, val delta: String?) : OpenCodeEvent()
    data class SessionCreated(val session: Session) : OpenCodeEvent()
    data class SessionUpdated(val session: Session) : OpenCodeEvent()
    data class SessionStatusChanged(val sessionID: String, val status: SessionStatus) : OpenCodeEvent()
    data class PermissionRequested(val permission: Permission) : OpenCodeEvent()
    data object Connected : OpenCodeEvent()
    data class Disconnected(val reason: String?) : OpenCodeEvent()
    data class Error(val throwable: Throwable) : OpenCodeEvent()
}

sealed class SessionStatus {
    data object Idle : SessionStatus()
    data object Busy : SessionStatus()
    data class Retry(val attempt: Int, val message: String) : SessionStatus()
}

// domain/model/Permission.kt
data class Permission(
    val id: String,
    val type: String,
    val sessionID: String,
    val messageID: String,
    val title: String,
    val metadata: Map<String, Any?>,
    val createdAt: Instant
)

// domain/model/ServerConfig.kt
data class ServerConfig(
    val url: String,
    val name: String,
    val username: String? = null,
    val password: String? = null,
    val isLocal: Boolean = false,
    val lastConnected: Instant? = null
)
```

---

## API Layer

### OpenCode API Interface

```kotlin
// core/network/OpenCodeApi.kt
interface OpenCodeApi {
    
    // Health
    @GET("global/health")
    suspend fun health(): HealthResponse
    
    // Sessions
    @GET("session")
    suspend fun listSessions(): List<SessionDto>
    
    @POST("session")
    suspend fun createSession(@Body request: CreateSessionRequest): SessionDto
    
    @GET("session/{id}")
    suspend fun getSession(@Path("id") id: String): SessionDto
    
    @DELETE("session/{id}")
    suspend fun deleteSession(@Path("id") id: String): Boolean
    
    @PATCH("session/{id}")
    suspend fun updateSession(
        @Path("id") id: String,
        @Body request: UpdateSessionRequest
    ): SessionDto
    
    @GET("session/status")
    suspend fun getSessionStatuses(): Map<String, SessionStatusDto>
    
    @POST("session/{id}/abort")
    suspend fun abortSession(@Path("id") id: String): Boolean
    
    @POST("session/{id}/fork")
    suspend fun forkSession(
        @Path("id") id: String,
        @Body request: ForkSessionRequest
    ): SessionDto
    
    // Messages
    @GET("session/{sessionId}/message")
    suspend fun getMessages(
        @Path("sessionId") sessionId: String,
        @Query("limit") limit: Int? = null
    ): List<MessageWithPartsDto>
    
    @POST("session/{sessionId}/message")
    suspend fun sendMessage(
        @Path("sessionId") sessionId: String,
        @Body request: SendMessageRequest
    ): MessageWithPartsDto
    
    @POST("session/{sessionId}/prompt_async")
    suspend fun sendMessageAsync(
        @Path("sessionId") sessionId: String,
        @Body request: SendMessageRequest
    )
    
    // Permissions
    @POST("session/{sessionId}/permissions/{permissionId}")
    suspend fun respondToPermission(
        @Path("sessionId") sessionId: String,
        @Path("permissionId") permissionId: String,
        @Body request: PermissionResponseRequest
    ): Boolean
    
    // Files
    @GET("file")
    suspend fun listFiles(@Query("path") path: String?): List<FileNodeDto>
    
    @GET("file/content")
    suspend fun readFile(@Query("path") path: String): FileContentDto
    
    @GET("file/status")
    suspend fun getFileStatus(): List<FileStatusDto>
    
    // Search
    @GET("find")
    suspend fun searchText(@Query("pattern") pattern: String): List<SearchResultDto>
    
    @GET("find/file")
    suspend fun searchFiles(
        @Query("query") query: String,
        @Query("type") type: String? = null,
        @Query("limit") limit: Int? = null
    ): List<String>
    
    // Config
    @GET("config")
    suspend fun getConfig(): ConfigDto
    
    @GET("config/providers")
    suspend fun getProviders(): ProvidersDto
    
    // Agents
    @GET("agent")
    suspend fun getAgents(): List<AgentDto>
}

// Request DTOs
@Serializable
data class CreateSessionRequest(
    val parentID: String? = null,
    val title: String? = null
)

@Serializable
data class SendMessageRequest(
    val model: ModelRefDto? = null,
    val agent: String? = null,
    val parts: List<PartInputDto>
)

@Serializable
data class PartInputDto(
    val type: String,
    val text: String? = null
)

@Serializable
data class PermissionResponseRequest(
    val response: String, // "allow" | "deny" | "always" | "never"
    val remember: Boolean? = null
)
```

### SSE Event Source

```kotlin
// core/network/OpenCodeEventSource.kt
class OpenCodeEventSource @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val settingsDataStore: SettingsDataStore
) {
    private var eventSource: EventSource? = null
    
    private val _events = MutableSharedFlow<OpenCodeEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<OpenCodeEvent> = _events.asSharedFlow()
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    fun connect() {
        if (_connectionState.value is ConnectionState.Connected) return
        
        scope.launch {
            val baseUrl = settingsDataStore.serverUrl.first()
            
            _connectionState.value = ConnectionState.Connecting
            
            val request = Request.Builder()
                .url("$baseUrl/event")
                .header("Accept", "text/event-stream")
                .build()
            
            eventSource = EventSources.createFactory(okHttpClient)
                .newEventSource(request, object : EventSourceListener() {
                    
                    override fun onOpen(eventSource: EventSource, response: Response) {
                        _connectionState.value = ConnectionState.Connected
                        _events.tryEmit(OpenCodeEvent.Connected)
                    }
                    
                    override fun onEvent(
                        eventSource: EventSource,
                        id: String?,
                        type: String?,
                        data: String
                    ) {
                        parseAndEmitEvent(data)
                    }
                    
                    override fun onClosed(eventSource: EventSource) {
                        _connectionState.value = ConnectionState.Disconnected
                        _events.tryEmit(OpenCodeEvent.Disconnected(null))
                    }
                    
                    override fun onFailure(
                        eventSource: EventSource,
                        t: Throwable?,
                        response: Response?
                    ) {
                        _connectionState.value = ConnectionState.Error(t?.message)
                        _events.tryEmit(OpenCodeEvent.Error(t ?: Exception("SSE error")))
                        scheduleReconnect()
                    }
                })
        }
    }
    
    fun disconnect() {
        reconnectJob?.cancel()
        eventSource?.cancel()
        eventSource = null
        _connectionState.value = ConnectionState.Disconnected
    }
    
    private fun parseAndEmitEvent(data: String) {
        try {
            val eventData = json.decodeFromString<EventDataDto>(data)
            val event = when (eventData.type) {
                "message.updated" -> parseMessageUpdated(eventData)
                "message.part.updated" -> parsePartUpdated(eventData)
                "session.created" -> parseSessionCreated(eventData)
                "session.updated" -> parseSessionUpdated(eventData)
                "session.status" -> parseSessionStatus(eventData)
                "permission.updated" -> parsePermission(eventData)
                else -> null
            }
            event?.let { _events.tryEmit(it) }
        } catch (e: Exception) {
            Log.e("SSE", "Failed to parse event", e)
        }
    }
    
    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(3000)
            connect()
        }
    }
}

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data class Error(val message: String?) : ConnectionState()
}
```

---

## Termux Integration

### Termux Bridge

```kotlin
// core/termux/TermuxBridge.kt
class TermuxBridge @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val TERMUX_PACKAGE = "com.termux"
        const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
        const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
        
        const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
        const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
        const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
        
        const val TERMUX_HOME = "/data/data/com.termux/files/home"
        const val TERMUX_BIN = "/data/data/com.termux/files/usr/bin"
    }
    
    private val _status = MutableStateFlow<TermuxStatus>(TermuxStatus.Unknown)
    val status: StateFlow<TermuxStatus> = _status.asStateFlow()
    
    sealed class TermuxStatus {
        data object Unknown : TermuxStatus()
        data object NotInstalled : TermuxStatus()
        data object Installed : TermuxStatus()
        data object SetupRequired : TermuxStatus()
        data object OpenCodeNotInstalled : TermuxStatus()
        data object Ready : TermuxStatus()
        data class ServerRunning(val port: Int) : TermuxStatus()
    }
    
    fun isTermuxInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    fun hasRunCommandPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            "com.termux.permission.RUN_COMMAND"
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    fun runCommand(
        executable: String,
        arguments: List<String> = emptyList(),
        workdir: String = TERMUX_HOME,
        background: Boolean = true,
        onResult: ((TermuxCommandResult) -> Unit)? = null
    ): Boolean {
        if (!isTermuxInstalled()) return false
        
        val intent = Intent().apply {
            setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
            action = ACTION_RUN_COMMAND
            putExtra(EXTRA_COMMAND_PATH, executable)
            putExtra(EXTRA_ARGUMENTS, arguments.toTypedArray())
            putExtra(EXTRA_WORKDIR, workdir)
            putExtra(EXTRA_BACKGROUND, background)
            
            if (!background) {
                putExtra(EXTRA_SESSION_ACTION, "0")
            }
            
            onResult?.let { callback ->
                val resultIntent = Intent(context, TermuxResultService::class.java)
                val executionId = TermuxResultService.getNextExecutionId()
                resultIntent.putExtra(TermuxResultService.EXTRA_EXECUTION_ID, executionId)
                
                val pendingIntent = PendingIntent.getService(
                    context,
                    executionId,
                    resultIntent,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE
                )
                putExtra(EXTRA_PENDING_INTENT, pendingIntent)
                TermuxResultService.registerCallback(executionId, callback)
            }
        }
        
        return try {
            context.startService(intent)
            true
        } catch (e: Exception) {
            Log.e("TermuxBridge", "Failed to run command", e)
            false
        }
    }
    
    fun startOpenCodeServer(
        port: Int = 4096,
        projectDir: String? = null
    ): Boolean {
        val args = mutableListOf("serve", "--port", port.toString())
        projectDir?.let { args.addAll(listOf("--cwd", it)) }
        
        return runCommand(
            executable = "$TERMUX_BIN/opencode",
            arguments = args,
            background = false
        )
    }
    
    fun checkOpenCodeInstalled(callback: (Boolean) -> Unit) {
        runCommand(
            executable = "$TERMUX_BIN/which",
            arguments = listOf("opencode"),
            background = true,
            onResult = { result ->
                callback(result.exitCode == 0)
            }
        )
    }
    
    fun installOpenCode(): Boolean {
        return runCommand(
            executable = "$TERMUX_BIN/bash",
            arguments = listOf("-c", """
                echo "=== Installing OpenCode ==="
                pkg update -y
                pkg install -y nodejs
                npm install -g opencode
                echo ""
                echo "=== Installation Complete ==="
                opencode --version
                echo ""
                echo "Press Enter to close..."
                read
            """.trimIndent()),
            background = false
        )
    }
    
    fun openTermuxSetup(): Boolean {
        return runCommand(
            executable = "$TERMUX_BIN/bash",
            arguments = listOf("-c", """
                echo "=== Pocket Code Setup ==="
                echo ""
                echo "Enabling external app access..."
                mkdir -p ~/.termux
                echo "allow-external-apps=true" >> ~/.termux/termux.properties
                echo ""
                echo "Done! Please restart Termux and Pocket Code."
                echo ""
                echo "Press Enter to close..."
                read
            """.trimIndent()),
            background = false
        )
    }
    
    suspend fun checkStatus(): TermuxStatus {
        return when {
            !isTermuxInstalled() -> TermuxStatus.NotInstalled
            !hasRunCommandPermission() -> TermuxStatus.SetupRequired
            else -> {
                var openCodeInstalled = false
                val latch = CountDownLatch(1)
                checkOpenCodeInstalled { installed ->
                    openCodeInstalled = installed
                    latch.countDown()
                }
                withContext(Dispatchers.IO) {
                    latch.await(5, TimeUnit.SECONDS)
                }
                if (openCodeInstalled) TermuxStatus.Ready
                else TermuxStatus.OpenCodeNotInstalled
            }
        }.also { _status.value = it }
    }
}

data class TermuxCommandResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int?,
    val error: String?
)
```

### Result Service

```kotlin
// core/termux/TermuxResultService.kt
class TermuxResultService : IntentService("TermuxResultService") {
    
    companion object {
        const val EXTRA_EXECUTION_ID = "execution_id"
        const val EXTRA_PLUGIN_RESULT_BUNDLE = "com.termux.EXTRA_PLUGIN_RESULT_BUNDLE"
        
        private var executionId = AtomicInteger(1000)
        private val callbacks = ConcurrentHashMap<Int, (TermuxCommandResult) -> Unit>()
        
        fun getNextExecutionId(): Int = executionId.incrementAndGet()
        
        fun registerCallback(id: Int, callback: (TermuxCommandResult) -> Unit) {
            callbacks[id] = callback
        }
    }
    
    override fun onHandleIntent(intent: Intent?) {
        if (intent == null) return
        
        val resultBundle = intent.getBundleExtra(EXTRA_PLUGIN_RESULT_BUNDLE)
        val execId = intent.getIntExtra(EXTRA_EXECUTION_ID, 0)
        
        val result = if (resultBundle != null) {
            TermuxCommandResult(
                stdout = resultBundle.getString("stdout", ""),
                stderr = resultBundle.getString("stderr", ""),
                exitCode = resultBundle.getInt("exitCode"),
                error = resultBundle.getString("errmsg")
            )
        } else {
            TermuxCommandResult("", "", null, "No result")
        }
        
        callbacks.remove(execId)?.invoke(result)
    }
}
```

---

## UI Components

### Chat Screen

```kotlin
// ui/screens/chat/ChatScreen.kt
@Composable
fun ChatScreen(
    sessionId: String,
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenFiles: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    
    val listState = rememberLazyListState()
    
    // Auto-scroll to bottom
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    Scaffold(
        topBar = {
            ChatTopBar(
                title = uiState.session?.title ?: "Chat",
                connectionState = connectionState,
                onBack = onNavigateBack,
                onTerminal = onOpenTerminal,
                onFiles = onOpenFiles
            )
        },
        bottomBar = {
            ChatInputBar(
                value = uiState.inputText,
                onValueChange = viewModel::updateInput,
                onSend = viewModel::sendMessage,
                isLoading = uiState.isSending,
                enabled = connectionState is ConnectionState.Connected
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(
                    items = messages,
                    key = { it.message.id }
                ) { messageWithParts ->
                    ChatMessage(
                        messageWithParts = messageWithParts,
                        onToolApprove = { viewModel.respondToPermission(it, "allow") },
                        onToolDeny = { viewModel.respondToPermission(it, "deny") }
                    )
                }
            }
            
            // Permission dialog
            uiState.pendingPermission?.let { permission ->
                PermissionDialog(
                    permission = permission,
                    onAllow = { viewModel.respondToPermission(permission.id, "allow") },
                    onDeny = { viewModel.respondToPermission(permission.id, "deny") },
                    onAlways = { viewModel.respondToPermission(permission.id, "always") }
                )
            }
        }
    }
}

// ui/screens/chat/ChatViewModel.kt
@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val messageRepository: MessageRepository,
    private val eventRepository: EventRepository
) : ViewModel() {
    
    private val sessionId: String = savedStateHandle.get<String>("sessionId")!!
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    val messages: StateFlow<List<MessageWithParts>> = 
        messageRepository.getMessagesForSession(sessionId)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    val connectionState: StateFlow<ConnectionState> = 
        eventRepository.connectionState
    
    init {
        loadSession()
        observeEvents()
    }
    
    private fun loadSession() {
        viewModelScope.launch {
            sessionRepository.getSession(sessionId)
                .onSuccess { session ->
                    _uiState.update { it.copy(session = session) }
                }
        }
    }
    
    private fun observeEvents() {
        viewModelScope.launch {
            eventRepository.events.collect { event ->
                handleEvent(event)
            }
        }
    }
    
    private fun handleEvent(event: OpenCodeEvent) {
        when (event) {
            is OpenCodeEvent.MessagePartUpdated -> {
                if (event.part.sessionID == sessionId) {
                    messageRepository.updatePart(event.part, event.delta)
                }
            }
            is OpenCodeEvent.PermissionRequested -> {
                if (event.permission.sessionID == sessionId) {
                    _uiState.update { it.copy(pendingPermission = event.permission) }
                }
            }
            is OpenCodeEvent.SessionStatusChanged -> {
                if (event.sessionID == sessionId) {
                    _uiState.update { it.copy(sessionStatus = event.status) }
                }
            }
            else -> {}
        }
    }
    
    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }
    
    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return
        
        _uiState.update { it.copy(inputText = "", isSending = true) }
        
        viewModelScope.launch {
            messageRepository.sendMessage(sessionId, text)
                .onSuccess {
                    _uiState.update { it.copy(isSending = false) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSending = false, error = error.message) }
                }
        }
    }
    
    fun respondToPermission(permissionId: String, response: String) {
        viewModelScope.launch {
            sessionRepository.respondToPermission(sessionId, permissionId, response)
            _uiState.update { it.copy(pendingPermission = null) }
        }
    }
}

data class ChatUiState(
    val session: Session? = null,
    val inputText: String = "",
    val isSending: Boolean = false,
    val sessionStatus: SessionStatus = SessionStatus.Idle,
    val pendingPermission: Permission? = null,
    val error: String? = null
)
```

---

## Navigation

```kotlin
// ui/navigation/Screen.kt
sealed class Screen(val route: String) {
    data object Setup : Screen("setup")
    data object Server : Screen("server")
    data object Sessions : Screen("sessions")
    data object Chat : Screen("chat/{sessionId}") {
        fun createRoute(sessionId: String) = "chat/$sessionId"
    }
    data object Terminal : Screen("terminal")
    data object Files : Screen("files")
    data object FileViewer : Screen("files/view?path={path}") {
        fun createRoute(path: String) = "files/view?path=${Uri.encode(path)}"
    }
    data object Settings : Screen("settings")
}

// ui/navigation/NavGraph.kt
@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Setup.route) {
            SetupScreen(
                onSetupComplete = {
                    navController.navigate(Screen.Server.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Server.route) {
            ServerScreen(
                onConnected = {
                    navController.navigate(Screen.Sessions.route) {
                        popUpTo(Screen.Server.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Sessions.route) {
            SessionListScreen(
                onSessionClick = { sessionId ->
                    navController.navigate(Screen.Chat.createRoute(sessionId))
                },
                onNewSession = { sessionId ->
                    navController.navigate(Screen.Chat.createRoute(sessionId))
                },
                onSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        
        composable(
            route = Screen.Chat.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) {
            ChatScreen(
                sessionId = it.arguments?.getString("sessionId")!!,
                onNavigateBack = { navController.popBackStack() },
                onOpenTerminal = { navController.navigate(Screen.Terminal.route) },
                onOpenFiles = { navController.navigate(Screen.Files.route) }
            )
        }
        
        composable(Screen.Terminal.route) {
            TerminalScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Files.route) {
            FileExplorerScreen(
                onFileClick = { path ->
                    navController.navigate(Screen.FileViewer.createRoute(path))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Screen.FileViewer.route,
            arguments = listOf(navArgument("path") { type = NavType.StringType })
        ) {
            FileViewerScreen(
                path = Uri.decode(it.arguments?.getString("path")!!),
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
```

---

## Implementation Phases

### Phase 1: Project Setup (Day 1)
- [x] Create implementation spec
- [ ] Initialize Android project
- [ ] Configure Gradle with dependencies
- [ ] Set up project structure
- [ ] Create Application class with Hilt
- [ ] Configure theme and colors

### Phase 2: Core Infrastructure (Day 2-3)
- [ ] Implement SettingsDataStore
- [ ] Create OpenCodeApi interface
- [ ] Implement OpenCodeEventSource (SSE)
- [ ] Set up Room database
- [ ] Create Termux bridge

### Phase 3: Server Connection (Day 4)
- [ ] Implement ServerScreen UI
- [ ] Local (Termux) mode
- [ ] Remote server mode
- [ ] Connection state management

### Phase 4: Chat Foundation (Day 5-7)
- [ ] Session list screen
- [ ] Basic chat UI
- [ ] Message sending
- [ ] SSE event handling
- [ ] Text streaming display

### Phase 5: Rich Content (Day 8-10)
- [ ] Markdown rendering
- [ ] Code syntax highlighting
- [ ] Tool execution cards
- [ ] Permission dialogs
- [ ] Error handling

### Phase 6: Terminal & Files (Day 11-14)
- [ ] Terminal screen
- [ ] Command execution via Termux
- [ ] File explorer tree
- [ ] File viewer with syntax highlighting
- [ ] Search functionality

### Phase 7: Polish (Day 15-17)
- [ ] Settings screen
- [ ] Theme support (light/dark)
- [ ] Session branching
- [ ] Offline indicators
- [ ] Error recovery

### Phase 8: Testing & Release (Day 18-21)
- [ ] Unit tests
- [ ] Integration tests
- [ ] UI tests
- [ ] Performance optimization
- [ ] Build release APK

---

## Resources

- [OpenCode Documentation](https://opencode.ai/docs/)
- [OpenCode SDK](https://opencode.ai/docs/sdk/)
- [OpenCode Server API](https://opencode.ai/docs/server/)
- [Termux RUN_COMMAND Intent](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Material 3 Components](https://m3.material.io/components)

---

*Last updated: January 2026*
