package com.example.dimohamster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.dimohamster.core.NativeRenderer
import com.example.dimohamster.database.HighScoreDatabase

/**
 * Settings overlay dialog for game controls
 */
@Composable
fun SettingsOverlay(
    onDismiss: () -> Unit,
    onBackToMainMenu: () -> Unit = {}
) {
    // State for settings
    var smoothingFactor by remember { mutableStateOf(0.3f) }  // Default: 30% (heavy smoothing)
    var sensitivity by remember { mutableStateOf(1.0f) }       // Default: 100%
    var trajectoryEnabled by remember { mutableStateOf(true) }
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

    Dialog(onDismissRequest = {
        onDismiss()
    }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xEE000000) // Semi-transparent black
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    text = "Game Settings",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Smoothing slider
                Text(
                    text = "Nose Smoothing: ${(smoothingFactor * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 16.sp
                )
                Text(
                    text = "Lower = smoother, Higher = more responsive",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Slider(
                    value = smoothingFactor,
                    onValueChange = {
                        smoothingFactor = it
                        NativeRenderer.setNoseSmoothingFactor(it)
                    },
                    valueRange = 0.0f..1.0f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Sensitivity slider
                Text(
                    text = "Sensitivity: ${(sensitivity * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 16.sp
                )
                Text(
                    text = "Adjust throwing power",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Slider(
                    value = sensitivity,
                    onValueChange = {
                        sensitivity = it
                        NativeRenderer.setSensitivity(it)
                    },
                    valueRange = 0.5f..1.5f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Camera background toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Camera Background",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Show camera feed in background",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = showCameraBackground,
                        onCheckedChange = {
                            showCameraBackground = it
                            NativeRenderer.setShowCameraBackground(it)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Trajectory preview toggle (kept for compatibility)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Trajectory Preview",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Show ball path while aiming (N/A)",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = trajectoryEnabled,
                        onCheckedChange = {
                            trajectoryEnabled = it
                            NativeRenderer.setTrajectoryPreviewEnabled(it)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Leaderboard button
                Button(
                    onClick = { showLeaderboard = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFC107) // Gold color
                    )
                ) {
                    Text(
                        text = "🏆 View Leaderboard",
                        fontSize = 16.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Close button (resume game)
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Text(
                        text = "Resume Game",
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Back to Main Menu button
                OutlinedButton(
                    onClick = {
                        NativeRenderer.setPaused(false)
                        onBackToMainMenu()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFF44336)
                    )
                ) {
                    Text(
                        text = "Back to Main Menu",
                        fontSize = 16.sp
                    )
                }
            }
        }
    }

    // Show leaderboard dialog
    if (showLeaderboard) {
        LeaderboardDialog(
            onDismiss = { showLeaderboard = false }
        )
    }
}

@Composable
fun LeaderboardDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { HighScoreDatabase(context) }
    val highScores = remember { database.getTopScores(10) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xEE000000) // Semi-transparent black
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    text = "🏆 Leaderboard",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFC107) // Gold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Scores list
                if (highScores.isEmpty()) {
                    // No scores yet
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No scores yet!",
                                fontSize = 18.sp,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Play a game to set a record",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Text(
                        text = "Close",
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
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
    val backgroundColor = when (rank) {
        1 -> Color(0xFF, 0xD7, 0x00, 0x40) // Gold with alpha
        2 -> Color(0xC0, 0xC0, 0xC0, 0x40) // Silver with alpha
        3 -> Color(0xCD, 0x7F, 0x32, 0x40) // Bronze with alpha
        else -> Color(0x33, 0x33, 0x33, 0x80) // Dark gray with alpha
    }

    val rankEmoji = when (rank) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> "$rank."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank
            Text(
                text = rankEmoji,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.width(40.dp)
            )

            // Player name
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = playerName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Level $level",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            // Score
            Text(
                text = score.toString(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFC107) // Gold
            )
        }
    }
}
