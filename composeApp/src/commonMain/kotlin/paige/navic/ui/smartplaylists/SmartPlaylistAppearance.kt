package paige.navic.ui.smartplaylists

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import paige.navic.domain.models.SmartPlaylist
import paige.navic.domain.models.SmartPlaylistType
import paige.navic.icons.Icons
import paige.navic.icons.filled.RepeatOn
import paige.navic.icons.filled.ShuffleOn

/**
 * Everything a Smart Playlist card needs to present itself. Adding a new playlist means
 * adding one definition here; the Library card renderer remains unchanged.
 */
data class SmartPlaylistAppearance(
	val title: String,
	val subtitle: String,
	val gradientColors: List<Color>,
	val icon: ImageVector,
	val artwork: SmartPlaylistArtwork,
	val badgeIcon: ImageVector? = null
) {
	fun brush(): Brush = Brush.linearGradient(gradientColors)
}

enum class SmartPlaylistArtwork {
	Trophy,
	Repeat,
	Discovery
}

fun SmartPlaylistType.appearance(): SmartPlaylistAppearance = when (this) {
	SmartPlaylistType.MostPlayed -> SmartPlaylistAppearance(
		title = "Most Played",
		subtitle = "Your all-time favorites",
		gradientColors = listOf(Color(0xFF7A4800), Color(0xFFD99418), Color(0xFFFFE3A2)),
		icon = SmartPlaylistIcons.Trophy,
		artwork = SmartPlaylistArtwork.Trophy,
		badgeIcon = SmartPlaylistIcons.Crown
	)

	SmartPlaylistType.OnRepeat -> SmartPlaylistAppearance(
		title = "On Repeat",
		subtitle = "In heavy rotation",
		gradientColors = listOf(Color(0xFF25105B), Color(0xFF6246C7), Color(0xFF2E8BD7)),
		icon = Icons.Filled.RepeatOn,
		artwork = SmartPlaylistArtwork.Repeat
	)

	SmartPlaylistType.NeverPlayed -> SmartPlaylistAppearance(
		title = "Never Played",
		subtitle = "Discover something new",
		gradientColors = listOf(Color(0xFF063C4B), Color(0xFF087F7B), Color(0xFF5CCB91)),
		icon = SmartPlaylistIcons.Compass,
		artwork = SmartPlaylistArtwork.Discovery,
		badgeIcon = Icons.Filled.ShuffleOn
	)
}

@Composable
fun SmartPlaylistCoverArtwork(
	appearance: SmartPlaylistAppearance,
	modifier: Modifier = Modifier
) {
	Box(modifier = modifier.background(appearance.brush()), contentAlignment = Alignment.Center) {
		Canvas(Modifier.fillMaxSize()) {
			val width = size.width
			val height = size.height
			when (appearance.artwork) {
				SmartPlaylistArtwork.Trophy -> {
					drawCircle(Color.White.copy(alpha = .13f), width * .58f, Offset(width * .18f, height * .16f))
					drawCircle(Color(0xFFFFF3C4).copy(alpha = .18f), width * .40f, Offset(width * .84f, height * .84f))
					drawCircle(Color.White.copy(alpha = .12f), width * .10f, Offset(width * .77f, height * .17f))
				}

				SmartPlaylistArtwork.Repeat -> {
					val stroke = Stroke(width = width * .045f, cap = StrokeCap.Round)
					drawArc(Color.White.copy(alpha = .18f), 205f, 215f, false, Offset(width * .04f, height * .04f), size * .92f, style = stroke)
					drawArc(Color(0xFFC8B8FF).copy(alpha = .28f), 22f, 190f, false, Offset(width * .17f, height * .17f), size * .66f, style = stroke)
					drawCircle(Color(0xFFB7E5FF).copy(alpha = .16f), width * .18f, Offset(width * .82f, height * .18f))
				}

				SmartPlaylistArtwork.Discovery -> {
					drawCircle(Color(0xFF052A37).copy(alpha = .34f), width * .40f, Offset(width * .69f, height * .62f))
					drawCircle(Color.White.copy(alpha = .16f), width * .28f, Offset(width * .69f, height * .62f), style = Stroke(width * .025f))
					drawCircle(Color.White.copy(alpha = .10f), width * .12f, Offset(width * .69f, height * .62f))
					drawCircle(Color.White.copy(alpha = .76f), width * .024f, Offset(width * .20f, height * .25f))
					drawCircle(Color.White.copy(alpha = .50f), width * .016f, Offset(width * .79f, height * .18f))
					drawCircle(Color.White.copy(alpha = .43f), width * .018f, Offset(width * .25f, height * .78f))
				}
			}
		}

		Icon(
			imageVector = appearance.icon,
			contentDescription = null,
			tint = Color.White,
			modifier = Modifier.size(52.dp).alpha(.94f)
		)

		appearance.badgeIcon?.let { badge ->
			Icon(
				imageVector = badge,
				contentDescription = null,
				tint = Color.White.copy(alpha = .88f),
				modifier = Modifier.align(Alignment.TopEnd).offset((-8).dp, 8.dp).size(20.dp)
			)
		}
		if (appearance.artwork == SmartPlaylistArtwork.Discovery) {
			Icon(
				imageVector = SmartPlaylistIcons.Sparkle,
				contentDescription = null,
				tint = Color.White.copy(alpha = .78f),
				modifier = Modifier.align(Alignment.BottomStart).offset(10.dp, (-10).dp).size(18.dp)
			)
		}
	}
}

@Composable
fun SmartPlaylistCoverPlaceholderSlot(
	playlist: SmartPlaylist
): @Composable BoxScope.() -> Unit {
	val appearance = playlist.type.appearance()
	return { SmartPlaylistCoverArtwork(appearance, Modifier.fillMaxSize()) }
}
