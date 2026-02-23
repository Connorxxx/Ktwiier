package com.connor.kwitter.features.editprofile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import coil3.compose.AsyncImage
import com.connor.kwitter.core.media.decodeToImageBitmap
import com.connor.kwitter.core.media.rememberImagePickerLauncher
import com.connor.kwitter.core.theme.KwitterTheme
import com.connor.kwitter.core.ui.GlassTopBar
import com.connor.kwitter.core.ui.GlassTopBarBackButton
import com.connor.kwitter.core.ui.GlassTopBarTitle
import com.connor.kwitter.core.util.resolveBackendUrl
import com.connor.kwitter.features.glass.NativeTopBarButtons
import com.connor.kwitter.features.glass.NativeTopBarModel
import com.connor.kwitter.features.glass.NativeTopBarSlot
import com.connor.kwitter.features.glass.PublishNativeTopBar
import kwitter.composeapp.generated.resources.Res
import kwitter.composeapp.generated.resources.profile_bio_label
import kwitter.composeapp.generated.resources.profile_display_name_label
import kwitter.composeapp.generated.resources.profile_edit
import kwitter.composeapp.generated.resources.profile_save
import kwitter.composeapp.generated.resources.profile_username_label
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    state: EditProfileUiState,
    useNativeTopBar: Boolean = false,
    onNativeTopBarModel: (NativeTopBarModel) -> Unit = {},
    onAction: (EditProfileIntent) -> Unit
) {
    // Show crop screen when pending image bytes are available
    val pendingBytes = state.pendingCropImageBytes
    if (pendingBytes != null) {
        AvatarCropScreen(
            imageBytes = pendingBytes,
            onConfirm = { croppedBytes ->
                onAction(EditProfileAction.AvatarCropConfirmed(croppedBytes))
            },
            useNativeTopBar = useNativeTopBar,
            onNativeTopBarModel = onNativeTopBarModel,
            onCancel = {
                onAction(EditProfileAction.AvatarCropCancelled)
            }
        )
        return
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val nativeTopTitle = stringResource(Res.string.profile_edit)

    val launchPicker = rememberImagePickerLauncher { media ->
        media?.let { onAction(EditProfileAction.AvatarSelected(it)) }
    }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            onAction(EditProfileAction.ErrorDismissed)
        }
    }

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            onAction(EditProfileNavAction.SaveSuccess)
        }
    }

    PublishNativeTopBar(
        onNativeTopBarModel,
        NativeTopBarModel.Title(
            title = nativeTopTitle,
            leadingButton = NativeTopBarButtons.back(enabled = !state.isSaving)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                )
            }
    ) {
        Scaffold(
            topBar = {
                NativeTopBarSlot(nativeTopBarEnabled = useNativeTopBar) {
                    EditProfileTopBar(
                        isSaving = state.isSaving,
                        onBackClick = { onAction(EditProfileNavAction.BackClick) },
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { paddingValues ->
            val topOverlayPadding = paddingValues.calculateTopPadding()
            val bottomInsetPadding = paddingValues.calculateBottomPadding()

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = topOverlayPadding,
                                bottom = bottomInsetPadding
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = bottomInsetPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(topOverlayPadding + 8.dp))

                        EditProfileAvatar(
                            croppedAvatarBytes = state.croppedAvatarBytes,
                            avatarUrl = state.avatarUrl.trim().ifBlank { null },
                            displayName = state.displayName,
                            isUploading = state.isUploadingAvatar,
                            enabled = !state.isSaving,
                            onClick = launchPicker,
                            modifier = Modifier.size(96.dp)
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            EditProfileTextField(
                                label = stringResource(Res.string.profile_display_name_label),
                                value = state.displayName,
                                onValueChange = { onAction(EditProfileAction.DisplayNameChanged(it)) },
                                enabled = !state.isSaving,
                                singleLine = true
                            )

                            EditProfileTextField(
                                label = stringResource(Res.string.profile_username_label),
                                value = state.username,
                                onValueChange = { onAction(EditProfileAction.UsernameChanged(it)) },
                                enabled = !state.isSaving,
                                singleLine = true
                            )

                            EditProfileTextField(
                                label = stringResource(Res.string.profile_bio_label),
                                value = state.bio,
                                onValueChange = { onAction(EditProfileAction.BioChanged(it)) },
                                enabled = !state.isSaving,
                                singleLine = false,
                                minLines = 3,
                                maxLines = 5
                            )
                        }

                        Spacer(modifier = Modifier.height(22.dp))

                        val canSave = !state.isSaving && !state.isUploadingAvatar
                        val buttonColors = if (state.isSaving) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = MaterialTheme.colorScheme.primary,
                                disabledContentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = { onAction(EditProfileAction.Save) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            enabled = canSave,
                            shape = RoundedCornerShape(14.dp),
                            colors = buttonColors,
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 0.dp,
                                pressedElevation = 0.dp,
                                focusedElevation = 0.dp,
                                hoveredElevation = 0.dp,
                                disabledElevation = 0.dp
                            )
                        ) {
                            if (state.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = stringResource(Res.string.profile_save),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileTopBar(
    isSaving: Boolean,
    onBackClick: () -> Unit,
) {
    GlassTopBar {
        CenterAlignedTopAppBar(
            title = {
                GlassTopBarTitle(text = stringResource(Res.string.profile_edit))
            },
            navigationIcon = {
                GlassTopBarBackButton(
                    onClick = onBackClick,
                    enabled = !isSaving,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            },
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun EditProfileAvatar(
    croppedAvatarBytes: ByteArray?,
    avatarUrl: String?,
    displayName: String,
    isUploading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initial = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val gradient = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(enabled = enabled && !isUploading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            croppedAvatarBytes != null -> {
                val bitmap: ImageBitmap = remember(croppedAvatarBytes) {
                    decodeToImageBitmap(croppedAvatarBytes)
                }
                Image(
                    bitmap = bitmap,
                    contentDescription = displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            !avatarUrl.isNullOrBlank() -> {
                AsyncImage(
                    model = resolveBackendUrl(avatarUrl),
                    contentDescription = displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.linearGradient(gradient),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        if (isUploading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.5.dp,
                    color = Color.White
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                CameraIcon(
                    modifier = Modifier.size(28.dp),
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun EditProfileTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    singleLine: Boolean,
    minLines: Int = 1,
    maxLines: Int = 1
) {
    val colors = MaterialTheme.colorScheme
    val textPrimary = colors.onSurface
    val textSecondary = colors.onSurfaceVariant
    val textMuted = colors.onSurfaceVariant.copy(alpha = 0.7f)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = textSecondary
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = textPrimary),
            enabled = enabled,
            singleLine = singleLine,
            minLines = if (!singleLine) minLines else 1,
            maxLines = if (!singleLine) maxLines else 1,
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedTextColor = textPrimary,
                unfocusedTextColor = textPrimary,
                disabledTextColor = textPrimary.copy(alpha = 0.6f),
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                disabledContainerColor = colors.surface,
                focusedIndicatorColor = colors.primary,
                unfocusedIndicatorColor = colors.outline,
                disabledIndicatorColor = colors.outline,
                cursorColor = colors.primary,
                focusedPlaceholderColor = textMuted,
                unfocusedPlaceholderColor = textMuted,
                disabledPlaceholderColor = textMuted
            )
        )
    }
}

@Composable
private fun CameraIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    val resolvedColor = if (color == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurface
    } else {
        color
    }
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.1f
        val outline = Stroke(width = stroke, cap = StrokeCap.Round)

        val bodyLeft = size.width * 0.1f
        val bodyTop = size.height * 0.3f
        val bodyRight = size.width * 0.9f
        val bodyBottom = size.height * 0.85f
        val cornerRadius = size.minDimension * 0.1f

        drawRoundRect(
            color = resolvedColor,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyRight - bodyLeft, bodyBottom - bodyTop),
            cornerRadius = CornerRadius(cornerRadius),
            style = outline
        )

        val bumpLeft = size.width * 0.32f
        val bumpRight = size.width * 0.68f
        val bumpTop = size.height * 0.15f
        drawLine(resolvedColor, Offset(bumpLeft, bodyTop), Offset(bumpLeft, bumpTop), stroke, cap = StrokeCap.Round)
        drawLine(resolvedColor, Offset(bumpLeft, bumpTop), Offset(bumpRight, bumpTop), stroke, cap = StrokeCap.Round)
        drawLine(resolvedColor, Offset(bumpRight, bumpTop), Offset(bumpRight, bodyTop), stroke, cap = StrokeCap.Round)

        val lensRadius = size.minDimension * 0.14f
        val lensCenterY = (bodyTop + bodyBottom) / 2f
        drawCircle(
            color = resolvedColor,
            radius = lensRadius,
            center = Offset(size.width * 0.5f, lensCenterY),
            style = outline
        )
    }
}

@Preview
@Composable
private fun EditProfileScreenPreview() {
    KwitterTheme(darkTheme = false) {
        EditProfileScreen(
            state = EditProfileUiState(
                displayName = "Connor",
                username = "connor",
                bio = "Building Ktwiier with Kotlin Multiplatform.",
                avatarUrl = ""
            ),
            onAction = {}
        )
    }
}

@Preview
@Composable
private fun EditProfileScreenDarkPreview() {
    KwitterTheme(darkTheme = true) {
        EditProfileScreen(
            state = EditProfileUiState(
                displayName = "Connor",
                username = "connor",
                bio = "Building Ktwiier with Kotlin Multiplatform.",
                avatarUrl = "",
                isSaving = true
            ),
            onAction = {}
        )
    }
}
