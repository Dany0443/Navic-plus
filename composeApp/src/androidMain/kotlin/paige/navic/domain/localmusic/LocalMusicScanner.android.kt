package paige.navic.domain.localmusic

import android.content.Context
import android.database.ContentObserver
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import paige.navic.domain.models.DomainContributor
import paige.navic.domain.models.DomainExplicitStatus
import paige.navic.domain.models.DomainReplayGain
import paige.navic.domain.models.DomainSong
import kotlin.time.Duration.Companion.milliseconds

actual class LocalMusicScanner(
	private val context: Context
) {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val changeFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
	private val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
		override fun onChange(selfChange: Boolean) {
			scope.launch {
				changeFlow.emit(Unit)
			}
		}
	}

	init {
		context.contentResolver.registerContentObserver(
			MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
			true,
			contentObserver
		)
	}

	actual suspend fun scan(): List<DomainSong> = withContext(Dispatchers.IO) {
		val projection = arrayOf(
			MediaStore.Audio.Media._ID,
			MediaStore.Audio.Media.TITLE,
			MediaStore.Audio.Media.ARTIST,
			MediaStore.Audio.Media.ALBUM,
			MediaStore.Audio.Media.DURATION,
			MediaStore.Audio.Media.DATA,
			MediaStore.Audio.Media.DATE_ADDED
		)

		val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
		val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

		context.contentResolver.query(
			MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
			projection,
			selection,
			null,
			sortOrder
		)?.use { cursor ->
			val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
			val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
			val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
			val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
			val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
			val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

			val songs = mutableListOf<DomainSong>()
			while (cursor.moveToNext()) {
				val path = cursor.getString(dataColumn)
				if (path == null || !path.isSupportedLocalMusicFile()) continue

				val retriever = MediaMetadataRetriever()
				try {
					retriever.setDataSource(path)
					val metadataTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).meaningfulMetadata()
					val metadataArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).meaningfulMetadata()
					val metadataAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).meaningfulMetadata()
					val metadataDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

					val title = metadataTitle ?: cursor.getString(titleColumn).meaningfulMetadata().orEmpty()
					val artist = metadataArtist ?: cursor.getString(artistColumn).meaningfulMetadata().orEmpty()
					val album = metadataAlbum ?: cursor.getString(albumColumn).meaningfulMetadata()
					val durationMs = metadataDuration?.toLongOrNull() ?: cursor.getLong(durationColumn)
					val fileExtension = path.substringAfterLast('.', "").lowercase()
					val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
						?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension).orEmpty()

					val song = DomainSong(
						id = "local_${cursor.getLong(idColumn)}",
						title = title.ifBlank { "Unknown title" },
						artistName = artist,
						displayAlbumArtist = null,
						albumArtistName = null,
						artistId = "local_artist_${artist.hashCode()}",
						albumTitle = album?.takeIf { it.isNotBlank() },
						albumId = null,
						parentId = null,
						comment = null,
						trackNumber = null,
						discNumber = null,
						isrc = emptyList(),
						year = null,
						genre = null,
						genres = emptyList(),
						moods = emptyList(),
						duration = durationMs.milliseconds,
						bpm = null,
						contributors = emptyList(),
						playCount = 0,
						userRating = null,
						averageRating = null,
						bitRate = null,
						bitDepth = null,
						sampleRate = null,
						audioChannelCount = null,
						replayGain = null,
						fileSize = 0L,
						fileExtension = fileExtension,
						mimeType = mimeType,
						filePath = path,
						starredAt = null,
						coverArtId = null,
						musicBrainzId = null,
						explicitStatus = DomainExplicitStatus.Clean
					)
					songs += song
				} finally {
					retriever.release()
				}
			}

			songs
		} ?: emptyList()
	}

	actual fun changes(): Flow<Unit> = changeFlow.asSharedFlow()
}

private fun String?.meaningfulMetadata(): String? =
	this?.trim()?.takeUnless { it.isEmpty() || it.equals("<unknown>", ignoreCase = true) }
