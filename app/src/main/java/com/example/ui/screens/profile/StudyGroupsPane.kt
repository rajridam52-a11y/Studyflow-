package com.example.ui.screens.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.StudyGroupDoc
import com.example.ui.components.EmptyStudyState
import com.example.viewmodel.StudyGroupsViewModel
import com.example.viewmodel.UiState

@Composable
fun StudyGroupsDialog(
    groupsViewModel: StudyGroupsViewModel,
    displayName: String,
    currentUserId: String,
    onDismiss: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val groupsState by groupsViewModel.groupsState.collectAsStateWithLifecycle()
    val selectedGroupId by groupsViewModel.selectedGroupId.collectAsStateWithLifecycle()
    val itemsState by groupsViewModel.groupItemsState.collectAsStateWithLifecycle()
    val feedback by groupsViewModel.feedbackMessage.collectAsStateWithLifecycle()

    LaunchedEffect(feedback) {
        feedback?.let {
            onShowMessage(it)
            groupsViewModel.clearFeedback()
        }
    }

    var showCreateGroup by remember { mutableStateOf(false) }
    var showJoinGroup by remember { mutableStateOf(false) }
    var showPostItem by remember { mutableStateOf(false) }

    val groupsList = (groupsState as? UiState.Success)?.data ?: emptyList()
    val activeGroup = groupsList.firstOrNull { it.id == selectedGroupId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(activeGroup?.name ?: "Private Study Groups", maxLines = 1)
                if (activeGroup != null) {
                    TextButton(onClick = { groupsViewModel.selectGroup(null) }) { Text("All Groups") }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 260.dp, max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (activeGroup == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showCreateGroup = true },
                            modifier = Modifier.weight(1f).testTag("create_study_group_btn")
                        ) { Text("Create Group") }
                        OutlinedButton(
                            onClick = { showJoinGroup = true },
                            modifier = Modifier.weight(1f).testTag("join_study_group_btn")
                        ) { Text("Join Code") }
                    }
                    when (val st = groupsState) {
                        UiState.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        is UiState.Error -> Text(st.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        is UiState.Success -> {
                            if (st.data.isEmpty()) {
                                EmptyStudyState(
                                    icon = Icons.Filled.Groups,
                                    title = "No Study Groups Yet",
                                    subtitle = "Create a private study group and share its 6-character invite code with classmates."
                                )
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(st.data, key = { it.id }) { grp ->
                                        Surface(
                                            shape = MaterialTheme.shapes.medium,
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            onClick = { groupsViewModel.selectGroup(grp.id) }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(grp.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                    Text(
                                                        "${grp.memberUids.size} members • Code: ${grp.inviteCode}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                Icon(Icons.Filled.ChevronRight, contentDescription = "Open group")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    GroupDetailContent(
                        group = activeGroup,
                        currentUserId = currentUserId,
                        itemsState = itemsState,
                        onCopyCode = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("StudyFlow Group Code", activeGroup.inviteCode))
                            onShowMessage("Copied invite code: ${activeGroup.inviteCode}")
                        },
                        onPostClick = { showPostItem = true },
                        onToggleComplete = { item -> groupsViewModel.toggleItemCompleted(activeGroup.id, item) },
                        onLeaveOrDelete = { groupsViewModel.leaveGroup(activeGroup) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )

    if (showCreateGroup) {
        var name by remember { mutableStateOf("") }
        var tag by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateGroup = false },
            title = { Text("Create Study Group") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Group Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tag,
                        onValueChange = { tag = it },
                        label = { Text("Subject Tag (e.g., AP Biology)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Description (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        groupsViewModel.createGroup(name, tag, desc, true, displayName)
                        showCreateGroup = false
                    },
                    enabled = name.trim().length >= 2
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroup = false }) { Text("Cancel") }
            }
        )
    }

    if (showJoinGroup) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showJoinGroup = false },
            title = { Text("Join with Invite Code") },
            text = {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(12) },
                    label = { Text("6-character Invite Code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        groupsViewModel.joinByCode(code, displayName)
                        showJoinGroup = false
                    },
                    enabled = code.trim().length >= 6
                ) { Text("Join") }
            },
            dismissButton = {
                TextButton(onClick = { showJoinGroup = false }) { Text("Cancel") }
            }
        )
    }

    if (showPostItem && activeGroup != null) {
        var itemType by remember { mutableStateOf("TASK") }
        var itemTitle by remember { mutableStateOf("") }
        var itemBody by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPostItem = false },
            title = { Text("Share in ${activeGroup.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("TASK", "GOAL", "NOTE", "QUIZ").forEach { t ->
                            FilterChip(selected = itemType == t, onClick = { itemType = t }, label = { Text(t) })
                        }
                    }
                    OutlinedTextField(
                        value = itemTitle,
                        onValueChange = { itemTitle = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = itemBody,
                        onValueChange = { itemBody = it },
                        label = { Text("Details / Note / Challenge prompt") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        groupsViewModel.postItem(activeGroup, displayName, itemType, itemTitle, itemBody)
                        showPostItem = false
                    },
                    enabled = itemTitle.isNotBlank()
                ) { Text("Share") }
            },
            dismissButton = {
                TextButton(onClick = { showPostItem = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun GroupDetailContent(
    group: StudyGroupDoc,
    currentUserId: String,
    itemsState: UiState<List<com.example.data.remote.GroupItemDoc>>,
    onCopyCode: () -> Unit,
    onPostClick: () -> Unit,
    onToggleComplete: (com.example.data.remote.GroupItemDoc) -> Unit,
    onLeaveOrDelete: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                onClick = onCopyCode,
                leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text("Code: ${group.inviteCode}") }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(onClick = onPostClick) { Text("+ Share") }
                TextButton(onClick = onLeaveOrDelete) {
                    Text(if (group.ownerId == currentUserId) "Delete" else "Leave")
                }
            }
        }
        Text(
            text = "Members (${group.memberNames.size}): ${group.memberNames.joinToString(", ")}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        when (itemsState) {
            UiState.Loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is UiState.Error -> Text(itemsState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            is UiState.Success -> {
                if (itemsState.data.isEmpty()) {
                    Text(
                        "No shared study tasks, notes, or quiz challenges yet. Tap + Share to collaborate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(itemsState.data, key = { it.id }) { item ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = item.completed,
                                        onCheckedChange = { onToggleComplete(item) }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${item.itemType} • ${item.title}", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "By ${item.authorName}${if (item.body.isNotBlank()) " — ${item.body}" else ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
