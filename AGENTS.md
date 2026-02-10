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

This project uses **bd** (beads) for issue tracking. Run `bd onboard` to get started.

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
| DTOs | `app/src/main/java/dev/blazelight/p4oc/data/remote/dto/Dtos.kt` |
| Mappers | `app/src/main/java/dev/blazelight/p4oc/data/remote/mapper/Mappers.kt` |
| Chat UI | `app/src/main/java/dev/blazelight/p4oc/ui/screens/chat/` |
| Terminal | `app/src/main/java/dev/blazelight/p4oc/ui/screens/terminal/` + `terminal/` |
| Theme system | `app/src/main/java/dev/blazelight/p4oc/ui/theme/` |

## Code conventions

- Use `LocalOpenCodeTheme.current` for colors, not `MaterialTheme.colorScheme`
- Use `Spacing.*` and `Sizing.*` tokens, not hardcoded `.dp` values
- Use `TuiShapes` for shapes (all 0dp corners)
- Package: `dev.blazelight.p4oc`

## Quick reference

```bash
bd ready              # Find available work
bd show <id>          # View issue details
bd update <id> --status in_progress  # Claim work
bd close <id>         # Complete work
bd sync               # Sync with git
```
