package dev.blazelight.p4oc.ui.screens.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import dev.blazelight.p4oc.ui.components.chat.hasVisibleUserText

internal enum class InitialTailDecision {
    ScrollToTail,
    KeepRestoredPosition,
    NoContent
}

internal fun previousUserMessageBlockIndex(
    blocks: List<MessageBlock>,
    firstVisibleBlockIndex: Int,
    firstVisibleItemScrollOffset: Int,
): Int? {
    var targetIndex: Int? = null
    if (firstVisibleBlockIndex in 0..blocks.size) {
        val firstVisibleBlock = blocks.getOrNull(firstVisibleBlockIndex)
        val partiallyVisibleUser =
            firstVisibleItemScrollOffset > 0 &&
                firstVisibleBlock is MessageBlock.UserBlock &&
                firstVisibleBlock.message.hasVisibleUserText()
        var index = when {
            partiallyVisibleUser -> firstVisibleBlockIndex
            firstVisibleBlockIndex == blocks.size -> blocks.lastIndex
            else -> firstVisibleBlockIndex - 1
        }

        while (index >= 0 && targetIndex == null) {
            val block = blocks[index]
            if (block is MessageBlock.UserBlock && block.message.hasVisibleUserText()) {
                targetIndex = index
            }
            index--
        }
    }
    return targetIndex
}

internal class ChatScrollRestorationState(
    shouldFollowTail: Boolean = true,
    didInitialTailScroll: Boolean = false,
    hasNewContentWhileAway: Boolean = false,
    showSearch: Boolean = false,
    searchQuery: String = "",
    currentMatchIndex: Int = 0,
) {
    var shouldFollowTail by mutableStateOf(shouldFollowTail)
    var didInitialTailScroll by mutableStateOf(didInitialTailScroll)
    var hasNewContentWhileAway by mutableStateOf(hasNewContentWhileAway)
    var showSearch by mutableStateOf(showSearch)
    var searchQuery by mutableStateOf(searchQuery)
    var currentMatchIndex by mutableIntStateOf(currentMatchIndex)

    fun onScrollSettled(isAtBottom: Boolean) {
        shouldFollowTail = isAtBottom
        if (isAtBottom) {
            hasNewContentWhileAway = false
        }
    }

    /** A user drag has begun; stop following the tail so IME pinning cannot fight the gesture. */
    fun onUserScrollStarted() {
        shouldFollowTail = false
    }

    fun onTailContentChanged(hasRenderableTail: Boolean): Boolean {
        val shouldScrollToTail = didInitialTailScroll && hasRenderableTail && shouldFollowTail
        if (didInitialTailScroll && hasRenderableTail && !shouldFollowTail) {
            hasNewContentWhileAway = true
        }
        return shouldScrollToTail
    }

    fun onJumpToBottom() {
        shouldFollowTail = true
        hasNewContentWhileAway = false
    }

    fun onJumpToPreviousUser() {
        shouldFollowTail = false
    }

    fun shouldPinTailForIme(composerFocused: Boolean, hasRenderableTail: Boolean): Boolean =
        composerFocused && hasRenderableTail && shouldFollowTail

    fun onContentReady(hasRenderableTail: Boolean): InitialTailDecision {
        val decision = when {
            !hasRenderableTail -> InitialTailDecision.NoContent
            didInitialTailScroll -> InitialTailDecision.KeepRestoredPosition
            shouldFollowTail -> InitialTailDecision.ScrollToTail
            else -> InitialTailDecision.KeepRestoredPosition
        }
        if (hasRenderableTail && !didInitialTailScroll) {
            didInitialTailScroll = true
        }
        return decision
    }

    companion object {
        val Saver: Saver<ChatScrollRestorationState, Any> = listSaver(
            save = {
                listOf(
                    it.shouldFollowTail,
                    it.didInitialTailScroll,
                    it.hasNewContentWhileAway,
                    it.showSearch,
                    it.searchQuery,
                    it.currentMatchIndex,
                )
            },
            restore = {
                ChatScrollRestorationState(
                    shouldFollowTail = it[0] as Boolean,
                    didInitialTailScroll = it[1] as Boolean,
                    hasNewContentWhileAway = it[2] as Boolean,
                    showSearch = it[3] as Boolean,
                    searchQuery = it[4] as String,
                    currentMatchIndex = it[5] as Int,
                )
            }
        )
    }
}
