package dan.sonora.domain.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavioural tests for [QueueManager]. Shuffle is stubbed with an identity permutation so
 * play order is deterministic (current entry first, remaining in natural order).
 */
class QueueManagerTest {

	private fun manager() = QueueManager<String>(shuffle = { it })

	private fun QueueManager<String>.currentItem(): String? = snapshot.currentEntry?.item

	@Test
	fun setItems_setsCurrentIndex() {
		val q = manager()
		q.setItems(listOf("a", "b", "c"), startIndex = 1)
		assertEquals(3, q.snapshot.size)
		assertEquals(1, q.snapshot.currentIndex)
		assertEquals("b", q.currentItem())
	}

	@Test
	fun setItems_empty_currentIndexIsMinusOne() {
		val q = manager()
		q.setItems(emptyList())
		assertEquals(-1, q.snapshot.currentIndex)
		assertNull(q.currentItem())
	}

	@Test
	fun next_offMode_stopsAtEnd() {
		val q = manager()
		q.setItems(listOf("a", "b"), startIndex = 1)
		assertNull(q.peekNextIndex(auto = false))
		assertFalse(q.advanceToNext(auto = false))
	}

	@Test
	fun next_allMode_wrapsAround() {
		val q = manager()
		q.setItems(listOf("a", "b"), startIndex = 1)
		q.setRepeatMode(RepeatMode.ALL)
		assertEquals(0, q.peekNextIndex(auto = false))
		assertTrue(q.advanceToNext(auto = false))
		assertEquals("a", q.currentItem())
	}

	@Test
	fun repeatOne_autoStays_manualAdvances() {
		val q = manager()
		q.setItems(listOf("a", "b", "c"), startIndex = 0)
		q.setRepeatMode(RepeatMode.ONE)
		assertEquals(0, q.peekNextIndex(auto = true))
		assertEquals(1, q.peekNextIndex(auto = false))
	}

	@Test
	fun previous_stopsOrWraps() {
		val q = manager()
		q.setItems(listOf("a", "b", "c"), startIndex = 0)
		assertNull(q.peekPreviousIndex())
		q.setRepeatMode(RepeatMode.ALL)
		assertEquals(2, q.peekPreviousIndex())
	}

	@Test
	fun insertAt_keepsCurrentEntry() {
		val q = manager()
		q.setItems(listOf("a", "b", "c"), startIndex = 2)
		q.insertAt(0, listOf("x", "y"))
		assertEquals("c", q.currentItem())
		assertEquals(4, q.snapshot.currentIndex)
	}

	@Test
	fun removeBeforeCurrent_shiftsIndex() {
		val q = manager()
		q.setItems(listOf("a", "b", "c"), startIndex = 2)
		q.removeAt(0)
		assertEquals("c", q.currentItem())
		assertEquals(1, q.snapshot.currentIndex)
	}

	@Test
	fun removeCurrent_movesToNextAtSameIndex() {
		val q = manager()
		q.setItems(listOf("a", "b", "c"), startIndex = 1)
		q.removeAt(1)
		assertEquals(1, q.snapshot.currentIndex)
		assertEquals("c", q.currentItem())
	}

	@Test
	fun removeLastRemaining_emptiesQueue() {
		val q = manager()
		q.setItems(listOf("a"), startIndex = 0)
		q.removeAt(0)
		assertTrue(q.snapshot.isEmpty)
		assertEquals(-1, q.snapshot.currentIndex)
	}

	@Test
	fun move_keepsCurrentEntry() {
		val q = manager()
		q.setItems(listOf("a", "b", "c"), startIndex = 0)
		q.move(0, 2)
		assertEquals("a", q.currentItem())
		assertEquals(2, q.snapshot.currentIndex)
	}

	@Test
	fun replaceAt_keepsIdAndCurrent() {
		val q = manager()
		q.setItems(listOf("a", "b", "c"), startIndex = 1)
		val idBefore = q.snapshot.entries[1].id
		q.replaceAt(1, "B")
		assertEquals("B", q.currentItem())
		assertEquals(idBefore, q.snapshot.entries[1].id)
	}

	@Test
	fun shuffle_currentStaysFirstInPlayOrder() {
		val q = manager()
		q.setItems(listOf("a", "b", "c", "d"), startIndex = 2)
		q.setShuffleEnabled(true)
		// identity shuffle => order is [c, a, b, d]; next after c is a.
		assertEquals(0, q.peekNextIndex(auto = false))
		q.advanceToNext(auto = false)
		assertEquals("a", q.currentItem())
	}

	@Test
	fun disableShuffle_returnsToNaturalOrder() {
		val q = manager()
		q.setItems(listOf("a", "b", "c"), startIndex = 0)
		q.setShuffleEnabled(true)
		q.setShuffleEnabled(false)
		assertEquals(1, q.peekNextIndex(auto = false))
	}

	@Test
	fun restore_rebuildsQueueAndClampsIndex() {
		val q = manager()
		q.restore(listOf("a", "b"), currentIndex = 9, repeatMode = RepeatMode.ALL, shuffleEnabled = false)
		assertEquals(1, q.snapshot.currentIndex)
		assertEquals(RepeatMode.ALL, q.snapshot.repeatMode)
	}

	@Test
	fun clear_resets() {
		val q = manager()
		q.setItems(listOf("a", "b"))
		q.clear()
		assertTrue(q.snapshot.isEmpty)
		assertNull(q.peekNextIndex(auto = false))
	}
}
