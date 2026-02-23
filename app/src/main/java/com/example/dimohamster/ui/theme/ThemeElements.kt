package com.example.dimohamster.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dimohamster.R

// Custom font
val pixelifySans = FontFamily(
    Font(R.font.pixelify_sans, FontWeight.Normal),
    Font(R.font.pixelify_sans, FontWeight.Bold)
)

val pressStart2P = FontFamily(
    Font(R.font.press_start_2p, FontWeight.Normal)
)

val blueBackground  = Color(red = 15, green= 31, blue = 48)

val btnColour = Color(red = 126, green= 126, blue = 126)

@Composable
fun BouncingRetroButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color(0xFFE85D04),
    textColor: Color = Color.White,
    fontSize: TextUnit = 22.sp,
    enabled: Boolean = true
) {
    var isPressed by remember { mutableStateOf(false) }

    // The amount the button physically "sinks" when pressed
    val verticalPressOffset by animateDpAsState(
        targetValue = if (isPressed && enabled) 6.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "press_depth"
    )

    // Current color based on enabled state
    val currentContainerColor = if (enabled) containerColor else Color(0xFFBBAAAA)

    // A darker shade for the "side" of the button to give it depth
    val sideColor = remember(currentContainerColor) {
        // Adjusts the color to be roughly 30% darker
        Color(
            red = (currentContainerColor.red * 0.7f),
            green = (currentContainerColor.green * 0.7f),
            blue = (currentContainerColor.blue * 0.7f),
            alpha = currentContainerColor.alpha
        )
    }

    Box(
        modifier = modifier
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            try {
                                awaitRelease()
                            } finally {
                                isPressed = false
                            }
                        },
                        onTap = { onClick() }
                    )
                }
            },
        contentAlignment = Alignment.TopCenter
    ) {
        // 1. THE DEPTH LAYER (The "Sides")
        // This stays static and provides the 3D protrusion effect
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp) // Match your button height
                .offset(y = 8.dp) // This creates the thickness visibility
                .background(sideColor, RoundedCornerShape(16.dp))
        )

        // 2. THE TOP FACE
        // This moves down to cover the "depth" layer when pressed
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .offset(y = verticalPressOffset)
                .background(currentContainerColor, RoundedCornerShape(16.dp))
                .border(2.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontFamily = pixelifySans,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = if (enabled) textColor else Color.Gray,
                letterSpacing = 2.sp
            )
        }
    }
}
