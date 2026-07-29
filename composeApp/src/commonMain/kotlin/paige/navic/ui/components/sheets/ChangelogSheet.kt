package paige.navic.ui.components.sheets

/**
 * Reserved extension point for a future Navic+-owned update provider.
 *
 * The original GitHub-release checker and its prompt were intentionally removed. This stub
 * must remain side-effect free until a replacement provider, verification policy, and UI are
 * designed for Navic+.
 */
internal object UpdateChecker {
	fun checkForUpdates() {
		// Intentionally disabled: do not perform network requests or show update prompts.
	}
}
