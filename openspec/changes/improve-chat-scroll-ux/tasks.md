# Tasks: Improve Chat Scroll UX

## 1. Core Scroll Logic
- [x] 1.1 Add `isAtBottom` derived state using `LazyListState.layoutInfo`
- [x] 1.2 Add `userScrolledAway` state variable
- [x] 1.3 Detect user scroll gesture and set `userScrolledAway = true` when scrolling up
- [x] 1.4 Modify `LaunchedEffect` to only auto-scroll when `!userScrolledAway`
- [x] 1.5 Reset `userScrolledAway = false` when user scrolls back to bottom manually

## 2. Jump to Bottom Button
- [x] 2.1 Create `JumpToBottomButton.kt` composable with down-arrow icon
- [x] 2.2 Add enter/exit animation (fade + slide up)
- [x] 2.3 Position button above input bar, right-aligned
- [x] 2.4 Show button only when `userScrolledAway && isStreaming`
- [x] 2.5 On click: scroll to bottom and set `userScrolledAway = false`

## 3. Visual Polish
- [x] 3.1 Add subtle badge/glow to button when new content arrived while away
- [x] 3.2 Ensure no collision with TodoTrackerFab
- [x] 3.3 Match button styling to Material 3 FAB guidelines

## 4. Edge Cases
- [x] 4.1 Handle rapid message bursts without jank (consider `scrollToItem` vs `animateScrollToItem`)
- [x] 4.2 Handle keyboard show/hide without breaking scroll state
- [x] 4.3 Test with very long messages (code blocks) and short messages

## 5. Validation
- [x] 5.1 Manual test: send message, scroll up during response, verify auto-scroll stops
- [x] 5.2 Manual test: tap jump button, verify scroll to bottom and auto-scroll resumes
- [x] 5.3 Manual test: manually scroll to bottom, verify auto-scroll resumes
- [x] 5.4 Test on slow device/emulator for performance
