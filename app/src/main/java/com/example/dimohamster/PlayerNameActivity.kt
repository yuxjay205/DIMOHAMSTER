package com.example.dimohamster

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.dimohamster.audio.BackgroundMusicManager
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
private val AccentOrangeLight = Color(0xFFFF7B2E)
private val TextDark = Color(0xFF333333)
private val TextMuted = Color(0xFF888888)

class PlayerNameActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupFullscreen()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prefs = getSharedPreferences("game_prefs", MODE_PRIVATE)
        val savedName = prefs.getString("player_name", "") ?: ""

        setContent {
            SUPERBALLTheme {
                PlayerNameScreen(
                    initialName = savedName,
                    onStartGame = { playerName ->
                        prefs.edit().putString("player_name", playerName).apply()
                        val intent = Intent(this, MainActivity::class.java)
                        intent.putExtra("player_name", playerName)
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
fun PlayerNameScreen(
    initialName: String,
    onStartGame: (String) -> Unit
) {
    var playerName by remember { mutableStateOf(initialName.ifEmpty { "" }) }
    val focusManager = LocalFocusManager.current

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
                        .shadow(
                            elevation = 4.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = Color.Transparent,
                            spotColor = Color.Transparent
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(DisplayDark)
                        .border(
                            width = 2.dp,
                            color = Color(0xFF1A1A1A),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(vertical = 20.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Small label
//                        Text(
//                            text = "PLAYER ID",
//                            fontSize = 10.sp,
//                            fontWeight = FontWeight.Bold,
//                            color = Color(0xFF666666),
//                            letterSpacing = 2.sp
//                        )
//                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "ENTER NAME",
                            fontSize = 22.sp,
                            fontFamily = pressStart2P,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFCC00),
                            letterSpacing = 3.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Input field with inset style
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFE0D8CC))
                        .border(
                            width = 1.dp,
                            color = Color(0xFFCCC4B8),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(4.dp)
                ) {
                    OutlinedTextField(
                        value = playerName,
                        onValueChange = {
                            if (it.length <= 12) {
                                playerName = it
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = pixelifySans,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = TextDark
                        ),
                        placeholder = {
                            Text(
                                text = "Your name",
                                fontFamily = pixelifySans,
                                fontSize = 22.sp,
                                color = TextMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (playerName.isNotBlank()) {
                                    onStartGame(playerName.trim())
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = AccentOrange,
                            focusedContainerColor = CreamLight,
                            unfocusedContainerColor = CreamLight
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Character counter
                Text(
                    text = "${playerName.length}/12",
                    fontFamily = pixelifySans,
                    fontSize = 12.sp,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Start button row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Decorative indicator dot
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .shadow(2.dp, CircleShape)
                            .clip(CircleShape)
                            .background(
                                if (playerName.isNotBlank()) Color(0xFF4CAF50) else Color(0xFF888888)
                            )
                    )

                    Spacer(modifier = Modifier.width(24.dp))

                    // Main start button
                    BouncingRetroButton(
                        text = "START",
                        onClick = {
                            if (playerName.isNotBlank()) {
                                onStartGame(playerName.trim())
                            }
                        },
                        modifier = Modifier.width(180.dp),
                        enabled = playerName.isNotBlank(),
                        containerColor = btnColour,
                        fontSize = 18.sp
                    )

                    Spacer(modifier = Modifier.width(24.dp))

                    // Speaker dots decoration
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
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

                Spacer(modifier = Modifier.height(24.dp))

                // Bottom info bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE8E0D5))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Your name appears on the leaderboard",
                        fontFamily = pixelifySans,
                        fontSize = 12.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}


