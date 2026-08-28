package dev.blazelight.p4oc.ui.components.chat

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.data.media.ChatMediaLoadResult
import dev.blazelight.p4oc.data.media.ChatMediaLoader
import dev.blazelight.p4oc.domain.model.Message
import dev.blazelight.p4oc.domain.model.MessageWithParts
import dev.blazelight.p4oc.domain.model.ModelRef
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.model.TokenUsage
import dev.blazelight.p4oc.domain.model.ToolState
import dev.blazelight.p4oc.ui.components.toolwidgets.ToolGroupWidget
import dev.blazelight.p4oc.ui.components.toolwidgets.ToolWidgetState
import dev.blazelight.p4oc.ui.theme.PocketCodeTheme
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class ChatAttachmentPresentationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun attachmentOnlyUserMessageRendersImage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val attachment = imagePart(id = "user-image", messageId = "user-message")

        setChatContent(loadedLoader(attachment.id)) {
            ChatMessage(
                messageWithParts = MessageWithParts(
                    message = userMessage(id = "user-message"),
                    parts = listOf(attachment),
                ),
                onToolApprove = {},
                onToolDeny = {},
                onToolAlways = {},
            )
        }

        waitForTag("chat_attachment_image_user-image")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
            )
            .assertContentDescriptionEquals(
                context.getString(
                    R.string.chat_attachment_open_image,
                    attachment.filename.orEmpty(),
                )
            )
    }

    @Test
    fun topLevelAssistantImageRenders() {
        val attachment = imagePart(id = "assistant-image", messageId = "assistant-message")

        setChatContent(loadedLoader(attachment.id)) {
            AssistantMessages(
                messagesWithParts = listOf(
                    MessageWithParts(
                        message = assistantMessage(id = "assistant-message"),
                        parts = listOf(attachment),
                    ),
                ),
                onToolApprove = {},
                onToolDeny = {},
                onToolAlways = {},
            )
        }

        waitForTag("chat_attachment_image_assistant-image")
            .assertIsDisplayed()
    }

    @Test
    fun completedToolImageRendersInCompactGroup() {
        val attachment = imagePart(id = "tool-image", messageId = "assistant-message")
        val tool = Part.Tool(
            id = "tool-part",
            sessionID = SESSION_ID,
            messageID = "assistant-message",
            callID = "tool-call",
            toolName = "read",
            state = ToolState.Completed(
                input = buildJsonObject {},
                output = "completed",
                title = "Read image",
                startedAt = 1L,
                endedAt = 2L,
                attachments = listOf(attachment),
            ),
        )

        setChatContent(loadedLoader(attachment.id)) {
            ToolGroupWidget(
                tools = listOf(tool),
                defaultState = ToolWidgetState.COMPACT,
                onToolApprove = {},
                onToolDeny = {},
                onToolAlways = {},
            )
        }

        waitForTag("chat_attachment_image_tool-image")
            .assertIsDisplayed()
    }

    @Test
    fun loadedPreviewOpensAndClosesFullscreenViewer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val attachment = imagePart(id = "fullscreen-image", messageId = "assistant-message")

        setChatContent(loadedLoader(attachment.id)) {
            AssistantMessages(
                messagesWithParts = listOf(
                    MessageWithParts(
                        message = assistantMessage(id = "assistant-message"),
                        parts = listOf(attachment),
                    ),
                ),
                onToolApprove = {},
                onToolDeny = {},
                onToolAlways = {},
            )
        }

        waitForTag("chat_attachment_image_fullscreen-image").performClick()
        waitForTag("chat_attachment_fullscreen_fullscreen-image")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            context.getString(
                R.string.chat_attachment_image_description,
                attachment.filename.orEmpty(),
            ),
            useUnmergedTree = true,
        ).assertIsDisplayed()

        val closeButton = composeRule.onNodeWithTag(
            "chat_attachment_close_fullscreen-image",
            useUnmergedTree = true,
        ).assertIsDisplayed()
            .assertHasClickAction()
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
            )
            .assertContentDescriptionEquals(
                context.getString(R.string.chat_attachment_close_preview)
            )
        closeButton.performClick()
        waitForTagToDisappear("chat_attachment_fullscreen_fullscreen-image")
        composeRule.onNodeWithTag(
            "chat_attachment_fullscreen_fullscreen-image",
            useUnmergedTree = true,
        ).assertDoesNotExist()
    }

    @Test
    fun unavailableImageShowsGenericMessageWithoutSourceDetails() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val attachment = imagePart(
            id = "unavailable-image",
            messageId = "assistant-message",
            url = SENSITIVE_URL,
        )

        setChatContent(ChatMediaLoader { ChatMediaLoadResult.Unavailable }) {
            AssistantMessages(
                messagesWithParts = listOf(
                    MessageWithParts(
                        message = assistantMessage(id = "assistant-message"),
                        parts = listOf(attachment),
                    ),
                ),
                onToolApprove = {},
                onToolDeny = {},
                onToolAlways = {},
            )
        }

        waitForTag("chat_attachment_unavailable_unavailable-image")
            .assertIsDisplayed()
            .assertTextEquals(context.getString(R.string.chat_attachment_image_unavailable))
        composeRule.onNodeWithText("super-secret", substring = true).assertDoesNotExist()
    }

    @Test
    fun nonImageAttachmentShowsMetadataWithoutLoadingMedia() {
        val loadInvocations = AtomicInteger()
        val attachment = Part.File(
            id = "document",
            sessionID = SESSION_ID,
            messageID = "assistant-message",
            mime = "application/pdf",
            filename = "release-notes.pdf",
            url = SENSITIVE_URL,
        )

        setChatContent(
            loader = ChatMediaLoader {
                loadInvocations.incrementAndGet()
                ChatMediaLoadResult.Unavailable
            },
        ) {
            ChatAttachment(part = attachment)
        }

        waitForTag("chat_attachment_document").assertIsDisplayed()
        composeRule.onNodeWithText(
            "release-notes.pdf",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            "application/pdf",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.waitForIdle()
        assertEquals(0, loadInvocations.get())
    }

    private fun setChatContent(
        loader: ChatMediaLoader,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            PocketCodeTheme {
                CompositionLocalProvider(LocalChatMediaLoader provides loader) {
                    content()
                }
            }
        }
    }

    private fun waitForTag(tag: String) = composeRule
        .onAllNodesWithTag(tag, useUnmergedTree = true)
        .also { nodes ->
            composeRule.waitUntil(timeoutMillis = ASYNC_TIMEOUT_MILLIS) {
                nodes.fetchSemanticsNodes().isNotEmpty()
            }
        }
        .let {
            composeRule.onNodeWithTag(tag, useUnmergedTree = true)
        }

    private fun waitForTagToDisappear(tag: String) {
        composeRule.waitUntil(timeoutMillis = ASYNC_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }

    private fun loadedLoader(vararg partIds: String) = ChatMediaLoader { part ->
        if (part.id in partIds) {
            ChatMediaLoadResult.Loaded(bytes = TINY_PNG_BYTES, mimeType = "image/png")
        } else {
            ChatMediaLoadResult.Unavailable
        }
    }

    private fun imagePart(
        id: String,
        messageId: String,
        url: String = "data:image/png;base64,ignored-by-fake",
    ) = Part.File(
        id = id,
        sessionID = SESSION_ID,
        messageID = messageId,
        mime = "image/png",
        filename = "$id.png",
        url = url,
    )

    private fun userMessage(id: String) = Message.User(
        id = id,
        sessionID = SESSION_ID,
        createdAt = 1L,
        agent = "build",
        model = ModelRef(providerID = "provider", modelID = "model"),
    )

    private fun assistantMessage(id: String) = Message.Assistant(
        id = id,
        sessionID = SESSION_ID,
        createdAt = 1L,
        completedAt = 2L,
        parentID = "user-message",
        providerID = "provider",
        modelID = "model",
        mode = "build",
        agent = "build",
        cost = 0.0,
        tokens = TokenUsage(input = 0, output = 0),
        finish = "stop",
    )

    private companion object {
        const val SESSION_ID = "session"
        const val ASYNC_TIMEOUT_MILLIS = 5_000L
        const val SENSITIVE_URL = "https://server.example/image.png?token=super-secret"
        val TINY_PNG_BYTES: ByteArray by lazy {
            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
                setPixel(0, 0, Color.WHITE)
            }
            try {
                ByteArrayOutputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                    output.toByteArray()
                }
            } finally {
                bitmap.recycle()
            }
        }
    }
}
