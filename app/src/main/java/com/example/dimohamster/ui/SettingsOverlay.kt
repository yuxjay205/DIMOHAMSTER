package com.example.dimohamster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.dimohamster.core.NativeRenderer
import com.example.dimohamster.database.HighScoreDatabase
import com.example.dimohamster.ui.theme.pixelifySans
import com.example.dimohamster.ui.theme.pressStart2P
import com.example.dimohamster.ui.theme.BouncingRetroButton
import com.example.dimohamster.ui.theme.btnColour

// Retro color palette
private val CreamBackground = Color(0xFFF5F0E8)
private val CreamLight = Color(0xFFFAF7F2)
private val CreamDark = Color(0xFFE8E0D5)
private val DisplayDark = Color(0xFF2A2A2A)
private val AccentOrange = Color(0xFFE85D04)
private val AccentGreen = Color(0xFF4CAF50)
private val AccentRed = Color(0xFFE53935)
private val TextDark = Color(0xFF333333)
private val TextMuted = Color(0xFF888888)

/**
 * Settings overlay dialog for game controls - Retro Style
 */
@Composable
fun SettingsOverlay(
    onDismiss: () -> Unit,
    onBackToMainMenu: () -> Unit = {}
) {
    var smoothingFactor by remember { mutableStateOf(0.3f) }
    var sensitivity by remember { mutableStateOf(1.0f) }
    var showCameraBackground by remember { mutableStateOf(true) }
    var showLeaderboard by remember { mutableStateOf(false) }

    // Pause game when settings opens
    LaunchedEffect(Unit) {
        NativeRenderer.setPaused(true)
    }

    // Resume game when settings closes
    DisposableEffect(Unit) {
        onDispose {
            NativeRenderer.setPaused(false)
        }
    }

    Dialog(onDismissRequest = { onDismiss() }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(20.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(CreamLight, CreamDark)
                    )
                )
                .border(
                    width = 5.dp,
                    color = Color.Gray,
                    shape = RoundedCornerShape(10.dp)
                )
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title display
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DisplayDark)
                        .border(2.dp, Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SETTINGS",
                        fontFamily = pressStart2P,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEEEEEE),
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Settings content area
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFE0D8CC))
                        .padding(16.dp)
                ) {
                    // Smoothing slider
                    RetroSettingSlider(
                        label = "Nose Smoothing",
                        value = smoothingFactor,
                        onValueChange = {
                            smoothingFactor = it
                            NativeRenderer.setNoseSmoothingFactor(it)
                        },
                        valueRange = 0.0f..1.0f,
                        valueText = "${(smoothingFactor * 100).toInt()}%"
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sensitivity slider
                    RetroSettingSlider(
                        label = "Sensitivity",
                        value = sensitivity,
                        onValueChange = {
                            sensitivity = it
                            NativeRenderer.setSensitivity(it)
                        },
                        valueRange = 0.5f..1.5f,
                        valueText = "${(sensitivity * 100).toInt()}%"
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Camera background toggle
                    RetroSettingToggle(
                        label = "Camera Background",
                        checked = showCameraBackground,
                        onCheckedChange = {
                            showCameraBackground = it
                            NativeRenderer.setShowCameraBackground(it)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Leaderboard button
                BouncingRetroButton(
                    text = "LEADERBOARD",
                    onClick = { showLeaderboard = true },
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = btnColour,
                    fontSize = 16.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Resume button
                BouncingRetroButton(
                    text = "RESUME",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = btnColour,
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Main menu button
                BouncingRetroButton(
                    text = "MAIN MENU",
                    onClick = {
                        NativeRenderer.setPaused(false)
                        onBackToMainMenu()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = btnColour,
                    fontSize = 16.sp
                )
            }
        }
    }

    // Leaderboard dialog
    if (showLeaderboard) {
        LeaderboardDialog(onDismiss = { showLeaderboard = false })
    }
}

@Composable
fun RetroSettingSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontFamily = pixelifySans,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(DisplayDark)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = valueText,
                    fontFamily = pixelifySans,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFCCCCCC)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = AccentOrange,
                activeTrackColor = AccentOrange,
                inactiveTrackColor = Color(0xFFCCC4B8)
            )
        )
    }
}

@Composable
fun RetroSettingToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontFamily = pixelifySans,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentGreen,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFCCC4B8)
            )
        )
    }
}

@Composable
fun LeaderboardDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val database = remember { HighScoreDatabase(context) }
    val highScores = remember { database.getTopScores(10) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .shadow(20.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(CreamLight, CreamDark)
                    )
                )
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DisplayDark)
                        .border(2.dp, Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "LEADERBOARD",
                        fontFamily = pressStart2P,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFCC00),
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scores list
                if (highScores.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFE0D8CC))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No scores yet!\nPlay a game to set a record",
                            fontFamily = pixelifySans,
                            fontSize = 14.sp,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFE0D8CC))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(highScores) { index, entry ->
                            LeaderboardEntry(
                                rank = index + 1,
                                playerName = entry.playerName,
                                score = entry.score,
                                level = entry.level
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Close button
                BouncingRetroButton(
                    text = "CLOSE",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = btnColour,
                    fontSize = 18.sp
                )
            }
        }
    }
}

@Composable
fun LeaderboardEntry(
    rank: Int,
    playerName: String,
    score: Int,
    level: Int
) {
    val rankColor = when (rank) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        else -> TextMuted
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CreamLight)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (rank <= 3) rankColor.copy(alpha = 0.2f) else Color(0xFFE0D8CC)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$rank",
                        fontFamily = pixelifySans,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (rank <= 3) rankColor else TextDark
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = playerName,
                        fontFamily = pixelifySans,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Text(
                        text = "Level $level",
                        fontFamily = pixelifySans,
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }
            Text(
                text = "$score",
                fontFamily = pixelifySans,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AccentOrange
            )
        }
    }
}
