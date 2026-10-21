package com.example.agent

import com.example.data.local.MemoryDao
import com.example.data.local.MemoryItemEntity
import com.example.data.preferences.JarvisPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MemoryAgent(
    private val memoryDao: MemoryDao,
    private val preferences: JarvisPreferences
) {

    fun getAllMemory(): Flow<List<MemoryItemEntity>> = memoryDao.getAllMemoryItems()

    fun searchMemory(query: String): Flow<List<MemoryItemEntity>> = memoryDao.searchMemory(query)

    fun getTotalMemoryUsedBytes(): Flow<Long> = memoryDao.getTotalMemoryUsedBytes()

    suspend fun saveFact(title: String, content: String, tags: String = "fact"): Result<Long> = withContext(Dispatchers.IO) {
        // Enforce privacy rule: Do not store passwords or API keys in ordinary memory
        val lower = content.lowercase()
        if (lower.contains("password") || lower.contains("api key") || lower.contains("secret key")) {
            return@withContext Result.failure(
                SecurityException("Security Policy: Passwords, API keys, and raw credentials cannot be stored in ordinary memory.")
            )
        }

        val size = (title.toByteArray(Charsets.UTF_8).size + content.toByteArray(Charsets.UTF_8).size).toLong()

        val item = MemoryItemEntity(
            category = "FACT",
            title = title,
            content = content,
            sizeInBytes = size,
            tags = tags
        )
        try {
            val id = memoryDao.insertMemory(item)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun savePreference(key: String, value: String): Result<Long> = withContext(Dispatchers.IO) {
        val size = (key.length + value.length).toLong()
        val item = MemoryItemEntity(
            category = "PREFERENCE",
            title = key,
            content = value,
            sizeInBytes = size,
            tags = "preference"
        )
        try {
            val id = memoryDao.insertMemory(item)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveConversationSummary(summary: String): Result<Long> = withContext(Dispatchers.IO) {
        val title = "Conversation Summary - ${java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}"
        val size = (title.length + summary.length).toLong()
        val item = MemoryItemEntity(
            category = "SUMMARY",
            title = title,
            content = summary,
            sizeInBytes = size,
            tags = "session_summary"
        )
        try {
            val id = memoryDao.insertMemory(item)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMemory(item: MemoryItemEntity): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            memoryDao.deleteMemory(item)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportMemoryAsJson(items: List<MemoryItemEntity>): String = withContext(Dispatchers.Default) {
        val array = JSONArray()
        for (item in items) {
            array.put(JSONObject().apply {
                put("id", item.id)
                put("category", item.category)
                put("title", item.title)
                put("content", item.content)
                put("timestamp", item.timestamp)
                put("sizeInBytes", item.sizeInBytes)
                put("tags", item.tags)
            })
        }
        JSONObject().apply {
            put("jarvis_memory_export_version", 1)
            put("quota_bytes", JarvisPreferences.MEMORY_QUOTA_BYTES)
            put("item_count", items.size)
            put("items", array)
        }.toString(2)
    }
}
