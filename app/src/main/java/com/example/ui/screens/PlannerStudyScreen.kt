package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.agent.JarvisAgentOrchestrator
import com.example.agent.McqQuestion
import com.example.agent.StudyAgent
import com.example.alarm.JarvisAlarmScheduler
import com.example.data.ai.GeminiService
import com.example.data.local.AlarmReminderEntity
import com.example.data.local.JarvisDatabase
import com.example.data.preferences.JarvisPreferences
import com.example.ui.theme.JarvisAlert
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisBlue
import com.example.ui.theme.JarvisCardBg
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisGlassBorder
import com.example.ui.theme.JarvisPurple
import com.example.ui.theme.JarvisSuccess
import com.example.ui.theme.JarvisSurfaceVariant
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.JarvisWarning
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PlannerStudyScreen(
    database: JarvisDatabase,
    preferences: JarvisPreferences,
    orchestrator: JarvisAgentOrchestrator,
    geminiService: GeminiService,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisBackground)
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = JarvisSurfaceVariant,
            contentColor = JarvisCyan,
            indicator = { tabPositions ->
                SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = JarvisCyan
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("SMART ALARMS", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                icon = { Icon(Icons.Default.Alarm, contentDescription = "Alarms") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("STUDY AGENT", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                icon = { Icon(Icons.Default.School, contentDescription = "Study") }
            )
        }

        if (selectedTab == 0) {
            AlarmsSubTab(database, orchestrator, context)
        } else {
            StudySubTab(database, preferences, geminiService)
        }
    }
}

