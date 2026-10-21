package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.agent.SensitiveActionRequest
import com.example.ui.theme.JarvisAlert
import com.example.ui.theme.JarvisCardBg
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisGlassBorder
import com.example.ui.theme.JarvisSuccess
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.JarvisWarning

@Composable
fun SensitiveActionDialog(
    request: SensitiveActionRequest,
    onConfirm: (editedPayload: String?) -> Unit,
    onCancel: () -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var currentPayload by remember { mutableStateOf(request.payloadContent) }

    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = JarvisCardBg,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, JarvisGlassBorder, RoundedCornerShape(16.dp))
                .testTag("sensitive_action_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header with Warning Icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Sensitive Action Warning",
                        tint = JarvisWarning
                    )
                    Text(
                        text = "SECURITY CONFIRMATION",
                        color = JarvisWarning,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = request.actionTitle,
                    color = JarvisTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = request.explanation,
                    color = JarvisTextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Target and Payload Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF070B14), RoundedCornerShape(10.dp))
                        .border(1.dp, JarvisGlassBorder.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    if (request.targetRecipient.isNotBlank()) {
                        Text(
                            text = "TARGET / RECIPIENT:",
                            color = JarvisCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = request.targetRecipient,
                            color = JarvisTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                        )
                    }

                    Text(
                        text = "ACTION PAYLOAD / CONTENT:",
                        color = JarvisCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (isEditing) {
                        OutlinedTextField(
                            value = currentPayload,
                            onValueChange = { currentPayload = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .testTag("edit_payload_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = JarvisTextPrimary,
                                unfocusedTextColor = JarvisTextPrimary,
                                focusedBorderColor = JarvisCyan,
                                unfocusedBorderColor = JarvisGlassBorder
                            )
                        )
                    } else {
                        Text(
                            text = currentPayload.ifBlank { "(No parameters)" },
                            color = JarvisTextPrimary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions: Cancel, Edit, Send/Execute
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("action_cancel_button"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = JarvisAlert
                        )
                    ) {
                        Text("Cancel", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { isEditing = !isEditing },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("action_edit_button"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = JarvisCyan
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = JarvisCyan,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text(if (isEditing) "Done" else "Edit", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            onConfirm(if (isEditing || currentPayload != request.payloadContent) currentPayload else null)
                        },
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("action_confirm_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JarvisSuccess,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = Color.Black,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text("Execute", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
