package com.connor.kwitter.features.mediaviewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import com.connor.kwitter.domain.post.model.PostMedia
import com.connor.kwitter.domain.post.model.PostMediaType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

data class MediaViewerUiState(
    val mediaList: List<PostMedia> = emptyList(),
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val showControls: Boolean = true
)

sealed interface MediaViewerIntent

sealed interface MediaViewerAction : MediaViewerIntent {
    data class Initialize(val mediaList: List<PostMedia>, val initialIndex: Int) : MediaViewerAction
    data class PageChanged(val index: Int) : MediaViewerAction
    data object TogglePlayPause : MediaViewerAction
    data class SeekTo(val positionMs: Long) : MediaViewerAction
    data object ToggleControls : MediaViewerAction
    data class UpdateProgress(val positionMs: Long, val durationMs: Long) : MediaViewerAction
    data class PlayingChanged(val isPlaying: Boolean) : MediaViewerAction
}

sealed interface MediaViewerNavAction : MediaViewerIntent {
    data object BackClick : MediaViewerNavAction
}

class MediaViewerViewModel : ViewModel() {

    private val _events = Channel<MediaViewerAction>(Channel.UNLIMITED)

    val uiState: StateFlow<MediaViewerUiState> = viewModelScope.launchMolecule(
        mode = RecompositionMode.Immediate
    ) {
        MediaViewerPresenter()
    }

    fun onEvent(event: MediaViewerAction) {
        _events.trySend(event)
    }

    @Composable
    private fun MediaViewerPresenter(): MediaViewerUiState {
        var state by remember { mutableStateOf(MediaViewerUiState()) }

        LaunchedEffect(Unit) {
            _events.receiveAsFlow().collect { action ->
                state = when (action) {
                    is MediaViewerAction.Initialize -> {
                        val isVideo = action.mediaList[action.initialIndex].type == PostMediaType.VIDEO
                        state.copy(
                            mediaList = action.mediaList,
                            currentIndex = action.initialIndex,
                            isPlaying = isVideo,
                            currentPositionMs = 0L,
                            durationMs = 0L,
                            showControls = !isVideo
                        )
                    }
                    is MediaViewerAction.PageChanged -> {
                        val isVideo = state.mediaList[action.index].type == PostMediaType.VIDEO
                        state.copy(
                            currentIndex = action.index,
                            isPlaying = isVideo,
                            currentPositionMs = 0L,
                            durationMs = 0L,
                            showControls = !isVideo
                        )
                    }
                    is MediaViewerAction.TogglePlayPause -> state.copy(
                        isPlaying = !state.isPlaying,
                        showControls = true
                    )
                    is MediaViewerAction.SeekTo -> state.copy(
                        currentPositionMs = action.positionMs
                    )
                    is MediaViewerAction.ToggleControls -> state.copy(
                        showControls = !state.showControls
                    )
                    is MediaViewerAction.UpdateProgress -> state.copy(
                        currentPositionMs = action.positionMs,
                        durationMs = action.durationMs
                    )
                    is MediaViewerAction.PlayingChanged -> state.copy(
                        isPlaying = action.isPlaying
                    )
                }
            }
        }

        return state
    }
}
