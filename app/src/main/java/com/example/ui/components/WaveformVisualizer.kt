package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JarvisBlue
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisCyanLight
import kotlin.random.Random

@Composable
fun WaveformVisualizer(
    amplitude: Float,
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    val barCount = 18
    val animatedHeights = remember {
        List(barCount) { Animatable(4f) }
    }

    LaunchedEffect(amplitude, isListening) {
        animatedHeights.forEachIndexed { index, animatable ->
            val targetHeight = if (isListening) {
                val distanceFactor = 1f - kotlin.math.abs(index - barCount / 2f) / (barCount / 2f)
                val randomVariation = (Random.nextFloat() * 0.4f) + 0.6f
                (8f + (amplitude * 44f * distanceFactor * randomVariation)).coerceIn(4f, 48f)
            } else {
                4f
            }
            animatable.animateTo(
                targetValue = targetHeight,
                animationSpec = tween(durationMillis = 80, easing = LinearEasing)
            )
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        animatedHeights.forEach { anim ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .width(3.5.dp)
                    .height(anim.value.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                JarvisCyanLight,
                                JarvisCyan,
                                JarvisBlue
                            )
                        )
                    )
            )
        }
    }
}
