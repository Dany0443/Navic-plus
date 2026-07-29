package paige.navic.domain.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** An item in the queue paired with a stable, engine-assigned identity. */
data class QueueEntry<T>(val id: Long, val item: T)

/**
 * Immutable snapshot of the queue. Everything a player surface needs to render the timeline
 * and current position within it, with no reference to any render pipeline.
 */
data class QueueSnapshot<T>(
	val entries: List<QueueEntry<T>> = emptyList(),
	val currentIndex: Int = -1,
	val repeatMode: RepeatMode = RepeatMode.OFF,
	val shuffleEnabled: Boolean = false,
) {
	val size: Int get() = entries.size
	val isEmpty: Boolean get() = entries.isEmpty()
	val currentEntry: QueueEntry<T>? get() = entries.getOrNull(currentIndex)
}

/**
 * The single source of truth for the playback queue.
 *
 * `QueueManager` is deliberately platform-neutral and generic over the item type so the same
 * queue/shuffle/repeat logic is unit-tested once and reused for any source (local, Subsonic,
 * future streaming providers). It never touches an `ExoPlayer` timeline: navigation is pure
 * index math, and only one or two entries are ever loaded into real players by the coordinator.
 *
 * Not thread-safe by design: it is owned by the engine and mutated only on the playback
 * (player-application) thread. State is published via [state] for observers to rebuild from.
 *
 * @param shuffle permutation applied to the non-current ids when shuffle is enabled; injectable
 *   for deterministic tests. Defaults to a real random shuffle.
 */
