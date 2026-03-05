# Play Store Release Preparation

Skill invoked via `/p:play-release [version]`. Produces signed AAB + APK artifacts ready for manual Play Console upload.

## Constraints

- **NEVER** push to remote — all git operations are local only
- **NEVER** upload to Play Console — just build artifacts
- **NEVER** skip a failing step — stop and report the failure
- **NEVER** log passwords or signing secrets
- Prefix every `./gradlew` command with `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk`

---

## Step 1 — Resolve Target Version

If the user passed a version argument (e.g. `/p:play-release 0.5.0`), use it.
Otherwise, read the current `versionName` from `app/build.gradle.kts` and **ask the user** what the new version should be. Do not guess.

Determine the new `versionCode` by reading the current value and adding 1.

Store for later:
- `NEW_VERSION_NAME` — e.g. `0.5.0`
- `NEW_VERSION_CODE` — e.g. `7`
- `TAG_NAME` — `v${NEW_VERSION_NAME}` (e.g. `v0.5.0`)

Verify the tag does not already exist:
```bash
git tag -l "$TAG_NAME"
```
If it exists, stop and tell the user.

---

## Step 2 — Pre-flight Checks

Run all checks. Stop on first failure.

### 2a. Clean git state
```bash
git status --porcelain
```
If output is non-empty, stop. Tell the user to commit or stash changes first.

### 2b. Theme violation check
```bash
./scripts/check_theme_violations.sh
```
Must exit 0.

### 2c. Unit tests
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
./gradlew test
```
Must exit 0.

### 2d. Smoke compile
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
./gradlew :app:compileDebugKotlin
```
Must exit 0. Fast sanity check before the longer release build.

### 2e. Signing config

Read `local.properties` and confirm all four properties are present and non-empty:
- `RELEASE_STORE_FILE`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

Also verify the keystore file at the `RELEASE_STORE_FILE` path actually exists on disk.

**Do NOT print the values** — just confirm presence.

---

## Step 3 — Version Bump

Edit `app/build.gradle.kts`. In the `defaultConfig` block, update exactly two lines:

```
versionCode = $NEW_VERSION_CODE
versionName = "$NEW_VERSION_NAME"
```

Verify the edit by reading the file back and confirming the new values.

---

## Step 4 — Changelog

### 4a. Gather commits since last tag

```bash
LAST_TAG=$(git describe --tags --abbrev=0)
git log "${LAST_TAG}..HEAD" --oneline --no-merges
```

### 4b. Play Store "What's New" (max 500 chars)

Draft a concise user-facing blurb. Rules:
- Max 500 characters (hard Play Store limit)
- No commit hashes, no technical jargon
- Focus on user-visible changes: new features, fixes, improvements
- Use bullet points with `•` prefix
- **Show to user for approval** before proceeding

### 4c. GitHub release notes (longer form)

Draft a longer changelog with:
- Section headers: `### Added`, `### Changed`, `### Fixed` (skip empty sections)
- One line per meaningful change
- "Full Changelog" compare link at the bottom:
  ```
  **Full Changelog**: https://github.com/theblazehen/P4OC/compare/${LAST_TAG}...v${NEW_VERSION_NAME}
  ```

Save GitHub notes to `RELEASE_NOTES.md` in repo root (scratch file — do NOT git-add).

---

## Step 5 — Build Artifacts

### 5a. Clean previous build outputs
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
./gradlew clean
```

### 5b. Play Store AAB (required)
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
./gradlew :app:bundleRelease
```
Expected: `app/build/outputs/bundle/release/app-release.aab`

### 5c. Signed release APK (required)
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
./gradlew :app:assembleRelease
```
Expected: `app/build/outputs/apk/release/app-release.apk`

### 5d. GitHub release APK (optional — ask user)

Ask: "Also build the debug-signed GitHub APK?" If yes:
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
./gradlew :app:assembleGithubRelease
```
Expected: `app/build/outputs/apk/githubRelease/app-githubRelease.apk`

