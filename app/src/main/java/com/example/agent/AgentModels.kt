package com.example.agent

enum class ActionCategory(val label: String, val requiresApproval: Boolean) {
    LOW_RISK("Low-Risk Action", false),
    PRE_AUTHORIZED("Pre-Authorized Action", false),
    SENSITIVE("Sensitive Action (Approval Required)", true),
    PERMISSION_REQUIRED("Android Permission Required", true)
}

enum class AgentTool {
    ANSWER_QUESTION,
    OPEN_APP,
    SEARCH_YOUTUBE,
    OPEN_URL,
    OPEN_SETTINGS,
    SCHEDULE_ALARM,
    CREATE_FILE,
    READ_FILE,
    DELETE_FILE,
    SEARCH_FILES,
    DRAFT_MESSAGE,
    SAVE_MEMORY,
    SEARCH_MEMORY,
    STUDY_EXPLAIN,
    STUDY_QUIZ,
    WEB_SEARCH,
    UNKNOWN
}

data class PlanStep(
    val stepIndex: Int,
    val tool: AgentTool,
    val description: String,
    val category: ActionCategory,
    val target: String = "",
    val payload: String = "",
    var isApproved: Boolean = false,
    var isExecuted: Boolean = false,
    var executionResult: String = ""
)

data class AgentPlan(
    val originalPrompt: String,
    val intentSummary: String,
    val steps: List<PlanStep>,
    val needsClarification: Boolean = false,
    val clarificationQuestion: String = ""
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val planStep: PlanStep? = null
)

enum class MessageSender {
    USER,
    JARVIS,
    SYSTEM
}

data class SensitiveActionRequest(
    val taskId: Long,
    val stepIndex: Int,
    val actionTitle: String,
    val actionType: AgentTool,
    val targetRecipient: String,
    val payloadContent: String,
    val explanation: String
)
