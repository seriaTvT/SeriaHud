package org.chtholly.seriahud.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Standard Material 3 corner-radius scale. Wired into MaterialTheme in Theme.kt
// so in-app components share one consistent shape language. The overlay HUD
// (OverlayService.kt) intentionally does NOT use these — it stays visually
// independent of the app theme so it reads over arbitrary host content.
val Shapes =
  Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )
