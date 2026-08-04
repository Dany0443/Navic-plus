import androidx.room3.gradle.RoomExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import com.mikepenz.aboutlibraries.plugin.AboutLibrariesExtension
import io.github.composegears.valkyrie.gradle.ValkyrieExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
	alias(libs.plugins.kotlinMultiplatform)
	alias(libs.plugins.kotlinMultiplatformLibrary)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.composeMultiplatform)
	alias(libs.plugins.composeCompiler)
	alias(libs.plugins.aboutLibraries)
	alias(libs.plugins.valkyrie)
	alias(libs.plugins.ksp)
	alias(libs.plugins.androidx.room3)
}

// remove material 2
configurations.all {
	exclude(group = "org.jetbrains.compose.material", module = "material")
	exclude(group = "androidx.compose.material", module = "material")
}

extensions.configure<ValkyrieExtension> {
	packageName = "dan.sonora.icons"
	generateAtSync = true
	outputDirectory = layout.buildDirectory.dir("generated/sources/valkyrie")

	iconPack {
		name = "Icons"
		targetSourceSet = "commonMain"

		nested {
			name = "Brand"
			sourceFolder = "brand"
		}

		nested {
			name = "Outlined"
			sourceFolder = "outlined"
		}

		nested {
			name = "Filled"
			sourceFolder = "filled"
		}
	}
}

extensions.configure<AboutLibrariesExtension> {
	export {
		outputFile = file("src/commonMain/composeResources/files/acknowledgements.json")
	}
}

tasks {
	register("compileDebugKotlinAndroid") {
		group = "build"
		description = "Alias task for Android Kotlin compile in this KMP module."
		dependsOn("compileAndroidMain")
	}
	matching { it.name.startsWith("ksp") }.configureEach {
		dependsOn(":composeApp:generateValkyrieImageVector")
	}
	named("copyNonXmlValueResourcesForCommonMain") {
		dependsOn(":composeApp:exportLibraryDefinitions")
	}
	withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile> {
		dependsOn(":composeApp:generateValkyrieImageVector")
	}
	withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
		compilerOptions {
			jvmTarget.set(JvmTarget.JVM_21)
			freeCompilerArgs.add("-Xexpect-actual-classes")

		}
	}
}

extensions.configure<KotlinMultiplatformExtension> {
	extensions.configure<KotlinMultiplatformAndroidLibraryExtension> {
		namespace = "dan.sonora"
		compileSdk = libs.versions.android.compileSdk.get().toInt()
		minSdk = libs.versions.android.minSdk.get().toInt()

		androidResources.enable = true

		packaging {
			resources {
				excludes += "/okhttp3/**"
				excludes += "/*.properties"
				excludes += "/org/antlr/**"
				excludes += "/com/android/tools/smali/**"
				excludes += "/org/eclipse/jgit/**"
				excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
				excludes += "/org/bouncycastle/**"
				excludes += "/META-INF/{AL2.0,LGPL2.1}"
			}
		}

		buildToolsVersion = "37.0.0"

		withHostTest { }
	}

	sourceSets {
		commonMain.dependencies {
			implementation(libs.bundles.cmp)
			implementation(libs.bundles.ktor)
			implementation(libs.bundles.coil)
			implementation(libs.bundles.cmpThirdParty)
			implementation(libs.bundles.androidx.lifecycle)
			implementation(libs.bundles.room)
			implementation(libs.bundles.koin)

			implementation(libs.androidx.navigation3.ui)
			implementation(libs.kotlinx.datetime)
			implementation(libs.kotlinx.serialization.json)
			implementation(libs.kotlinx.collections.immutable)
			implementation(libs.androidx.datastore.preferences)
			implementation(libs.coil.gif)

			implementation(libs.subsonicKotlin)
		}

		commonTest.dependencies {
			implementation(kotlin("test"))
		}

		androidMain.dependencies {
			implementation(libs.bundles.ktor.android)
			implementation(libs.bundles.androidx.android)
			implementation(libs.bundles.media3)
		}

	}

	compilerOptions {
		freeCompilerArgs.addAll("-Xexpect-actual-classes", "-Xexplicit-backing-fields")
	}
}

extensions.configure<RoomExtension> {
	schemaDirectory("$projectDir/schemas")
}

dependencies {
	add("kspAndroid", libs.androidx.room3.compiler)
	add("kspCommonMainMetadata", libs.androidx.room3.compiler)
}
