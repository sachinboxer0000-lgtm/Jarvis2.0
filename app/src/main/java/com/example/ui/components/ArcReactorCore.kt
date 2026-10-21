package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JarvisAlert
import com.example.ui.theme.JarvisBlue
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisCyanLight
import com.example.ui.theme.JarvisGlow
import com.example.ui.theme.JarvisSuccess
import com.example.ui.theme.JarvisTextSecondary
import com.example.voice.VoiceState
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ArcReactorCore(
    voiceState: VoiceState,
    audioAmplitude: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "arc_reactor")

    // Continuous rotation for outer tech ring
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_rot"
    )

    // Reverse rotation for inner segmented ring
    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_rot"
    )

    // Pulse scale for core breathing effect
    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_pulse"
    )

    val coreColor = when (voiceState) {
        VoiceState.IDLE -> JarvisCyan
        VoiceState.LISTENING -> JarvisGlow
        VoiceState.PROCESSING -> JarvisBlue
        VoiceState.SPEAKING -> JarvisSuccess
        VoiceState.ERROR -> JarvisAlert
    }

    val statusText = when (voiceState) {
        VoiceState.IDLE -> "JARVIS ONLINE"
        VoiceState.LISTENING -> "LISTENING..."
        VoiceState.PROCESSING -> "ANALYZING..."
        VoiceState.SPEAKING -> "RESPONDING"
        VoiceState.ERROR -> "SYSTEM ALERT"
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .testTag("arc_reactor_core"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(200.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = size.width / 2f

                // Outer ambient glow ring
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            coreColor.copy(alpha = 0.25f),
                            coreColor.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = baseRadius
                    ),
                    radius = baseRadius,
                    center = center
                )

                // Outer rotating segmented tech ring
                rotate(outerRotation, pivot = center) {
                    val segments = 8
                    val sweep = 30f
                    for (i in 0 until segments) {
                        val startAngle = i * (360f / segments)
                        drawArc(
                            color = coreColor.copy(alpha = 0.6f),
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(center.x - baseRadius * 0.88f, center.y - baseRadius * 0.88f),
                            size = androidx.compose.ui.geometry.Size(baseRadius * 1.76f, baseRadius * 1.76f),
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // Inner rotating ring with counter-spin
                rotate(innerRotation, pivot = center) {
                    val spokes = 12
                    for (i in 0 until spokes) {
                        val angleRad = Math.toRadians((i * (360.0 / spokes)).toDouble())
                        val r1 = baseRadius * 0.55f
                        val r2 = baseRadius * (0.72f + (audioAmplitude * 0.12f))
                        val p1 = Offset((center.x + r1 * cos(angleRad)).toFloat(), (center.y + r1 * sin(angleRad)).toFloat())
                        val p2 = Offset((center.x + r2 * cos(angleRad)).toFloat(), (center.y + r2 * sin(angleRad)).toFloat())
                        drawLine(
                            color = JarvisCyanLight.copy(alpha = 0.7f),
                            start = p1,
                            end = p2,
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Glowing inner core circle with reactive pulse
                val effectivePulse = if (voiceState == VoiceState.LISTENING) {
                    corePulse * (1f + audioAmplitude * 0.25f)
                } else {
                    corePulse
                }
                val coreRadius = baseRadius * 0.42f * effectivePulse

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White,
                            coreColor,
                            coreColor.copy(alpha = 0.4f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = coreRadius
                    ),
                    radius = coreRadius,
                    center = center
                )

                // High-tech center emblem ring
                drawCircle(
                    color = Color.White.copy(alpha = 0.9f),
                    radius = baseRadius * 0.18f,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Central mini text label
            Text(
                text = "AI",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = statusText,
            color = coreColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp
        )
    }
}
