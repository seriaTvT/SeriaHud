package org.chtholly.seriahud.theme

import androidx.compose.ui.graphics.Color

// Color palette for the overlay HUD. Intentionally independent of the app's
// adaptive M3 color scheme: the HUD renders over arbitrary host apps/games with
// unpredictable backgrounds, so it needs fixed high-contrast colors rather than
// wallpaper-derived dynamic color. Promoting the previously-scattered literals
// in OverlayService.kt into a named, swappable palette sets up user-selectable
// HUD themes (Phase 3) without yet adding any persisted settings.
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
        background = Color(0xAA000000),
        graphLine = Color(0xFFE5C07B),
        graphBackground = Color(0x33FFFFFF),
      )
  }
}
