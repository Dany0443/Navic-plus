package paige.navic.ui.smartplaylists

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Simple inline icons used for Smart Playlist placeholders.
 *
 * They are defined here (instead of in the Library UI) so new Smart Playlists can easily
 * define their own visual identity without touching layout code.
 */
object SmartPlaylistIcons {
	val Trophy: ImageVector by lazy {
		Builder(
			name = "SmartTrophy",
			defaultWidth = 24.dp,
			defaultHeight = 24.dp,
			viewportWidth = 24f,
			viewportHeight = 24f
		).apply {
			// Cup
			path(
				fill = SolidColor(Color.Black),
				pathFillType = PathFillType.NonZero
			) {
				moveTo(7f, 4f)
				lineTo(17f, 4f)
				lineTo(17f, 9f)
				curveTo(17f, 12f, 14.8f, 14f, 12f, 14f)
				curveTo(9.2f, 14f, 7f, 12f, 7f, 9f)
				close()
			}
			// Handles
			path(
				fill = SolidColor(Color.Black),
				pathFillType = PathFillType.NonZero
			) {
				moveTo(5.5f, 6f)
				curveTo(4.1f, 6.2f, 3.2f, 7.2f, 3.2f, 8.6f)
				curveTo(3.2f, 10.9f, 5.1f, 12.2f, 6.8f, 12.6f)
				lineTo(6.8f, 11.2f)
				curveTo(5.8f, 10.9f, 4.7f, 10.1f, 4.7f, 8.6f)
				curveTo(4.7f, 7.9f, 5f, 7.4f, 5.5f, 7.2f)
				close()
				moveTo(18.5f, 6f)
				curveTo(19.9f, 6.2f, 20.8f, 7.2f, 20.8f, 8.6f)
				curveTo(20.8f, 10.9f, 18.9f, 12.2f, 17.2f, 12.6f)
				lineTo(17.2f, 11.2f)
				curveTo(18.2f, 10.9f, 19.3f, 10.1f, 19.3f, 8.6f)
				curveTo(19.3f, 7.9f, 19f, 7.4f, 18.5f, 7.2f)
				close()
			}
			// Stem + base
			path(
				fill = SolidColor(Color.Black),
				pathFillType = PathFillType.NonZero
			) {
				moveTo(11f, 14f)
				lineTo(13f, 14f)
				lineTo(13f, 17f)
				lineTo(16f, 17f)
				lineTo(16f, 19f)
				lineTo(8f, 19f)
				lineTo(8f, 17f)
				lineTo(11f, 17f)
				close()
			}
		}.build()
	}

	val Crown: ImageVector by lazy {
		Builder(
			name = "SmartCrown",
			defaultWidth = 24.dp,
			defaultHeight = 24.dp,
			viewportWidth = 24f,
			viewportHeight = 24f
		).apply {
			path(
				fill = SolidColor(Color.Black),
				pathFillType = PathFillType.NonZero
			) {
				// Three spikes
				moveTo(4f, 9f)
				lineTo(7.2f, 13f)
				lineTo(12f, 7f)
				lineTo(16.8f, 13f)
				lineTo(20f, 9f)
				lineTo(20f, 18f)
				lineTo(4f, 18f)
				close()
				// Base highlight cutout
				moveTo(6f, 16f)
				lineTo(18f, 16f)
				lineTo(18f, 17f)
				lineTo(6f, 17f)
				close()
			}
		}.build()
	}

	val Compass: ImageVector by lazy {
		Builder(
			name = "SmartCompass",
			defaultWidth = 24.dp,
			defaultHeight = 24.dp,
			viewportWidth = 24f,
			viewportHeight = 24f
		).apply {
			// Outer circle
			path(
				fill = SolidColor(Color.Black),
				pathFillType = PathFillType.NonZero
			) {
				moveTo(12f, 2.5f)
				curveTo(6.75f, 2.5f, 2.5f, 6.75f, 2.5f, 12f)
				curveTo(2.5f, 17.25f, 6.75f, 21.5f, 12f, 21.5f)
				curveTo(17.25f, 21.5f, 21.5f, 17.25f, 21.5f, 12f)
				curveTo(21.5f, 6.75f, 17.25f, 2.5f, 12f, 2.5f)
				close()
				// inner cutout
				moveTo(12f, 4.2f)
				curveTo(16.32f, 4.2f, 19.8f, 7.68f, 19.8f, 12f)
				curveTo(19.8f, 16.32f, 16.32f, 19.8f, 12f, 19.8f)
				curveTo(7.68f, 19.8f, 4.2f, 16.32f, 4.2f, 12f)
				curveTo(4.2f, 7.68f, 7.68f, 4.2f, 12f, 4.2f)
				close()
			}
			// Needle
			path(
				fill = SolidColor(Color.Black),
				pathFillType = PathFillType.NonZero
			) {
				moveTo(14.8f, 9.2f)
				lineTo(12.8f, 13.2f)
				lineTo(9.2f, 14.8f)
				lineTo(11.2f, 10.8f)
				close()
			}
			// Center dot
			path(
				fill = SolidColor(Color.Black),
				pathFillType = PathFillType.NonZero
			) {
				moveTo(12f, 11f)
				curveTo(12.55f, 11f, 13f, 11.45f, 13f, 12f)
				curveTo(13f, 12.55f, 12.55f, 13f, 12f, 13f)
				curveTo(11.45f, 13f, 11f, 12.55f, 11f, 12f)
				curveTo(11f, 11.45f, 11.45f, 11f, 12f, 11f)
				close()
			}
		}.build()
	}

	val Sparkle: ImageVector by lazy {
		Builder(
			name = "SmartSparkle",
			defaultWidth = 24.dp,
			defaultHeight = 24.dp,
			viewportWidth = 24f,
			viewportHeight = 24f
		).apply {
			path(
				fill = SolidColor(Color.Black),
				pathFillType = PathFillType.NonZero
			) {
				// A simple 4-point sparkle.
				moveTo(12f, 4f)
				lineTo(13.2f, 10.8f)
				lineTo(20f, 12f)
				lineTo(13.2f, 13.2f)
				lineTo(12f, 20f)
				lineTo(10.8f, 13.2f)
				lineTo(4f, 12f)
				lineTo(10.8f, 10.8f)
				close()
			}
		}.build()
	}
}
