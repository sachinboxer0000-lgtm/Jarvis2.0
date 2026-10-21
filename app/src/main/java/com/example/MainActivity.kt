package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agent.JarvisAgentOrchestrator
import com.example.data.ai.GeminiService
import com.example.data.local.JarvisDatabase
import com.example.data.preferences.JarvisPreferences
import com.example.ui.components.SensitiveActionDialog
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MemoryFilesScreen
import com.example.ui.screens.PlannerStudyScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisSurfaceVariant
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTheme
import com.example.voice.JarvisVoiceManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var database: JarvisDatabase
    private lateinit var preferences: JarvisPreferences
    private lateinit var geminiService: GeminiService
    private lateinit var voiceManager: JarvisVoiceManager
    private lateinit var orchestrator: JarvisAgentOrchestrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        database = JarvisDatabase.getDatabase(this)
        preferences = JarvisPreferences(this)
        geminiService = GeminiService(preferences)
        voiceManager = JarvisVoiceManager(this, preferences)
        orchestrator = JarvisAgentOrchestrator(this, database, preferences, geminiService, voiceManager)

        setContent {
            JarvisTheme {
                JarvisAppRoot(
                    database = database,
                    preferences = preferences,
                    geminiService = geminiService,
                    voiceManager = voiceManager,
                    orchestrator = orchestrator,
                    onOpenAppSettings = {
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", packageName, null)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(intent)
                        } catch (_: Exception) {}
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        voiceManager.destroy()
        super.onDestroy()
    }
}

@Composable
fun JarvisAppRoot(
    database: JarvisDatabase,
    preferences: JarvisPreferences,
    geminiService: GeminiService,
    voiceManager: JarvisVoiceManager,
    orchestrator: JarvisAgentOrchestrator,
    onOpenAppSettings: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    val pendingSensitiveAction by orchestrator.pendingSensitiveAction.collectAsState()

    // Runtime Permission Launcher (requested on-demand, NOT on startup)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Voice manager checks permission on demand
    }

    val requestPermissionsExplicitly: () -> Unit = {
        val permsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permsToRequest.add(Manifest.permission.READ_CONTACTS)
        permissionLauncher.launch(permsToRequest.toTypedArray())
    }

    val requestMicOnly: () -> Unit = {
        permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisBackground),
        bottomBar = {
            NavigationBar(
                containerColor = JarvisSurfaceVariant,
                contentColor = JarvisCyan,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .testTag("main_navigation_bar")
            ) {
                val navItems = listOf(
                    Triple(0, "Core", Icons.Default.RadioButtonChecked),
                    Triple(1, "Tasks", Icons.Default.Assignment),
                    Triple(2, "Memory", Icons.Default.FolderShared),
                    Triple(3, "Planner", Icons.Default.School),
                    Triple(4, "System", Icons.Default.Tune)
                )

                navItems.forEach { (index, label, icon) ->
                    val isSelected = selectedTab == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = label
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = JarvisCyan,
                            indicatorColor = JarvisCyan,
                            unselectedIconColor = JarvisTextMuted,
                            unselectedTextColor = JarvisTextMuted
                        ),
                        modifier = Modifier.testTag("nav_item_$index")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            when (selectedTab) {
                0 -> HomeScreen(
                    orchestrator = orchestrator,
                    voiceManager = voiceManager,
                    onRequestMicPermission = requestMicOnly,
                    onOpenAppSettings = onOpenAppSettings
                )
                1 -> TasksScreen(
                    database = database
                )
                2 -> MemoryFilesScreen(
                    database = database,
                    preferences = preferences
                )
                3 -> PlannerStudyScreen(
                    database = database,
                    preferences = preferences,
                    orchestrator = orchestrator,
                    geminiService = geminiService
                )
                4 -> SettingsScreen(
                    preferences = preferences,
                    geminiService = geminiService,
                    onPermissionsRequest = requestPermissionsExplicitly,
                    onOpenAppSettings = onOpenAppSettings
                )
            }

            // Sensitive Action Confirmation Dialog (Tier C Consequential Action)
            pendingSensitiveAction?.let { request ->
                SensitiveActionDialog(
                    request = request,
                    onConfirm = { editedPayload ->
                        scope.launch {
                            orchestrator.approveSensitiveAction(request, editedPayload)
                        }
                    },
                    onCancel = {
                        scope.launch {
                            orchestrator.cancelSensitiveAction(request)
                        }
                    }
                )
            }
        }
    }
}
