package com.example.agent

import com.example.data.ai.GeminiService
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

class PlanDecomposer(private val geminiService: GeminiService) {

    suspend fun decomposeInstruction(prompt: String, hasApiKey: Boolean): AgentPlan {
        val trimmed = prompt.trim()

        // If Gemini is available, we can request a structured decomposition, or fallback to our rich deterministic agent engine
        if (hasApiKey) {
            val aiPlan = tryDecomposeWithGemini(trimmed)
            if (aiPlan != null && aiPlan.steps.isNotEmpty()) {
                return aiPlan
            }
        }

        return decomposeLocally(trimmed)
    }

    private suspend fun tryDecomposeWithGemini(prompt: String): AgentPlan? {
        val systemPrompt = """
You are the JARVIS Autonomous Android Agent Planner. Decompose the user's prompt into an execution plan.
Return ONLY valid JSON matching this schema:
{
  "intentSummary": "string summary",
  "needsClarification": boolean,
  "clarificationQuestion": "string if ambiguous else empty",
  "steps": [
    {
      "stepIndex": 0,
      "tool": "ANSWER_QUESTION|OPEN_APP|SEARCH_YOUTUBE|OPEN_URL|OPEN_SETTINGS|SCHEDULE_ALARM|CREATE_FILE|READ_FILE|DELETE_FILE|SEARCH_FILES|DRAFT_MESSAGE|SAVE_MEMORY|SEARCH_MEMORY|STUDY_EXPLAIN|STUDY_QUIZ|WEB_SEARCH",
      "description": "step description",
      "category": "LOW_RISK|PRE_AUTHORIZED|SENSITIVE|PERMISSION_REQUIRED",
      "target": "recipient name, app name, file name, or topic",
      "payload": "message content, code content, search query, or timestamp expression"
    }
  ]
}
Rule: Sending messages or deleting files MUST ALWAYS have category "SENSITIVE".
"""
        return try {
            val response = geminiService.generateChat(
                userPrompt = "Decompose this command: \"$prompt\"",
                systemPrompt = systemPrompt
            ).getOrNull() ?: return null

            val cleaned = response.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val json = JSONObject(cleaned)
            val intentSummary = json.optString("intentSummary", "Processing task")
            val needsClarification = json.optBoolean("needsClarification", false)
            val clarificationQuestion = json.optString("clarificationQuestion", "")
            val stepsArray = json.optJSONArray("steps") ?: JSONArray()
            val stepsList = mutableListOf<PlanStep>()

            for (i in 0 until stepsArray.length()) {
                val s = stepsArray.getJSONObject(i)
                val toolStr = s.optString("tool", "ANSWER_QUESTION")
                val tool = try { AgentTool.valueOf(toolStr) } catch (_: Exception) { AgentTool.ANSWER_QUESTION }
                val catStr = s.optString("category", "LOW_RISK")
                val cat = try { ActionCategory.valueOf(catStr) } catch (_: Exception) {
                    if (tool == AgentTool.DRAFT_MESSAGE || tool == AgentTool.DELETE_FILE) ActionCategory.SENSITIVE else ActionCategory.LOW_RISK
                }

                stepsList.add(
                    PlanStep(
                        stepIndex = i,
                        tool = tool,
                        description = s.optString("description", "Execute $toolStr"),
                        category = cat,
                        target = s.optString("target", ""),
                        payload = s.optString("payload", "")
                    )
                )
            }

            AgentPlan(
                originalPrompt = prompt,
                intentSummary = intentSummary,
                steps = stepsList,
                needsClarification = needsClarification,
                clarificationQuestion = clarificationQuestion
            )
        } catch (_: Exception) {
            null
        }
    }

