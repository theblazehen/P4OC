---
id: oa-zclo
status: closed
deps: [oa-i7ba]
links: []
created: 2026-02-22T11:57:39Z
type: feature
priority: 0
assignee: Jasmin Le Roux
---
# Encrypted credential storage with CredentialStore

## Problem

Passwords are stored in plaintext in Android DataStore Preferences via `stringPreferencesKey("password")` in `SettingsDataStore.kt:24`. They're stored TWICE: once as the current password, and once embedded in the `RecentServer` JSON string. On a rooted device, ADB backup, or forensic extraction, every saved credential is trivially readable.

## What To Do

### 1. Add dependency
- `gradle/libs.versions.toml`: Add `security-crypto = "1.1.0-alpha06"` to versions, add library entry
- `app/build.gradle.kts`: Add `implementation(libs.security.crypto)`

### 2. Create CredentialStore
New file: `app/src/main/java/dev/blazelight/p4oc/core/security/CredentialStore.kt`
- Backed by `EncryptedSharedPreferences` with `MasterKey.DEFAULT_MASTER_KEY_SPEC` (AES256_GCM)
- API: `save(url: String, username: String?, password: String)`, `get(url: String, username: String?): String?`, `remove(url: String, username: String?)`
- Key format: deterministic hash of `"$url|$username"` to avoid leaking server URLs in pref key names
- Register in Koin as `single { CredentialStore(androidContext()) }`

### 3. Migrate existing plaintext passwords
In `SettingsDataStore.kt`:
- Add one-time migration flag `KEY_CREDENTIALS_MIGRATED` (booleanPreferencesKey)
- On init (non-blocking): if not migrated, read `KEY_PASSWORD` + `KEY_USERNAME` + `KEY_SERVER_URL`, write to `CredentialStore`, remove `KEY_PASSWORD` from DataStore
- Also migrate passwords from `RecentServer` entries to `CredentialStore`
- Migration must be idempotent (safe to restart mid-migration)

### 4. Wire CredentialStore through connection flow
Files to modify:
- `core/network/ConnectionManager.kt`: Inject `CredentialStore`. In `connect()`, resolve password as: explicit override → `CredentialStore.get(config.url, config.username)`
- `ui/screens/server/ServerViewModel.kt`: On successful connect, save to `CredentialStore`. On auto-reconnect, get from `CredentialStore` instead of `config.password`
- `core/network/PtyWebSocketClient.kt`: Get auth material from `CredentialStore` instead of `config.password`
- `core/network/Connection.kt`: Remove `password` field from `ServerConfig` data class (password should NOT travel through app state)

### 5. Remove password from SettingsDataStore reads/writes
- Remove `KEY_PASSWORD` from `setCredentials()`, `setServerConfig()`, `saveLastConnection()`, `getLastConnection()`
- Remove `cachedPassword` volatile field
- Remove `getCachedPassword()` method
- Update all callers

## Key Files
- `core/datastore/SettingsDataStore.kt` — current plaintext storage (lines 24, 67, 75, 81, 99-100, 137-147, 180-191, 208-211, 218, 229, 239)
- `core/network/ConnectionManager.kt` — uses password for OkHttp auth interceptor (lines 46, 53, 68, 103, 112-113, 119, 128-129, 135-143)
- `core/network/PtyWebSocketClient.kt` — reads `config.password` for WebSocket auth (lines 99-100)
- `core/network/Connection.kt` — `ServerConfig` has `password` field (line 11)
- `ui/screens/server/ServerViewModel.kt` — passes password through connect flow (lines 56, 80-81, 105, 107, 109, 115-117, 162, 194)

## Acceptance Criteria
- [ ] Passwords encrypted at rest via `EncryptedSharedPreferences`
- [ ] `ServerConfig` no longer carries `password` field
- [ ] Existing plaintext passwords migrated on first launch
- [ ] `KEY_PASSWORD` removed from DataStore after migration
- [ ] Auto-reconnect still works with stored credentials
- [ ] Manual connect saves credentials to `CredentialStore`
- [ ] Recent servers work without embedded passwords
- [ ] `./gradlew :app:compileDebugKotlin` passes

## Acceptance Criteria

Passwords encrypted via EncryptedSharedPreferences. Plaintext removed. Migration works. Compile clean.

