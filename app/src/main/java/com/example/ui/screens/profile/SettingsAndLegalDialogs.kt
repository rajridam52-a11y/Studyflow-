package com.example.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.SubjectEntity
import com.example.data.local.UserProfileEntity
import com.example.viewmodel.MainStudyFlowViewModel

@Composable
fun EditProfileDialog(
    profile: UserProfileEntity?,
    onDismiss: () -> Unit,
    onSave: (
        displayName: String,
        gradeLevel: String,
        schoolName: String,
        academicYear: String,
        prefStart: Int,
        prefEnd: Int,
        dailyGoalMins: Int,
        sound: Boolean,
        vibration: Boolean,
        language: String
    ) -> Unit
) {
    var name by remember { mutableStateOf(profile?.displayName ?: "Student") }
    var gradeLevel by remember { mutableStateOf(profile?.gradeLevel ?: "High School") }
    var schoolName by remember { mutableStateOf(profile?.schoolName.orEmpty()) }
    var academicYear by remember { mutableStateOf(profile?.academicYear ?: "2026-2027") }
    var prefStart by remember { mutableIntStateOf(profile?.preferredStartHour ?: 16) }
    var prefEnd by remember { mutableIntStateOf(profile?.preferredEndHour ?: 21) }
    var dailyGoal by remember { mutableStateOf((profile?.dailyGoalMinutes ?: 90).toString()) }
    var sound by remember { mutableStateOf(profile?.soundEnabled ?: true) }
    var vibration by remember { mutableStateOf(profile?.vibrationEnabled ?: true) }
    var language by remember { mutableStateOf(profile?.language ?: "English") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Student Profile") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Grade / Level", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Middle School", "High School", "College", "University").forEach { lvl ->
                        FilterChip(selected = gradeLevel == lvl, onClick = { gradeLevel = lvl }, label = { Text(lvl) })
                    }
                }
                OutlinedTextField(value = schoolName, onValueChange = { schoolName = it }, label = { Text("School / University") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = academicYear, onValueChange = { academicYear = it }, label = { Text("Academic Year / Semester") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = dailyGoal,
                    onValueChange = { dailyGoal = it.filter(Char::isDigit) },
                    label = { Text("Daily Study Goal (minutes)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Preferred Study Window: ${prefStart}:00 - ${prefEnd}:00", style = MaterialTheme.typography.labelLarge)
                Slider(value = prefStart.toFloat(), onValueChange = { prefStart = it.toInt().coerceAtMost(prefEnd - 1) }, valueRange = 6f..20f)
                Slider(value = prefEnd.toFloat(), onValueChange = { prefEnd = it.toInt().coerceAtLeast(prefStart + 1) }, valueRange = 10f..23f)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Default Reminder Sound")
                    Switch(checked = sound, onCheckedChange = { sound = it })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Default Vibration")
                    Switch(checked = vibration, onCheckedChange = { vibration = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name,
                        gradeLevel,
                        schoolName,
                        academicYear,
                        prefStart,
                        prefEnd,
                        dailyGoal.toIntOrNull()?.coerceIn(15, 720) ?: 90,
                        sound,
                        vibration,
                        language
                    )
                    onDismiss()
                },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SubjectsManagerDialog(
    subjects: List<SubjectEntity>,
    viewModel: MainStudyFlowViewModel,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var newCode by remember { mutableStateOf("") }
    var newTeacher by remember { mutableStateOf("") }
    var targetAtt by remember { mutableStateOf("75") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Academic Subjects") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Subject Name (e.g., Physics)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("subject_name_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newCode, onValueChange = { newCode = it }, label = { Text("Code") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = targetAtt,
                        onValueChange = { targetAtt = it.filter(Char::isDigit) },
                        label = { Text("Min Att %") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(value = newTeacher, onValueChange = { newTeacher = it }, label = { Text("Instructor (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = {
                        viewModel.saveSubject(
                            name = newName,
                            code = newCode,
                            teacherName = newTeacher,
                            targetAttendancePercent = targetAtt.toIntOrNull() ?: 75
                        )
                        newName = ""
                        newCode = ""
                        newTeacher = ""
                    },
                    enabled = newName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().testTag("add_subject_save_btn")
                ) { Text("Add Subject") }

                HorizontalDivider()
                if (subjects.isEmpty()) {
                    Text("No subjects added yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(subjects, key = { it.id }) { s ->
                            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(s.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text("Target attendance: ${s.targetAttendancePercent}%", style = MaterialTheme.typography.labelSmall)
                                    }
                                    IconButton(onClick = { viewModel.deleteSubject(s.id) }) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete subject")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
fun PinSecurityDialog(
    pinEnabled: Boolean,
    biometricEnabled: Boolean,
    onEnablePin: (String) -> Unit,
    onDisablePin: () -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var pinInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App Lock & Privacy") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (pinEnabled) "PIN Lock is currently enabled." else "Set a 4 to 8 digit PIN to protect your academic workspace.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { if (it.length <= 8) pinInput = it.filter(Char::isDigit) },
                    label = { Text(if (pinEnabled) "New PIN (4-8 digits)" else "Enter PIN (4-8 digits)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        onEnablePin(pinInput)
                        pinInput = ""
                    },
                    enabled = pinInput.length >= 4,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (pinEnabled) "Update PIN" else "Enable PIN Lock") }
                if (pinEnabled) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Unlock with Biometrics")
                        Switch(checked = biometricEnabled, onCheckedChange = onToggleBiometric)
                    }
                    OutlinedButton(onClick = onDisablePin, modifier = Modifier.fillMaxWidth()) {
                        Text("Disable App Lock")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
fun InfoLegalDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun FeedbackDialog(
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send StudyFlow Feedback") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Share suggestions or bug reports...") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(text)
                    onDismiss()
                },
                enabled = text.isNotBlank()
            ) { Text("Submit") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun DeleteAllDataDialog(
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Account Data") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "This permanently deletes all StudyFlow cloud records and local offline data for this Google account. Type DELETE to confirm.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = confirmText,
                    onValueChange = { confirmText = it },
                    label = { Text("Type DELETE") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmDelete()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                enabled = confirmText.trim().equals("DELETE", ignoreCase = true)
            ) { Text("Delete Everything") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
