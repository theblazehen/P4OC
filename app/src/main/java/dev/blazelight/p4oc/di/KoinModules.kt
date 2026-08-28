package dev.blazelight.p4oc.di

import androidx.lifecycle.SavedStateHandle
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.core.haptic.HapticFeedback
import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.core.network.MdnsDiscoveryManager
import dev.blazelight.p4oc.core.network.PtyWebSocketClient
import dev.blazelight.p4oc.core.network.ServerConnectionRegistry
import dev.blazelight.p4oc.core.notification.NotificationEventObserver
import dev.blazelight.p4oc.core.notification.NotificationHelper
import dev.blazelight.p4oc.core.security.CredentialStore
import dev.blazelight.p4oc.data.files.FileRepository
import dev.blazelight.p4oc.data.remote.mapper.EventMapper
import dev.blazelight.p4oc.data.remote.mapper.MessageMapper
import dev.blazelight.p4oc.data.server.ActiveServerApiProvider
import dev.blazelight.p4oc.data.server.StaleWorkspaceClientException
import dev.blazelight.p4oc.data.session.SessionRepositoryImpl
import dev.blazelight.p4oc.data.session.SessionRepositoryProvider
import dev.blazelight.p4oc.ui.screens.chat.ChatViewModel
import dev.blazelight.p4oc.ui.screens.chat.ModelSelectionCoordinator
import dev.blazelight.p4oc.ui.screens.files.FilesViewModel
import dev.blazelight.p4oc.ui.screens.licenses.LicensesViewModel
import dev.blazelight.p4oc.ui.screens.projects.ProjectsViewModel
import dev.blazelight.p4oc.ui.screens.server.ServerViewModel
import dev.blazelight.p4oc.ui.screens.sessions.SessionListViewModel
import dev.blazelight.p4oc.ui.screens.settings.AgentsConfigViewModel
import dev.blazelight.p4oc.ui.screens.settings.ChatSettingsViewModel
import dev.blazelight.p4oc.ui.screens.settings.ModelControlsViewModel
import dev.blazelight.p4oc.ui.screens.settings.NotificationSettingsViewModel
import dev.blazelight.p4oc.ui.screens.settings.ProviderConfigViewModel
import dev.blazelight.p4oc.ui.screens.settings.SettingsConnectionContext
import dev.blazelight.p4oc.ui.screens.settings.SettingsViewModel
import dev.blazelight.p4oc.ui.screens.settings.SkillsViewModel
import dev.blazelight.p4oc.ui.screens.settings.VisualSettingsViewModel
import dev.blazelight.p4oc.ui.screens.terminal.TerminalViewModel
import dev.blazelight.p4oc.ui.tabs.TabManager
import dev.blazelight.p4oc.ui.workspace.WorkspaceRepositoryOwner
import dev.blazelight.p4oc.ui.workspace.WorkspaceViewModel
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

val appModule = module {
    // JSON serialization
    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = false
            explicitNulls = false
            coerceInputValues = true
        }
    }

    // Security - must be created before SettingsDataStore (used in migration)
    single { CredentialStore(androidContext()) }

    // Core services
    single { SettingsDataStore(androidContext(), get()) }
    single { NotificationHelper(androidContext()) }
    single { NotificationEventObserver(get(), get(), get(), get()) }
    single { HapticFeedback(androidContext()) }

    // Tab management (singleton for app lifetime)
    single { TabManager() }
    single { ModelSelectionCoordinator() }
}

val networkModule = module {
    // Mappers
    single { MessageMapper() }
    single { EventMapper(get(), get()) }

    // Network
    single { MdnsDiscoveryManager(androidContext()) }
    factory { params -> PtyWebSocketClient(get(), params.get(), params.get()) }
    single {
        ServerConnectionRegistry(
            settingsDataStore = get(),
            connectionManagerFactory = { ConnectionManager(get(), get(), get()) },
        )
    }
    single<ActiveServerApiProvider> {
        val registry: ServerConnectionRegistry = get()
        ActiveServerApiProvider { serverRef, generation ->
            val activeGeneration = registry.generation(serverRef)
            if (activeGeneration != generation) {
                throw StaleWorkspaceClientException(
                    "Workspace generation ${generation.value} does not match server " +
                        "${serverRef.endpointKey} generation ${activeGeneration?.value ?: "<none>"}",
                )
            }
            registry.api(serverRef) ?: throw StaleWorkspaceClientException(
                "No connected API for workspace server ${serverRef.endpointKey} generation=${generation.value}",
            )
        }
    }
    single { SessionRepositoryProvider(get(), get(), get(), json = get()) }
}

val viewModelModule = module {
    viewModelOf(::ServerViewModel)
    viewModel { (client: dev.blazelight.p4oc.data.workspace.WorkspaceClient) ->
        ModelControlsViewModel(
            client,
            get(),
            get(),
        )
    }
    viewModel { (client: dev.blazelight.p4oc.data.workspace.WorkspaceClient) -> AgentsConfigViewModel(client, get()) }
    viewModelOf(::VisualSettingsViewModel)
    viewModelOf(::ChatSettingsViewModel)
    viewModel { (client: dev.blazelight.p4oc.data.workspace.WorkspaceClient) -> SkillsViewModel(client, get()) }
    viewModel { params ->
        SettingsViewModel(
            settingsDataStore = get(),
            serverConnectionRegistry = get(),
            connectionContext = params.get<SettingsConnectionContext>(),
        )
    }
    viewModelOf(::NotificationSettingsViewModel)
    viewModelOf(::LicensesViewModel)
    viewModel { params ->
        ProviderConfigViewModel(
            params.get<WorkspaceRepositoryOwner>().workspaceClient,
            get(),
            get(),
        )
    }
    viewModel { params -> ProjectsViewModel(params.get(), get()) }
    viewModel { params ->
        WorkspaceViewModel(params.get<WorkspaceRepositoryOwner>())
    }
    viewModel { params ->
        val owner = params.get<WorkspaceRepositoryOwner>()
        ChatViewModel(
            get(),
            owner.workspaceClient,
            owner.sessionRepository,
            owner.uploadCoordinator,
            get(),
            get(),
            get<ModelSelectionCoordinator>(),
            get(),
        )
    }
    viewModel { params ->
        SessionListViewModel(params.get<SessionRepositoryImpl>(), get<SavedStateHandle>())
    }
    viewModel { params -> FilesViewModel(params.get<FileRepository>(), params.get(), get<SavedStateHandle>()) }
    viewModel { params ->
        val owner = params.get<WorkspaceRepositoryOwner>()
        TerminalViewModel(
            savedStateHandle = get(),
            context = androidContext(),
            ptyWebSocket = get { parametersOf(owner.workspace.server, owner.generation) },
            workspaceOwner = owner,
            serverConnectionRegistry = get(),
        )
    }
}

val allModules = listOf(
    appModule,
    networkModule,
    viewModelModule
)
