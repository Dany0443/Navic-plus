package dan.sonora.di

import com.russhwolf.settings.Settings
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import dan.sonora.ui.navigation.PersistentViewModelStoreOwner

val appModule = module {
	single { Settings() }
	singleOf(::PersistentViewModelStoreOwner)
}
