package paige.navic.domain.localmusic

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalMusicScannerTest {
	@Test
	fun supportedAudioFilesAreRecognized() {
		assertTrue("song.mp3".isSupportedLocalMusicFile())
		assertTrue("track.m4a".isSupportedLocalMusicFile())
		assertTrue("album.flac".isSupportedLocalMusicFile())
		assertTrue("demo.OPUS".isSupportedLocalMusicFile())
		assertFalse("cover.jpg".isSupportedLocalMusicFile())
		assertFalse("notes.txt".isSupportedLocalMusicFile())
	}
}
