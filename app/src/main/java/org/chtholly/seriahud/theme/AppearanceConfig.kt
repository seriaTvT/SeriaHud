package org.chtholly.seriahud.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

@Stable
object AppearanceConfig {
    var cardAlpha by mutableFloatStateOf(1f)
    var backgroundImageUri by mutableStateOf<String?>(null)

    private const val PREFS_NAME = "appearance_settings"
    private const val KEY_CARD_ALPHA = "card_alpha"
    private const val KEY_BG_URI = "background_image_uri"

    fun updateAlpha(alpha: Float) {
        cardAlpha = alpha.coerceIn(0f, 1f)
    }

    fun updateBackgroundUri(uri: String?) {
        backgroundImageUri = uri
    }

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        cardAlpha = prefs.getFloat(KEY_CARD_ALPHA, 1f).coerceIn(0f, 1f)
        backgroundImageUri = prefs.getString(KEY_BG_URI, null)
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putFloat(KEY_CARD_ALPHA, cardAlpha)
            putString(KEY_BG_URI, backgroundImageUri)
            apply()
        }
    }
}

object CardStyleProvider {
    @Composable
    fun getCardColors(originalColor: Color = MaterialTheme.colorScheme.surfaceVariant) = CardDefaults.elevatedCardColors(
        containerColor = originalColor.copy(alpha = AppearanceConfig.cardAlpha),
        contentColor = determineContentColor(originalColor),
        disabledContainerColor = originalColor.copy(alpha = AppearanceConfig.cardAlpha * 0.38f),
        disabledContentColor = determineContentColor(originalColor).copy(alpha = 0.38f)
    )

    @Composable
    private fun determineContentColor(originalColor: Color): Color {
        val isDarkTheme = isSystemInDarkTheme()
        val luminance = originalColor.luminance()
        val threshold = if (isDarkTheme) 0.4f else 0.6f
        return if (luminance > threshold) Color.Black else Color.White
    }
}

@Composable
fun getCardColors(originalColor: Color = MaterialTheme.colorScheme.surfaceVariant) = CardStyleProvider.getCardColors(originalColor)
