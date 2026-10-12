package com.adhavaa.commander.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.adhavaa.commander.R

/**
 * Tactical HUD palette (after the reference the user chose): charcoal hull, hairline panels, signal green for
 * "go / active", amber for "watch this", a mint panel for the one thing to read first. Dark only (D-37).
 */
object Hud {
    val bg = Color(0xFF0C0E0D)
    val panel = Color(0xFF131614)
    val panelHi = Color(0xFF1B1F1C)
    val line = Color(0xFF2A2F2B)
    val grid = Color(0xFF1E2320)
    val contour = Color(0xFF232825)
    val ink = Color(0xFFE3E6E1)
    val muted = Color(0xFF8E948F)
    val dim = Color(0xFF5B625C)
    val green = Color(0xFF6BEA8F)
    val greenDeep = Color(0xFF16301F)
    val mint = Color(0xFF93F0AC)
    val mintDeep = Color(0xFF52C97A)
    val onMint = Color(0xFF08180D)
    val amber = Color(0xFFE8C45E)
    val amberDeep = Color(0xFF312912)
}

val Mono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

/** Mono carries the HUD (labels, numbers, headings); the system sans carries sentences people read. */
object HudType {
    val label = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.1.sp)
    val title = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.3.sp)
    val value = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    val bigValue = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    val headline = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp)
    val body = TextStyle(fontSize = 14.sp, lineHeight = 20.sp)
    val small = TextStyle(fontSize = 12.sp, lineHeight = 17.sp)
}

private val Scheme = darkColorScheme(
    primary = Hud.green,
    onPrimary = Hud.onMint,
    primaryContainer = Hud.greenDeep,
    onPrimaryContainer = Hud.green,
    secondary = Hud.amber,
    onSecondary = Hud.onMint,
    tertiary = Hud.mint,
    background = Hud.bg,
    onBackground = Hud.ink,
    surface = Hud.panel,
    onSurface = Hud.ink,
    surfaceVariant = Hud.panelHi,
    onSurfaceVariant = Hud.muted,
    surfaceContainer = Hud.panel,
    surfaceContainerHigh = Hud.panelHi,
    surfaceContainerHighest = Hud.panelHi,
    outline = Hud.line,
    outlineVariant = Hud.grid,
)

private val Type = Typography().let {
    it.copy(
        labelLarge = it.labelLarge.copy(fontFamily = Mono, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
        labelMedium = it.labelMedium.copy(fontFamily = Mono),
        labelSmall = it.labelSmall.copy(fontFamily = Mono),
        titleMedium = it.titleMedium.copy(fontFamily = Mono, fontWeight = FontWeight.Bold),
    )
}

@Composable
fun CommanderTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Type, content = content)
}
