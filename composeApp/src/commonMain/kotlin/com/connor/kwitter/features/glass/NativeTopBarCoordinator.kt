package com.connor.kwitter.features.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.connor.kwitter.features.NavigationRoute

class NativeTopBarCoordinator(
    private val supportsRoute: (NavigationRoute?) -> Boolean
) {
    private val modelsByRoute = mutableStateMapOf<NavigationRoute, NativeTopBarModel>()
    private val actionHandlersByRoute = mutableStateMapOf<NavigationRoute, (NativeTopBarAction) -> Unit>()

    fun publishModel(route: NavigationRoute, model: NativeTopBarModel) {
        modelsByRoute[route] = model
    }

    fun publishActionHandler(route: NavigationRoute, actionHandler: (NativeTopBarAction) -> Unit) {
        actionHandlersByRoute[route] = actionHandler
    }

    fun pruneTo(activeRoutes: Set<NavigationRoute>) {
        modelsByRoute.keys.toList().forEach { route ->
            if (route !in activeRoutes) {
                modelsByRoute.remove(route)
            }
        }
        actionHandlersByRoute.keys.toList().forEach { route ->
            if (route !in activeRoutes) {
                actionHandlersByRoute.remove(route)
            }
        }
    }

    fun resolveModel(
        route: NavigationRoute?,
        fallbackRoute: NavigationRoute? = null
    ): NativeTopBarModel {
        if (!supportsRoute(route)) return NativeTopBarModel.Hidden
        if (route == null) return NativeTopBarModel.Hidden

        val directModel = modelsByRoute[route]
        if (directModel != null) return directModel

        if (supportsRoute(fallbackRoute) && fallbackRoute != null) {
            modelsByRoute[fallbackRoute]?.let { fallbackModel ->
                return fallbackModel
            }
        }

        return NativeTopBarModel.Hidden
    }

    fun actionHandlersSnapshot(): Map<NavigationRoute, (NativeTopBarAction) -> Unit> =
        actionHandlersByRoute.toMap()

    fun resolveActionHandler(
        route: NavigationRoute?,
        fallbackRoute: NavigationRoute? = null,
        handlersByRoute: Map<NavigationRoute, (NativeTopBarAction) -> Unit> = actionHandlersByRoute
    ): ((NativeTopBarAction) -> Unit)? {
        if (!supportsRoute(route)) return null
        if (route == null) return null

        val directHandler = handlersByRoute[route]
        if (directHandler != null) return directHandler

        if (supportsRoute(fallbackRoute) && fallbackRoute != null) {
            handlersByRoute[fallbackRoute]?.let { fallbackHandler ->
                return fallbackHandler
            }
        }

        return null
    }
}

@Composable
fun rememberNativeTopBarCoordinator(
    supportsRoute: (NavigationRoute?) -> Boolean
): NativeTopBarCoordinator = remember(supportsRoute) { NativeTopBarCoordinator(supportsRoute) }

@Composable
fun rememberNativeTopBarBinding(
    coordinator: NativeTopBarCoordinator,
    route: NavigationRoute,
    onAction: (NativeTopBarAction) -> Unit = {}
): (NativeTopBarModel) -> Unit {
    val latestOnAction by rememberUpdatedState(onAction)
    val publishModel = remember(coordinator, route) {
        { model: NativeTopBarModel ->
            coordinator.publishModel(route, model)
        }
    }

    SideEffect {
        coordinator.publishActionHandler(route) { action ->
            latestOnAction(action)
        }
    }

    return publishModel
}
