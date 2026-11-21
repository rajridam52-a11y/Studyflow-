package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.local.AppPreferences
import com.example.ui.auth.signOutUser
import com.example.ui.screens.profile.*
import com.example.ui.theme.AccentPalette
import com.example.ui.theme.ThemeMode
import com.example.viewmodel.MainStudyFlowViewModel
import com.example.viewmodel.StudyDateFormatters
import com.example.viewmodel.StudyFlowDashboardData
import com.example.viewmodel.StudyGroupsViewModel

@Composable
fun ProfileScreen(
    data: StudyFlowDashboardData,
    preferences: AppPreferences,
    isSyncing: Boolean,
    mainViewModel: MainStudyFlowViewModel,
    groupsViewModel: StudyGroupsViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var showEditProfile by remember { mutableStateOf(false) }
    var showSubjects by remember { mutableStateOf(false) }
    var showStudyGroups by remember { mutableStateOf(false) }
    var showSecurity by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showFeedback by remember { mutableStateOf(false) }
    var showDeleteData by remember { mutableStateOf(false) }

    val jsonExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) mainViewModel.exportBackupJson(uri)
    }

    val jsonImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) mainViewModel.importBackupJson(uri)
    }

    val profile = data.profile

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("profile_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "profile_card") {
            ElevatedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(54.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = (profile?.displayName?.firstOrNull() ?: 'S').uppercase(),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = profile?.displayName ?: "Student",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${profile?.gradeLevel ?: "High School"}${if (!profile?.schoolName.isNullOrBlank()) " • ${profile?.schoolName}" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!profile?.email.isNullOrBlank()) {
                                    Text(
                                        text = profile?.email.orEmpty(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        FilledTonalButton(
                            onClick = { showEditProfile = true },
                            modifier = Modifier.testTag("edit_profile_button")
                        ) { Text("Edit") }
                    }
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${profile?.currentStreak ?: 0}d", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Streak", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${data.subjects.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Subjects", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${profile?.dailyGoalMinutes ?: 90}m", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Daily Goal", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // Academic Workspace Shortcuts
        item(key = "academic_tools") {
            ElevatedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Academic Setup & Collaboration", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    ProfileActionRow(
                        icon = Icons.Filled.Book,
                        title = "Manage Subjects (${data.subjects.size})",
                        subtitle = "Configure subjects, course codes, and attendance targets",
                        onClick = { showSubjects = true },
                        testTag = "manage_subjects_row"
                    )
                    ProfileActionRow(
                        icon = Icons.Filled.Groups,
                        title = "Private Study Groups",
                        subtitle = "Create or join groups with invite codes, share goals, notes & tasks",
                        onClick = { showStudyGroups = true },
                        testTag = "study_groups_row"
                    )
                }
            }
        }

        // Cloud Sync & JSON Backup
        item(key = "sync_backup") {
            ElevatedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Cloud Sync & Local Backup", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        if (isSyncing) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                    Text(
                        text = "Status: ${preferences.syncStatusMessage}" +
                            if (preferences.lastSyncMillis > 0L) " • Last: ${StudyDateFormatters.formatDateTime(preferences.lastSyncMillis)}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Offline Mode", style = MaterialTheme.typography.bodyMedium)
                            Text("Keep all edits strictly local until you re-enable sync", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = preferences.offlineMode, onCheckedChange = mainViewModel::setOfflineMode)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { mainViewModel.triggerCloudSync() },
                            enabled = !isSyncing && !preferences.offlineMode,
                            modifier = Modifier.weight(1f).testTag("sync_now_button")
                        ) { Text("Sync Now") }
                        OutlinedButton(
                            onClick = { jsonExportLauncher.launch("StudyFlow-Backup-${StudyDateFormatters.todayDayKey()}.json") },
                            modifier = Modifier.weight(1f)
                        ) { Text("Export JSON") }
                    }
                    OutlinedButton(
                        onClick = { jsonImportLauncher.launch(arrayOf("application/json", "*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Restore Backup from JSON File") }
                }
            }
        }

        // Appearance & Security
        item(key = "appearance_security") {
            ElevatedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Theme & Accent", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = preferences.themeMode == mode,
                                onClick = { mainViewModel.setThemeMode(mode) },
                                label = { Text(mode.label) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AccentPalette.entries.forEach { palette ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { mainViewModel.setAccentPalette(palette) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(palette.previewColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (preferences.accentPalette == palette) {
                                        Icon(Icons.Filled.Check, contentDescription = "Selected ${palette.label}", tint = MaterialTheme.colorScheme.onPrimary)
                                    }
                                }
                                Text(palette.label.split(" ").first(), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    HorizontalDivider()
                    ProfileActionRow(
                        icon = Icons.Filled.Lock,
                        title = if (preferences.pinLockEnabled) "App Lock Enabled" else "App Lock (PIN & Biometrics)",
                        subtitle = "Protect your study notes, reminders, and progress on shared devices",
                        onClick = { showSecurity = true }
                    )
                }
            }
        }

        // Support, Legal & Account Actions
        item(key = "support_legal") {
            ElevatedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Help, Privacy & Account", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    ProfileActionRow(Icons.Filled.HelpOutline, "Help & FAQ", "Reminders, smart rescheduling, offline backups", { showHelp = true })
                    ProfileActionRow(Icons.Filled.Feedback, "Send Feedback", "Report an issue or suggest improvements", { showFeedback = true })
                    ProfileActionRow(Icons.Filled.PrivacyTip, "Privacy Policy", "How your academic data is stored and protected", { showPrivacy = true })
                    ProfileActionRow(Icons.Filled.Info, "About StudyFlow", "Version ${stringResource(R.string.app_version_display)} • ${stringResource(R.string.app_tagline)}", { showAbout = true })
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    ProfileActionRow(Icons.Filled.DeleteForever, "Delete Account Data", "Permanently erase all cloud and local records", { showDeleteData = true })
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { signOutUser(credentialManager, scope) },
                        modifier = Modifier.fillMaxWidth().testTag("sign_out_button")
                    ) {
                        Icon(Icons.Filled.Logout, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sign Out")
                    }
                }
            }
        }
    }

    if (showEditProfile) {
        EditProfileDialog(
            profile = profile,
            onDismiss = { showEditProfile = false },
            onSave = mainViewModel::updateProfile
        )
    }
    if (showSubjects) {
        SubjectsManagerDialog(
            subjects = data.subjects,
            viewModel = mainViewModel,
            onDismiss = { showSubjects = false }
        )
    }
    if (showStudyGroups) {
        StudyGroupsDialog(
            groupsViewModel = groupsViewModel,
            displayName = profile?.displayName ?: "Student",
            currentUserId = mainViewModel.currentUserId,
            onDismiss = { showStudyGroups = false },
            onShowMessage = mainViewModel::postMessage
        )
    }
    if (showSecurity) {
        PinSecurityDialog(
            pinEnabled = preferences.pinLockEnabled,
            biometricEnabled = preferences.biometricEnabled,
            onEnablePin = mainViewModel::enablePinLock,
            onDisablePin = mainViewModel::disablePinLock,
            onToggleBiometric = mainViewModel::setBiometricEnabled,
            onDismiss = { showSecurity = false }
        )
    }
    if (showHelp) {
        InfoLegalDialog(
            title = "StudyFlow Help & FAQ",
            body = "• Why might reminders delay on some phones?\n" +
                "Android manufacturers sometimes apply aggressive battery restrictions. Keep Notifications enabled and grant Alarms & Reminders permission in Reminders -> Reliability Info.\n\n" +
                "• How does Smart Rescheduling work?\n" +
                "When a reminder or study session is marked Missed, StudyFlow inspects your Timetable and Preferred Study Hours to suggest open conflict-free slots.\n\n" +
                "• Does StudyFlow work offline?\n" +
                "Yes! Homework, notes, flashcards, focus timer, and attendance are saved locally in Room and stay safe if you are offline or if cloud sync fails.",
            onDismiss = { showHelp = false }
        )
    }
    if (showPrivacy) {
        InfoLegalDialog(
            title = "StudyFlow Privacy Policy",
            body = "StudyFlow stores your academic records locally on your device using Room and syncs them only to your private Firebase Firestore account partition when online.\n\n" +
                "• Data collected: Your Google sign-in identity (UID, display name, email) and the study items you create.\n" +
                "• Study Groups: Only members invited via a private 6-character code can view shared group tasks or notes.\n" +
                "• AI Study Companion: Only the study prompt you submit is sent securely via Firebase AI Logic; no personal credentials are included.\n" +
                "• Control: You may export a full JSON backup or permanently delete all cloud and local records anytime.",
            onDismiss = { showPrivacy = false }
        )
    }
    if (showAbout) {
        InfoLegalDialog(
            title = "About StudyFlow",
            body = "StudyFlow v1.0.0\nStudy smarter. Stay organized. Achieve more.\n\n" +
                "Built with Kotlin, Jetpack Compose, Material 3, Room Database, WorkManager, DataStore, Firebase Authentication, Cloud Firestore, and Firebase AI Logic.",
            onDismiss = { showAbout = false }
        )
    }
    if (showFeedback) {
        FeedbackDialog(
            onSubmit = { msg -> mainViewModel.postMessage("Thank you for your feedback! Saved note: ${msg.take(40)}") },
            onDismiss = { showFeedback = false }
        )
    }
    if (showDeleteData) {
        DeleteAllDataDialog(
            onConfirmDelete = {
                mainViewModel.deleteAllMyData {
                    signOutUser(credentialManager, scope)
                }
            },
            onDismiss = { showDeleteData = false }
        )
    }
}

@Composable
private fun ProfileActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (testTag != null) it.testTag(testTag) else it }
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
