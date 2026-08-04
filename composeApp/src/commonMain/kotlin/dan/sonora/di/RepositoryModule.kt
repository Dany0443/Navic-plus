package dan.sonora.di

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import dan.sonora.domain.repositories.AlbumRepository
import dan.sonora.domain.repositories.ArtistRepository
import dan.sonora.domain.repositories.CollectionRepository
import dan.sonora.domain.repositories.DbRepository
import dan.sonora.domain.repositories.GenreRepository
import dan.sonora.domain.repositories.LyricsRepository
import dan.sonora.domain.repositories.PlaylistRepository
import dan.sonora.domain.repositories.RadioRepository
import dan.sonora.domain.repositories.SearchRepository
import dan.sonora.domain.repositories.ShareRepository
import dan.sonora.domain.repositories.SongRepository

val repositoryModule = module {
	singleOf(::AlbumRepository)
	singleOf(::ArtistRepository)
	singleOf(::DbRepository)
	singleOf(::GenreRepository)
	singleOf(::LyricsRepository)
	singleOf(::SearchRepository)
	singleOf(::ShareRepository)
	singleOf(::CollectionRepository)
	singleOf(::PlaylistRepository)
	singleOf(::SongRepository)
	singleOf(::RadioRepository)
}
