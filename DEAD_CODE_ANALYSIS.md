# Dead Code Analysis Report

Generated: 2026-02-01
Updated: 2026-02-01 (removed unused duplicates)

## Summary

| Tool | Findings |
|------|----------|
| **detekt** | 150+ issues (complexity, long methods, empty constructors) |
| **Android Lint** | 122 unused resources, 3 errors |
| **R8 printusage** | 2,461 lines of unused code in app package |

---

## Critical Issues Found

### 1. Missing Class (Lint Error)
```
AndroidManifest.xml:47 - MissingClass
Class referenced: dev.blazelight.p4oc.core.termux.TermuxResultService
```
**Action:** Either create this service or remove from manifest.

### 2. Outdated ProGuard Rules
The `proguard-rules.pro` had rules for old package `com.pocketcode` - **FIXED** to `dev.blazelight.p4oc`.

### 3. Unused Scaffold Padding (Lint Error)
```
MainTabScreen.kt:116 - UnusedMaterial3ScaffoldPaddingParameter
```

---

## Unused Resources (122 total)

### Colors (3)
- `R.color.primary`
- `R.color.primary_dark`
- `R.color.surface`

### Mipmaps (2)
- `R.mipmap.ic_launcher_foreground`
- `R.mipmap.ic_launcher_round`

### Strings (~100+)
Many unused strings, particularly:
- `server_connection_mode`, `server_mode_local`, `server_mode_remote`
- All `termux_*` strings (17+ strings)
- Various UI strings that were never wired up

---

## Unused DAO Methods (R8 Analysis)

These Room DAO methods are defined but never called:

### MessageDao
- `deleteMessage()`
- `deletePart()`
- `insertMessage()` (individual)
- `insertPart()` (individual)
- `updateMessage()`
- `updatePart()`
- `insertMessageWithParts()`

### ServerConfigDao
- `deleteServerConfig()`
- `insertServerConfig()`
- `updateServerConfig()`

### SessionDao
- `deleteSession()`
- `insertSession()`
- `updateSession()`

**Note:** These may be kept for future use, but R8 will strip them from release builds.

---

## Unused API Methods (R8 Analysis)

These OpenCodeApi methods are never called:
- `addMcpServer()`
- `executeShellCommand()`
- `forkSession()`
- `initSession()`
- `log()`

---

## Code Complexity Issues (detekt)

### Long Methods (>60 lines)
| File | Method | Lines |
|------|--------|-------|
| `ChatScreen.kt` | `ChatScreen` | 266 |
| `TabNavHost.kt` | `TabNavHost` | 214 |
| `Mappers.kt` | `mapToEvent` | 173 |
| `SessionListScreen.kt` | `SessionListScreen` | 170 |
| `ModelAgentSelector.kt` | `ModelPickerDialog` | 170 |
| `FilePickerDialog.kt` | `FilePickerDialog` | 154 |
| `MessageRepositoryImpl.kt` | `toEntity` | 144 |

### High Cyclomatic Complexity (>15)
| File | Method | Complexity |
|------|--------|------------|
| `Mappers.kt` | `mapToDomain` | 44 |
| `Mappers.kt` | `mapToEvent` | 37 |
| `SyntaxHighlighter.kt` | `findNumberEnd` | 35 |
| `ToolComponents.kt` | `EnhancedToolPart` | 33 |
| `SyntaxHighlighter.kt` | `highlightLine` | 30 |
| `ChatScreen.kt` | `ChatScreen` | 30 |
| `MessageRepositoryImpl.kt` | `toEntity` | 28 |

### Classes with Too Many Functions (>11)
- `OpenCodeApi` - 61 functions
- `ChatViewModel` - 37 functions
- `SettingsDataStore` - 23 functions
- `MessageDao` - 16 functions
- `PtyTerminalClient` - 16 functions
- `TerminalViewModel` - 16 functions
- `VisualSettingsViewModel` - 15 functions

---

## Parallel Implementations Removed

The following unused duplicate files were deleted:

| File | Lines | Reason |
|------|-------|--------|
| `PermissionDialog.kt` | 65 | Replaced by `PermissionDialogEnhanced.kt` |
| `ChatInputBarWithAutocomplete.kt` | 191 | Replaced by `ChatInputBar.kt` |
| `QuestionDialog.kt` | 277 | Replaced by `InlineQuestionCard.kt` |
| `TermuxResultService` (manifest entry) | - | Class never existed |

**Total removed: ~533 lines of dead code**

## Remaining Parallel Implementations

| Duplication | Status | Notes |
|-------------|--------|-------|
| `ConnectionState` (2 versions) | Keep both | Different purposes (SSE vs PTY) |
| Inline syntax highlighter in `PermissionDialogEnhanced` | Refactor later | Works, but duplicates `SyntaxHighlighter` |
| Error handling pattern in ViewModels | Keep | Standard pattern, not true duplication |

---

## Files to Clean Up

### Empty Default Constructors
These mapper classes have unnecessary empty constructors:
- `Mappers.kt:19` - SessionMapper
- `Mappers.kt:46` - MessageMapper
- `Mappers.kt:200` - PartMapper
- `Mappers.kt:411` - CommandMapper
- `Mappers.kt:471` - ProjectMapper
- `Mappers.kt:493` - ProviderMapper
- `Mappers.kt:510` - TodoMapper
- `Mappers.kt:524` - ModelMapper
- `Mappers.kt:543` - AgentMapper

---

## Recommendations

### Quick Wins (Low Effort, High Impact)
1. **Delete unused strings** - 100+ unused string resources
2. **Delete unused colors** - 3 unused color resources
3. **Remove TermuxResultService** from manifest or create it
4. **Fix MainTabScreen padding** - use the scaffold padding parameter

### Medium Effort
1. **Refactor large composables** - Split ChatScreen, SessionListScreen, TabNavHost
2. **Simplify mappers** - Break down complex when statements
3. **Remove unused DAO methods** - Or mark with `@Suppress("unused")`

### Consider Keeping (Future Use)
1. **API methods** - May be needed for future features
2. **DAO CRUD methods** - Standard persistence layer, useful for future

---

## How to Run Analysis

### detekt (one-time)
```bash
java -jar detekt-cli-1.23.8-all.jar \
  --input app/src/main/java \
  --config detekt.yml \
  --report html:detekt-report.html
```

### Android Lint
```bash
./gradlew :app:lint
# Report: app/build/reports/lint-results-debug.html
```

### R8 Dead Code Report
```bash
./gradlew :app:assembleRelease
# Report: app/build/outputs/mapping/release/unused-code.txt
```

---

## Tooling Added

1. **detekt.yml** - Custom configuration for dead code detection
2. **proguard-rules.pro** - Added `-printusage` and `-printseeds` for R8 analysis
3. **detekt-cli-1.23.8-all.jar** - Downloaded for one-time analysis (can delete after)