### 5e. Verify artifacts

For each built artifact, confirm the file exists and print its size:
```bash
ls -lh app/build/outputs/bundle/release/app-release.aab
ls -lh app/build/outputs/apk/release/app-release.apk
ls -lh app/build/outputs/apk/githubRelease/app-githubRelease.apk  # if built
```

### 5f. Archive R8 mapping file

Critical for deobfuscating Play Console crash reports:
```bash
mkdir -p mappings
cp app/build/outputs/mapping/release/mapping.txt "mappings/mapping-v${NEW_VERSION_NAME}.txt"
```
Remind user to also upload mapping.txt to Play Console when submitting the release.

---

## Step 6 — Git Commit + Tag

### 6a. Stage files
```bash
git add app/build.gradle.kts
git add "mappings/mapping-v${NEW_VERSION_NAME}.txt"
```
Do NOT stage `RELEASE_NOTES.md`.

### 6b. Commit
```bash
git commit -m "release: v${NEW_VERSION_NAME} (versionCode ${NEW_VERSION_CODE})"
```

### 6c. Create annotated tag
```bash
git tag -a "v${NEW_VERSION_NAME}" -m "Release ${NEW_VERSION_NAME}"
```

### 6d. Verify
```bash
git log --oneline -1
git tag -l "v${NEW_VERSION_NAME}"
```

---

## Step 7 — Summary

Print:

```
═══════════════════════════════════════════════
  Release v${NEW_VERSION_NAME} prepared
═══════════════════════════════════════════════

  Version:     ${NEW_VERSION_NAME} (code ${NEW_VERSION_CODE})
  Tag:         v${NEW_VERSION_NAME} (local only)

  Artifacts:
    AAB:       app/build/outputs/bundle/release/app-release.aab (SIZE)
    APK:       app/build/outputs/apk/release/app-release.apk (SIZE)
    GH APK:    app/build/outputs/apk/githubRelease/app-githubRelease.apk (SIZE)  [if built]
    Mapping:   mappings/mapping-v${NEW_VERSION_NAME}.txt

  Next steps (manual):
    1. git push origin main && git push origin v${NEW_VERSION_NAME}
    2. Upload AAB to Play Console → Production track
    3. Upload mapping.txt to Play Console → App bundle explorer
    4. Paste "What's New" into Play Console release notes
    5. Create GitHub Release for tag v${NEW_VERSION_NAME}
       - Paste RELEASE_NOTES.md content as body
       - Attach APK artifacts
    6. rm RELEASE_NOTES.md
═══════════════════════════════════════════════
```

---

## Error Recovery

| Failure | Action |
|---------|--------|
| Dirty working tree | List dirty files. Let user decide to stash/commit. |
| Tests fail | Stop. Show which tests failed. Do not proceed. |
| Theme violations | Stop. Show violations. Do not proceed. |
| Build fails | Print Gradle error. Check signing vs code issue. Do not proceed. |
| Tag already exists | Stop. Ask user for a different version. |
| Signing props missing | Tell user exactly which `RELEASE_*` property is missing from `local.properties`. |
| Keystore file not found | Tell user the path from `RELEASE_STORE_FILE` doesn't exist on disk. |

---

## Quick Reference (manual copy-paste)

```bash
# Pre-flight
git status --porcelain
./scripts/check_theme_violations.sh
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk && ./gradlew test
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk && ./gradlew :app:compileDebugKotlin

# Build
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk && ./gradlew clean
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk && ./gradlew :app:bundleRelease
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk && ./gradlew :app:assembleRelease
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk && ./gradlew :app:assembleGithubRelease  # optional

# Verify
ls -lh app/build/outputs/bundle/release/app-release.aab
ls -lh app/build/outputs/apk/release/app-release.apk
ls -lh app/build/outputs/apk/githubRelease/app-githubRelease.apk
```
