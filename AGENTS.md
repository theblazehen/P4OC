# Agent Instructions

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

## Landing the Plane (Session Completion)

**When ending a work session**, you MUST complete ALL steps below. Work is NOT complete until `git push` succeeds.

**MANDATORY WORKFLOW:**

1. **File issues for remaining work** - Create issues for anything that needs follow-up
2. **Run quality gates** (if code changed) - Tests, linters, builds
3. **Update issue status** - Close finished work, update in-progress items
4. **PUSH TO REMOTE** - This is MANDATORY:
   ```bash
   git pull --rebase
   bd sync
   git push
   git status  # MUST show "up to date with origin"
   ```
5. **Clean up** - Clear stashes, prune remote branches
6. **Verify** - All changes committed AND pushed
7. **Hand off** - Provide context for next session

**CRITICAL RULES:**
- Work is NOT complete until `git push` succeeds
- NEVER stop before pushing - that leaves work stranded locally
- NEVER say "ready to push when you are" - YOU must push
- If push fails, resolve and retry until it succeeds

