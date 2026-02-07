package com.connor.kwitter.features.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.connor.kwitter.domain.post.model.Post
import com.connor.kwitter.features.post.formatPostTime
import kwitter.composeapp.generated.resources.Res
import kwitter.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onAction: (HomeIntent) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            onAction(HomeAction.ErrorDismissed)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.home_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { onAction(HomeAction.LogoutClick) }) {
                        LogoutIcon(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAction(HomeNavAction.CreatePostClick) },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                PlusIcon(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onAction(HomeAction.Refresh) },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.posts.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(Res.string.home_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.posts, key = { it.id }) { post ->
                            PostItem(
                                post = post,
                                onClick = { onAction(HomeNavAction.PostClick(post.id)) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PostItem(
    post: Post,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = post.authorName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = formatPostTime(post.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = post.content,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis
        )

        if (post.replyCount > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${post.replyCount} replies",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LogoutIcon(
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
        // Door frame (right side open)
        val left = size.width * 0.3f
        val top = size.height * 0.15f
        val bottom = size.height * 0.85f
        val right = size.width * 0.75f
        // Top line
        drawLine(resolvedColor, Offset(left, top), Offset(right, top), stroke, cap = StrokeCap.Round)
        // Bottom line
        drawLine(resolvedColor, Offset(left, bottom), Offset(right, bottom), stroke, cap = StrokeCap.Round)
        // Left line
        drawLine(resolvedColor, Offset(left, top), Offset(left, bottom), stroke, cap = StrokeCap.Round)
        // Arrow line (horizontal)
        val arrowY = size.height * 0.5f
        val arrowLeft = size.width * 0.1f
        val arrowRight = size.width * 0.65f
        drawLine(resolvedColor, Offset(arrowLeft, arrowY), Offset(arrowRight, arrowY), stroke, cap = StrokeCap.Round)
        // Arrow head
        val arrowSize = size.width * 0.15f
        drawLine(resolvedColor, Offset(arrowLeft, arrowY), Offset(arrowLeft + arrowSize, arrowY - arrowSize), stroke, cap = StrokeCap.Round)
        drawLine(resolvedColor, Offset(arrowLeft, arrowY), Offset(arrowLeft + arrowSize, arrowY + arrowSize), stroke, cap = StrokeCap.Round)
    }
}

@Composable
private fun PlusIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    val resolvedColor = if (color == Color.Unspecified) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        color
    }
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.14f
        val margin = size.minDimension * 0.2f
        // Horizontal line
        drawLine(resolvedColor, Offset(margin, center.y), Offset(size.width - margin, center.y), stroke, cap = StrokeCap.Round)
        // Vertical line
        drawLine(resolvedColor, Offset(center.x, margin), Offset(center.x, size.height - margin), stroke, cap = StrokeCap.Round)
    }
}