    fun decomposeLocally(prompt: String): AgentPlan {
        val lower = prompt.lowercase(Locale.ROOT).trim()

        // 1. Messaging (Sensitive Action - Rule C)
        // e.g. "send Priya a message saying we will meet under the tree tomorrow morning"
        if (lower.contains("send") && (lower.contains("message") || lower.contains("sms") || lower.contains("whatsapp"))) {
            val (recipient, message) = extractRecipientAndMessage(prompt)
            val isAmbiguous = recipient.isBlank()

            val step = PlanStep(
                stepIndex = 0,
                tool = AgentTool.DRAFT_MESSAGE,
                description = "Prepare communication for recipient '$recipient'",
                category = ActionCategory.SENSITIVE,
                target = recipient,
                payload = message
            )
            return AgentPlan(
                originalPrompt = prompt,
                intentSummary = "Draft message to $recipient",
                steps = listOf(step),
                needsClarification = isAmbiguous,
                clarificationQuestion = if (isAmbiguous) "Which contact would you like to message, and what should it say?" else ""
            )
        }

        // 2. Alarms & Reminders (Smart Alarms - Rule B/A)
        // e.g. "Tomorrow at 9 AM, remind me to bring milk" or "Remind me in 10 minutes to check oven"
        if (lower.contains("remind") || lower.contains("alarm") || lower.contains("schedule reminder")) {
            val parsed = parseAlarmNaturalLanguage(prompt)
            val step = PlanStep(
                stepIndex = 0,
                tool = AgentTool.SCHEDULE_ALARM,
                description = "Schedule alarm for ${parsed.title}",
                category = ActionCategory.PRE_AUTHORIZED,
                target = parsed.title,
                payload = parsed.timestamp.toString()
            )
            return AgentPlan(
                originalPrompt = prompt,
                intentSummary = "Schedule reminder for ${parsed.title}",
                steps = listOf(step)
            )
        }

        // 3. YouTube Search
        // e.g. "Open YouTube and search for Arijit Singh songs" or "play despacito on youtube"
        if (lower.contains("youtube")) {
            val query = prompt.replace(Regex("(?i)(open|search|play|on|youtube|find|for)"), "").trim()
            val step = PlanStep(
                stepIndex = 0,
                tool = AgentTool.SEARCH_YOUTUBE,
                description = "Search YouTube for '$query'",
                category = ActionCategory.LOW_RISK,
                target = "YouTube",
                payload = query.ifBlank { "Trending" }
            )
            return AgentPlan(
                originalPrompt = prompt,
                intentSummary = "Search YouTube for $query",
                steps = listOf(step)
            )
        }

        // 4. Open Installed Apps
        // e.g. "Open WhatsApp", "Open Camera", "Open Settings", "Launch Chrome"
        if (lower.startsWith("open ") || lower.startsWith("launch ")) {
            val appName = prompt.substringAfter(" ").trim()
            val step = PlanStep(
                stepIndex = 0,
                tool = if (appName.equals("settings", true)) AgentTool.OPEN_SETTINGS else AgentTool.OPEN_APP,
                description = "Launch application '$appName'",
                category = ActionCategory.LOW_RISK,
                target = appName,
                payload = ""
            )
            return AgentPlan(
                originalPrompt = prompt,
                intentSummary = "Launch $appName",
                steps = listOf(step)
            )
        }

        // 5. File Manager - Delete File (Sensitive)
        if (lower.contains("delete file") || lower.contains("remove file")) {
            val fileName = prompt.substringAfter("file", "").trim()
            val step = PlanStep(
                stepIndex = 0,
                tool = AgentTool.DELETE_FILE,
                description = "Permanently delete file '$fileName'",
                category = ActionCategory.SENSITIVE,
                target = fileName,
                payload = ""
            )
            return AgentPlan(
                originalPrompt = prompt,
                intentSummary = "Delete file $fileName",
                steps = listOf(step)
            )
        }

        // 6. File Manager - Create File
        // e.g. "Create HTML file portfolio.html with dark mode"
        if (lower.contains("create file") || lower.contains("make a file") || lower.contains("write code") || lower.endsWith(".html") || lower.endsWith(".js") || lower.endsWith(".css") || lower.endsWith(".kt") || lower.endsWith(".txt")) {
            val fileName = extractFileName(prompt)
            val step = PlanStep(
                stepIndex = 0,
                tool = AgentTool.CREATE_FILE,
                description = "Generate and save document '$fileName'",
                category = ActionCategory.LOW_RISK,
                target = fileName,
                payload = prompt
            )
            return AgentPlan(
                originalPrompt = prompt,
                intentSummary = "Create file $fileName",
                steps = listOf(step)
            )
        }

        // 7. Long-Term Memory (30MB)
        // e.g. "Remember that my car registration is 4492" or "Save to memory:..."
        if (lower.startsWith("remember") || lower.contains("save to memory") || lower.contains("note that")) {
            val fact = prompt.replace(Regex("(?i)^(jarvis,?\\s*)?(remember that|remember|save to memory:?|note that)"), "").trim()
            val step = PlanStep(
                stepIndex = 0,
                tool = AgentTool.SAVE_MEMORY,
                description = "Save fact to 30MB long-term memory",
                category = ActionCategory.LOW_RISK,
                target = "Memory Fact",
                payload = fact
            )
            return AgentPlan(
                originalPrompt = prompt,
                intentSummary = "Commit fact to memory",
                steps = listOf(step)
            )
        }

        // 8. Study Agent (Hindi explanation or MCQ quiz)
        // e.g. "Explain photosynthesis in Hindi" or "quiz on modern history"
        if (lower.contains("hindi") && (lower.contains("explain") || lower.contains("samjhao") || lower.contains("batao")) || lower.contains("study") || lower.contains("quiz")) {
            val isQuiz = lower.contains("quiz") || lower.contains("mcq") || lower.contains("test")
            val topic = prompt.replace(Regex("(?i)(explain|in hindi|hindi me|samjhao|quiz on|mcq on|test)"), "").trim()
            val step = PlanStep(
                stepIndex = 0,
                tool = if (isQuiz) AgentTool.STUDY_QUIZ else AgentTool.STUDY_EXPLAIN,
                description = if (isQuiz) "Generate interactive MCQ quiz for '$topic'" else "Explain '$topic' in clear Hindi/Hinglish",
                category = ActionCategory.LOW_RISK,
                target = topic,
                payload = prompt
            )
            return AgentPlan(
                originalPrompt = prompt,
                intentSummary = if (isQuiz) "Generate quiz on $topic" else "Explain $topic in Hindi",
                steps = listOf(step)
            )
        }

        // 9. Web Research
        // e.g. "Search the web for James Webb space telescope findings"
        if (lower.startsWith("search the web") || lower.startsWith("google ") || lower.startsWith("research ")) {
            val query = prompt.replace(Regex("(?i)^(search the web for|search web for|google|research)"), "").trim()
            val step = PlanStep(
                stepIndex = 0,
                tool = AgentTool.WEB_SEARCH,
                description = "Perform web research on '$query'",
                category = ActionCategory.LOW_RISK,
                target = query,
                payload = query
            )
            return AgentPlan(
                originalPrompt = prompt,
                intentSummary = "Web research on $query",
                steps = listOf(step)
            )
        }

        // Default: General intelligent conversation / direct execution
        val step = PlanStep(
            stepIndex = 0,
            tool = AgentTool.ANSWER_QUESTION,
            description = "Respond to user inquiry",
            category = ActionCategory.LOW_RISK,
            target = "General Assistant",
            payload = prompt
        )
        return AgentPlan(
            originalPrompt = prompt,
            intentSummary = "Answer user inquiry",
            steps = listOf(step)
        )
    }

