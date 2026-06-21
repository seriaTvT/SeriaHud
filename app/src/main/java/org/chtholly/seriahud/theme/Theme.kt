package org.chtholly.seriahud.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.rememberDynamicColorScheme

@Composable
fun SeriaHudTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color (Material You wallpaper colors) is available on Android 12+.
  // Falls back to the pastel-pink MaterialKolor palette below API 31 or if disabled.
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      // Full M3 tonal palette generated from SeedColor for all API levels.
      else -> rememberDynamicColorScheme(seedColor = SeedColor, isDark = darkTheme)
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, shapes = Shapes, content = content)
}
