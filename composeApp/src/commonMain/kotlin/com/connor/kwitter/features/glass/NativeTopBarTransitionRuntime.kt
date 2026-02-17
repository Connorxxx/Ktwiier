package com.connor.kwitter.features.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.navigationevent.NavigationEventTransitionState
import com.connor.kwitter.features.NavigationRoute

private const val FallbackBackTransitionDurationMillis = 400

private data class TopBarRouteTransition(
    val fromRoute: NavigationRoute?,
    val toRoute: NavigationRoute?
)

private data class NativeTopBarTransitionState(
    val predictiveBackProgress: Float,
    val predictiveTargetRoute: NavigationRoute?,
    val isPredictiveBackInProgress: Boolean,
    val fallbackBackProgress: Float,
    val fallbackTransitionFromRoute: NavigationRoute?,
    val fallbackTransitionToRoute: NavigationRoute?,
    val isFallbackBackTransitionActive: Boolean,
    val pendingFallbackFromRoute: NavigationRoute?,
    val shouldHoldFromModelBeforeFallback: Boolean
)

@Composable
fun SyncNativeTopBarController(
    controller: NativeTopBarController?,
    coordinator: NativeTopBarCoordinator,
    backStackSnapshot: List<NavigationRoute>,
    navigationTransitionState: NavigationEventTransitionState,
    predictBackTargetRoute: (List<NavigationRoute>) -> NavigationRoute?
) {
    if (controller == null) return

    val currentRoute = backStackSnapshot.lastOrNull()
    val previousRouteInBackStack = backStackSnapshot.getOrNull(backStackSnapshot.lastIndex - 1)
    val transitionState = rememberNativeTopBarTransitionState(
        backStackSnapshot = backStackSnapshot,
        navigationTransitionState = navigationTransitionState,
        predictBackTargetRoute = predictBackTargetRoute
    )

    val currentTopBarModel = coordinator.resolveModel(
        route = currentRoute,
        fallbackRoute = previousRouteInBackStack
    )
    val targetTopBarModel = coordinator.resolveModel(route = transitionState.predictiveTargetRoute)
    val fallbackFromModel = coordinator.resolveModel(route = transitionState.fallbackTransitionFromRoute)
    val fallbackToModel = coordinator.resolveModel(route = transitionState.fallbackTransitionToRoute)
    val pendingFallbackFromModel = coordinator.resolveModel(route = transitionState.pendingFallbackFromRoute)

    SideEffect {
        when {
            transitionState.isPredictiveBackInProgress -> {
                controller.setInteractiveModelTransition(
                    fromModel = currentTopBarModel,
                    toModel = targetTopBarModel,
                    progress = transitionState.predictiveBackProgress
                )
            }

            transitionState.isFallbackBackTransitionActive -> {
                controller.setInteractiveModelTransition(
                    fromModel = fallbackFromModel,
                    toModel = fallbackToModel,
                    progress = transitionState.fallbackBackProgress
                )
            }

            transitionState.shouldHoldFromModelBeforeFallback -> {
                controller.setModel(pendingFallbackFromModel)
            }

            else -> {
                controller.setModel(currentTopBarModel)
            }
        }
    }
}

