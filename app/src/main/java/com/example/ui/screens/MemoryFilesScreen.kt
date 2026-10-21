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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Html
import androidx.compose.material.icons.filled.Javascript
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.window.Dialog
import com.example.agent.FileManagerAgent
import com.example.agent.MemoryAgent
import com.example.data.local.JarvisDatabase
import com.example.data.local.ManagedFileEntity
import com.example.data.local.MemoryItemEntity
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MemoryFilesScreen(
    database: JarvisDatabase,
    preferences: JarvisPreferences,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }

    val memoryAgent = remember { MemoryAgent(database.memoryDao(), preferences) }
    val fileManager = remember { FileManagerAgent(context, database.fileDao()) }

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
                text = { Text("30 MB MEMORY", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                icon = { Icon(Icons.Default.Memory, contentDescription = "Memory") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("FILE MANAGER", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                icon = { Icon(Icons.Default.Description, contentDescription = "Files") }
            )
        }

        if (selectedTab == 0) {
            MemorySubTab(database, memoryAgent, context)
        } else {
            FilesSubTab(database, fileManager, context)
        }
    }
}

@Composable
fun MemorySubTab(
    database: JarvisDatabase,
    memoryAgent: MemoryAgent,
    context: Context
) {
    val scope = rememberCoroutineScope()
    val totalUsedBytes by memoryAgent.getTotalMemoryUsedBytes().collectAsState(initial = 0L)
    val maxQuota = JarvisPreferences.MEMORY_QUOTA_BYTES

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }
    var showAddDialog by remember { mutableStateOf(false) }

    val allItems by memoryAgent.getAllMemory().collectAsState(initial = emptyList())
    val filteredItems = allItems.filter { item ->
        val matchesCategory = (selectedCategory == "ALL" || item.category.equals(selectedCategory, true))
        val matchesSearch = searchQuery.isBlank() ||
                item.title.contains(searchQuery, ignoreCase = true) ||
                item.content.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesSearch
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 30MB Quota Storage Gauge
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = JarvisCardBg,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, JarvisGlassBorder, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "30 MiB LONG-TERM MEMORY",
                        color = JarvisCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    val usedKb = totalUsedBytes / 1024.0
                    val totalMb = maxQuota / (1024.0 * 1024.0)
                    Text(
                        text = String.format(Locale.US, "%.2f KB / %.0f MB", usedKb, totalMb),
                        color = JarvisTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val progress = (totalUsedBytes.toFloat() / maxQuota.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = JarvisCyan,
                    trackColor = JarvisSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.testTag("add_memory_fact_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Fact", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Fact", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val json = memoryAgent.exportMemoryAsJson(allItems)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("JARVIS Memory Export", json))
                                Toast.makeText(context, "Exported ${allItems.size} items to clipboard (JSON)", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.testTag("export_memory_btn")
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export JSON", fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search and Filter Row
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search persistent memory...", color = JarvisTextMuted, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = JarvisCyan) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_memory_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = JarvisTextPrimary,
                unfocusedTextColor = JarvisTextPrimary,
                focusedBorderColor = JarvisCyan,
                unfocusedBorderColor = JarvisGlassBorder
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Category Filter Chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val categories = listOf("ALL", "FACT", "PREFERENCE", "SUMMARY")
            items(categories) { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = JarvisCyan,
                        selectedLabelColor = Color.Black
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Memory Items List
        if (filteredItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No memory records found.",
                    color = JarvisTextMuted,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredItems, key = { it.id }) { item ->
                    MemoryItemCard(item = item, onDelete = {
                        scope.launch { memoryAgent.deleteMemory(item) }
                    })
                }
            }
        }
    }

    if (showAddDialog) {
        AddFactDialog(
            onDismiss = { showAddDialog = false },
            onSave = { title, content ->
                scope.launch {
                    val res = memoryAgent.saveFact(title, content)
                    if (res.isFailure) {
                        Toast.makeText(context, res.exceptionOrNull()?.message, Toast.LENGTH_LONG).show()
                    }
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
fun MemoryItemCard(item: MemoryItemEntity, onDelete: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = JarvisCardBg,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, JarvisGlassBorder, RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.category,
                        color = JarvisCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${item.sizeInBytes} bytes",
                        color = JarvisTextMuted,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.title,
                    color = JarvisTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = item.content,
                    color = JarvisTextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Memory",
                    tint = JarvisAlert.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun AddFactDialog(onDismiss: () -> Unit, onSave: (title: String, content: String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = JarvisCardBg,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, JarvisGlassBorder, RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "SAVE TO 30MB MEMORY",
                    color = JarvisCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title / Key") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Information / Fact") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { if (title.isNotBlank() && content.isNotBlank()) onSave(title, content) },
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = Color.Black)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
fun FilesSubTab(
    database: JarvisDatabase,
    fileManager: FileManagerAgent,
    context: Context
) {
    val scope = rememberCoroutineScope()
    val files by database.fileDao().getAllFiles().collectAsState(initial = emptyList())
    var showCreateDialog by remember { mutableStateOf(false) }
    var viewingFile by remember { mutableStateOf<ManagedFileEntity?>(null) }
    var fileContentToView by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "JARVIS FILE MANAGER",
                    color = JarvisCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Path: /jarvis_workspace (${files.size} documents)",
                    color = JarvisTextSecondary,
                    fontSize = 11.sp
                )
            }

            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = Color.Black),
                modifier = Modifier.testTag("create_new_file_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New File", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (files.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Workspace is empty. Create code, HTML, or notes documents.",
                    color = JarvisTextMuted,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(files, key = { it.id }) { file ->
                    FileCard(
                        file = file,
                        onOpen = {
                            scope.launch {
                                val content = fileManager.readFile(file.relativePath).getOrDefault(file.contentSnippet)
                                fileContentToView = content
                                viewingFile = file
                            }
                        },
                        onShare = {
                            scope.launch {
                                val intentRes = fileManager.shareFile(file.id)
                                if (intentRes.isSuccess) {
                                    context.startActivity(intentRes.getOrNull())
                                } else {
                                    Toast.makeText(context, "Share error: ${intentRes.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onDelete = {
                            scope.launch { fileManager.deleteFile(file.id) }
                        }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateFileDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, content ->
                scope.launch {
                    fileManager.createFile(name, content)
                }
                showCreateDialog = false
            }
        )
    }

    if (viewingFile != null) {
        FileViewerDialog(
            file = viewingFile!!,
            initialContent = fileContentToView,
            onDismiss = { viewingFile = null },
            onSave = { updatedContent ->
                scope.launch {
                    fileManager.createFile(viewingFile!!.fileName, updatedContent)
                    viewingFile = null
                }
            }
        )
    }
}

@Composable
fun FileCard(
    file: ManagedFileEntity,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val fileIcon = when (file.fileType) {
        "HTML" -> Icons.Default.Html
        "JAVASCRIPT" -> Icons.Default.Javascript
        "KOTLIN" -> Icons.Default.Code
        else -> Icons.Default.Description
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = JarvisCardBg,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, JarvisGlassBorder, RoundedCornerShape(10.dp))
            .clickable { onOpen() }
            .testTag("file_item_${file.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = fileIcon,
                    contentDescription = file.fileType,
                    tint = JarvisCyan,
                    modifier = Modifier.size(28.dp)
                )

                Column {
                    Text(
                        text = file.fileName,
                        color = JarvisTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${file.fileType} • ${file.sizeBytes} bytes",
                        color = JarvisTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = JarvisCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = JarvisAlert,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CreateFileDialog(onDismiss: () -> Unit, onConfirm: (name: String, content: String) -> Unit) {
    var fileName by remember { mutableStateOf("document.html") }
    var content by remember { mutableStateOf("<!DOCTYPE html>\n<html>\n<body>\n<h1>Hello from JARVIS</h1>\n</body>\n</html>") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = JarvisCardBg,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, JarvisGlassBorder, RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("CREATE DOCUMENT / CODE FILE", color = JarvisCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("File Name (.html, .kt, .js, .txt)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("File Contents") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    maxLines = 10
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { if (fileName.isNotBlank()) onConfirm(fileName, content) },
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = Color.Black)
                    ) {
                        Text("Create")
                    }
                }
            }
        }
    }
}

@Composable
fun FileViewerDialog(
    file: ManagedFileEntity,
    initialContent: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var content by remember { mutableStateOf(initialContent) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = JarvisCardBg,
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
                .border(1.dp, JarvisGlassBorder, RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = file.fileName,
                        color = JarvisCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${file.sizeBytes} B",
                        color = JarvisTextMuted,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = JarvisTextPrimary,
                        unfocusedTextColor = JarvisTextPrimary,
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisGlassBorder
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onDismiss) { Text("Close") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(content) },
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = Color.Black)
                    ) {
                        Text("Save Changes")
                    }
                }
            }
        }
    }
}
