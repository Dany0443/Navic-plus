package dan.sonora.domain.stats

/**
 * A [StatsProvider] that connects by entering a username rather than through a browser
 * handshake — the shape used by services whose listening data is public.
 *
 * The onboarding picks a flow from the provider's capabilities alone: a non-null
 * [StatsProvider.authorizationUrl] opens a browser, and implementing this interface
 * offers a username dialog. Neither path names a specific provider.
 */
interface UsernameConnectableProvider {
	/** Pre-filled server address, shown when the user opens the advanced section. */
	val defaultServerUrl: String

	/**
	 * Verifies [username] against [serverUrl] and, if it exists, stores it so the
	 * provider reports itself connected.
	 *
	 * Throws when the username is unknown or the server is unreachable, so the caller
	 * can surface the failure instead of leaving a broken connection in place.
	 */
	suspend fun connect(username: String, serverUrl: String)
}
