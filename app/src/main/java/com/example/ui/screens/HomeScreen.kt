package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.agent.ChatMessage
import com.example.agent.JarvisAgentOrchestrator
import com.example.agent.MessageSender
import com.example.ui.components.ArcReactorCore
import com.example.ui.components.WaveformVisualizer
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisBlue
import com.example.ui.theme.JarvisCardBg
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisGlassBorder
import com.example.ui.theme.JarvisGlow
import com.example.ui.theme.JarvisSuccess
import com.example.ui.theme.JarvisSurfaceVariant
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.JarvisWarning
import com.example.voice.JarvisVoiceManager
import com.example.voice.VoiceState

@Composable
fun HomeScreen(
    orchestrator: JarvisAgentOrchestrator,
    voiceManager: JarvisVoiceManager,
    onRequestMicPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val voiceState by voiceManager.voiceState.collectAsState()
    val audioAmplitude by voiceManager.audioAmplitude.collectAsState()
    val recognizedLiveText by voiceManager.recognizedText.collectAsState()
    val messages by orchestrator.chatMessages.collectAsState()
    val isExecuting by orchestrator.isExecuting.collectAsState()

    var textInput by remember { mutableStateOf("") }
    var showMicExplanationDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val quickCommands = listOf(
        "Tomorrow at 9 AM, remind me to bring milk",
        "Send Priya a message: meeting tomorrow at 10 AM",
        "Explain Newton's third law in Hindi",
        "Create HTML file portfolio.html",
        "Search YouTube for lo-fi chill music",
        "Remember that my favorite book is Cosmos"
    )

    fun handleMicClick() {
        if (voiceState == VoiceState.LISTENING) {
            voiceManager.stopListening()
            return
        }

        if (!voiceManager.hasMicPermission()) {
            showMicExplanationDialog = true
            return
        }

        val started = voiceManager.startListening()
        if (!started) {
            if (!voiceManager.isSpeechRecognitionAvailable()) {
                Toast.makeText(
                    context,
                    "वॉइस सेवा इस डिवाइस/एमुलेटर पर उपलब्ध नहीं है। कृपया नीचे टाइप करके उपयोग करें।",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisBackground)
    ) {
        // Holographic Arc Reactor Core Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            ArcReactorCore(
                voiceState = voiceState,
                audioAmplitude = audioAmplitude,
                onClick = { handleMicClick() }
            )
        }

        // Live Audio Waveform when listening
        WaveformVisualizer(
            amplitude = audioAmplitude,
            isListening = voiceState == VoiceState.LISTENING
        )

        // Live recognized speech preview
        if (voiceState == VoiceState.LISTENING && recognizedLiveText.isNotBlank()) {
            Text(
                text = "\"$recognizedLiveText\"",
                color = JarvisGlow,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp)
            )
        }

        // Quick command chips row
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickCommands) { command ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = JarvisCardBg,
                    modifier = Modifier
                        .border(1.dp, JarvisGlassBorder, RoundedCornerShape(20.dp))
                        .clickable { orchestrator.handleUserInstruction(command) }
                        .testTag("quick_command_chip")
                ) {
                    Text(
                        text = command,
                        color = JarvisCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Chat Message Log
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatBubble(
                    message = msg,
                    onSpeak = { voiceManager.speak(msg.text) },
                    onCopy = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("JARVIS Text", msg.text))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // Bottom text and voice input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Mic Toggle Button
            IconButton(
                onClick = { handleMicClick() },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (voiceState == VoiceState.LISTENING) JarvisGlow else JarvisSurfaceVariant)
                    .border(1.dp, JarvisCyan, CircleShape)
                    .testTag("mic_button")
            ) {
                Icon(
                    imageVector = if (voiceState == VoiceState.LISTENING) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = "Voice Input Toggle",
                    tint = if (voiceState == VoiceState.LISTENING) Color.Black else JarvisCyan
                )
            }

            // Text Input Field
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field"),
                placeholder = {
                    Text(
                        "Command in English or Hindi...",
                        color = JarvisTextMuted,
                        fontSize = 13.sp
                    )
                },
                maxLines = 3,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = JarvisTextPrimary,
                    unfocusedTextColor = JarvisTextPrimary,
                    focusedContainerColor = JarvisCardBg,
                    unfocusedContainerColor = JarvisCardBg,
                    focusedBorderColor = JarvisCyan,
                    unfocusedBorderColor = JarvisGlassBorder
                )
            )

            // Send Button
            IconButton(
                onClick = {
                    if (textInput.isNotBlank()) {
                        val prompt = textInput
                        textInput = ""
                        orchestrator.handleUserInstruction(prompt)
                    }
                },
                enabled = textInput.isNotBlank() && !isExecuting,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (textInput.isNotBlank()) JarvisCyan else JarvisSurfaceVariant)
                    .testTag("send_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send Command",
                    tint = if (textInput.isNotBlank()) Color.Black else JarvisTextMuted
                )
            }
        }
    }

    // Informative Reassuring Microphone Explanation Dialog
    if (showMicExplanationDialog) {
        Dialog(onDismissRequest = { showMicExplanationDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = JarvisCardBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, JarvisGlassBorder, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Mic Info",
                            tint = JarvisCyan
                        )
                        Text(
                            text = "माइक्रोफ़ोन अनुमति (Microphone Info)",
                            color = JarvisCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Android में आवाज़ पहचानने (Speech-to-Text) के लिए माइक्रोफ़ोन की आवश्यकता होती है, जिसे सिस्टम 'Record Audio' कहता है।",
                        color = JarvisTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "• JARVIS आपकी कोई भी ऑडियो गुप्त रूप से रिकॉर्ड या सेव नहीं करता।\n• इसका उपयोग केवल आपकी आवाज़ को कमांड में बदलने के लिए होता है।\n• यदि आप अनुमति नहीं देना चाहते, तो आप नीचे चैट बॉक्स में लिखकर 100% सभी फीचर्स चला सकते हैं।",
                        color = JarvisTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showMicExplanationDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("टाइप करें", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                showMicExplanationDialog = false
                                onOpenAppSettings()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("सेटिंग्स", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                showMicExplanationDialog = false
                                onRequestMicPermission()
                            },
                            modifier = Modifier.weight(1.2f),
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = Color.Black)
                        ) {
                            Text("अनुमति दें", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    onSpeak: () -> Unit,
    onCopy: () -> Unit
) {
    val isUser = message.sender == MessageSender.USER
    val bubbleColor = if (isUser) JarvisSurfaceVariant else JarvisCardBg
    val borderColor = if (isUser) JarvisBlue else JarvisGlassBorder

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(14.dp))
                .background(bubbleColor)
                .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isUser) "YOU" else "JARVIS AI",
                    color = if (isUser) JarvisBlue else JarvisCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                if (!isUser) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onSpeak, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Speak Response",
                                tint = JarvisCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Text",
                                tint = JarvisTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = message.text,
                color = JarvisTextPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}
