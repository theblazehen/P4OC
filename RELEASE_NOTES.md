# OpenCode Android v0.9.0

### Added
- **Seeded server discovery** — recently used and typed server URLs are now seeded into discovery for faster reconnects
- **Unified connect probe and session prefetch** — session availability is prefetched through shared deferred work to reduce connection latency
- **Self-signed TLS toggle** — per-server option to trust self-signed certificates when connecting to private deployments
- **Session data cache** — session/project data is cached to reduce duplicate fetches and improve screen transitions
- **Fallback theme system** — cached fallback theme loads while the full theme initializes asynchronously

### Changed
- **Server URL normalization** — connect URLs are normalized more consistently while preserving trusted TLS state
- **Connection plumbing** — shared `ConnectionPool`, improved predictive back handling, and nullable VCS branch support
- **CI hardening** — GitHub Actions now runs Detekt, `lintDebug`, compile checks, tests, and uploads a debug APK artifact
- **Session/tab navigation** — routing and session loading paths were simplified around shared prefetch behavior

### Fixed
- **Explicit server path handling** — manually entered server paths are preserved instead of being stripped during connect
- **TLS trust seeding regression** — seeded servers keep their self-signed trust configuration after normalization
- **Bug sweep** — 9 miscellaneous fixes including tab routing cleanup and credential migration removal

**Full Changelog**: https://github.com/theblazehen/P4OC/compare/v0.8.0...v0.9.0
