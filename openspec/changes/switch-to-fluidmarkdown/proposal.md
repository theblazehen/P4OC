# Change: Switch to FluidMarkdown for Streaming Chat Rendering

## Why
The current `compose-markdown` library (0.5.4) has rendering issues that degrade the chat experience:
1. **Heading size propagation bug** - Text after headings renders incorrectly large
2. **Poor table support** - Tables don't render nicely, which is common in LLM output
3. **No streaming awareness** - Full re-render on every token update, causing potential jank
4. **Scroll sync issues** - Content height changes unpredictably during streaming, breaking auto-scroll

FluidMarkdown (by AntGroup) is purpose-built for AI chat streaming on mobile, with native Android support, proper table rendering, and incremental update handling.

## What Changes
- Replace `compose-markdown` dependency with FluidMarkdown's Android module (local)
- Create Compose interop wrapper using `AndroidView` for `PrinterMarkDownTextView`
- Adapt Material 3 theme colors to FluidMarkdown's `MarkdownStyles` API
- Update `ChatMessage.kt` to use new streaming-aware markdown component
- Add scroll height change callback integration for auto-scroll coordination
- Remove the `compactMarkdown()` preprocessing hack (FluidMarkdown handles spacing properly)
- Fix scroll trigger to use content height changes instead of just message count

## Impact
- Affected specs: `chat-markdown-rendering` (new capability)
- Affected code:
  - `app/build.gradle.kts` - dependency swap
  - `settings.gradle.kts` - include FluidMarkdown module
  - `ChatMessage.kt` - replace `MarkdownText` with FluidMarkdown wrapper
  - `ChatScreen.kt` - scroll trigger based on height changes
  - New: `StreamingMarkdown.kt` - Compose/View interop component
  - New: `MarkdownStyleMapper.kt` - Material3 → FluidMarkdown style mapping
- **BREAKING**: None (internal rendering change, same visual output intent)

## Dependencies
- FluidMarkdown Android module (Apache 2.0 license) - included as local module
- Markwon library (transitive, already Apache 2.0)
