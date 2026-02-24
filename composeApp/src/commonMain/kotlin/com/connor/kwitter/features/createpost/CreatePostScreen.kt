package com.connor.kwitter.features.createpost

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.connor.kwitter.core.media.MediaThumbnailImage
import com.connor.kwitter.core.media.SelectedMedia
import com.connor.kwitter.core.media.rememberMediaPickerLauncher
import com.connor.kwitter.core.result.errorOrNull
import com.connor.kwitter.core.theme.KwitterTheme
import com.connor.kwitter.core.ui.ErrorStateCard
import com.connor.kwitter.core.ui.GlassTopBar
import com.connor.kwitter.core.ui.GlassTopBarIconContentColor
import com.connor.kwitter.core.ui.GlassTopBarIconButton
import com.connor.kwitter.core.ui.GlassTopBarInnerIconSize
import com.connor.kwitter.core.ui.GlassTopBarTitle
import com.connor.kwitter.core.util.resolveBackendUrl
import com.connor.kwitter.features.glass.NativeTopBarButtons
import com.connor.kwitter.features.glass.NativeTopBarModel
import com.connor.kwitter.features.glass.NativeTopBarSlot
import com.connor.kwitter.features.glass.PublishNativeTopBar
import kwitter.composeapp.generated.resources.Res
import kwitter.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

