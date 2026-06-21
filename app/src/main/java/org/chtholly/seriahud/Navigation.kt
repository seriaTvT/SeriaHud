package org.chtholly.seriahud

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Navigation 3 destinations. Keys are @Serializable so the back stack survives
// process death (rememberNavBackStack persists them). Chart carries the record
// file path as a String rather than a File, since keys must be serializable.
@Serializable
sealed interface Route : NavKey {
  @Serializable data object Home : Route

  @Serializable data object Records : Route

  @Serializable data object Settings : Route

  @Serializable data class Chart(val path: String) : Route
}
