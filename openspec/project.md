# Project Context

## Purpose
Pocket Code (P4OC) is a native Android client for the OpenCode AI coding agent. It enables developers to interact with OpenCode servers either locally via Termux integration or remotely over LAN/internet, providing a mobile-first experience for AI-assisted coding with real-time chat, terminal emulation, and file management.

## Tech Stack
- **Language:** Kotlin 2.0.10 with JVM target 17
- **UI Framework:** Jetpack Compose with Material 3
- **Build System:** Gradle 8.5.2 with Kotlin DSL
- **Min SDK:** 26 (Android 8.0) | **Target SDK:** 35
- **Architecture:** MVVM + Clean Architecture + Repository Pattern
- **DI:** Hilt 2.51.1 with KSP
- **Networking:** OkHttp 4.12 + Retrofit 2.11 + kotlinx-serialization
- **SSE:** LaunchDarkly okhttp-eventsource 4.1.1
- **Database:** Room 2.6.1
- **Preferences:** DataStore 1.1.1
- **Terminal:** Termux terminal-view/emulator 0.118.0
- **Image Loading:** Coil 2.7.0
- **Markdown:** FluidMarkdown (local module, streaming-optimized)
- **Navigation:** Navigation Compose 2.8.0

## Project Conventions

### Code Style
- Follow official Kotlin code style (`kotlin.code.style=official`)
- Use kotlinx-serialization for JSON (not Gson/Moshi)
- Prefer sealed classes/interfaces for domain models with exhaustive when statements
- Use `@Serializable` annotations on DTOs, map to domain models in separate mapper files
- Package structure: `dev.blazelight.p4oc` (actual), documentation references `com.pocketcode` (legacy)

### Architecture Patterns
- **Clean Architecture layers:** domain (models, repository interfaces) → data (DTOs, mappers, repository impls) → ui (screens, viewmodels, components)
- **Single Activity:** MainActivity hosts all navigation via NavGraph
- **State Management:** ViewModels expose StateFlow, collect with `collectAsStateWithLifecycle()`
- **Repository Pattern:** Interfaces in `domain/repository/`, implementations in `data/repository/`
- **DTOs vs Domain:** All API DTOs in `data/remote/dto/Dtos.kt`, mappers in `data/remote/mapper/Mappers.kt`
- **Event-driven updates:** SSE events from OpenCodeEventSource drive UI updates

### Testing Strategy
- Unit tests with JUnit 4 + kotlinx-coroutines-test + MockK
- Android instrumentation tests with Espresso + Compose UI testing
- Focus on repository and ViewModel testing
- Manual testing via connected device/emulator

### Git Workflow
- Feature branches merged locally to main
- Conventional commit messages: `feat:`, `fix:`, `refactor:`, `style:`, `perf:`, `docs:`
- Use beads (`bd`) for issue tracking
- No force push to main

## Domain Context
- **OpenCode Server API:** RESTful API with SSE for real-time events (see `OPENCODE_OVERVIEW.md`)
- **Sessions:** Represent chat conversations with the AI, contain messages with parts
- **Parts:** 12 types including Text, Tool, Reasoning, File, Patch, Agent, Subtask, etc.
- **Events:** 28 SSE event types for real-time updates (message updates, permissions, questions, PTY)
- **Permissions:** Tool execution requires user approval via permission dialogs
- **Questions:** Interactive LLM questions with option selection
- **PTY:** Pseudo-terminal for terminal emulation via WebSocket
- **Termux Integration:** Execute commands and run OpenCode server locally via RUN_COMMAND intent

## Important Constraints
- Must comply with OpenCode Server API specification at https://opencode.ai/docs/server/
- Termux RUN_COMMAND requires Termux:Tasker plugin or appropriate permissions
- SSE connection must handle reconnection gracefully
- Offline support is planned but not yet implemented
- Screenshots in automation don't work (device limitation)
- UI should minimize jank and follow TUI-like density (reduced padding)
- Reference UI: https://github.com/btriapitsyn/openchamber

## External Dependencies
- **OpenCode Server:** Primary backend (default: 192.168.24.25:4096 for dev)
- **Termux:** For local server execution and terminal integration
- **Termux:Tasker:** For RUN_COMMAND intent permissions
- **JitPack:** For Termux terminal dependencies
- **Google Maven:** For AndroidX and Compose dependencies

