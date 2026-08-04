package dan.sonora.ui.screens.stats

/** Stable matching for remote names against Sonora's canonical library names. */
internal fun insightKey(value: String): String = buildString {
	var separatorPending = false
	value.trim().lowercase().forEach { char ->
		val folded = when (char) {
			'à', 'á', 'â', 'ä', 'ã', 'å' -> 'a'
			'ç' -> 'c'
			'è', 'é', 'ê', 'ë' -> 'e'
			'ì', 'í', 'î', 'ï' -> 'i'
			'ñ' -> 'n'
			'ò', 'ó', 'ô', 'ö', 'õ' -> 'o'
			'ù', 'ú', 'û', 'ü' -> 'u'
			'ý', 'ÿ' -> 'y'
			'ß' -> 's'
			else -> char
		}
		if (folded.isLetterOrDigit()) {
			if (separatorPending && isNotEmpty()) append(' ')
			append(folded)
			separatorPending = false
		} else {
			separatorPending = true
		}
	}
}

internal fun insightUrlKey(value: String): String = value.trim().trimEnd('/').lowercase()
