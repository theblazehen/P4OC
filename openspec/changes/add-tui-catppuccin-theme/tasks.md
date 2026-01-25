## 1. Catppuccin Color System
- [x] 1.1 Create `ui/theme/CatppuccinColors.kt` with all 4 flavor definitions (Mocha, Macchiato, Frappé, Latte)
- [x] 1.2 Update `ui/theme/Color.kt` to use Catppuccin Mocha colors
- [x] 1.3 Update `ui/theme/Theme.kt` to map Catppuccin → Material3 color scheme

## 2. Typography & Density
- [x] 2.1 Update `ui/theme/Typography.kt` to use `FontFamily.Monospace` for all text styles
- [x] 2.2 Reduce line heights (bodyLarge: 18sp, bodyMedium: 16sp, bodySmall: 14sp)
- [ ] 2.3 Create `ui/theme/TuiDimens.kt` with compact spacing constants (deferred - inline values used)

## 3. Compact Tool Call Display
- [x] 3.1 Create `ui/components/chat/CollapsedToolSummary.kt` with one-liner tool summary
- [x] 3.2 Add `ToolChip` composable with status icons (✓ ⟳ ✗ ○)
- [x] 3.3 Add `CompactToolRow` composable for expanded view
- [x] 3.4 Update `ChatMessage.kt` to use collapsed/expanded tool display
- [x] 3.5 Reduce Allow/Deny button padding in `CollapsedToolSummary.kt`

## 4. Message Display Redesign
- [x] 4.1 Remove header row (icon + "You"/"Assistant") from `UserMessage`
- [x] 4.2 Add Surface2 background + Mauve left border to user messages
- [x] 4.3 Remove header row from `AssistantMessage` (keep token info aligned right)
- [x] 4.4 Remove `HorizontalDivider` between messages

## 5. Markdown Table Styling
- [x] 5.1 Update `MarkdownStyleMapper.kt` to configure TablePlugin with dark theme colors
- [x] 5.2 Set table border color to Surface2
- [x] 5.3 Set row backgrounds to Base (body) / Surface0 (header)

## 6. Global Density Pass
- [x] 6.1 Reduce vertical padding in `ChatMessage.kt` (2dp → 1dp)
- [x] 6.2 Reduce spacing in `ChatScreen.kt` LazyColumn (4dp → 1dp)
- [x] 6.3 Change `RoundedCornerShape(8.dp)` → `RoundedCornerShape(0.dp)` throughout
- [x] 6.4 Reduce padding in tool components (12dp → 6dp)
- [x] 6.5 Reduce padding in reasoning/file/patch parts

## 7. Validation
- [x] 7.1 Build and test on device
- [ ] 7.2 Verify streaming markdown still works (awaiting user test)
- [ ] 7.3 Verify tool approval flow works (awaiting user test)
- [ ] 7.4 Verify scroll behavior unchanged (awaiting user test)
