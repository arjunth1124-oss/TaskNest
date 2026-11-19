package com.arjun.tasknest.ui.tasklist

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.arjun.tasknest.domain.Task
import kotlinx.coroutines.launch

@Composable
fun TaskListRoute(
    onAddTask: () -> Unit,
    onOpenTask: (String) -> Unit,
    viewModel: TaskListViewModel = hiltViewModel(),
) {
    val tasks = viewModel.tasks.collectAsLazyPagingItems()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()

    TaskListScreen(
        tasks = tasks,
        query = query,
        syncState = syncState,
        onQueryChange = viewModel::onQueryChange,
        onRefresh = viewModel::onRefresh,
        onToggle = { viewModel.onToggleCompleted(it) },
        onDelete = { viewModel.onDelete(it) },
        onAddTask = onAddTask,
        onOpenTask = onOpenTask,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    tasks: LazyPagingItems<Task>,
    query: String,
    syncState: SyncUiState,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onToggle: (Task) -> Unit,
    onDelete: (Task) -> Unit,
    onAddTask: () -> Unit,
    onOpenTask: (String) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("TaskNest")
                        SyncStatusLine(syncState)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTask) {
                Icon(Icons.Filled.Add, contentDescription = "Add task")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!syncState.isOnline) OfflineBanner()

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Search tasks") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            PullToRefreshBox(
                isRefreshing = syncState.isSyncing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                val isEmpty = tasks.itemCount == 0 && tasks.loadState.refresh is LoadState.NotLoading
                if (isEmpty) {
                    EmptyState(isSearching = query.isNotBlank())
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(count = tasks.itemCount, key = tasks.itemKey { it.id }) { index ->
                            val task = tasks[index] ?: return@items
                            SwipeToDeleteRow(
                                onDelete = {
                                    onDelete(task)
                                    scope.launch { snackbarHostState.showSnackbar("Task deleted") }
                                },
                            ) {
                                TaskRow(
                                    task = task,
                                    onToggle = { onToggle(task) },
                                    onClick = { onOpenTask(task.id) },
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncStatusLine(state: SyncUiState) {
    val (icon, text) = when {
        !state.isOnline && state.pendingCount > 0 ->
            Icons.Outlined.CloudOff to "Offline · ${state.pendingCount} change(s) saved on device"
        !state.isOnline -> Icons.Outlined.CloudOff to "Offline"
        state.isSyncing -> Icons.Outlined.Sync to "Syncing…"
        state.pendingCount > 0 -> Icons.Outlined.CloudUpload to "${state.pendingCount} change(s) waiting to sync"
        state.lastSyncedAt != null -> Icons.Outlined.CloudDone to "Synced ${relativeTime(state.lastSyncedAt)}"
        else -> Icons.Outlined.CloudUpload to "Not synced yet"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

private fun relativeTime(time: Long): String =
    DateUtils.getRelativeTimeSpanString(
        time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
    ).toString().lowercase()

@Composable
private fun OfflineBanner() {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
            "You're offline. Changes are saved and will sync automatically.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteRow(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        content()
    }
}

@Composable
private fun TaskRow(task: Task, onToggle: () -> Unit, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Checkbox(checked = task.isCompleted, onCheckedChange = { onToggle() })
        },
        headlineContent = {
            Text(
                task.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
            )
        },
        supportingContent = if (task.description.isNotBlank()) {
            { Text(task.description, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        trailingContent = if (task.isPendingSync) {
            {
                Icon(
                    Icons.Outlined.CloudUpload,
                    contentDescription = "Waiting to sync",
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun EmptyState(isSearching: Boolean) {
    // LazyColumn wrapper keeps pull-to-refresh working on an empty list.
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        item {
            Column(
                Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (isSearching) "No matching tasks" else "No tasks yet",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    if (isSearching) "Try a different search." else "Tap + to add one. It works offline too.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
