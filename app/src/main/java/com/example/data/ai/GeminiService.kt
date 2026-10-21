package com.example.data.ai

import com.example.data.preferences.JarvisPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiService(private val preferences: JarvisPreferences) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun testConnection(apiKey: String, model: String): Result<ConnectionTestResult> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("API Key cannot be blank. Please enter your Gemini API key."))
        }

        val startTime = System.currentTimeMillis()
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val bodyJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "JARVIS online status verification check. Respond with: 'JARVIS core operational.'")
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 50)
                put("temperature", 0.2)
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(bodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val duration = System.currentTimeMillis() - startTime
                val respBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val errorMsg = parseErrorMessage(response.code, respBody)
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val replyText = parseReplyText(respBody)
                Result.success(
                    ConnectionTestResult(
                        latencyMs = duration,
                        reply = replyText.ifBlank { "Core operational" },
                        modelUsed = model
                    )
                )
            }
        } catch (e: IOException) {
            Result.failure(Exception("Network error connecting to Gemini: ${e.localizedMessage ?: "Check your internet connection"}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAvailableModels(apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.success(JarvisPreferences.DEFAULT_MODELS)
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
        val request = Request.Builder().url(url).get().build()

        try {
            client.newCall(request).execute().use { response ->
                val respBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception(parseErrorMessage(response.code, respBody)))
                }

                val json = JSONObject(respBody)
                val modelsArray = json.optJSONArray("models") ?: JSONArray()
                val resultList = mutableListOf<String>()

                for (i in 0 until modelsArray.length()) {
                    val m = modelsArray.getJSONObject(i)
                    val name = m.optString("name").replace("models/", "")
                    val methods = m.optJSONArray("supportedGenerationMethods")
                    var canGenerate = false
                    if (methods != null) {
                        for (j in 0 until methods.length()) {
                            if (methods.getString(j) == "generateContent") {
                                canGenerate = true
                                break
                            }
                        }
                    }
                    if (canGenerate && (name.contains("gemini-2.") || name.contains("gemini-1.5") || name.contains("gemini-flash") || name.contains("gemini-pro"))) {
                        if (!name.contains("vision") && !name.contains("embedding") && !name.contains("aqa")) {
                            resultList.add(name)
                        }
                    }
                }

                val finalModels = if (resultList.isNotEmpty()) {
                    resultList.distinct().sortedDescending()
                } else {
                    JarvisPreferences.DEFAULT_MODELS
                }
                Result.success(finalModels)
            }
        } catch (e: Exception) {
            Result.failure(Exception("Could not fetch remote models: ${e.localizedMessage}"))
        }
    }

    suspend fun generateChat(
        userPrompt: String,
        conversationHistory: List<Pair<String, String>> = emptyList(),
        systemPrompt: String = SYSTEM_INSTRUCTION
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = preferences.getEffectiveApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Gemini API key is not configured. Please add your key in Settings or set it in the Secrets panel."))
        }

        val model = preferences.getSelectedModel()
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val contentsArray = JSONArray()

        // Append recent conversation turns
        for ((role, text) in conversationHistory.takeLast(6)) {
            contentsArray.put(JSONObject().apply {
                put("role", if (role.equals("model", true) || role.equals("assistant", true)) "model" else "user")
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", text) })
                })
            })
        }

        // Append current prompt
        contentsArray.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().apply {
                put(JSONObject().apply { put("text", userPrompt) })
            })
        })

        val requestJson = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", systemPrompt) })
                })
            })
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.6)
                put("maxOutputTokens", 1500)
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(requestJson.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception(parseErrorMessage(response.code, body)))
                }
                val text = parseReplyText(body)
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseErrorMessage(code: Int, body: String): String {
        return try {
            val json = JSONObject(body)
            val errObj = json.optJSONObject("error")
            val msg = errObj?.optString("message") ?: ""
            val status = errObj?.optString("status") ?: ""
            when {
                code == 400 && msg.contains("API key not valid", true) -> "Invalid Gemini API Key. Please verify your credentials."
                code == 403 || msg.contains("PERMISSION_DENIED", true) -> "Access denied (403): Key lacks permissions or project quota exceeded."
                code == 404 -> "Selected model was not found for this API tier ($status)."
                code == 429 || msg.contains("RESOURCE_EXHAUSTED", true) -> "Gemini API Quota Exceeded (429). Please retry shortly or use a different model."
                msg.isNotBlank() -> "Gemini error ($code): $msg"
                else -> "HTTP $code: Server rejected request."
            }
        } catch (_: Exception) {
            "HTTP $code: $body"
        }
    }

    private fun parseReplyText(body: String): String {
        return try {
            val json = JSONObject(body)
            val candidates = json.optJSONArray("candidates") ?: return ""
            if (candidates.length() == 0) return ""
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content") ?: return ""
            val parts = content.optJSONArray("parts") ?: return ""
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                sb.append(part.optString("text", ""))
            }
            sb.toString().trim()
        } catch (_: Exception) {
            ""
        }
    }

    companion object {
        const val SYSTEM_INSTRUCTION = """
You are JARVIS — an elite autonomous personal AI employee, executive assistant, and mobile operating agent on Android.
- Your persona is articulate, confident, loyal, and efficient.
- You can communicate naturally in English, Hindi, and Hinglish depending on what the user speaks.
- When explaining complex concepts (like study topics), provide clear, insightful breakdowns with examples.
- When generating code, generate clean, complete, modern code with no omissions.
- For daily tasks, alarms, notes, and file operations, be precise and helpful.
- Keep spoken or chat replies concise and structured unless in-depth explanation is requested.
"""
    }
}

data class ConnectionTestResult(
    val latencyMs: Long,
    val reply: String,
    val modelUsed: String
)
