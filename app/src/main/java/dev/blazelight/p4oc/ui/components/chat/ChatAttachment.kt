package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.data.media.ChatMediaLoadResult
import dev.blazelight.p4oc.data.media.ChatMediaLoader
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.ui.components.TuiLoadingIndicator
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.TuiShapes
import kotlinx.coroutines.CancellationException

val LocalChatMediaLoader = staticCompositionLocalOf<ChatMediaLoader> {
    ChatMediaLoader { ChatMediaLoadResult.Unavailable }
}

@Composable
@Suppress("FunctionNaming")
fun ChatAttachmentList(
    parts: List<Part.File>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        parts.forEach { part ->
            ChatAttachment(part = part)
        }
    }
}

@Composable
@Suppress("FunctionNaming")
fun ChatAttachment(
    part: Part.File,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOpenCodeTheme.current
    val filename = part.filename?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.chat_attachment_unnamed)
    val mimeType = part.mime.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.chat_attachment_unknown_mime)
    val isImage = part.mime.trim().startsWith(prefix = "image/", ignoreCase = true)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("chat_attachment_${part.id}"),
        color = theme.backgroundPanel,
        shape = TuiShapes.extraSmall,
        border = BorderStroke(Sizing.strokeThin, theme.border),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            AttachmentMetadata(filename = filename, mimeType = mimeType)
            if (isImage) {
                ChatImageAttachment(part = part, filename = filename)
            }
        }
    }
}

@Composable
@Suppress("FunctionNaming")
private fun AttachmentMetadata(filename: String, mimeType: String) {
    val theme = LocalOpenCodeTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.hairline)) {
        Text(
            text = filename,
            style = MaterialTheme.typography.labelMedium,
            color = theme.text,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = mimeType,
            style = MaterialTheme.typography.labelSmall,
            color = theme.textMuted,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
@Suppress("FunctionNaming")
private fun ChatImageAttachment(part: Part.File, filename: String) {
    val loader = LocalChatMediaLoader.current
    val loadState = remember(loader, part) {
        mutableStateOf<ChatMediaLoadResult?>(null)
    }

    LaunchedEffect(loader, part) {
        loadState.value = try {
            loader.load(part)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            ChatMediaLoadResult.Unavailable
        }
    }

    when (val result = loadState.value) {
        null -> AttachmentLoading(filename = filename)
        ChatMediaLoadResult.Unavailable -> AttachmentUnavailable(partId = part.id)
        is ChatMediaLoadResult.Loaded -> LoadedImagePreview(
            part = part,
            filename = filename,
            loaded = result,
        )
    }
}

@Composable
@Suppress("FunctionNaming")
private fun AttachmentLoading(filename: String, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.chat_attachment_loading, filename)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(
                min = Sizing.listItemHeightLg,
                max = Sizing.chatAttachmentPreviewMaxHeight,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        TuiLoadingIndicator(text = description)
    }
}

@Composable
@Suppress("FunctionNaming")
private fun AttachmentUnavailable(partId: String) {
    val theme = LocalOpenCodeTheme.current
    Text(
        text = stringResource(R.string.chat_attachment_image_unavailable),
        style = MaterialTheme.typography.labelSmall,
        color = theme.error,
        modifier = Modifier.testTag("chat_attachment_unavailable_$partId"),
    )
}

@Composable
@Suppress("FunctionNaming")
private fun LoadedImagePreview(
    part: Part.File,
    filename: String,
    loaded: ChatMediaLoadResult.Loaded,
) {
    var imageDecoded by remember(part, loaded) { mutableStateOf(false) }
    var imageDecodeFailed by remember(part, loaded) { mutableStateOf(false) }
    var showFullscreen by remember(part, loaded) { mutableStateOf(false) }
    val imageDescription = stringResource(R.string.chat_attachment_image_description, filename)
    val openDescription = stringResource(R.string.chat_attachment_open_image, filename)

    if (imageDecodeFailed) {
        AttachmentUnavailable(partId = part.id)
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(
                    min = Sizing.listItemHeightLg,
                    max = Sizing.chatAttachmentPreviewMaxHeight,
                ),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = loaded.bytes,
                contentDescription = if (imageDecoded) openDescription else imageDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = Sizing.chatAttachmentPreviewMaxHeight)
                    .then(
                        if (imageDecoded) {
                            Modifier
                                .testTag("chat_attachment_image_${part.id}")
                                .clickable(role = Role.Button) { showFullscreen = true }
                        } else {
                            Modifier
                        }
                    ),
                onSuccess = {
                    imageDecoded = true
                    imageDecodeFailed = false
                },
                onError = {
                    imageDecoded = false
                    imageDecodeFailed = true
                },
            )
            if (!imageDecoded) {
                AttachmentLoading(filename = filename, modifier = Modifier.fillMaxSize())
            }
        }
    }

    if (showFullscreen && imageDecoded && !imageDecodeFailed) {
        FullscreenAttachmentImage(
            partId = part.id,
            filename = filename,
            bytes = loaded.bytes,
            onDismiss = { showFullscreen = false },
        )
    }
}

@Composable
@Suppress("FunctionNaming")
private fun FullscreenAttachmentImage(
    partId: String,
    filename: String,
    bytes: ByteArray,
    onDismiss: () -> Unit,
) {
    val theme = LocalOpenCodeTheme.current
    val imageDescription = stringResource(R.string.chat_attachment_image_description, filename)
    val closeDescription = stringResource(R.string.chat_attachment_close_preview)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("chat_attachment_fullscreen_$partId"),
            color = theme.background,
            shape = TuiShapes.extraLarge,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = bytes,
                    contentDescription = imageDescription,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                FullscreenAttachmentCloseButton(
                    partId = partId,
                    closeDescription = closeDescription,
                    onDismiss = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }
    }
}

@Composable
@Suppress("FunctionNaming")
private fun FullscreenAttachmentCloseButton(
    partId: String,
    closeDescription: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOpenCodeTheme.current
    IconButton(
        onClick = onDismiss,
        modifier = modifier
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.End
                )
            )
            .padding(Spacing.sm)
            .size(Sizing.iconButtonLg)
            .background(theme.backgroundPanel, TuiShapes.extraSmall)
            .testTag("chat_attachment_close_$partId")
            .semantics { contentDescription = closeDescription },
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            tint = theme.text,
        )
    }
}
