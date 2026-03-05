<!-- OPENSPEC:START -->
# OpenSpec Instructions

These instructions are for AI assistants working in this project.

Always open `@/openspec/AGENTS.md` when the request:
- Mentions planning or proposals (words like proposal, spec, change, plan)
- Introduces new capabilities, breaking changes, architecture shifts, or big performance/security work
- Sounds ambiguous and you need the authoritative spec before coding

Use `@/openspec/AGENTS.md` to learn:
- How to create and apply change proposals
- Spec format and conventions
- Project structure and guidelines

Keep this managed block so 'openspec update' can refresh the instructions.

<!-- OPENSPEC:END -->

# Agent instructions

This project uses **tk** (ticket) for issue tracking. Run `tk` to get started.

## Build verification

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
./gradlew :app:compileDebugKotlin
```

## Key source locations

| What | Where |
|------|-------|
| Domain models | `app/src/main/java/dev/blazelight/p4oc/domain/model/` |
| API interface | `app/src/main/java/dev/blazelight/p4oc/core/network/OpenCodeApi.kt` |
| SSE events | `app/src/main/java/dev/blazelight/p4oc/core/network/OpenCodeEventSource.kt` |
| DTOs | `app/src/main/java/dev/blazelight/p4oc/data/remote/dto/` |
| Mappers | `app/src/main/java/dev/blazelight/p4oc/data/remote/mapper/Mappers.kt` |
| Chat UI | `app/src/main/java/dev/blazelight/p4oc/ui/screens/chat/` |
| Terminal | `app/src/main/java/dev/blazelight/p4oc/ui/screens/terminal/` + `terminal/` |
| Theme system | `app/src/main/java/dev/blazelight/p4oc/ui/theme/` |

## Code conventions

- Use `LocalOpenCodeTheme.current` for colors, not `MaterialTheme.colorScheme`
- `MaterialTheme.typography` IS the custom typography and is fine to use
- Use `Spacing.*` and `Sizing.*` tokens, not hardcoded `.dp` values
  - `Spacing.hairline` (1dp) — fine pixel-perfect TUI borders, tiny padding
  - `Sizing.indicatorDot` / `indicatorDotActive` — status dot sizes
  - `Sizing.diffGutterWidth` — line number gutters in diff views
  - `Sizing.panelWidthSm/Md/Lg` — fixed-width columns (80/120/180dp)
  - `Sizing.strokeMd` / `strokeThin` / `dividerThickness` — border/divider widths
  - `Sizing.buttonHeightSm/Md/Lg` — button heights (32/36/44dp)
- Use `TuiShapes` for shapes (all 0dp corners)
- Add `role = Role.Button` / `Role.Tab` to actionable `.clickable` modifiers
- Add meaningful `contentDescription` to functional icons (not decorative ones)
- Add `Modifier.testTag(...)` to key interactive elements for UI testing
- Package: `dev.blazelight.p4oc`

## Skills

| Skill | Invocation | What it does |
|-------|-----------|--------------|
| Play Release | `/p:play-release [version]` | Full Play Store release prep: preflight checks, version bump, changelog, signed AAB/APK build, git tag |

## Quick reference

```bash
tk ready              # Find available work
tk show <id>          # View issue details
tk start <id>         # Claim work
tk close <id>         # Complete work
```
