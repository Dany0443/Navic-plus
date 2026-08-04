package dan.sonora.androidApp.shared

import dan.sonora.util.core.ResourceProvider

class AndroidResourceProvider(
	override val appIconDefault: Int = app.sonora.R.mipmap.ic_launcher,
	override val appIconInverted: Int = app.sonora.R.mipmap.ic_launcher_inverted,
	override val icSonora: Int = app.sonora.R.drawable.ic_sonora,
	override val animLibrary: Int = app.sonora.R.drawable.anim_library,
	override val animPlaylist: Int = app.sonora.R.drawable.anim_playlist,
	override val animArtist: Int = app.sonora.R.drawable.anim_artist,
	override val animPause: Int = app.sonora.R.drawable.anim_pause
) : ResourceProvider
