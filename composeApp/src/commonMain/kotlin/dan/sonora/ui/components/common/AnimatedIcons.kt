package dan.sonora.ui.components.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import dan.sonora.ui.navigation.Screen

@Composable
expect fun animatedTabIconPainter(destination: Screen): Painter?

@Composable
expect fun playPauseIconPainter(reversed: Boolean): Painter?
