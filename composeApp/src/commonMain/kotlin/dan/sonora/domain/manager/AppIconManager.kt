package dan.sonora.domain.manager

import dan.sonora.domain.models.settings.AppIconVariant

expect class AppIconManager {
	fun setVariant(newVariant: AppIconVariant)
	fun getIcon(variant: AppIconVariant): Any?
}
