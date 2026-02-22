package dev.blazelight.p4oc.di

import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.core.security.CredentialStore
import dev.blazelight.p4oc.core.network.DirectoryManager
import dev.blazelight.p4oc.core.network.PtyWebSocketClient
import dev.blazelight.p4oc.core.notification.NotificationEventObserver
import dev.blazelight.p4oc.core.notification.NotificationHelper
import dev.blazelight.p4oc.data.remote.mapper.EventMapper
import dev.blazelight.p4oc.data.remote.mapper.MessageMapper
import dev.blazelight.p4oc.ui.screens.chat.ChatViewModel
import dev.blazelight.p4oc.ui.screens.files.FilesViewModel
import dev.blazelight.p4oc.ui.screens.server.ServerViewModel
import dev.blazelight.p4oc.ui.screens.sessions.SessionListViewModel
import dev.blazelight.p4oc.ui.screens.settings.AgentsConfigViewModel
import dev.blazelight.p4oc.ui.screens.settings.ModelControlsViewModel
import dev.blazelight.p4oc.ui.screens.settings.SkillsViewModel
import dev.blazelight.p4oc.ui.screens.settings.VisualSettingsViewModel
import dev.blazelight.p4oc.ui.screens.settings.SettingsViewModel
import dev.blazelight.p4oc.ui.screens.settings.ProviderConfigViewModel
import dev.blazelight.p4oc.ui.screens.projects.ProjectsViewModel
import dev.blazelight.p4oc.ui.screens.terminal.TerminalViewModel
import dev.blazelight.p4oc.ui.tabs.TabManager
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
    single { NotificationEventObserver(get(), get()) }

    // Tab management (singleton for app lifetime)
    single { TabManager() }
}

val networkModule = module {
    // Mappers (stateless mappers are objects — only DI-dependent ones registered here)
    single { MessageMapper(get()) }
    single { EventMapper(get(), get()) }

    // Network
    single { DirectoryManager(get()) }
    factory { PtyWebSocketClient(get()) }
    single { ConnectionManager(get(), get(), get()) }
}

val viewModelModule = module {
    viewModelOf(::FilesViewModel)
    viewModelOf(::ServerViewModel)
    viewModelOf(::SessionListViewModel)
    viewModelOf(::ModelControlsViewModel)
    viewModelOf(::AgentsConfigViewModel)
    viewModelOf(::VisualSettingsViewModel)
    viewModelOf(::SkillsViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::ProviderConfigViewModel)
    viewModelOf(::ProjectsViewModel)
    viewModel { params -> ChatViewModel(params.get(), get(), get(), get(), get()) }
    viewModel { params -> TerminalViewModel(params.get(), androidContext(), get(), get()) }
}

val allModules = listOf(
    appModule,
    networkModule,
    viewModelModule
)