@Composable
private fun rememberNativeTopBarTransitionState(
    backStackSnapshot: List<NavigationRoute>,
    navigationTransitionState: NavigationEventTransitionState,
    predictBackTargetRoute: (List<NavigationRoute>) -> NavigationRoute?
): NativeTopBarTransitionState {
    val latestPredictBackTargetRoute by rememberUpdatedState(predictBackTargetRoute)

    val predictiveBackProgress = when (val transition = navigationTransitionState) {
        is NavigationEventTransitionState.InProgress -> {
            if (transition.direction == NavigationEventTransitionState.TRANSITIONING_BACK) {
                transition.latestEvent.progress.coerceIn(0f, 1f)
            } else {
                0f
            }
        }

        NavigationEventTransitionState.Idle -> 0f
    }
    val predictiveTargetRoute = if (predictiveBackProgress > 0f) {
        latestPredictBackTargetRoute(backStackSnapshot)
    } else {
        null
    }
    val isPredictiveBackInProgress = predictiveTargetRoute != null && predictiveBackProgress > 0f

    val fallbackTopBarTransitionProgress = remember { Animatable(1f) }
    var fallbackTransitionFromRoute by remember { mutableStateOf<NavigationRoute?>(null) }
    var fallbackTransitionToRoute by remember { mutableStateOf<NavigationRoute?>(null) }
    var sawPredictiveBackProgress by remember { mutableStateOf(false) }
    var previousBackStackSnapshot by remember {
        mutableStateOf(backStackSnapshot)
    }

    LaunchedEffect(isPredictiveBackInProgress) {
        if (isPredictiveBackInProgress) {
            sawPredictiveBackProgress = true
        }
    }

    LaunchedEffect(backStackSnapshot, isPredictiveBackInProgress) {
        if (isPredictiveBackInProgress) {
            fallbackTopBarTransitionProgress.stop()
            fallbackTopBarTransitionProgress.snapTo(1f)
            fallbackTransitionFromRoute = null
            fallbackTransitionToRoute = null
            return@LaunchedEffect
        }

        val currentBackStackSnapshot = backStackSnapshot
        val fallbackTransition = inferredBackRouteTransition(
            previousBackStack = previousBackStackSnapshot,
            currentBackStack = currentBackStackSnapshot,
            predictBackTargetRoute = latestPredictBackTargetRoute
        )

        if (fallbackTransition != null) {
            if (!sawPredictiveBackProgress) {
                fallbackTransitionFromRoute = fallbackTransition.fromRoute
                fallbackTransitionToRoute = fallbackTransition.toRoute
                fallbackTopBarTransitionProgress.snapTo(0f)
                fallbackTopBarTransitionProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = FallbackBackTransitionDurationMillis)
                )
                fallbackTransitionFromRoute = null
                fallbackTransitionToRoute = null
            }
            sawPredictiveBackProgress = false
        } else if (!isPredictiveBackInProgress) {
            // Predictive gesture cancelled (or no back transition happened).
            sawPredictiveBackProgress = false
        }

        previousBackStackSnapshot = currentBackStackSnapshot
    }

    val pendingFallbackBackTransition = inferredBackRouteTransition(
        previousBackStack = previousBackStackSnapshot,
        currentBackStack = backStackSnapshot,
        predictBackTargetRoute = latestPredictBackTargetRoute
    )

    return NativeTopBarTransitionState(
        predictiveBackProgress = predictiveBackProgress,
        predictiveTargetRoute = predictiveTargetRoute,
        isPredictiveBackInProgress = isPredictiveBackInProgress,
        fallbackBackProgress = fallbackTopBarTransitionProgress.value,
        fallbackTransitionFromRoute = fallbackTransitionFromRoute,
        fallbackTransitionToRoute = fallbackTransitionToRoute,
        isFallbackBackTransitionActive =
            fallbackTransitionFromRoute != null && fallbackTransitionToRoute != null,
        pendingFallbackFromRoute = pendingFallbackBackTransition?.fromRoute,
        shouldHoldFromModelBeforeFallback =
            pendingFallbackBackTransition != null && !sawPredictiveBackProgress
    )
}

private fun inferredBackRouteTransition(
    previousBackStack: List<NavigationRoute>,
    currentBackStack: List<NavigationRoute>,
    predictBackTargetRoute: (List<NavigationRoute>) -> NavigationRoute?
): TopBarRouteTransition? {
    val fromRoute = previousBackStack.lastOrNull() ?: return null
    val toRoute = currentBackStack.lastOrNull() ?: return null
    if (fromRoute == toRoute) return null

    val predictedBackTarget = predictBackTargetRoute(previousBackStack) ?: return null
    if (predictedBackTarget != toRoute) return null

    return TopBarRouteTransition(fromRoute = fromRoute, toRoute = toRoute)
}
