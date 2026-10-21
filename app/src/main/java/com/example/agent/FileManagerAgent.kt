package com.example.agent

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.local.FileDao
import com.example.data.local.ManagedFileEntity
import com.example.data.preferences.JarvisPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FileManagerAgent(
    private val context: Context,
    private val fileDao: FileDao
) {
    private val workspaceDir = File(context.filesDir, "jarvis_workspace").apply {
        if (!exists()) mkdirs()
    }

    suspend fun createFile(
        fileName: String,
        content: String,
        fileType: String = ""
    ): Result<ManagedFileEntity> = withContext(Dispatchers.IO) {
        val sanitizedName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val targetFile = File(workspaceDir, sanitizedName)

        val extension = targetFile.extension.lowercase()
        val detectedType = when {
            fileType.isNotBlank() -> fileType.uppercase()
            extension in listOf("html", "htm") -> "HTML"
            extension in listOf("js", "ts") -> "JAVASCRIPT"
            extension in listOf("css") -> "CSS"
            extension in listOf("kt", "kts") -> "KOTLIN"
            extension in listOf("json") -> "JSON"
            extension in listOf("md") -> "MARKDOWN"
            else -> "TXT"
        }

        val mimeType = when (detectedType) {
            "HTML" -> "text/html"
            "JAVASCRIPT" -> "text/javascript"
            "CSS" -> "text/css"
            "KOTLIN" -> "text/x-kotlin"
            "JSON" -> "application/json"
            "MARKDOWN" -> "text/markdown"
            else -> "text/plain"
        }

        try {
            targetFile.writeText(content, Charsets.UTF_8)
            val size = targetFile.length()

            val snippet = if (content.length > 200) content.take(200) + "..." else content

            val entity = ManagedFileEntity(
                fileName = sanitizedName,
                relativePath = targetFile.absolutePath,
                mimeType = mimeType,
                fileType = detectedType,
                sizeBytes = size,
                contentSnippet = snippet
            )

            // Update if existing or insert new
            val existing = fileDao.getFileByPath(targetFile.absolutePath)
            val id = if (existing != null) {
                fileDao.updateFile(entity.copy(id = existing.id))
                existing.id
            } else {
                fileDao.insertFile(entity)
            }

            Result.success(entity.copy(id = id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readFile(relativePath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val file = File(relativePath)
            if (!file.exists()) return@withContext Result.failure(Exception("File not found at $relativePath"))
            Result.success(file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFile(fileId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val entity = fileDao.getFileById(fileId) ?: return@withContext Result.failure(Exception("File record not found"))
            val physicalFile = File(entity.relativePath)
            if (physicalFile.exists()) {
                physicalFile.delete()
            }
            fileDao.deleteFile(entity)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun shareFile(fileId: Long): Result<Intent> = withContext(Dispatchers.IO) {
        try {
            val entity = fileDao.getFileById(fileId) ?: return@withContext Result.failure(Exception("File not found"))
            val file = File(entity.relativePath)
            if (!file.exists()) return@withContext Result.failure(Exception("Physical file missing"))

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = entity.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, entity.fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Result.success(Intent.createChooser(shareIntent, "Share ${entity.fileName} via JARVIS"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getWorkspaceDirectory(): File = workspaceDir
}