@Composable
fun AlarmsSubTab(
    database: JarvisDatabase,
    orchestrator: JarvisAgentOrchestrator,
    context: Context
) {
    val scope = rememberCoroutineScope()
    val alarms by database.alarmReminderDao().getAllAlarms().collectAsState(initial = emptyList())
    val alarmScheduler = remember { JarvisAlarmScheduler(context) }
    var alarmInputText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "NATURAL LANGUAGE SCHEDULER",
            color = JarvisCyan,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Text(
            text = "Type or speak: \"Tomorrow at 9 AM, remind me to bring milk\"",
            color = JarvisTextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = alarmInputText,
                onValueChange = { alarmInputText = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("alarm_input_field"),
                placeholder = { Text("e.g. In 15 minutes call doctor", color = JarvisTextMuted, fontSize = 12.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = JarvisTextPrimary,
                    unfocusedTextColor = JarvisTextPrimary,
                    focusedBorderColor = JarvisCyan,
                    unfocusedBorderColor = JarvisGlassBorder
                )
            )

            Button(
                onClick = {
                    if (alarmInputText.isNotBlank()) {
                        orchestrator.handleUserInstruction("Remind me: $alarmInputText")
                        alarmInputText = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = Color.Black),
                modifier = Modifier.testTag("add_alarm_button")
            ) {
                Text("Schedule", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "SCHEDULED TIMELINE (${alarms.size})",
            color = JarvisCyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (alarms.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No scheduled alarms or reminders.", color = JarvisTextMuted, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(alarms, key = { it.id }) { alarm ->
                    AlarmCard(
                        alarm = alarm,
                        onCancel = {
                            scope.launch {
                                alarmScheduler.cancelAlarm(alarm.id)
                                database.alarmReminderDao().deleteAlarm(alarm)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AlarmCard(alarm: AlarmReminderEntity, onCancel: () -> Unit) {
    val dateStr = SimpleDateFormat("EEE, MMM dd 'at' hh:mm a", Locale.getDefault()).format(Date(alarm.triggerTimestamp))
    val isUpcoming = alarm.triggerTimestamp > System.currentTimeMillis() && !alarm.isCompleted

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = JarvisCardBg,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, JarvisGlassBorder, RoundedCornerShape(10.dp))
            .testTag("alarm_item_${alarm.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alarm.title,
                    color = JarvisTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = dateStr,
                    color = if (isUpcoming) JarvisCyan else JarvisTextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                if (alarm.isCompleted) {
                    Text(
                        text = "Completed",
                        color = JarvisSuccess,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Default.AlarmOff,
                    contentDescription = "Cancel Alarm",
                    tint = JarvisAlert,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun StudySubTab(
    database: JarvisDatabase,
    preferences: JarvisPreferences,
    geminiService: GeminiService
) {
    val scope = rememberCoroutineScope()
    val studyAgent = remember { StudyAgent(geminiService, database.studyDao()) }

    var topicInput by remember { mutableStateOf("Newton's Laws of Motion") }
    var isLoading by remember { mutableStateOf(false) }
    var explanationText by remember { mutableStateOf("") }
    var quizQuestions by remember { mutableStateOf<List<McqQuestion>>(emptyList()) }
    var selectedAnswers by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var score by remember { mutableIntStateOf(-1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "AI STUDY & REVISION MENTOR",
            color = JarvisCyan,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Text(
            text = "Master any concept with simple Hindi explanations and test yourself with MCQs",
            color = JarvisTextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )

        OutlinedTextField(
            value = topicInput,
            onValueChange = { topicInput = it },
            label = { Text("Study Subject / Chapter Topic") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("study_topic_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = JarvisTextPrimary,
                unfocusedTextColor = JarvisTextPrimary,
                focusedBorderColor = JarvisCyan,
                unfocusedBorderColor = JarvisGlassBorder
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (topicInput.isNotBlank()) {
                        isLoading = true
                        scope.launch {
                            val hindiLesson = studyAgent.explainLessonInHindi(topicInput, preferences.isApiKeyConfigured())
                            explanationText = hindiLesson
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("explain_hindi_button"),
                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = Color.Black)
            ) {
                Text("Explain in Hindi", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    if (topicInput.isNotBlank()) {
                        isLoading = true
                        scope.launch {
                            val quiz = studyAgent.generateMcqQuiz(topicInput, preferences.isApiKeyConfigured())
                            quizQuestions = quiz
                            selectedAnswers = emptyMap()
                            score = -1
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("generate_quiz_button"),
                colors = ButtonDefaults.buttonColors(containerColor = JarvisPurple, contentColor = Color.White)
            ) {
                Text("Generate Quiz", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = JarvisCyan)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("JARVIS synthesizing academic material...", color = JarvisCyan, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (explanationText.isNotBlank()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = JarvisCardBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, JarvisGlassBorder, RoundedCornerShape(12.dp))
                                .padding(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "HINDI EXPLANATION (पाठ स्पष्टीकरण)",
                                    color = JarvisCyan,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = explanationText,
                                    color = JarvisTextPrimary,
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }
                }

                if (quizQuestions.isNotEmpty()) {
                    item {
                        Text(
                            text = "INTERACTIVE MCQ TEST",
                            color = JarvisPurple,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(quizQuestions.indices.toList()) { qIdx ->
                        val q = quizQuestions[qIdx]
                        val userChoice = selectedAnswers[qIdx]

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = JarvisCardBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, JarvisGlassBorder, RoundedCornerShape(10.dp))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Q${qIdx + 1}: ${q.question}",
                                    color = JarvisTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                q.options.forEachIndexed { optIdx, optText ->
                                    val isSelected = userChoice == optIdx
                                    val isCorrect = optIdx == q.correctIndex

                                    val optionBg = when {
                                        userChoice == null -> JarvisSurfaceVariant
                                        isSelected && isCorrect -> JarvisSuccess.copy(alpha = 0.25f)
                                        isSelected && !isCorrect -> JarvisAlert.copy(alpha = 0.25f)
                                        !isSelected && isCorrect && userChoice != null -> JarvisSuccess.copy(alpha = 0.15f)
                                        else -> JarvisSurfaceVariant
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(optionBg)
                                            .border(1.dp, if (isSelected) JarvisCyan else Color.Transparent, RoundedCornerShape(8.dp))
                                            .clickable(enabled = userChoice == null) {
                                                selectedAnswers = selectedAnswers + (qIdx to optIdx)
                                            }
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = optText,
                                            color = JarvisTextPrimary,
                                            fontSize = 13.sp
                                        )
                                    }
                                }

                                if (userChoice != null && q.explanation.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Explanation: ${q.explanation}",
                                        color = JarvisCyan,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    item {
                        if (selectedAnswers.size == quizQuestions.size && score == -1) {
                            val correctCount = quizQuestions.indices.count { selectedAnswers[it] == quizQuestions[it].correctIndex }
                            score = correctCount
                        }

                        if (score != -1) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = JarvisSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = "🎯 Quiz Completed! Score: $score / ${quizQuestions.size}",
                                    color = JarvisSuccess,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(14.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
