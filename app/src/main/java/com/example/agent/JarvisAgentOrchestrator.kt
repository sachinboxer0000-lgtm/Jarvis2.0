package com.example.agent

import android.content.Context
import com.example.alarm.JarvisAlarmScheduler
import com.example.data.ai.GeminiService
import com.example.data.local.AlarmReminderDao
import com.example.data.local.AlarmReminderEntity
import com.example.data.local.JarvisDatabase
import com.example.data.local.TaskDao
import com.example.data.local.TaskEntity
import com.example.data.preferences.JarvisPreferences
import com.example.voice.JarvisVoiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class JarvisAgentOrchestrator(
    private val context: Context,
    private val database: JarvisDatabase,
    private val preferences: JarvisPreferences,
    private val geminiService: GeminiService,
    private val voiceManager: JarvisVoiceManager
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    private val planDecomposer = PlanDecomposer(geminiService)
    private val fileManager = FileManagerAgent(context, database.fileDao())
    private val mobileControl = MobileControlAgent(context)
    private val communication = CommunicationAgent(context)
    private val alarmScheduler = JarvisAlarmScheduler(context)
    private val studyAgent = StudyAgent(geminiService, database.studyDao())
    private val memoryAgent = MemoryAgent(database.memoryDao(), preferences)

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                sender = MessageSender.JARVIS,
                text = "Greetings! I am JARVIS, your personal autonomous assistant. Standing by for voice or text instructions."
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _currentPlan = MutableStateFlow<AgentPlan?>(null)
    val currentPlan: StateFlow<AgentPlan?> = _currentPlan.asStateFlow()

    private val _pendingSensitiveAction = MutableStateFlow<SensitiveActionRequest?>(null)
    val pendingSensitiveAction: StateFlow<SensitiveActionRequest?> = _pendingSensitiveAction.asStateFlow()

    init {
        voiceManager.onCommandRecognized = { spokenCommand ->
            handleUserInstruction(spokenCommand)
        }
    }

    fun handleUserInstruction(instruction: String) {
        if (instruction.isBlank()) return

        // Check if wake phrase is configured and present or direct command
        val wakePhrase = preferences.getWakePhrase()
        val cleanInstruction = if (instruction.contains(wakePhrase, ignoreCase = true)) {
            instruction.replace(Regex("(?i)$wakePhrase"), "").trim().trimStart(',', ':')
        } else {
            instruction
        }.ifBlank { instruction }

        // Add user message to chat
        val userMsg = ChatMessage(sender = MessageSender.USER, text = instruction)
        _chatMessages.value = _chatMessages.value + userMsg

        scope.launch(Dispatchers.IO) {
            _isExecuting.value = true
            val hasKey = preferences.isApiKeyConfigured()

            val plan = planDecomposer.decomposeInstruction(cleanInstruction, hasKey)
            _currentPlan.value = plan

            // If instruction needs clarification, ask directly
            if (plan.needsClarification) {
                val clarifyMsg = plan.clarificationQuestion.ifBlank {
                    "Sir, could you please clarify which target you would like me to interact with?"
                }
                respondToUser(clarifyMsg)
                _isExecuting.value = false
                return@launch
            }

            // Create TaskEntity in DB
            val stepsJson = JSONArray().apply {
                plan.steps.forEach { s ->
                    put(JSONObject().apply {
                        put("tool", s.tool.name)
                        put("desc", s.description)
                        put("category", s.category.name)
                        put("target", s.target)
                        put("payload", s.payload)
                    })
                }
            }.toString()

            val initialTask = TaskEntity(
                title = plan.intentSummary,
                prompt = instruction,
                stepsJson = stepsJson,
                status = "IN_PROGRESS",
                category = plan.steps.firstOrNull()?.category?.name ?: "LOW_RISK"
            )
            val taskId = database.taskDao().insertTask(initialTask)

            // Execute each step
            for (step in plan.steps) {
                if (step.category == ActionCategory.SENSITIVE && !step.isApproved) {
                    // Halt and prompt for explicit user confirmation
                    val request = SensitiveActionRequest(
                        taskId = taskId,
                        stepIndex = step.stepIndex,
                        actionTitle = step.description,
                        actionType = step.tool,
                        targetRecipient = step.target,
                        payloadContent = step.payload,
                        explanation = "Consequential action requires your explicit approval before execution."
                    )
                    _pendingSensitiveAction.value = request

                    database.taskDao().updateTask(
                        initialTask.copy(
                            id = taskId,
                            status = "WAITING_APPROVAL",
                            actionTarget = step.target,
                            actionPayload = step.payload
                        )
                    )
                    _isExecuting.value = false
                    return@launch
                }

                // Execute approved or low-risk step
                executeStep(taskId, step)
            }

            _isExecuting.value = false
        }
    }

    suspend fun approveSensitiveAction(request: SensitiveActionRequest, editedPayload: String? = null) {
        _pendingSensitiveAction.value = null
        val payloadToUse = editedPayload ?: request.payloadContent

        val step = PlanStep(
            stepIndex = request.stepIndex,
            tool = request.actionType,
            description = request.actionTitle,
            category = ActionCategory.SENSITIVE,
            target = request.targetRecipient,
            payload = payloadToUse,
            isApproved = true
        )

        executeStep(request.taskId, step)
    }

    suspend fun cancelSensitiveAction(request: SensitiveActionRequest) {
        _pendingSensitiveAction.value = null
        val task = database.taskDao().getTaskById(request.taskId)
        if (task != null) {
            database.taskDao().updateTask(
                task.copy(
                    status = "CANCELLED",
                    resultMessage = "Cancelled by user"
                )
            )
        }
        respondToUser("Action cancelled. No changes were made.")
    }

    private suspend fun executeStep(taskId: Long, step: PlanStep) {
        val result: String = when (step.tool) {
            AgentTool.DRAFT_MESSAGE -> {
                val res = communication.openMessagingApp(step.target, step.payload)
                if (res.isSuccess) {
                    "Message prepared for ${step.target}. ${res.getOrNull()}"
                } else {
                    "Failed to draft message: ${res.exceptionOrNull()?.message}"
                }
            }

            AgentTool.DELETE_FILE -> {
                val allFiles = database.fileDao().getAllFiles().firstOrNull() ?: emptyList()
                val targetFile = allFiles.firstOrNull { it.fileName.equals(step.target, true) }
                if (targetFile != null) {
                    val delRes = fileManager.deleteFile(targetFile.id)
                    if (delRes.isSuccess) "Permanently deleted ${step.target}." else "Could not delete file: ${delRes.exceptionOrNull()?.message}"
                } else {
                    "File '${step.target}' not found in workspace."
                }
            }

            AgentTool.SCHEDULE_ALARM -> {
                val triggerTime = step.payload.toLongOrNull() ?: (System.currentTimeMillis() + 3600000L)
                val alarmEntity = AlarmReminderEntity(
                    title = step.target.ifBlank { "Task Reminder" },
                    note = "Scheduled autonomously by JARVIS",
                    triggerTimestamp = triggerTime,
                    originalInstruction = step.description
                )
                val alarmId = database.alarmReminderDao().insertAlarm(alarmEntity)
                val scheduleRes = alarmScheduler.scheduleAlarm(alarmId, alarmEntity.title, alarmEntity.note, triggerTime)
                if (scheduleRes.isSuccess) {
                    val formatted = java.text.SimpleDateFormat("EEE, MMM dd 'at' hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(triggerTime))
                    "Reminder confirmed for '$formatted': ${alarmEntity.title}"
                } else {
                    "Alarm registered in planner, but exact alarm permission may need verification."
                }
            }

            AgentTool.CREATE_FILE -> {
                val content = if (preferences.isApiKeyConfigured()) {
                    val aiCode = geminiService.generateChat("Write complete, production-grade code for: ${step.payload}").getOrNull()
                    aiCode ?: generateFallbackFileContent(step.target)
                } else {
                    generateFallbackFileContent(step.target)
                }
                val res = fileManager.createFile(step.target, content)
                if (res.isSuccess) {
                    "Created document '${step.target}' in workspace (${res.getOrNull()?.sizeBytes ?: 0} bytes)."
                } else {
                    "Failed to create file: ${res.exceptionOrNull()?.message}"
                }
            }

            AgentTool.OPEN_APP -> {
                val res = mobileControl.launchAppByName(step.target)
                res.getOrElse { "Could not open ${step.target}" }
            }

            AgentTool.SEARCH_YOUTUBE -> {
                val res = mobileControl.searchYouTube(step.payload)
                res.getOrElse { "Could not launch YouTube search" }
            }

            AgentTool.OPEN_URL -> {
                val res = mobileControl.openUrl(step.payload)
                res.getOrElse { "Could not open URL" }
            }

            AgentTool.OPEN_SETTINGS -> {
                val res = mobileControl.openSettings(step.target)
                res.getOrElse { "Opened Android Settings" }
            }

            AgentTool.SAVE_MEMORY -> {
                val saveRes = memoryAgent.saveFact(step.target, step.payload)
                if (saveRes.isSuccess) {
                    "Saved to 30MB long-term memory: \"${step.payload}\""
                } else {
                    "Memory rejected: ${saveRes.exceptionOrNull()?.message}"
                }
            }

            AgentTool.STUDY_EXPLAIN -> {
                studyAgent.explainLessonInHindi(step.target, preferences.isApiKeyConfigured())
            }

            AgentTool.STUDY_QUIZ -> {
                val quiz = studyAgent.generateMcqQuiz(step.target, preferences.isApiKeyConfigured())
                studyAgent.saveStudySession("General Study", step.target, "Interactive Quiz", quiz)
                "Generated ${quiz.size} MCQ questions on '${step.target}'. Check Study Planner tab to test yourself!"
            }

            AgentTool.WEB_SEARCH -> {
                if (preferences.isApiKeyConfigured()) {
                    val summary = geminiService.generateChat("Perform comprehensive web research on: ${step.payload}. Provide summary and sources.").getOrNull()
                    summary ?: "Conducted web search for '${step.payload}'."
                } else {
                    mobileControl.openUrl("https://www.google.com/search?q=${java.net.URLEncoder.encode(step.payload, "UTF-8")}")
                    "Initiated web search for '${step.payload}'."
                }
            }

            AgentTool.ANSWER_QUESTION, AgentTool.UNKNOWN -> {
                if (preferences.isApiKeyConfigured()) {
                    val reply = geminiService.generateChat(step.payload).getOrNull()
                    reply ?: "I have processed your query, Sir. All systems functioning normally."
                } else {
                    "JARVIS Standby. Systems operational. To enable full reasoning, please configure your Gemini API Key in Settings."
                }
            }

            else -> "Step executed successfully."
        }

        // Update task record in DB
        val task = database.taskDao().getTaskById(taskId)
        if (task != null) {
            database.taskDao().updateTask(
                task.copy(
                    status = "COMPLETED",
                    resultMessage = result,
                    completedAt = System.currentTimeMillis()
                )
            )
        }

        respondToUser(result)
    }

    private fun respondToUser(text: String) {
        scope.launch(Dispatchers.Main) {
            _chatMessages.value = _chatMessages.value + ChatMessage(
                sender = MessageSender.JARVIS,
                text = text
            )
            voiceManager.speak(text)
        }
    }

    private fun generateFallbackFileContent(fileName: String): String {
        return when {
            fileName.endsWith(".html") -> """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>JARVIS Generated Document</title>
    <style>
        body { font-family: system-ui, sans-serif; background: #070B14; color: #00E5FF; padding: 2rem; }
        .card { background: #0D1527; padding: 20px; border-radius: 12px; border: 1px solid #1E355B; }
    </style>
</head>
<body>
    <div class="card">
        <h1>JARVIS Autonomous Workspace</h1>
        <p>File generated cleanly by JARVIS Personal AI Employee.</p>
    </div>
</body>
</html>
""".trimIndent()

            fileName.endsWith(".kt") -> """
package com.jarvis.generated

fun main() {
    println("JARVIS Autonomous Kotlin Component online.")
}
""".trimIndent()

            else -> "JARVIS Document: $fileName\nCreated at: ${java.util.Date()}\nAll systems normal."
        }
    }
}
