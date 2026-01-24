# Change: Improve Chat Scroll UX During Streaming

## Why
The current chat scroll behavior forces users to the bottom whenever new messages arrive, regardless of whether they want to read earlier content. Modern chat apps (ChatGPT, Claude, etc.) implement smart auto-scroll that respects user intent: auto-scroll when at bottom, stop when user scrolls up, and provide a "jump to bottom" button to re-engage.

## What Changes
- Add "near bottom" detection to determine when auto-scroll should be active
- Disable auto-scroll when user scrolls away from bottom
- Add floating "Jump to Bottom" button when scrolled away during streaming
- Re-enable auto-scroll when user taps button or manually scrolls to bottom
- Show unread indicator on jump button when new content arrives while scrolled away

## Impact
- Affected specs: `chat-scroll-behavior` (new capability)
- Affected code:
  - `ChatScreen.kt` - scroll state management, FAB placement
  - `ChatViewModel.kt` - streaming state exposure
  - New component: `JumpToBottomButton.kt`
