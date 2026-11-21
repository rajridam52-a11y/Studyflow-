package com.example.ui.navigation

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.backup.DataExportManager
import com.example.data.local.AppPreferences
import com.example.data.local.PreferencesManager
import com.example.data.local.StudyFlowDatabase
import com.example.data.remote.AiStudyRepository
import com.example.data.remote.CloudSyncRepository
import com.example.data.remote.StudyGroupRepository
import com.example.ui.components.CustomizeDashboardDialog
import com.example.ui.components.GlobalQuickAddDialog
import com.example.ui.components.GlobalSearchDialog
import com.example.ui.components.NotificationCenterDialog
import com.example.ui.screens.*
import com.example.viewmodel.*
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable object PlannerRoute
@Serializable object RemindersRoute
@Serializable object StudyRoute
@Serializable object ProfileRoute

private data class NavDestinationItem(
    val route: Any,
    val label: String,
    val routeKey: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val PrimaryDestinations = listOf(
    NavDestinationItem(HomeRoute, "Home", "home", Icons.Filled.Home, Icons.Outlined.Home),
    NavDestinationItem(PlannerRoute, "Planner", "planner", Icons.Filled.CalendarToday, Icons.Outlined.CalendarToday),
    NavDestinationItem(RemindersRoute, "Reminders", "reminders", Icons.Filled.NotificationsActive, Icons.Outlined.Notifications),
    NavDestinationItem(StudyRoute, "Study", "study", Icons.Filled.AutoStories, Icons.Outlined.AutoStories),
    NavDestinationItem(ProfileRoute, "Profile", "profile", Icons.Filled.Person, Icons.Outlined.Person)
)

@Composable
fun StudyFlowAppShell(
    user: FirebaseUser,
    preferences: AppPreferences,
    preferencesManager: PreferencesManager
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val uid = user.uid

    val localDb = remember(appContext) { StudyFlowDatabase.getInstance(appContext) }
    val firestore = remember(appContext) {
        FirebaseFirestore.getInstance(appContext.getString(R.string.firestore_database_id))
    }

    val mainViewModel: MainStudyFlowViewModel = viewModel(
        key = "main_$uid",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainStudyFlowViewModel(
                    appContext = appContext,
                    db = localDb,
                    preferencesManager = preferencesManager,
                    cloudSyncRepository = CloudSyncRepository(firestore, localDb),
                    exportManager = DataExportManager(appContext, localDb),
                    currentUserId = uid,
                    initialDisplayName = user.displayName.orEmpty(),
                    initialEmail = user.email.orEmpty()
                ) as T
            }
        }
    )

    val timerViewModel: FocusTimerViewModel = viewModel(
        key = "timer_$uid",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return FocusTimerViewModel(
                    appContext = appContext,
                    onSessionCompleted = { mode, minutes, subjectId, subjectName, startedAt ->
                        mainViewModel.recordFocusSession(mode, minutes, subjectId, subjectName, startedAt)
                    }
                ) as T
            }
        }
    )

    val aiViewModel: AiAssistantViewModel = viewModel(
        key = "ai_$uid",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AiAssistantViewModel(
                    aiRepository = AiStudyRepository(),
                    preferencesManager = preferencesManager
                ) as T
            }
        }
    )

    val groupsViewModel: StudyGroupsViewModel = viewModel(
        key = "groups_$uid",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return StudyGroupsViewModel(
                    repository = StudyGroupRepository(firestore),
                    currentUserId = uid
                ) as T
            }
        }
    )

    val dashboardState by mainViewModel.dashboardState.collectAsStateWithLifecycle()
    val statusMessage by mainViewModel.statusMessage.collectAsStateWithLifecycle()
    val isSyncing by mainViewModel.isSyncing.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            mainViewModel.clearStatusMessage()
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    var showQuickAdd by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var showCustomize by remember { mutableStateOf(false) }
    var pendingStudyToolIndex by remember { mutableIntStateOf(-1) }

    val navigateToKey: (String) -> Unit = { key ->
        val route: Any = when (key) {
            "planner" -> PlannerRoute
            "reminders" -> RemindersRoute
            "study" -> StudyRoute
            "profile" -> ProfileRoute
            else -> HomeRoute
        }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showQuickAdd = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("global_quick_add_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Quick Add")
                }
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar {
                        PrimaryDestinations.forEach { dest ->
                            val selected = currentDest?.hasRoute(dest.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToKey(dest.routeKey) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_${dest.routeKey}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail {
                        Spacer(Modifier.height(12.dp))
                        PrimaryDestinations.forEach { dest ->
                            val selected = currentDest?.hasRoute(dest.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToKey(dest.routeKey) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("rail_${dest.routeKey}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    when (val state = dashboardState) {
                        UiState.Loading -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator()
                                    Text("Loading your StudyFlow workspace...", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }

                        is UiState.Error -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }

                        is UiState.Success -> {
                            val data = state.data
                            NavHost(
                                navController = navController,
                                startDestination = HomeRoute,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                composable<HomeRoute> {
                                    HomeScreen(
                                        data = data,
                                        preferences = preferences,
                                        viewModel = mainViewModel,
                                        onOpenSearch = { showSearch = true },
                                        onOpenNotifications = { showNotifications = true },
                                        onOpenCustomizeDashboard = { showCustomize = true },
                                        onOpenQuickAdd = { showQuickAdd = true },
                                        onNavigateTab = navigateToKey,
                                        onNavigateToStudyTool = { toolIdx ->
                                            pendingStudyToolIndex = toolIdx
                                            navigateToKey("study")
                                        }
                                    )
                                }
                                composable<PlannerRoute> {
                                    PlannerScreen(data = data, viewModel = mainViewModel)
                                }
                                composable<RemindersRoute> {
                                    RemindersScreen(
                                        data = data,
                                        viewModel = mainViewModel,
                                        onOpenNotificationCenter = { showNotifications = true }
                                    )
                                }
                                composable<StudyRoute> {
                                    StudyScreen(
                                        initialToolIndex = pendingStudyToolIndex,
                                        onToolIndexConsumed = { pendingStudyToolIndex = -1 },
                                        data = data,
                                        mainViewModel = mainViewModel,
                                        timerViewModel = timerViewModel,
                                        aiViewModel = aiViewModel
                                    )
                                }
                                composable<ProfileRoute> {
                                    ProfileScreen(
                                        data = data,
                                        preferences = preferences,
                                        isSyncing = isSyncing,
                                        mainViewModel = mainViewModel,
                                        groupsViewModel = groupsViewModel
                                    )
                                }
                            }

                            if (showQuickAdd) {
                                GlobalQuickAddDialog(
                                    subjects = data.subjects,
                                    onDismiss = { showQuickAdd = false },
                                    onQuickAddSubject = { name -> mainViewModel.saveSubject(name = name) },
                                    onAddHomework = { sId, sName, title, desc, due, prio ->
                                        mainViewModel.saveHomework(
                                            subjectId = sId,
                                            subjectName = sName,
                                            title = title,
                                            description = desc,
                                            dueAtMillis = due,
                                            priority = prio
                                        )
                                    },
                                    onAddReminder = { title, notes, cat, prio, trig ->
                                        mainViewModel.saveReminder(
                                            title = title,
                                            notes = notes,
                                            category = cat,
                                            priority = prio,
                                            triggerAtMillis = trig,
                                            advanceMinutes = 10,
                                            repeatRule = "NONE",
                                            customRepeatDays = "",
                                            soundEnabled = data.profile?.soundEnabled ?: true,
                                            vibrationEnabled = data.profile?.vibrationEnabled ?: true
                                        )
                                    },
                                    onAddTimetableSession = { sId, sName, title, day, startMin, endMin ->
                                        mainViewModel.saveTimetableSession(
                                            subjectId = sId,
                                            subjectName = sName,
                                            title = title,
                                            dayOfWeek = day,
                                            startMinuteOfDay = startMin,
                                            endMinuteOfDay = endMin,
                                            sessionKind = "STUDY",
                                            notifyBeforeMinutes = 10,
                                            location = ""
                                        )
                                    },
                                    onAddNote = { sId, sName, ch, title, content ->
                                        mainViewModel.saveNote(
                                            subjectId = sId,
                                            subjectName = sName,
                                            chapterName = ch,
                                            title = title,
                                            content = content,
                                            important = false,
                                            bookmarked = false
                                        )
                                    },
                                    onAddGoal = { title, period, target, unit, sId ->
                                        mainViewModel.saveGoal(
                                            title = title,
                                            period = period,
                                            targetValue = target,
                                            unit = unit,
                                            subjectId = sId
                                        )
                                    }
                                )
                            }

                            if (showSearch) {
                                GlobalSearchDialog(
                                    data = data,
                                    onDismiss = { showSearch = false },
                                    onNavigateToRoute = navigateToKey
                                )
                            }

                            if (showNotifications) {
                                NotificationCenterDialog(
                                    notifications = data.notifications,
                                    onDismiss = { showNotifications = false },
                                    onMarkAllRead = mainViewModel::markAllNotificationsRead,
                                    onClearAll = mainViewModel::clearNotifications
                                )
                            }

                            if (showCustomize) {
                                CustomizeDashboardDialog(
                                    currentOrder = preferences.dashboardOrder,
                                    hiddenCards = preferences.hiddenDashboardCards,
                                    onDismiss = { showCustomize = false },
                                    onSave = mainViewModel::saveDashboardLayout
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
