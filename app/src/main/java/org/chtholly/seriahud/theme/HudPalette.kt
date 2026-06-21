package org.chtholly.seriahud.theme

import androidx.compose.ui.graphics.Color

// Color palette for the overlay HUD. Intentionally independent of the app's
// adaptive M3 color scheme: the HUD renders over arbitrary host apps/games with
// unpredictable backgrounds, so it needs fixed high-contrast colors rather than
// wallpaper-derived dynamic color. `background` is an opaque base; OverlayUI
// applies the user's opacity on top of it.
data class HudPalette(
  val fps: Color,
  val gpu: Color,
  val cpu: Color,
  val cpuCore: Color,
  val ram: Color,
  val battery: Color,
  val text: Color,
  val background: Color,
  val graphLine: Color,
  val graphBackground: Color,
) {
  companion object {
    // Atom One Dark-style palette — the original hardcoded HUD colors.
    val Default =
      HudPalette(
        fps = Color(0xFFE5C07B),
        gpu = Color(0xFF98C379),
        cpu = Color(0xFF61AFEF),
        cpuCore = Color(0xFF56B6C2),
        ram = Color(0xFFC678DD),
        battery = Color(0xFFD19A66),
        text = Color.White,
        background = Color(0xFF000000),
        graphLine = Color(0xFFE5C07B),
        graphBackground = Color(0x33FFFFFF),
      )

    // Soft pastel pink, matching the app's brand.
    val Sakura =
      HudPalette(
        fps = Color(0xFFFF8FB3),
        gpu = Color(0xFFB39DDB),
        cpu = Color(0xFF80DEEA),
        cpuCore = Color(0xFFA5D6A7),
        ram = Color(0xFFF48FB1),
        battery = Color(0xFFFFCC80),
        text = Color.White,
        background = Color(0xFF1A1014),
        graphLine = Color(0xFFFF8FB3),
        graphBackground = Color(0x33FFFFFF),
      )

    // High-contrast monochrome (single light tone for every metric).
    val Mono =
      HudPalette(
        fps = Color(0xFFF5F5F5),
        gpu = Color(0xFFF5F5F5),
        cpu = Color(0xFFF5F5F5),
        cpuCore = Color(0xFFF5F5F5),
        ram = Color(0xFFF5F5F5),
        battery = Color(0xFFF5F5F5),
        text = Color(0xFFF5F5F5),
        background = Color(0xFF000000),
        graphLine = Color(0xFFF5F5F5),
        graphBackground = Color(0x33FFFFFF),
      )

    // Solarized Dark accents on the solarized base.
    val Solarized =
      HudPalette(
        fps = Color(0xFFB58900),
        gpu = Color(0xFF859900),
        cpu = Color(0xFF268BD2),
        cpuCore = Color(0xFF2AA198),
        ram = Color(0xFF6C71C4),
        battery = Color(0xFFCB4B16),
        text = Color(0xFFEEE8D5),
        background = Color(0xFF002B36),
        graphLine = Color(0xFFB58900),
        graphBackground = Color(0x33FFFFFF),
      )

    // Order is the persisted accentPresetIndex; keep in sync with the
    // preset name list in SettingsScreen.
    val Presets = listOf(Default, Sakura, Mono, Solarized)

    fun byIndex(index: Int): HudPalette = Presets.getOrElse(index) { Default }
  }
}
