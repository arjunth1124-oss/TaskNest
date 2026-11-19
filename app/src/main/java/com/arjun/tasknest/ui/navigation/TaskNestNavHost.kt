package com.arjun.tasknest.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.arjun.tasknest.ui.edit.TaskEditRoute
import com.arjun.tasknest.ui.edit.TaskEditViewModel
import com.arjun.tasknest.ui.tasklist.TaskListRoute

private const val ROUTE_LIST = "tasks"
private const val ROUTE_EDIT = "edit?${TaskEditViewModel.ARG_TASK_ID}={${TaskEditViewModel.ARG_TASK_ID}}"

private fun editRoute(taskId: String?) =
    if (taskId == null) "edit" else "edit?${TaskEditViewModel.ARG_TASK_ID}=$taskId"

@Composable
fun TaskNestNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = ROUTE_LIST) {
        composable(ROUTE_LIST) {
            TaskListRoute(
                onAddTask = { navController.navigate(editRoute(null)) },
                onOpenTask = { id -> navController.navigate(editRoute(id)) },
            )
        }
        composable(
            route = ROUTE_EDIT,
            arguments = listOf(
                navArgument(TaskEditViewModel.ARG_TASK_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            TaskEditRoute(onDone = { navController.popBackStack() })
        }
    }
}
