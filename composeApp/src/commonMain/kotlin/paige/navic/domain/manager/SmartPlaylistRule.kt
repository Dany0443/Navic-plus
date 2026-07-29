package paige.navic.domain.manager

import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.DomainSongListType
import paige.navic.domain.models.SmartPlaylist
import paige.navic.domain.models.SmartPlaylistType

class SmartPlaylistRule(
    val id: String,
    val type: SmartPlaylistType,
    val title: String,
    val icon: String,
    val listType: DomainSongListType,
	/**
	 * Optional custom selector. When set, this completely defines how songs are chosen and ordered
	 * for the Smart Playlist.
	 *
	 * This keeps Smart Playlist generation extensible without forcing everything into
	 * simple `filter`/`sort` lambdas.
	 */
	val select: ((List<DomainSong>) -> List<DomainSong>)? = null,
    val filter: (List<DomainSong>) -> List<DomainSong>,
    val sort: (List<DomainSong>) -> List<DomainSong> = { it },
    val limit: Int? = null
) {
    fun execute(songs: List<DomainSong>): SmartPlaylist {
		val selected = select?.invoke(songs) ?: run {
			val filtered = filter(songs)
			val sorted = sort(filtered)
			limit?.let { sorted.take(it) } ?: sorted
		}

        return SmartPlaylist(
            id = id,
            type = type,
            title = title,
            icon = icon,
			songCount = selected.size,
            listType = listType
        )
    }
}
