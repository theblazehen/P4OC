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

# Agent Instructions

Ref @FULL_AUTO.md

This project uses **bd** (beads) for issue tracking. Run `bd onboard` to get started.

## Project Documentation

| Document | Purpose |
|----------|---------|
| `IMPLEMENTATION.md` | Project overview, architecture, domain models reference, implementation status |
| `QUESTION_IMPLEMENTATION.md` | Question tool feature implementation details |
| `OPENCODE_OVERVIEW.md` | OpenCode server API reference (conceptual) |

## Key Source Locations

| What | Where |
|------|-------|
| Domain models | `app/src/main/java/com/pocketcode/domain/model/` |
| API interface | `app/src/main/java/com/pocketcode/core/network/OpenCodeApi.kt` |
| SSE events | `app/src/main/java/com/pocketcode/core/network/OpenCodeEventSource.kt` |
| DTOs | `app/src/main/java/com/pocketcode/data/remote/dto/Dtos.kt` |
| Mappers | `app/src/main/java/com/pocketcode/data/remote/mapper/Mappers.kt` |
| Chat UI | `app/src/main/java/com/pocketcode/ui/screens/chat/` |
| Terminal | `app/src/main/java/com/pocketcode/ui/screens/terminal/` + `terminal/` |

## Quick Reference

```bash
bd ready              # Find available work
bd show <id>          # View issue details
bd update <id> --status in_progress  # Claim work
bd close <id>         # Complete work
bd sync               # Sync with git
```
