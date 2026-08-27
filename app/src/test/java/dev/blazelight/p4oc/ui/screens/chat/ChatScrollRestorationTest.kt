package dev.blazelight.p4oc.ui.screens.chat

import dev.blazelight.p4oc.domain.model.Message
import dev.blazelight.p4oc.domain.model.MessageWithParts
import dev.blazelight.p4oc.domain.model.ModelRef
import dev.blazelight.p4oc.domain.model.Part
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatScrollRestorationTest {

    @Test
    fun sameSessionRestoresAwayFromTailWithNewContentInsteadOfForcingTail() {
        val state = ChatScrollRestorationState()

        state.onContentReady(hasRenderableTail = true)
        state.onScrollSettled(isAtBottom = false)
        state.onTailContentChanged(hasRenderableTail = true)

        assertFalse(state.shouldFollowTail)
        assertTrue(state.didInitialTailScroll)
        assertTrue(state.hasNewContentWhileAway)
    }

    @Test
    fun differentSessionsDoNotShareScrollRestorationState() {
        val sessionA = ChatScrollRestorationState()
        val sessionB = ChatScrollRestorationState()

        sessionA.onContentReady(hasRenderableTail = true)
        sessionA.onScrollSettled(isAtBottom = false)
        sessionA.onTailContentChanged(hasRenderableTail = true)

        assertTrue(sessionB.shouldFollowTail)
        assertFalse(sessionB.didInitialTailScroll)
        assertFalse(sessionB.hasNewContentWhileAway)
    }

    @Test
    fun searchNavigationDisablesFollowTailAndRestoresForSameSessionOnly() {
        val state = ChatScrollRestorationState()
        val otherSession = ChatScrollRestorationState()

        state.onContentReady(hasRenderableTail = true)
        state.shouldFollowTail = false
        state.onTailContentChanged(hasRenderableTail = true)

        assertFalse(state.shouldFollowTail)
        assertTrue(state.hasNewContentWhileAway)
        assertTrue(otherSession.shouldFollowTail)
        assertFalse(otherSession.hasNewContentWhileAway)
    }

    @Test
    fun returningToOlderPositionDoesNotForceTailOnNextContentChange() {
        val state = ChatScrollRestorationState()

        state.onContentReady(hasRenderableTail = true)
        state.onScrollSettled(isAtBottom = false)
        state.onTailContentChanged(hasRenderableTail = true)

        assertFalse(state.shouldFollowTail)
        assertTrue(state.hasNewContentWhileAway)
    }

    @Test
    fun jumpToBottomResumesFollowTailAndClearsNewContentAffordance() {
        val state = ChatScrollRestorationState()

        state.onContentReady(hasRenderableTail = true)
        state.onScrollSettled(isAtBottom = false)
        state.onTailContentChanged(hasRenderableTail = true)
        state.onJumpToBottom()

        assertTrue(state.shouldFollowTail)
        assertFalse(state.hasNewContentWhileAway)
    }

    @Test
    fun assistantAnchorFindsNearestPrecedingUserBlock() {
        val blocks = listOf(
            userBlock("user-1"),
            assistantBlock(),
            userBlock("user-2"),
            assistantBlock(),
        )

        assertEquals(
            2,
            previousUserMessageBlockIndex(
                blocks = blocks,
                firstVisibleBlockIndex = 3,
                firstVisibleItemScrollOffset = 400,
            ),
        )
    }

    @Test
    fun partiallyScrolledUserSnapsToCurrentUserAndAlignedUserMovesEarlier() {
        val blocks = listOf(
            userBlock("user-1"),
            assistantBlock(),
            userBlock("user-2"),
        )

        assertEquals(
            2,
            previousUserMessageBlockIndex(
                blocks = blocks,
                firstVisibleBlockIndex = 2,
                firstVisibleItemScrollOffset = 1,
            ),
        )
        assertEquals(
            0,
            previousUserMessageBlockIndex(
                blocks = blocks,
                firstVisibleBlockIndex = 2,
                firstVisibleItemScrollOffset = 0,
            ),
        )
    }

    @Test
    fun repeatedNavigationWalksBackwardThroughUserBlocks() {
        val blocks = listOf(
            userBlock("user-1"),
            assistantBlock(),
            userBlock("user-2"),
            assistantBlock(),
            userBlock("user-3"),
        )

        val newest = checkNotNull(previousUserMessageBlockIndex(blocks, blocks.size, 0))
        val middle = checkNotNull(previousUserMessageBlockIndex(blocks, newest, 0))
        val oldest = checkNotNull(previousUserMessageBlockIndex(blocks, middle, 0))

        assertEquals(4, newest)
        assertEquals(2, middle)
        assertEquals(0, oldest)
        assertNull(previousUserMessageBlockIndex(blocks, oldest, 0))
    }

    @Test
    fun virtualTrailingAnchorFindsLatestUserBeforePendingRows() {
        val blocks = listOf(
            userBlock("user-1"),
            assistantBlock(),
        )

        assertEquals(
            0,
            previousUserMessageBlockIndex(
                blocks = blocks,
                firstVisibleBlockIndex = blocks.size,
                firstVisibleItemScrollOffset = 37,
            ),
        )
    }

    @Test
    fun partiallyScrolledInvisibleUserSkipsToPreviousVisibleUser() {
        val blocks = listOf(
            userBlock("user-1"),
            userBlock("blank-user", text = " \n\t"),
            userBlock("ignored-user", ignored = true),
            userBlock("synthetic-user", synthetic = true),
        )

        assertEquals(
            0,
            previousUserMessageBlockIndex(
                blocks = blocks,
                firstVisibleBlockIndex = 3,
                firstVisibleItemScrollOffset = 1,
            ),
        )
    }

    @Test
    fun virtualTrailingAnchorSkipsEveryKindOfInvisibleUserRecord() {
        val blocks = listOf(
            userBlock("user-1"),
            assistantBlock(),
            userBlock("synthetic-user", synthetic = true),
            userBlock("ignored-user", ignored = true),
            userBlock("blank-user", text = "   "),
        )

        assertEquals(
            0,
            previousUserMessageBlockIndex(
                blocks = blocks,
                firstVisibleBlockIndex = blocks.size,
                firstVisibleItemScrollOffset = 37,
            ),
        )
        assertNull(
            previousUserMessageBlockIndex(
                blocks = blocks.drop(2),
                firstVisibleBlockIndex = blocks.size - 2,
                firstVisibleItemScrollOffset = 0,
            ),
        )
    }

    @Test
    fun invalidHeaderAndNoUserAnchorsHaveNoTarget() {
        val blocksWithUser = listOf(userBlock("user-1"), assistantBlock())
        val assistantOnly = listOf(assistantBlock(), assistantBlock())

        assertNull(
            previousUserMessageBlockIndex(
                blocksWithUser,
                firstVisibleBlockIndex = -1,
                firstVisibleItemScrollOffset = 0,
            ),
        )
        assertNull(
            previousUserMessageBlockIndex(
                blocksWithUser,
                firstVisibleBlockIndex = blocksWithUser.size + 1,
                firstVisibleItemScrollOffset = 0,
            ),
        )
        assertNull(previousUserMessageBlockIndex(assistantOnly, assistantOnly.size, 0))
        assertNull(
            previousUserMessageBlockIndex(
                emptyList(),
                firstVisibleBlockIndex = 0,
                firstVisibleItemScrollOffset = 0,
            ),
        )
    }

    @Test
    fun jumpToPreviousUserDisablesTailFollowingWithoutClearingNewContent() {
        val state = ChatScrollRestorationState(
            shouldFollowTail = true,
            hasNewContentWhileAway = true,
        )

        state.onJumpToPreviousUser()

        assertFalse(state.shouldFollowTail)
        assertTrue(state.hasNewContentWhileAway)
    }

    @Test
    fun followingTailPinsWhenComposerFocusedAndContentPresent() {
        val state = ChatScrollRestorationState()
        state.onContentReady(hasRenderableTail = true)

        assertTrue(state.shouldPinTailForIme(composerFocused = true, hasRenderableTail = true))
    }

    @Test
    fun scrolledAwayReaderIsNeverPinnedByIme() {
        val state = ChatScrollRestorationState()
        state.onContentReady(hasRenderableTail = true)
        state.onScrollSettled(isAtBottom = false)

        assertFalse(state.shouldPinTailForIme(composerFocused = true, hasRenderableTail = true))
    }

    @Test
    fun userDragStartDisablesImePinAndSettledAtBottomResumesFollowing() {
        val state = ChatScrollRestorationState()
        state.onContentReady(hasRenderableTail = true)

        assertTrue(state.shouldPinTailForIme(composerFocused = true, hasRenderableTail = true))

        state.onUserScrollStarted()
        assertFalse(state.shouldPinTailForIme(composerFocused = true, hasRenderableTail = true))

        state.onScrollSettled(isAtBottom = true)
        assertTrue(state.shouldPinTailForIme(composerFocused = true, hasRenderableTail = true))
    }

    @Test
    fun nonComposerFocusDoesNotPinTail() {
        val state = ChatScrollRestorationState()
        state.onContentReady(hasRenderableTail = true)

        assertFalse(state.shouldPinTailForIme(composerFocused = false, hasRenderableTail = true))
    }

    @Test
    fun freshFollowingTailWithEmptyTranscriptIsNeverPinnedByIme() {
        val state = ChatScrollRestorationState()

        assertFalse(state.shouldPinTailForIme(composerFocused = true, hasRenderableTail = false))
    }

    @Test
    fun contentNotReadyDoesNotConsumeInitialTailRestoration() {
        val state = ChatScrollRestorationState()

        assertEquals(InitialTailDecision.NoContent, state.onContentReady(hasRenderableTail = false))
        assertFalse(state.didInitialTailScroll)

        assertEquals(InitialTailDecision.ScrollToTail, state.onContentReady(hasRenderableTail = true))
        assertTrue(state.didInitialTailScroll)
    }

    @Test
    fun initialTailRestorationHappensOnceAndDoesNotOverrideRestoredAwayPosition() {
        val state = ChatScrollRestorationState()

        assertEquals(InitialTailDecision.ScrollToTail, state.onContentReady(hasRenderableTail = true))
        state.onScrollSettled(isAtBottom = false)

        assertEquals(InitialTailDecision.KeepRestoredPosition, state.onContentReady(hasRenderableTail = true))
        assertFalse(state.shouldFollowTail)
    }

    private fun userBlock(
        id: String,
        text: String = id,
        synthetic: Boolean = false,
        ignored: Boolean = false,
    ): MessageBlock.UserBlock = MessageBlock.UserBlock(
        message = MessageWithParts(
            message = Message.User(
                id = id,
                sessionID = "session-1",
                createdAt = 1L,
                agent = "general",
                model = ModelRef(providerID = "provider-1", modelID = "model-1"),
            ),
            parts = listOf(
                Part.Text(
                    id = "part-$id",
                    sessionID = "session-1",
                    messageID = id,
                    text = text,
                    synthetic = synthetic,
                    ignored = ignored,
                ),
            ),
        ),
    )

    private fun assistantBlock(): MessageBlock.AssistantBlock =
        MessageBlock.AssistantBlock(messages = emptyList())
}
