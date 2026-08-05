package dan.sonora.domain.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import sonora.composeapp.generated.resources.Res
import sonora.composeapp.generated.resources.notice_added_to_queue
import sonora.composeapp.generated.resources.notice_lastfm_sign_in_failed
import sonora.composeapp.generated.resources.notice_play_next
import org.jetbrains.compose.resources.StringResource
import dan.sonora.domain.models.snackbars.PlayerEvent

class SnackBarManager {
	private val _events = MutableSharedFlow<PlayerEvent>()
	val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

	fun notify(resource: StringResource, vararg args: Any) {
		scope.launch {
			_events.emit(PlayerEvent(resource, args.toList()))
		}
	}

	fun notifyAddedToQueue() = notify(Res.string.notice_added_to_queue)
	fun notifyPlayNext() = notify(Res.string.notice_play_next)
	fun notifyLastFmSignInFailed() = notify(Res.string.notice_lastfm_sign_in_failed)
}
