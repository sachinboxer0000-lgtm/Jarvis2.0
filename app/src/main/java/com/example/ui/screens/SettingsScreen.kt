package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.ai.GeminiService
import com.example.data.preferences.ConnectionStatus
import com.example.data.preferences.JarvisPreferences
import com.example.service.JarvisVoiceService
import com.example.ui.theme.JarvisAlert
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisBlue
import com.example.ui.theme.JarvisCardBg
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisGlassBorder
import com.example.ui.theme.JarvisSuccess
import com.example.ui.theme.JarvisSurfaceVariant
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.JarvisWarning
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: JarvisPreferences,
    geminiService: GeminiService,
    onPermissionsRequest: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val connectionStatus by preferences.connectionStatusFlow.collectAsState()
    val connectionMessage by preferences.connectionMessageFlow.collectAsState()
    val selectedModel by preferences.selectedModelFlow.collectAsState()
    val availableModels by preferences.modelsListFlow.collectAsState()
    val wakePhrase by preferences.wakePhraseFlow.collectAsState()
    val voiceLang by preferences.voiceLangFlow.collectAsState()
    val isTtsEnabled by preferences.ttsEnabledFlow.collectAsState()
    val isBackgroundServiceEnabled by preferences.backgroundServiceFlow.collectAsState()

    var customApiKeyInput by remember { mutableStateOf(preferences.getCustomApiKey()) }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var wakePhraseInput by remember { mutableStateOf(wakePhrase) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var isRefreshingModels by remember { mutableStateOf(false) }
    var isModelDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "SYSTEM & AI CONFIGURATION",
            color = JarvisCyan,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        // 1. Connection Status Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = JarvisCardBg,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, JarvisGlassBorder, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                val (statusIcon, statusColor, statusLabel) = when (connectionStatus) {
                    ConnectionStatus.CONNECTED -> Triple(Icons.Default.CloudDone, JarvisSuccess, "CONNECTED & ACTIVE")
                    ConnectionStatus.TESTING -> Triple(Icons.Default.HourglassTop, JarvisCyan, "TESTING LATENCY...")
                    ConnectionStatus.ERROR -> Triple(Icons.Default.Error, JarvisAlert, "CONNECTION ERROR")
                    ConnectionStatus.UNCONFIGURED -> Triple(Icons.Default.CloudOff, JarvisWarning, "UNCONFIGURED / NOT TESTED")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = statusIcon, contentDescription = statusLabel, tint = statusColor)
                        Text(
                            text = statusLabel,
                            color = statusColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (isTestingConnection) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = JarvisCyan)
                    }
                }

                if (connectionMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = connectionMessage,
                        color = JarvisTextSecondary,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            isTestingConnection = true
                            scope.launch {
                                val key = preferences.getEffectiveApiKey()
                                val model = preferences.getSelectedModel()
                                val res = geminiService.testConnection(key, model)
                                if (res.isSuccess) {
                                    val r = res.getOrNull()!!
                                    preferences.setConnectionResult(
                                        ConnectionStatus.CONNECTED,
                                        "Verified in ${r.latencyMs}ms using ${r.modelUsed}. Core response: \"${r.reply}\""
                                    )
                                } else {
                                    preferences.setConnectionResult(
                                        ConnectionStatus.ERROR,
                                        res.exceptionOrNull()?.message ?: "Connection test failed."
                                    )
                                }
                                isTestingConnection = false
                            }
                        },
                        enabled = !isTestingConnection,
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = Color.Black),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_connection_btn")
                    ) {
                        Text("Test Connection", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            isRefreshingModels = true
                            scope.launch {
                                val key = preferences.getEffectiveApiKey()
                                val res = geminiService.fetchAvailableModels(key)
                                if (res.isSuccess) {
                                    preferences.setAvailableModels(res.getOrNull()!!)
                                    Toast.makeText(context, "Loaded ${res.getOrNull()?.size} Gemini models", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Model listing: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                }
                                isRefreshingModels = false
                            }
                        },
                        enabled = !isRefreshingModels,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisCyan),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("refresh_models_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Refresh Models", fontSize = 12.sp)
                    }
                }
            }
        }

        // 2. Gemini API Key Configuration
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = JarvisCardBg,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, JarvisGlassBorder, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Key, contentDescription = "API Key", tint = JarvisCyan)
                    Text("GEMINI API CREDENTIALS", color = JarvisCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Text(
                    text = "Configure in-app credentials without rebuilding the app. Stored privately on-device in secure sandboxed preferences.",
                    color = JarvisTextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                )

                OutlinedTextField(
                    value = customApiKeyInput,
                    onValueChange = { customApiKeyInput = it },
                    label = { Text("Gemini API Key") },
                    visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                            Icon(
                                imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle Visibility",
                                tint = JarvisCyan
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = JarvisTextPrimary,
                        unfocusedTextColor = JarvisTextPrimary,
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisGlassBorder
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = {
                            preferences.setCustomApiKey(customApiKeyInput)
                            Toast.makeText(context, "API Key saved. Ready for testing.", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = Color.Black),
                        modifier = Modifier.testTag("save_api_key_btn")
                    ) {
                        Text("Save Credentials", fontSize = 12.sp)
                    }
                }
            }
        }

        // 3. Model Selection Dropdown
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = JarvisCardBg,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, JarvisGlassBorder, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("SELECTED GEMINI MODEL", color = JarvisCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "Select active model from verified Google AI generative models",
                    color = JarvisTextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                )

                ExposedDropdownMenuBox(
                    expanded = isModelDropdownExpanded,
                    onExpandedChange = { isModelDropdownExpanded = !isModelDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedModel,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isModelDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .testTag("model_selector_dropdown"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary,
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisGlassBorder
                        )
                    )

                    ExposedDropdownMenu(
                        expanded = isModelDropdownExpanded,
                        onDismissRequest = { isModelDropdownExpanded = false }
                    ) {
                        availableModels.forEach { modelName ->
                            DropdownMenuItem(
                                text = { Text(modelName, color = JarvisTextPrimary) },
                                onClick = {
                                    preferences.setSelectedModel(modelName)
                                    isModelDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // 4. Voice & Interaction Preferences
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = JarvisCardBg,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, JarvisGlassBorder, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("VOICE & CONVERSATION", color = JarvisCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = wakePhraseInput,
                    onValueChange = {
                        wakePhraseInput = it
                        preferences.setWakePhrase(it)
                    },
                    label = { Text("Wake Phrase (e.g. Hey JARVIS)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Spoken Responses Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Spoken Audio Feedback (TTS)", color = JarvisTextPrimary, fontSize = 13.sp)
                        Text("Spoken replies in Hindi/English", color = JarvisTextMuted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = isTtsEnabled,
                        onCheckedChange = { preferences.setTtsEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = JarvisCyan, checkedTrackColor = JarvisBlue)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Background Service Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Background Voice Standby Service", color = JarvisTextPrimary, fontSize = 13.sp)
                        Text("Foreground service with persistent notification", color = JarvisTextMuted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = isBackgroundServiceEnabled,
                        onCheckedChange = { enabled ->
                            preferences.setBackgroundServiceEnabled(enabled)
                            val svcIntent = Intent(context, JarvisVoiceService::class.java).apply {
                                action = if (enabled) JarvisVoiceService.ACTION_START else JarvisVoiceService.ACTION_STOP
                            }
                            if (enabled) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(svcIntent)
                                } else {
                                    context.startService(svcIntent)
                                }
                            } else {
                                context.stopService(svcIntent)
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = JarvisCyan, checkedTrackColor = JarvisBlue)
                    )
                }
            }
        }

        // 5. System Permissions & Hindi Guidance
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = JarvisCardBg,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, JarvisGlassBorder, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Security, contentDescription = "Security", tint = JarvisCyan)
                    Text("SYSTEM PERMISSIONS AUDIT", color = JarvisCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                PermissionRow("Microphone (Voice)", ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionRow("Notifications", ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                }
                PermissionRow("Contacts (Communication Agent)", ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)

                Spacer(modifier = Modifier.height(12.dp))

                // Explanatory FAQ for the user
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF070B14), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Info, contentDescription = "FAQ", tint = JarvisCyan, modifier = Modifier.size(16.dp))
                            Text(
                                text = "माइक्रोफ़ोन को Android 'Record Audio' क्यों कहता है?",
                                color = JarvisCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Android में किसी भी ऐप द्वारा आपकी आवाज़ सुनकर उसे टेक्स्ट में बदलने (Speech-to-Text) के लिए RECORD_AUDIO नाम की सिस्टम परमिशन की आवश्यकता होती है। JARVIS कभी भी आपकी आवाज़ रिकॉर्ड या किसी सर्वर पर सेव नहीं करता। यह पूरी तरह सुरक्षित है और यदि आप अनुमति नहीं देना चाहते, तो आप सीधे लिखकर भी 100% बात कर सकते हैं।",
                            color = JarvisTextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenAppSettings,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("App Settings", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onPermissionsRequest,
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = Color.Black),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Request Perms", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionRow(name: String, isGranted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, color = JarvisTextPrimary, fontSize = 12.sp)
        Text(
            text = if (isGranted) "GRANTED" else "NOT GRANTED",
            color = if (isGranted) JarvisSuccess else JarvisWarning,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