    private fun extractRecipientAndMessage(prompt: String): Pair<String, String> {
        val pattern = Regex("(?i)send\\s+([A-Za-z0-9_]+)\\s+(?:a\\s+)?(?:message|sms|text)\\s+(?:saying|that)?\\s*(.*)")
        val match = pattern.find(prompt)
        if (match != null) {
            val recipient = match.groupValues[1].trim()
            val message = match.groupValues[2].trim()
            return Pair(recipient, message)
        }

        // Fallback: "message Priya: meeting tomorrow"
        val altPattern = Regex("(?i)(?:message|text)\\s+([A-Za-z0-9_]+)\\s*[:,-]?\\s*(.*)")
        val altMatch = altPattern.find(prompt)
        if (altMatch != null) {
            return Pair(altMatch.groupValues[1].trim(), altMatch.groupValues[2].trim())
        }

        return Pair("", prompt)
    }

    private fun extractFileName(prompt: String): String {
        val pattern = Regex("([a-zA-Z0-9_-]+\\.(html|css|js|txt|kt|json|md))")
        val match = pattern.find(prompt)
        if (match != null) {
            return match.groupValues[1]
        }
        return when {
            prompt.contains("html", true) -> "index.html"
            prompt.contains("javascript", true) || prompt.contains("js", true) -> "script.js"
            prompt.contains("css", true) -> "styles.css"
            prompt.contains("kotlin", true) -> "Solution.kt"
            prompt.contains("json", true) -> "data.json"
            else -> "notes_${System.currentTimeMillis() % 10000}.txt"
        }
    }

    data class ParsedAlarm(val title: String, val timestamp: Long)

    private fun parseAlarmNaturalLanguage(prompt: String): ParsedAlarm {
        val cal = Calendar.getInstance()
        var title = "Reminder"
        val lower = prompt.lowercase(Locale.ROOT)

        // Check if tomorrow
        if (lower.contains("tomorrow")) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        // Time parsing e.g. "9 am", "9:30 pm", "at 14:00"
        val timePattern = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", RegexOption.IGNORE_CASE)
        val match = timePattern.find(prompt)
        if (match != null) {
            var hour = match.groupValues[1].toIntOrNull() ?: 9
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            val amPm = match.groupValues[3].lowercase(Locale.ROOT)
            if (amPm == "pm" && hour < 12) hour += 12
            if (amPm == "am" && hour == 12) hour = 0
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
        } else if (lower.contains("in 10 minutes") || lower.contains("10 min")) {
            cal.add(Calendar.MINUTE, 10)
        } else if (lower.contains("in 30 minutes") || lower.contains("30 min")) {
            cal.add(Calendar.MINUTE, 30)
        } else if (lower.contains("in 1 hour") || lower.contains("1 hour")) {
            cal.add(Calendar.HOUR_OF_DAY, 1)
        } else {
            // Default 1 hour from now
            cal.add(Calendar.HOUR_OF_DAY, 1)
        }

        // Extract title e.g. "remind me to bring milk" -> "bring milk"
        val reminderRegex = Regex("(?i)remind\\s+me\\s+to\\s+(.*)")
        val reminderMatch = reminderRegex.find(prompt)
        if (reminderMatch != null) {
            title = reminderMatch.groupValues[1].trim()
        } else {
            val forRegex = Regex("(?i)alarm\\s+for\\s+(.*)")
            val forMatch = forRegex.find(prompt)
            if (forMatch != null) {
                title = forMatch.groupValues[1].trim()
            }
        }

        return ParsedAlarm(title = title.capitalize(Locale.ROOT), timestamp = cal.timeInMillis)
    }
}
