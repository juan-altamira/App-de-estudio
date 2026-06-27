package com.estudio.antiprocrastinacion.app.ui.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val StudyLightColors: ColorScheme =
    lightColorScheme(
        primary = Color(0xFF0C5F67),
        onPrimary = Color(0xFFF7FEFF),
        primaryContainer = Color(0xFFBEEEF2),
        onPrimaryContainer = Color(0xFF042B2F),
        secondary = Color(0xFFB7652A),
        onSecondary = Color(0xFFFFFAF7),
        secondaryContainer = Color(0xFFF8D8BF),
        onSecondaryContainer = Color(0xFF3E1600),
        tertiary = Color(0xFF1F6A45),
        onTertiary = Color(0xFFF7FFF9),
        tertiaryContainer = Color(0xFFCBEFD7),
        onTertiaryContainer = Color(0xFF0C2618),
        background = Color(0xFFF4F1ED),
        onBackground = Color(0xFF18171A),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF18171A),
        surfaceVariant = Color(0xFFE6E6EE),
        onSurfaceVariant = Color(0xFF4B4D57),
        outline = Color(0xFFD5D7DE),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFBFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
    )

private val StudyDarkColors: ColorScheme =
    darkColorScheme(
        primary = Color(0xFF84E7EF),
        onPrimary = Color(0xFF081719),
        primaryContainer = Color(0xFF1F3A40),
        onPrimaryContainer = Color(0xFFD7FBFE),
        secondary = Color(0xFFF2A66D),
        onSecondary = Color(0xFF281208),
        secondaryContainer = Color(0xFF3A271A),
        onSecondaryContainer = Color(0xFFFBD5B7),
        tertiary = Color(0xFFA8F0C4),
        onTertiary = Color(0xFF0A1E14),
        tertiaryContainer = Color(0xFF133A27),
        onTertiaryContainer = Color(0xFFE0FBE8),
        background = Color(0xFF0E0B0B),
        onBackground = Color(0xFFF5F3F0),
        surface = Color(0xFF1B1C22),
        onSurface = Color(0xFFF5F3F0),
        surfaceVariant = Color(0xFF2A2B35),
        onSurfaceVariant = Color(0xFFC7C9D3),
        outline = Color(0xFF343844),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF410002),
        errorContainer = Color(0xFF5F1D22),
        onErrorContainer = Color(0xFFFFDAD6),
    )

private val StudyTypography =
    Typography(
        headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
        headlineMedium = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
        headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
        titleSmall = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
        bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
        bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
        labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
        labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
    )

private val StudyShapes =
    Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        small = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    )

@Composable
fun StudyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) StudyDarkColors else StudyLightColors,
        typography = StudyTypography,
        shapes = StudyShapes,
        content = content,
    )
}
