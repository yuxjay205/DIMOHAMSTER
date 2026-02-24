package com.example.dimohamster

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.dimohamster.audio.BackgroundMusicManager
import com.example.dimohamster.audio.SoundEffectManager
import com.example.dimohamster.database.HighScoreDatabase
import com.example.dimohamster.ui.theme.SUPERBALLTheme
import com.example.dimohamster.ui.theme.pixelifySans
import com.example.dimohamster.ui.theme.pressStart2P
import com.example.dimohamster.ui.theme.blueBackground
import com.example.dimohamster.ui.theme.btnColour
import com.example.dimohamster.ui.theme.BouncingRetroButton

// Retro color palette
private val CreamBackground = Color(0xFFF5F0E8)
private val CreamLight = Color(0xFFFAF7F2)
private val CreamDark = Color(0xFFE8E0D5)
private val DisplayDark = Color(0xFF2A2A2A)
private val DisplayGray = Color(0xFF3A3A3A)
private val AccentOrange = Color(0xFFE85D04)
private val AccentGreen = Color(0xFF4CAF50)
private val TextDark = Color(0xFF333333)
private val TextMuted = Color(0xFF888888)

class MainMenuActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainMenuActivity"
    }

    // Permission launcher for camera
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i(TAG, "Camera permission granted")
        } else {
            Log.w(TAG, "Camera permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupFullscreen()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        BackgroundMusicManager.start(this)
        SoundEffectManager.init(this)

        // Request camera permission on app open
        requestCameraPermission()

        setContent {
            SUPERBALLTheme {
                MainMenuScreen(
                    onPlayClicked = {
                        val intent = Intent(this, PlayerNameActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        BackgroundMusicManager.resume()
    }

    override fun onPause() {
        super.onPause()
        BackgroundMusicManager.pause()
    }

    private fun requestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.i(TAG, "Camera permission already granted")
            }
            else -> {
                Log.i(TAG, "Requesting camera permission")
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun setupFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
        )
    }
}

@Composable
fun MainMenuScreen(
    onPlayClicked: () -> Unit
) {
    var showLeaderboard by remember { mutableStateOf(false) }
    var showHowToPlay by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(blueBackground),
        contentAlignment = Alignment.Center
    ) {
        // Main card container
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(32.dp),
                    ambientColor = Color(0x40000000),
                    spotColor = Color(0x30000000)
                )
                .clip(RoundedCornerShape(32.dp))
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
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Inset display area for title
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DisplayDark)
                        .border(
                            width = 2.dp,
                            color = Color(0xFF1A1A1A),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(vertical = 24.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Small label
//                        Text(
//                            text = "NOSE CONTROL",
//                            fontSize = 10.sp,
//                            fontWeight = FontWeight.Bold,
//                            color = Color(0xFF666666),
//                            letterSpacing = 2.sp
//                        )
//                        Spacer(modifier = Modifier.height(8.dp))
                        // Main title
//                        Text(
//                            text = "SUPERBALL",
//                            fontSize = 36.sp,
//                            fontWeight = FontWeight.Bold,
//                            color = Color(0xFFEEEEEE),
//                            letterSpacing = 6.sp
//                        )
                        Image(painter = painterResource(id = R.drawable.superball_logo),
                            contentDescription = null)
//                        Spacer(modifier = Modifier.height(4.dp))
                        // Decorative line
//                        Box(
//                            modifier = Modifier
//                                .width(140.dp)
//                                .height(2.dp)
//                                .background(AccentOrange)
//                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Play button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left indicator
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .shadow(2.dp, CircleShape)
                            .clip(CircleShape)
                            .background(AccentGreen)
                    )

                    Spacer(modifier = Modifier.width(20.dp))

                    BouncingRetroButton(
                        text = "PLAY",
                        onClick = onPlayClicked,
                        modifier = Modifier.width(200.dp),
                        containerColor = btnColour,
                        fontSize = 30.sp
                    )

                    Spacer(modifier = Modifier.width(20.dp))

                    // Speaker dots
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        repeat(3) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                repeat(2) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF888888))
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Secondary buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Leaderboard button
                    BouncingRetroButton(
                        text = "SCORES",
                        onClick = { showLeaderboard = true },
                        modifier = Modifier.weight(1f),
                        containerColor = btnColour,
                        fontSize = 30.sp
                    )

                    // How to Play button
                    BouncingRetroButton(
                        text = "HOW TO",
                        onClick = { showHowToPlay = true },
                        modifier = Modifier.weight(1f),
                        containerColor = btnColour,
                        fontSize = 30.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Bottom version bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE0D8CC))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "v1.0",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(AccentGreen)
                            )
                            Text(
                                text = "READY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }
    }

    // Leaderboard dialog
    if (showLeaderboard) {
        RetroLeaderboardDialog(onDismiss = { showLeaderboard = false })
    }

    // How to Play dialog
    if (showHowToPlay) {
        RetroHowToPlayDialog(onDismiss = { showHowToPlay = false })
    }
}

@Composable
fun RetroButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(12.dp)
            ),
        colors = ButtonDefaults.buttonColors(
            containerColor = CreamLight
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 1.dp
        )
    ) {
        Text(
            text = text,
            fontFamily = pixelifySans,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun RetroLeaderboardDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val database = remember { HighScoreDatabase(context) }
    val highScores = remember { database.getTopScores(10) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
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
                            RetroScoreEntry(
                                rank = index + 1,
                                playerName = entry.playerName,
                                score = entry.score,
                                level = entry.level
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Close button
                BouncingRetroButton(
                    text = "CLOSE",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), // Padding for the protrusion
                    containerColor = btnColour,
                    fontSize = 18.sp
                )
            }
        }
    }
}

@Composable
fun RetroScoreEntry(
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
                // Rank
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (rank <= 3) rankColor.copy(alpha = 0.2f) else Color(
                                0xFFE0D8CC
                            )
                        ),
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
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AccentOrange
            )
        }
    }
}

@Composable
fun RetroHowToPlayDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
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
                        text = "HOW TO PLAY",
                        fontFamily = pressStart2P,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFCC00),
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Instructions
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFE0D8CC))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RetroInstructionItem("1", "Move nose left/right to control paddle")
                    RetroInstructionItem("2", "Open mouth to launch the ball")
                    RetroInstructionItem("3", "Break all bricks to advance")
                    RetroInstructionItem("4", "Catch power-ups for abilities!")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Power-ups section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DisplayDark)
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "POWER-UPS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF888888),
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        RetroPowerUpItem(Color(0xFF2196F3), "Wide Paddle")
                        RetroPowerUpItem(Color(0xFF4CAF50), "Slow Ball")
                        RetroPowerUpItem(Color(0xFFF44336), "Extra Life")
                        RetroPowerUpItem(Color(0xFFFFAB00), "Big Ball")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tip
                Text(
                    text = "Tip: Fewer lives = more power-up drops!",
                    fontSize = 11.sp,
                    fontFamily = pixelifySans,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Close button
                BouncingRetroButton(
                    text = "GOT IT!",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), // Padding for the protrusion
                    containerColor = btnColour,
                    fontSize = 18.sp
                )
            }
        }
    }
}

@Composable
fun RetroInstructionItem(number: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(AccentOrange),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                fontFamily = pixelifySans,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontFamily = pixelifySans,
            fontSize = 13.sp,
            color = TextDark
        )
    }
}

@Composable
fun RetroPowerUpItem(color: Color, name: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = name,
            fontFamily = pixelifySans,
            fontSize = 12.sp,
            color = Color(0xFFCCCCCC)
        )
    }
}