private const val MAX_POST_LENGTH = 280
private const val MAX_MEDIA_COUNT = 4
private const val PREVIEW_POST_ID_LENGTH = 8

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(
    state: CreatePostUiState,
    useNativeTopBar: Boolean = false,
    onNativeTopBarModel: (NativeTopBarModel) -> Unit = {},
    onAction: (CreatePostIntent) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val isReply = state.parentId != null
    val nativeTopTitle = if (isReply) {
        stringResource(Res.string.create_post_reply_title)
    } else {
        stringResource(Res.string.create_post_title)
    }
    val nativeTopSubtitle = if (isReply) {
        stringResource(Res.string.create_post_replying_to)
    } else {
        null
    }
    val errorMessage = state.submitResult.errorOrNull()
    val textProgress = (state.content.length.toFloat() / MAX_POST_LENGTH.toFloat()).coerceIn(0f, 1f)
    val canSubmit = state.content.isNotBlank() && !state.isUploading && !state.isLoading

    val launchPicker = rememberMediaPickerLauncher { media ->
        if (media.isNotEmpty()) {
            onAction(CreatePostAction.MediaSelected(media))
        }
    }

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            onAction(CreatePostNavAction.OnPostCreated)
        }
    }

    PublishNativeTopBar(
        onNativeTopBarModel,
        NativeTopBarModel.Title(
            title = nativeTopTitle,
            subtitle = nativeTopSubtitle,
            leadingButton = NativeTopBarButtons.close()
        )
    )

        Scaffold(
        topBar = {
            NativeTopBarSlot(nativeTopBarEnabled = useNativeTopBar) {
                CreatePostTopBar(
                    isReply = isReply,
                    onClose = { onAction(CreatePostNavAction.BackClick) }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        val topOverlayPadding = paddingValues.calculateTopPadding()
        val bottomInsetPadding = paddingValues.calculateBottomPadding()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomInsetPadding)
                .imePadding()
                .pointerInput(Unit) {
                    detectTapGestures { focusManager.clearFocus() }
                }
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(topOverlayPadding))

            if (errorMessage != null) {
                ErrorStateCard(
                    message = errorMessage,
                    onDismiss = { onAction(CreatePostAction.ErrorDismissed) }
                )
            }

            if (isReply) {
                ReplyContextCard(
                    parentId = state.parentId,
                    authorName = state.replyTargetAuthorName,
                    avatarUrl = state.replyTargetAvatarUrl,
                    content = state.replyTargetContent
                )
            }

            ComposerInputCard(
                content = state.content,
                isReply = isReply,
                currentUserAvatarUrl = state.currentUserAvatarUrl,
                isEnabled = !state.isLoading,
                onContentChanged = { input ->
                    if (input.length <= MAX_POST_LENGTH) {
                        onAction(CreatePostAction.ContentChanged(input))
                    }
                }
            )

            MediaSection(
                state = state,
                onLaunchPicker = launchPicker,
                onRemoveMedia = { index -> onAction(CreatePostAction.RemoveMedia(index)) }
            )

            ComposerStatusSection(
                contentLength = state.content.length,
                textProgress = textProgress,
                isUploading = state.isUploading
            )

            Button(
                onClick = { onAction(CreatePostAction.SubmitClicked) },
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (isReply) {
                            stringResource(Res.string.create_post_reply_button)
                        } else {
                            stringResource(Res.string.create_post_button)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePostTopBar(
    isReply: Boolean,
    onClose: () -> Unit
) {
    val actionIconColor = GlassTopBarIconContentColor()
    GlassTopBar {
        CenterAlignedTopAppBar(
            title = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GlassTopBarTitle(
                        text = if (isReply) {
                            stringResource(Res.string.create_post_reply_title)
                        } else {
                            stringResource(Res.string.create_post_title)
                        }
                    )
                    if (isReply)
                        Text(
                            text = if (isReply) {
                                stringResource(Res.string.create_post_replying_to)
                            } else {
                                stringResource(Res.string.create_post_placeholder)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                }
            },
            navigationIcon = {
                GlassTopBarIconButton(
                    onClick = onClose,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    CloseIcon(
                        modifier = Modifier.size(GlassTopBarInnerIconSize),
                        color = actionIconColor
                    )
                }
            },
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun ReplyContextCard(
    parentId: String?,
    authorName: String?,
    avatarUrl: String?,
    content: String?
) {
    val resolvedAuthor = authorName?.trim().orEmpty().ifBlank {
        stringResource(Res.string.create_post_reply_context_unknown_author)
    }
    val resolvedContent = content?.trim().orEmpty().ifBlank {
        stringResource(Res.string.create_post_reply_context_unknown_content)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(Res.string.create_post_reply_context_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                ConversationAvatar(
                    name = resolvedAuthor,
                    avatarUrl = avatarUrl,
                    size = 38.dp
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = resolvedAuthor,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = resolvedContent,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            parentId?.let {
                Text(
                    text = "${stringResource(Res.string.create_post_reply_context_post_id)} ${
                        it.take(
                            PREVIEW_POST_ID_LENGTH
                        )
                    }",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ComposerInputCard(
    content: String,
    isReply: Boolean,
    currentUserAvatarUrl: String?,
    isEnabled: Boolean,
    onContentChanged: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ConversationAvatar(
                    name = stringResource(Res.string.create_post_current_user),
                    avatarUrl = currentUserAvatarUrl,
                    size = 36.dp
                )
                Text(
                    text = stringResource(Res.string.create_post_current_user),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            OutlinedTextField(
                value = content,
                onValueChange = onContentChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 300.dp),
                placeholder = {
                    Text(
                        text = if (isReply) {
                            stringResource(Res.string.create_post_reply_placeholder)
                        } else {
                            stringResource(Res.string.create_post_placeholder)
                        }
                    )
                },
                enabled = isEnabled,
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )

            if (isReply)
                Text(
                    text = stringResource(Res.string.create_post_content_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
        }
    }
}

@Composable
private fun MediaSection(
    state: CreatePostUiState,
    onLaunchPicker: () -> Unit,
    onRemoveMedia: (Int) -> Unit
) {
    val canAddMore = !state.isLoading && state.selectedMedia.size < MAX_MEDIA_COUNT

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.create_post_media_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${state.selectedMedia.size}/$MAX_MEDIA_COUNT",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (canAddMore) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        )
                        .clickable(enabled = canAddMore, onClick = onLaunchPicker)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ImageIcon(
                            modifier = Modifier.size(18.dp),
                            color = if (canAddMore) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            }
                        )
                        Text(
                            text = stringResource(Res.string.create_post_media_add),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (canAddMore) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                Text(
                    text = if (state.isUploading) {
                        stringResource(Res.string.create_post_uploading)
                    } else {
                        stringResource(Res.string.create_post_media_limit)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (state.selectedMedia.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 8.dp)
                ) {
                    itemsIndexed(state.selectedMedia) { index, media ->
                        MediaThumbnail(
                            media = media,
                            isUploaded = index < state.uploadedMedia.size,
                            onRemove = { onRemoveMedia(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerStatusSection(
    contentLength: Int,
    textProgress: Float,
    isUploading: Boolean
) {
    val counterColor = when {
        contentLength >= MAX_POST_LENGTH -> MaterialTheme.colorScheme.error
        contentLength >= MAX_POST_LENGTH - 24 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val statusText = when {
        contentLength >= MAX_POST_LENGTH -> stringResource(Res.string.create_post_status_limit)
        contentLength == 0 -> stringResource(Res.string.create_post_status_need_text)
        else -> stringResource(Res.string.create_post_status_ready)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "$contentLength/$MAX_POST_LENGTH",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = counterColor
            )
            Spacer(modifier = Modifier.weight(1f))

            if (isUploading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(Res.string.create_post_uploading),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            if (textProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(textProgress)
                        .clip(RoundedCornerShape(999.dp))
                        .background(counterColor)
                )
            }
        }
    }
}

@Composable
private fun ConversationAvatar(
    name: String,
    avatarUrl: String? = null,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val avatarModifier = modifier.size(size)

    if (!avatarUrl.isNullOrBlank()) {
        AsyncImage(
            model = resolveBackendUrl(avatarUrl),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = avatarModifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
    } else {
        Box(
            modifier = avatarModifier
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun MediaThumbnail(
    media: SelectedMedia,
    isUploaded: Boolean,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 110.dp, height = 88.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        MediaThumbnailImage(
            media = media,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        if (!isUploaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            CloseIcon(
                modifier = Modifier.size(10.dp),
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun CloseIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    val resolvedColor = if (color == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurface
    } else {
        color
    }
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.12f
        val margin = size.minDimension * 0.2f
        drawLine(
            color = resolvedColor,
            start = Offset(margin, margin),
            end = Offset(size.width - margin, size.height - margin),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = resolvedColor,
            start = Offset(size.width - margin, margin),
            end = Offset(margin, size.height - margin),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun ImageIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    val resolvedColor = if (color == Color.Unspecified) {
        MaterialTheme.colorScheme.primary
    } else {
        color
    }
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.1f
        val margin = size.minDimension * 0.1f
        drawRoundRect(
            color = resolvedColor,
            topLeft = Offset(margin, margin),
            size = Size(size.width - margin * 2, size.height - margin * 2),
            cornerRadius = CornerRadius(size.minDimension * 0.15f),
            style = Stroke(width = stroke)
        )

        val mountainBaseY = size.height * 0.7f
        val peakX = size.width * 0.4f
        val peakY = size.height * 0.35f
        drawLine(
            color = resolvedColor,
            start = Offset(margin * 2, mountainBaseY),
            end = Offset(peakX, peakY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = resolvedColor,
            start = Offset(peakX, peakY),
            end = Offset(size.width * 0.65f, mountainBaseY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )

        val smallPeakX = size.width * 0.7f
        val smallPeakY = size.height * 0.5f
        drawLine(
            color = resolvedColor,
            start = Offset(size.width * 0.55f, mountainBaseY),
            end = Offset(smallPeakX, smallPeakY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = resolvedColor,
            start = Offset(smallPeakX, smallPeakY),
            end = Offset(size.width - margin * 2, mountainBaseY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )

        val sunRadius = size.minDimension * 0.08f
        drawCircle(
            color = resolvedColor,
            radius = sunRadius,
            center = Offset(size.width * 0.7f, size.height * 0.3f)
        )
    }
}

@Preview
@Composable
private fun CreatePostScreenPreview() {
    KwitterTheme(darkTheme = false) {
        CreatePostScreen(
            state = CreatePostUiState(
                content = "Launching a new feature preview this week.",
                selectedMedia = emptyList()
            ),
            onAction = {}
        )
    }
}

@Preview
@Composable
private fun CreatePostReplyPreview() {
    KwitterTheme(darkTheme = true) {
        CreatePostScreen(
            state = CreatePostUiState(
                content = "Totally agree with your point about reducing cognitive load.",
                parentId = "12345678-9876",
                replyTargetAuthorName = "Connor",
                replyTargetContent = "The current compose UI feels too bare. Users need context on who and what they are replying to."
            ),
            onAction = {}
        )
    }
}
