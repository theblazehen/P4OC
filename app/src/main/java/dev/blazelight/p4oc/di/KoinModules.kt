package dev.blazelight.p4oc.di

import androidx.room.Room
import dev.blazelight.p4oc.core.database.PocketCodeDatabase
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.core.network.DirectoryManager
import dev.blazelight.p4oc.core.network.PtyWebSocketClient
import dev.blazelight.p4oc.core.notification.NotificationEventObserver
import dev.blazelight.p4oc.core.notification.NotificationHelper
import dev.blazelight.p4oc.data.remote.mapper.*
import dev.blazelight.p4oc.data.repository.EventRepositoryImpl
import dev.blazelight.p4oc.data.repository.FileRepositoryImpl
import dev.blazelight.p4oc.data.repository.MessageRepositoryImpl
import dev.blazelight.p4oc.data.repository.SessionRepositoryImpl
import dev.blazelight.p4oc.domain.repository.EventRepository
import dev.blazelight.p4oc.domain.repository.FileRepository
import dev.blazelight.p4oc.domain.repository.MessageRepository
import dev.blazelight.p4oc.domain.repository.SessionRepository
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
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
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

    // Core services
    single { SettingsDataStore(androidContext()) }
    single { NotificationHelper(androidContext()) }
    single { NotificationEventObserver(get(), get()) }
}

val databaseModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            PocketCodeDatabase::class.java,
            "pocketcode.db"
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    single { get<PocketCodeDatabase>().sessionDao() }
    single { get<PocketCodeDatabase>().messageDao() }
    single { get<PocketCodeDatabase>().serverConfigDao() }
}

val networkModule = module {
    // Mappers
    singleOf(::ProjectMapper)
    singleOf(::SessionMapper)
    singleOf(::PartMapper)
    singleOf(::ProviderMapper)
    singleOf(::AgentMapper)
    singleOf(::CommandMapper)
    singleOf(::TodoMapper)
    singleOf(::SymbolMapper)
    singleOf(::StatusMapper)
    single { MessageMapper(get()) }
    single { EventMapper(get(), get(), get(), get(), get()) }

    // Network
    single { DirectoryManager(get()) }
    factory { PtyWebSocketClient(get()) }
    single { ConnectionManager(get(), get(), get()) }
}

val repositoryModule = module {
    singleOf(::SessionRepositoryImpl) bind SessionRepository::class
    singleOf(::MessageRepositoryImpl) bind MessageRepository::class
    singleOf(::FileRepositoryImpl) bind FileRepository::class
    singleOf(::EventRepositoryImpl) bind EventRepository::class
    
    // Tab management (singleton for app lifetime)
    single { TabManager() }
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
    viewModel { params -> ChatViewModel(params.get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { params -> TerminalViewModel(params.get(), androidContext(), get(), get()) }
}

val allModules = listOf(
    appModule,
    databaseModule,
    networkModule,
    repositoryModule,
    viewModelModule
)
