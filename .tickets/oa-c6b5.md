---
id: oa-c6b5
status: closed
deps: [oa-zclo]
links: []
created: 2026-02-22T11:58:01Z
type: task
priority: 1
assignee: Jasmin Le Roux
---
# Fix RecentServer JSON + remove runBlocking from SettingsDataStore

## Problem 1: Hand-rolled JSON with regex parsing

`RecentServer` in `SettingsDataStore.kt:433-469` uses manual JSON construction and REGEX to parse JSON, despite kotlinx.serialization being used everywhere else. The `toJson()` builds strings manually, and `fromJson()` uses regex patterns like `""""password"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""` to extract fields. The list is split by `},` which breaks on values containing `},`.

## Problem 2: runBlocking in init blocks main thread

`SettingsDataStore.kt:69-77` uses `runBlocking` in `init {}` to preload cached values. Since it's a Koin singleton created during `Application.onCreate()`, this blocks the main thread on every cold start. On slow devices or large DataStore files, this risks ANR.

## What To Do

### 1. Make RecentServer @Serializable
- Add `@Serializable` annotation to `RecentServer`
- Remove `password` field (passwords now in `CredentialStore` from ticket oa-zclo)
- Delete `toJson()` and `fromJson()` methods entirely
- Replace list serialization: `Json.encodeToString(servers)` / `Json.decodeFromString<List<RecentServer>>(json)`

### 2. Migration from old format
- Keep a one-time migration path: try to parse old delimiter-based format (`split("},")`), convert to new serialized format
- If old format has passwords, migrate them to `CredentialStore` (coordinate with ticket oa-zclo)
- Rewrite the DataStore value with clean kotlinx.serialization JSON

### 3. Replace runBlocking with background collector
- Remove `runBlocking` block from `init {}`
- Create internal `CoroutineScope(Dispatchers.IO + SupervisorJob())` 
- Launch a collector on `context.dataStore.data` that keeps `cachedServerUrl` and `cachedUsername` updated
- Keep defaults safe until first DataStore emission (`DEFAULT_LOCAL_URL` for URL, `null` for username)
- Remove `cachedPassword` entirely (moved to `CredentialStore`)

## Key File
`app/src/main/java/dev/blazelight/p4oc/core/datastore/SettingsDataStore.kt`
- Lines 69-77: `runBlocking` init
- Lines 433-469: `RecentServer` with manual JSON
- Lines 249-251: List split by "},"
- Lines 267-293: `addRecentServer` method

## Acceptance Criteria
- [ ] `RecentServer` is `@Serializable`, no more regex JSON parsing
- [ ] No `runBlocking` in `SettingsDataStore`
- [ ] Existing recent servers migrated from old format
- [ ] `cachedServerUrl` and `cachedUsername` still work (non-blocking)
- [ ] App cold start doesn't block main thread for DataStore I/O
- [ ] `./gradlew :app:compileDebugKotlin` passes

## Acceptance Criteria

RecentServer uses @Serializable. No runBlocking. Old format migrated. Compile clean.

