package dev.blazelight.p4oc.di

import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.core.security.CredentialStore
import dev.blazelight.p4oc.core.network.MdnsDiscoveryManager
import dev.blazelight.p4oc.core.network.PtyWebSocketClient
import dev.blazelight.p4oc.core.haptic.HapticFeedback
import dev.blazelight.p4oc.core.notification.NotificationEventObserver
import dev.blazelight.p4oc.core.notification.NotificationHelper

import dev.blazelight.p4oc.data.remote.mapper.EventMapper
import dev.blazelight.p4oc.data.remote.mapper.MessageMapper
import dev.blazelight.p4oc.data.server.ActiveServerApiProvider
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.domain.workspace.Workspace
import dev.blazelight.p4oc.ui.screens.chat.ChatViewModel
import dev.blazelight.p4oc.ui.screens.server.ServerViewModel
import dev.blazelight.p4oc.ui.screens.settings.AgentsConfigViewModel
import dev.blazelight.p4oc.ui.screens.settings.ModelControlsViewModel
import dev.blazelight.p4oc.ui.screens.settings.SkillsViewModel
import dev.blazelight.p4oc.ui.screens.settings.VisualSettingsViewModel
import dev.blazelight.p4oc.ui.screens.settings.SettingsViewModel
import dev.blazelight.p4oc.ui.screens.settings.NotificationSettingsViewModel
import dev.blazelight.p4oc.ui.screens.settings.ProviderConfigViewModel
import dev.blazelight.p4oc.ui.screens.projects.ProjectsViewModel
import dev.blazelight.p4oc.ui.screens.terminal.TerminalViewModel
import dev.blazelight.p4oc.ui.tabs.TabManager
import dev.blazelight.p4oc.ui.workspace.WorkspaceViewModel
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
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


}

val networkModule = module {
    // Mappers (stateless mappers are objects — only DI-dependent ones registered here)
    single { MessageMapper(get()) }
    single { EventMapper(get(), get()) }

    // Network
    single { MdnsDiscoveryManager(androidContext()) }
    factory { PtyWebSocketClient(get()) }
    single { ConnectionManager(get(), get()) }
    single<ActiveServerApiProvider> {
        val connectionManager: ConnectionManager = get()
        ActiveServerApiProvider { serverRef, generation ->
            val activeBaseUrl = connectionManager.currentBaseUrl
                ?: throw IllegalStateException("No active server for workspace ${serverRef.endpointKey} generation=${generation.value}")
            val activeServerRef = ServerRef.fromEndpoint(activeBaseUrl)
            check(activeServerRef == serverRef) {
                "Workspace server ${serverRef.endpointKey} does not match active server ${activeServerRef.endpointKey}"
            }
            val activeGeneration = connectionManager.currentGeneration
            check(activeGeneration == generation) {
                "Workspace generation ${generation.value} does not match active generation ${activeGeneration?.value ?: "<none>"}"
            }
            connectionManager.requireApi()
        }
    }
}

val viewModelModule = module {
    viewModelOf(::ServerViewModel)
    viewModelOf(::ModelControlsViewModel)
    viewModelOf(::AgentsConfigViewModel)
    viewModelOf(::VisualSettingsViewModel)
    viewModelOf(::SkillsViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::NotificationSettingsViewModel)
    viewModelOf(::ProviderConfigViewModel)
    viewModelOf(::ProjectsViewModel)
    viewModel { params ->
        WorkspaceViewModel(
            tabId = params.get<String>(),
            workspace = params.get<Workspace>(),
            generation = params.get<ServerGeneration>(),
            activeServerApiProvider = get(),
            messageMapper = get(),
            connectionManager = get(),
        )
    }
    viewModel { params -> ChatViewModel(params.get(), params.get(), params.get(), get(), get(), get()) }
    viewModel { params -> TerminalViewModel(params.get(), androidContext(), get(), get()) }
}

val allModules = listOf(
    appModule,
    networkModule,
    viewModelModule
)
