---
id: oa-gs5t
status: closed
deps: [oa-b388]
links: []
created: 2026-02-22T11:58:58Z
type: task
priority: 2
assignee: Jasmin Le Roux
---
# Split Dtos.kt + convert stateless mappers to objects

## Problem 1: Dtos.kt is 1041 lines

Single file contains ALL DTOs for the entire app: projects, sessions, messages, parts, permissions, questions, files, providers, models, agents, commands, configs, todos, events, auth, PTY, search, tools. Finding anything requires scrolling through the entire file.

## Problem 2: Stateless mappers registered as Koin singletons

9 of 11 mapper classes have zero-arg constructors and no state. They're registered as Koin `singleOf()` but could be `object` declarations, which is more idiomatic and removes DI boilerplate. Only `MessageMapper(json: Json)` and `EventMapper(json, messageMapper)` have actual dependencies.

## What To Do

### Part A: Split Dtos.kt
Split into ~16 domain-grouped files in `app/src/main/java/dev/blazelight/p4oc/data/remote/dto/`:
- `ProjectDtos.kt`: ProjectDto, ProjectTimeDto, VcsInfoDto, PathInfoDto
- `SessionDtos.kt`: SessionDto, TimeDto, SessionSummaryDto, FileDiffDto, SessionShareDto, etc.
- `MessageDtos.kt`: MessageWrapperDto, MessageInfoDto, MessageTimeDto, etc.
- `PartDtos.kt`: PartDto, PartTimeDto, ToolStateDto, PartInputDto, ModelInput, SendMessageRequest
- `PermissionDtos.kt`: PermissionDto, PermissionToolDto, PermissionResponseRequest
- `QuestionDtos.kt`: QuestionRequestDto, QuestionToolRefDto, QuestionDto, etc.
- `FileDtos.kt`: FileNodeDto, FileContentDto, PatchDto, HunkDto, etc.
- `ProviderDtos.kt`: ProviderDto, ModelDto, ModelApiDto, etc.
- `AgentDtos.kt`: AgentDto, PermissionRuleDto
- `CommandDtos.kt`: CommandDto, ExecuteCommandRequest, ShellCommandRequest
- `ConfigDtos.kt`: ConfigDto, AgentConfigDto, ProviderConfigDto, etc.
- `TodoDtos.kt`: TodoDto
- `EventDtos.kt`: EventDataDto, GlobalEventDto, all event properties DTOs
- `StatusDtos.kt`: LspStatusDto, FormatterStatusDto, McpStatusDto
- `AuthDtos.kt`: OAuthDto, ApiAuthDto, etc.
- `PtyDtos.kt`: PtyDto, CreatePtyRequest, etc.
- `ToolDtos.kt`: ToolListDto, ToolDto, etc.

All stay in package `dev.blazelight.p4oc.data.remote.dto` — wildcard imports continue to work.
Delete `Dtos.kt` after split.

### Part B: Convert stateless mappers to objects
In `app/src/main/java/dev/blazelight/p4oc/data/remote/mapper/Mappers.kt`:

Convert to `object` (no constructor deps):
- `ProjectMapper`, `SessionMapper`, `PartMapper`, `ProviderMapper`, `AgentMapper`, `CommandMapper`, `TodoMapper`, `SymbolMapper`, `StatusMapper`

Keep as classes with DI:
- `MessageMapper(json: Json)` — unchanged
- `EventMapper(json: Json, messageMapper: MessageMapper)` — reduced from 5 constructor params to 2, reference object mappers directly

### Part C: Update Koin registrations
In `KoinModules.kt`:
- Remove 9 `singleOf()` registrations for now-object mappers
- Keep `single { MessageMapper(get()) }`
- Change `EventMapper` to `single { EventMapper(get(), get()) }` (2 params instead of 5)

### Part D: Update ViewModel constructors
- Remove mapper params that are now objects from ViewModel constructors
- ChatViewModel: 9 params → ~5 (remove sessionMapper, partMapper, commandMapper, todoMapper)
- Update call sites to use `SessionMapper.mapToDomain(dto)` style (object access)
- Update Koin viewModel registration to match reduced params

### Part E: Update other ViewModels
Check and update:
- `SessionListViewModel` — uses SessionMapper
- `ProjectsViewModel` — uses ProjectMapper  
- Any other VMs using the now-object mappers

## Acceptance Criteria
- [ ] `Dtos.kt` deleted, replaced by ~16 domain-grouped files
- [ ] 9 stateless mappers converted to `object`
- [ ] `EventMapper` constructor reduced to 2 params
- [ ] ChatViewModel constructor reduced from 9 to ~5 params
- [ ] All Koin registrations updated
- [ ] All ViewModel call sites updated
- [ ] `./gradlew :app:compileDebugKotlin` passes

## Acceptance Criteria

Dtos split into domain files. Stateless mappers are objects. ChatViewModel params reduced. Compile clean.

