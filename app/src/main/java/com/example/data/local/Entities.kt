package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_items")
data class MemoryItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: String, // FACT, PREFERENCE, SUMMARY, FILE_REF, STUDY_RECORD
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sizeInBytes: Long = 0,
    val tags: String = ""
)

@Entity(tableName = "jarvis_tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val prompt: String,
    val stepsJson: String = "[]",
    val currentStepIndex: Int = 0,
    val status: String = "PENDING", // PENDING, IN_PROGRESS, WAITING_APPROVAL, COMPLETED, FAILED, CANCELLED
    val category: String = "LOW_RISK", // LOW_RISK, PRE_AUTHORIZED, SENSITIVE, PERMISSION_REQUIRED
    val actionTarget: String = "",
    val actionPayload: String = "",
    val resultMessage: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0
)

@Entity(tableName = "alarms_reminders")
data class AlarmReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val note: String = "",
    val triggerTimestamp: Long,
    val isRecurring: Boolean = false,
    val recurrenceRule: String = "", // NONE, DAILY, WEEKLY
    val isEnabled: Boolean = true,
    val isCompleted: Boolean = false,
    val isMissed: Boolean = false,
    val originalInstruction: String = ""
)

@Entity(tableName = "study_plans")
data class StudyPlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subject: String,
    val chapterTitle: String,
    val lessonNotes: String = "",
    val isCompleted: Boolean = false,
    val mcqQuizJson: String = "[]",
    val scheduledTimestamp: Long = 0,
    val score: Int = -1
)

@Entity(tableName = "managed_files")
data class ManagedFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val relativePath: String,
    val mimeType: String,
    val fileType: String, // TXT, HTML, CSS, JS, KT, JSON, MD
    val sizeBytes: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val contentSnippet: String = ""
)