class QueueManager<T>(
	private val shuffle: (List<Long>) -> List<Long> = { it.shuffled() },
) {
	private var nextId = 0L
	private var entries: List<QueueEntry<T>> = emptyList()
	private var currentIndex: Int = -1
	private var repeatMode: RepeatMode = RepeatMode.OFF
	private var shuffleEnabled: Boolean = false

	/** Playback order as ids; maintained only while shuffle is enabled. */
	private var shuffleOrderIds: List<Long> = emptyList()

	private val _state = MutableStateFlow(QueueSnapshot<T>())
	val state: StateFlow<QueueSnapshot<T>> = _state.asStateFlow()

	val snapshot: QueueSnapshot<T> get() = _state.value

	// region mutations

	/** Replace the entire queue and set the current position to [startIndex]. */
	fun setItems(items: List<T>, startIndex: Int = 0) {
		entries = items.map { QueueEntry(nextId++, it) }
		currentIndex = if (entries.isEmpty()) -1 else startIndex.coerceIn(0, entries.lastIndex)
		rebuildShuffleOrder()
		publish()
	}

	/** Append [items] to the end of the queue. */
	fun addAll(items: List<T>) = insertAt(entries.size, items)

	/** Insert [items] at [index] (clamped). New ids join the shuffle order after the current. */
	fun insertAt(index: Int, items: List<T>) {
		if (items.isEmpty()) return
		val at = index.coerceIn(0, entries.size)
		val newEntries = items.map { QueueEntry(nextId++, it) }
		val currentId = currentEntryId()
		entries = entries.toMutableList().apply { addAll(at, newEntries) }
		reindexTo(currentId, fallback = if (entries.isEmpty()) -1 else at)
		if (shuffleEnabled) {
			val insertPos = (shuffleOrderIds.indexOf(currentId) + 1).coerceIn(0, shuffleOrderIds.size)
			shuffleOrderIds = shuffleOrderIds.toMutableList()
				.apply { addAll(insertPos, newEntries.map { it.id }) }
		}
		publish()
	}

	/** Remove the entry at [index]. Adjusts the current position to stay on the same entry. */
	fun removeAt(index: Int) {
		if (index !in entries.indices) return
		val removed = entries[index]
		val currentId = currentEntryId()
		entries = entries.toMutableList().apply { removeAt(index) }
		if (shuffleEnabled) shuffleOrderIds = shuffleOrderIds - removed.id
		val fallback = if (entries.isEmpty()) -1 else index.coerceAtMost(entries.lastIndex)
		reindexTo(if (removed.id == currentId) null else currentId, fallback)
		publish()
	}

	/** Move the entry from [from] to [to]. Play order (shuffle) is unaffected. */
	fun move(from: Int, to: Int) {
		if (from !in entries.indices || to !in entries.indices || from == to) return
		val currentId = currentEntryId()
		entries = entries.toMutableList().apply { add(to, removeAt(from)) }
		reindexTo(currentId, fallback = currentIndex)
		publish()
	}

	/** Replace the item at [index], keeping its id (and thus its play-order position). */
	fun replaceAt(index: Int, item: T) {
		if (index !in entries.indices) return
		entries = entries.toMutableList().apply { this[index] = QueueEntry(this[index].id, item) }
		publish()
	}

	/** Clear the queue entirely. */
	fun clear() {
		entries = emptyList()
		currentIndex = -1
		shuffleOrderIds = emptyList()
		publish()
	}

	/** Point the current position at [index] without altering contents. */
	fun seekToIndex(index: Int) {
		if (index !in entries.indices) return
		currentIndex = index
		publish()
	}

	fun setRepeatMode(mode: RepeatMode) {
		if (mode == repeatMode) return
		repeatMode = mode
		publish()
	}

	fun setShuffleEnabled(enabled: Boolean) {
		if (enabled == shuffleEnabled) return
		shuffleEnabled = enabled
		rebuildShuffleOrder()
		publish()
	}

	/**
	 * Restore a persisted queue. Assigns fresh ids and rebuilds shuffle order.
	 */
	fun restore(items: List<T>, currentIndex: Int, repeatMode: RepeatMode, shuffleEnabled: Boolean) {
		this.repeatMode = repeatMode
		this.shuffleEnabled = shuffleEnabled
		entries = items.map { QueueEntry(nextId++, it) }
		this.currentIndex = if (entries.isEmpty()) -1 else currentIndex.coerceIn(0, entries.lastIndex)
		rebuildShuffleOrder()
		publish()
	}

	// endregion

	// region navigation (pure — does not mutate)

	/**
	 * The index that would play next, or null if there is none.
	 *
	 * @param auto true for a natural end-of-track transition (honours [RepeatMode.ONE]),
	 *   false for an explicit "next" command (always advances past the current item).
	 */
	fun peekNextIndex(auto: Boolean): Int? {
		if (entries.isEmpty()) return null
		if (auto && repeatMode == RepeatMode.ONE) return currentIndex
		val order = playOrderIds()
		val pos = order.indexOf(currentEntryId())
		if (pos == -1) return order.firstOrNull()?.let(::indexOfId)
		return when {
			pos < order.lastIndex -> indexOfId(order[pos + 1])
			repeatMode == RepeatMode.ALL -> indexOfId(order.first())
			else -> null
		}
	}

	/** The index that would play on "previous", or null if at the start with no wrap. */
	fun peekPreviousIndex(): Int? {
		if (entries.isEmpty()) return null
		val order = playOrderIds()
		val pos = order.indexOf(currentEntryId())
		if (pos == -1) return order.firstOrNull()?.let(::indexOfId)
		return when {
			pos > 0 -> indexOfId(order[pos - 1])
			repeatMode == RepeatMode.ALL -> indexOfId(order.last())
			else -> null
		}
	}

	/** Advance to [peekNextIndex]; returns true if the position moved to a valid entry. */
	fun advanceToNext(auto: Boolean): Boolean {
		val next = peekNextIndex(auto) ?: return false
		currentIndex = next
		publish()
		return true
	}

	/** Move to [peekPreviousIndex]; returns true if the position moved. */
	fun advanceToPrevious(): Boolean {
		val prev = peekPreviousIndex() ?: return false
		currentIndex = prev
		publish()
		return true
	}

	// endregion

	// region internals

	private fun currentEntryId(): Long? = entries.getOrNull(currentIndex)?.id

	private fun indexOfId(id: Long): Int = entries.indexOfFirst { it.id == id }

	private fun playOrderIds(): List<Long> =
		if (shuffleEnabled) shuffleOrderIds else entries.map { it.id }

	/** Recompute [currentIndex] to keep pointing at [id]; use [fallback] if it is gone/null. */
	private fun reindexTo(id: Long?, fallback: Int) {
		currentIndex = id?.let { indexOfId(it).takeIf { i -> i >= 0 } }
			?: fallback.coerceIn(-1, entries.lastIndex)
	}

	/** Build shuffle order as current-first, remainder permuted; no-op when shuffle is off. */
	private fun rebuildShuffleOrder() {
		if (!shuffleEnabled || entries.isEmpty()) {
			shuffleOrderIds = emptyList()
			return
		}
		val currentId = currentEntryId()
		val others = entries.map { it.id }.filter { it != currentId }
		shuffleOrderIds = buildList {
			if (currentId != null) add(currentId)
			addAll(shuffle(others))
		}
	}

	private fun publish() {
		_state.value = QueueSnapshot(
			entries = entries,
			currentIndex = currentIndex,
			repeatMode = repeatMode,
			shuffleEnabled = shuffleEnabled,
		)
	}

	// endregion
}
