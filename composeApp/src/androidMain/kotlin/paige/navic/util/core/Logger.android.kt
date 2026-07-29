package paige.navic.util.core

import android.util.Log

actual object Logger {
	actual fun e(tag: String, msg: String, tr: Throwable?) {
		Log.e(tag, safeMessage(msg, tr))
	}

	actual fun i(tag: String, msg: String, tr: Throwable?) {
		Log.i(tag, safeMessage(msg, tr))
	}

	actual fun w(tag: String, msg: String, tr: Throwable?) {
		Log.w(tag, safeMessage(msg, tr))
	}

	private fun safeMessage(message: String, throwable: Throwable?): String {
		val redacted = SENSITIVE_QUERY_PARAMETER.replace(message) { "${it.groupValues[1]}<redacted>" }
		return throwable?.let { "$redacted (${it::class.simpleName ?: "error"})" } ?: redacted
	}

	private val SENSITIVE_QUERY_PARAMETER = Regex(
		"(?i)([?&](?:token|sk|session[_-]?key|password|passwd|secret|api[_-]?key|authorization|bearer)=)[^&\\s]+"
	)
}
