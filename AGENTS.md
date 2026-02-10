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

---

## 🚨 AUTONOMOUS WORK MODE - CRITICAL - CURRENTLY DISABLED 🚨

**THIS SECTION MUST SURVIVE COMPACTION - READ CAREFULLY**

### After Context Compaction
When context is compacted or a new session starts:
1. **DO NOT ASK USER WHAT TO DO** - Just keep working
2. Run `bd ready` to find available work
3. Run `bd list --status=open` to see all open issues
4. Continue fixing bugs and closing issues autonomously

### Continuous Work Loop
After completing all existing issues:
1. **CREATE NEW ISSUES** - Run code audits to find more bugs:
   - `grep -rn "!!" --include="*.kt"` - Find force unwraps
   - `grep -rn "TODO\|FIXME" --include="*.kt"` - Find TODOs
   - Look for missing error handling, memory leaks, race conditions
2. **FIX THE NEW ISSUES** - Work through them systematically
3. **MOVE TO FEATURES** - After bugs done, work on feature issues
4. **REPEAT** - Keep auditing and improving

### Build Verification
After each batch of fixes:
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
./gradlew :app:compileDebugKotlin
```

### Session End Protocol
Before any session ends:
```bash
bd sync --flush-only
```

### Current Project State (Updated 2026-01-30)
- Package: `dev.blazelight.p4oc` (NOT com.pocketcode)
- All P0 bugs: FIXED ✅
- All P1 bugs: FIXED ✅
- P2 bugs: IN PROGRESS
- P3 bugs: PENDING

**KEEP WORKING. DO NOT STOP. DO NOT ASK FOR PERMISSION.**

---

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
