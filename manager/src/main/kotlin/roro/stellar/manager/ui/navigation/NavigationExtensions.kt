package roro.stellar.manager.ui.navigation

import androidx.navigation.NavController

fun NavController.safePopBackStack(): Boolean =
    if (previousBackStackEntry != null) popBackStack() else false

fun NavController.popToGraphStart() {
    val start = currentDestination?.parent?.startDestinationRoute ?: return
    if (currentDestination?.route != start) {
        popBackStack(start, inclusive = false)
    }
}
