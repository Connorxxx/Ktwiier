package com.connor.kwitter.features.messagesearch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.connor.kwitter.core.ui.ErrorStateCard
import com.connor.kwitter.core.ui.GlassTopBar
import com.connor.kwitter.core.ui.GlassTopBarBackButton
import com.connor.kwitter.core.util.formatPostTime
import com.connor.kwitter.domain.messaging.model.MessageSearchItem
import com.connor.kwitter.features.glass.NativeTopBarButtons
import com.connor.kwitter.features.glass.NativeTopBarModel
import com.connor.kwitter.features.glass.NativeTopBarSlot
import com.connor.kwitter.features.glass.PublishNativeTopBar
import com.connor.kwitter.features.glass.getNativeTopBarController
import com.connor.kwitter.features.search.SearchIcon
import kwitter.composeapp.generated.resources.Res
import kwitter.composeapp.generated.resources.message_search_no_results
import kwitter.composeapp.generated.resources.message_search_placeholder
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageSearchScreen(
    state: MessageSearchUiState,
    useNativeTopBar: Boolean = false,
    onNativeTopBarModel: (NativeTopBarModel) -> Unit = {},
    onAction: (MessageSearchIntent) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val nativeTopBarController = remember { getNativeTopBarController() }
    val searchPlaceholder = stringResource(Res.string.message_search_placeholder)
    val noResultsText = stringResource(Res.string.message_search_no_results)

    PublishNativeTopBar(
        onNativeTopBarModel,
        NativeTopBarModel.Search(
            query = state.query,
            placeholder = searchPlaceholder,
            leadingButton = NativeTopBarButtons.back()
        )
    )

    Scaffold(
        topBar = {
            NativeTopBarSlot(nativeTopBarEnabled = useNativeTopBar) {
                MessageSearchTopBar(
                    query = state.query,
                    placeholder = searchPlaceholder,
                    onQueryChange = { onAction(MessageSearchAction.UpdateQuery(it)) },
                    onSearch = {
                        focusManager.clearFocus()
                        onAction(MessageSearchAction.SubmitSearch)
                    },
                    onBackClick = {
                        focusManager.clearFocus()
                        onAction(MessageSearchNavAction.BackClick)
                    }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        val topPadding = paddingValues.calculateTopPadding()
        val bottomPadding = paddingValues.calculateBottomPadding()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding, bottom = bottomPadding)
        ) {
            when {
                state.isSearching -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }

                state.error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        ErrorStateCard(
                            message = state.error,
                            onDismiss = { onAction(MessageSearchAction.ErrorDismissed) }
                        )
                    }
                }

                state.hasSearched && state.results.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = noResultsText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                state.results.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 8.dp,
                            end = 16.dp,
                            bottom = 8.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(
                            items = state.results,
                            key = { it.message.id }
                        ) { item ->
                            MessageSearchResultItem(
                                item = item,
                                onClick = {
                                    onAction(MessageSearchNavAction.ResultClick(item.message.id))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageSearchTopBar(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBackClick: () -> Unit
) {
    GlassTopBar {
        CenterAlignedTopAppBar(
            title = {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    leadingIcon = {
                        SearchIcon(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() })
                )
            },
            navigationIcon = {
                GlassTopBarBackButton(
                    onClick = onBackClick,
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
private fun MessageSearchResultItem(
    item: MessageSearchItem,
    onClick: () -> Unit
) {
    val highlightColor = MaterialTheme.colorScheme.primary
    val annotatedContent = remember(item.highlightedContent, highlightColor) {
        parseHighlightedContent(item.highlightedContent, highlightColor)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = annotatedContent,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = formatPostTime(item.message.createdAt),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Parses `<mark>...</mark>` tags from FTS5 `highlight()` into [AnnotatedString]
 * with a bold + colored [SpanStyle] for matched segments.
 */
private fun parseHighlightedContent(html: String, highlightColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val openTag = "<mark>"
        val closeTag = "</mark>"

        while (cursor < html.length) {
            val openIndex = html.indexOf(openTag, cursor)
            if (openIndex == -1) {
                append(html.substring(cursor))
                break
            }
            // Text before <mark>
            append(html.substring(cursor, openIndex))

            val contentStart = openIndex + openTag.length
            val closeIndex = html.indexOf(closeTag, contentStart)
            if (closeIndex == -1) {
                append(html.substring(openIndex))
                break
            }

            // Highlighted segment
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = highlightColor))
            append(html.substring(contentStart, closeIndex))
            pop()

            cursor = closeIndex + closeTag.length
        }
    }
}
