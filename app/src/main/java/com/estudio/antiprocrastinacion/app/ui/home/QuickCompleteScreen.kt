package com.estudio.antiprocrastinacion.app.ui.home

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val QuickCompleteBackground = Color(0xFF040706)
private val QuickCompleteGreen = Color(0xFF63FF2A)
private val QuickCompleteGreenSoft = Color(0xFFB2FF86)
private val QuickCompleteGreenDeep = Color(0xFF103410)

@Composable
fun QuickCompleteScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val inspectionMode = LocalInspectionMode.current
    val animationsEnabled = inspectionMode || ValueAnimator.areAnimatorsEnabled()
    val contentAlpha = remember { Animatable(if (animationsEnabled) 0f else 1f) }
    val contentOffsetY = remember { Animatable(if (animationsEnabled) 22f else 0f) }
    val orbScale = remember { Animatable(if (animationsEnabled) 0.8f else 1f) }
    val orbAlpha = remember { Animatable(if (animationsEnabled) 0f else 1f) }
    val checkScale = remember { Animatable(if (animationsEnabled) 0.52f else 1f) }
    val checkAlpha = remember { Animatable(if (animationsEnabled) 0f else 1f) }
    val glowStrength = remember { Animatable(if (animationsEnabled) 0.25f else 1f) }

    LaunchedEffect(Unit) {
        if (!animationsEnabled) {
            contentAlpha.snapTo(1f)
            contentOffsetY.snapTo(0f)
            orbScale.snapTo(1f)
            orbAlpha.snapTo(1f)
            checkScale.snapTo(1f)
            checkAlpha.snapTo(1f)
            glowStrength.snapTo(1f)
            return@LaunchedEffect
        }

        launch {
            contentAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 220, easing = LinearOutSlowInEasing),
            )
        }
        launch {
            contentOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            )
        }
        delay(90)
        launch {
            orbAlpha.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 170))
        }
        launch {
            glowStrength.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 220))
        }
        orbScale.animateTo(
            targetValue = 1.06f,
            animationSpec = spring(dampingRatio = 0.58f, stiffness = 430f),
        )
        orbScale.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 110))
        delay(70)
        launch {
            checkAlpha.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 120))
        }
        checkScale.animateTo(
            targetValue = 1.08f,
            animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
        )
        checkScale.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 90))
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(QuickCompleteBackground)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(0.22f),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Volver",
                    tint = Color.White,
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(0.56f)
                    .padding(horizontal = 24.dp)
                    .graphicsLayer {
                        alpha = contentAlpha.value
                        translationY = contentOffsetY.value
                    },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                SuccessOrb(
                    modifier =
                        Modifier
                            .graphicsLayer {
                                alpha = orbAlpha.value
                                scaleX = orbScale.value
                                scaleY = orbScale.value
                            },
                    glowStrength = glowStrength.value,
                    checkAlpha = checkAlpha.value,
                    checkScale = checkScale.value,
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Todo al día",
                        style = MaterialTheme.typography.displaySmall.copy(
                            color = Color.White,
                            lineHeight = 44.sp,
                        ),
                    )
                    Text(
                        text = "No tenés tarjetas pendientes",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Text(
                        text = "Buen trabajo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFD2D7D1),
                    )
                }
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(0.22f)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = QuickCompleteGreen,
                        contentColor = Color(0xFF071106),
                    ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Home,
                    contentDescription = null,
                )
                Text(
                    text = "Volver al inicio",
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun SuccessOrb(
    glowStrength: Float,
    checkAlpha: Float,
    checkScale: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(232.dp)
                .drawBehind {
                    drawCircle(
                        brush =
                            Brush.radialGradient(
                                colors =
                                    listOf(
                                        QuickCompleteGreen.copy(alpha = 0.30f * glowStrength),
                                        QuickCompleteGreen.copy(alpha = 0.12f * glowStrength),
                                        Color.Transparent,
                                    ),
                            ),
                        radius = size.minDimension * (0.56f + (0.18f * glowStrength)),
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(176.dp)
                    .clip(CircleShape)
                    .background(
                        brush =
                            Brush.radialGradient(
                                colors =
                                    listOf(
                                        QuickCompleteGreenDeep.copy(alpha = 0.94f),
                                        Color(0xFF071A08),
                                    ),
                            ),
                    )
                    .border(
                        width = 5.dp,
                        brush = Brush.linearGradient(listOf(QuickCompleteGreenSoft, QuickCompleteGreen)),
                        shape = CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Completado",
                tint = QuickCompleteGreen,
                modifier =
                    Modifier
                        .size(92.dp)
                        .graphicsLayer {
                            alpha = checkAlpha
                            scaleX = checkScale
                            scaleY = checkScale
                        },
            )
        }
    }
}
