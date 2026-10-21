package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_items ORDER BY timestamp DESC")
    fun getAllMemoryItems(): Flow<List<MemoryItemEntity>>

    @Query("SELECT * FROM memory_items WHERE category = :category ORDER BY timestamp DESC")
    fun getMemoryByCategory(category: String): Flow<List<MemoryItemEntity>>

    @Query("SELECT * FROM memory_items WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchMemory(query: String): Flow<List<MemoryItemEntity>>

    @Query("SELECT COALESCE(SUM(sizeInBytes), 0) FROM memory_items")
    fun getTotalMemoryUsedBytes(): Flow<Long>

    @Query("SELECT * FROM memory_items WHERE id = :id LIMIT 1")
    suspend fun getMemoryById(id: Long): MemoryItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(item: MemoryItemEntity): Long

    @Update
    suspend fun updateMemory(item: MemoryItemEntity)

    @Delete
    suspend fun deleteMemory(item: MemoryItemEntity)

    @Query("DELETE FROM memory_items")
    suspend fun clearAllMemory()
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM jarvis_tasks ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM jarvis_tasks WHERE status IN ('PENDING', 'IN_PROGRESS', 'WAITING_APPROVAL') ORDER BY createdAt ASC")
    fun getActiveTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM jarvis_tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Long): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("DELETE FROM jarvis_tasks WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    suspend fun clearHistory()
}

@Dao
interface AlarmReminderDao {
    @Query("SELECT * FROM alarms_reminders ORDER BY triggerTimestamp ASC")
    fun getAllAlarms(): Flow<List<AlarmReminderEntity>>

    @Query("SELECT * FROM alarms_reminders WHERE isEnabled = 1 AND isCompleted = 0 ORDER BY triggerTimestamp ASC")
    suspend fun getPendingAlarmsSync(): List<AlarmReminderEntity>

    @Query("SELECT * FROM alarms_reminders WHERE id = :id LIMIT 1")
    suspend fun getAlarmById(id: Long): AlarmReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: AlarmReminderEntity): Long

    @Update
    suspend fun updateAlarm(alarm: AlarmReminderEntity)

    @Delete
    suspend fun deleteAlarm(alarm: AlarmReminderEntity)
}

@Dao
interface StudyDao {
    @Query("SELECT * FROM study_plans ORDER BY scheduledTimestamp ASC, id ASC")
    fun getAllStudyPlans(): Flow<List<StudyPlanEntity>>

    @Query("SELECT * FROM study_plans WHERE id = :id LIMIT 1")
    suspend fun getStudyPlanById(id: Long): StudyPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudyPlan(plan: StudyPlanEntity): Long

    @Update
    suspend fun updateStudyPlan(plan: StudyPlanEntity)

    @Delete
    suspend fun deleteStudyPlan(plan: StudyPlanEntity)
}

@Dao
interface FileDao {
    @Query("SELECT * FROM managed_files ORDER BY updatedAt DESC")
    fun getAllFiles(): Flow<List<ManagedFileEntity>>

    @Query("SELECT * FROM managed_files WHERE fileName LIKE '%' || :query || '%' OR contentSnippet LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchFiles(query: String): Flow<List<ManagedFileEntity>>

    @Query("SELECT * FROM managed_files WHERE id = :id LIMIT 1")
    suspend fun getFileById(id: Long): ManagedFileEntity?

    @Query("SELECT * FROM managed_files WHERE relativePath = :relativePath LIMIT 1")
    suspend fun getFileByPath(relativePath: String): ManagedFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: ManagedFileEntity): Long

    @Update
    suspend fun updateFile(file: ManagedFileEntity)

    @Delete
    suspend fun deleteFile(file: ManagedFileEntity)
}
