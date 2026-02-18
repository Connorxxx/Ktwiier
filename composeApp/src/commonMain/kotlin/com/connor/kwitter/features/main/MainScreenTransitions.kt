package com.connor.kwitter.features.main

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.TransformOrigin
import androidx.navigation3.scene.Scene
import com.connor.kwitter.features.NavigationRoute

internal const val MainTabSceneMetadataKey = "main.tab.scene"
internal val mainTabSceneMetadata = mapOf(MainTabSceneMetadataKey to true)

private fun Scene<NavigationRoute>.isMainTabScene(): Boolean =
    metadata[MainTabSceneMetadataKey] == true

internal fun shouldUseMainTabCrossFade(
    initialState: Scene<NavigationRoute>,
    targetState: Scene<NavigationRoute>
): Boolean = initialState.isMainTabScene() && targetState.isMainTabScene()

internal object MainScreenTransitions {
    private const val MainTabFadeInMillis = 220
    private const val MainTabFadeOutMillis = 180
    private const val SceneTransitionDurationMillis = 400
    private const val SceneExitFadeMillis = 300
    private val SceneTransformOrigin = TransformOrigin(0.5f, 0.5f)

    fun mainTabSwitch() =
        fadeIn(animationSpec = tween(MainTabFadeInMillis)) togetherWith
            fadeOut(animationSpec = tween(MainTabFadeOutMillis))

    fun push() =
        (slideInHorizontally(
            initialOffsetX = { fullWidth -> fullWidth / 3 },
            animationSpec = tween(SceneTransitionDurationMillis)
        ) + fadeIn(
            animationSpec = tween(SceneTransitionDurationMillis)
        ) + scaleIn(
            initialScale = 0.92f,
            transformOrigin = SceneTransformOrigin,
            animationSpec = tween(SceneTransitionDurationMillis)
        )) togetherWith (
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth / 4 },
                animationSpec = tween(SceneTransitionDurationMillis)
            ) + fadeOut(
                animationSpec = tween(SceneExitFadeMillis)
            ) + scaleOut(
                targetScale = 0.95f,
                transformOrigin = SceneTransformOrigin,
                animationSpec = tween(SceneTransitionDurationMillis)
            )
        )

    fun pop(includeTransformOrigin: Boolean) =
        if (includeTransformOrigin) {
            (slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth / 4 },
                animationSpec = tween(SceneTransitionDurationMillis)
            ) + fadeIn(
                animationSpec = tween(SceneTransitionDurationMillis)
            ) + scaleIn(
                initialScale = 0.95f,
                transformOrigin = SceneTransformOrigin,
                animationSpec = tween(SceneTransitionDurationMillis)
            )) togetherWith (
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth / 3 },
                    animationSpec = tween(SceneTransitionDurationMillis)
                ) + fadeOut(
                    animationSpec = tween(SceneExitFadeMillis)
                ) + scaleOut(
                    targetScale = 0.92f,
                    transformOrigin = SceneTransformOrigin,
                    animationSpec = tween(SceneTransitionDurationMillis)
                )
            )
        } else {
            (slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth / 4 },
                animationSpec = tween(SceneTransitionDurationMillis)
            ) + fadeIn(
                animationSpec = tween(SceneTransitionDurationMillis)
            ) + scaleIn(
                initialScale = 0.95f,
                animationSpec = tween(SceneTransitionDurationMillis)
            )) togetherWith (
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth / 3 },
                    animationSpec = tween(SceneTransitionDurationMillis)
                ) + fadeOut(
                    animationSpec = tween(SceneExitFadeMillis)
                ) + scaleOut(
                    targetScale = 0.92f,
                    animationSpec = tween(SceneTransitionDurationMillis)
                )
            )
        }
}
