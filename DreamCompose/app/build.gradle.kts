plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.spotless)
}

android {
	namespace = "com.neilturner.dreamcompose"
	compileSdk {
		version = release(37)
	}

	defaultConfig {
		applicationId = "com.neilturner.dreamcompose"
		// 26, not 24: LeakCanary (debug-only) declares minSdk 26, and the manifest merger rejects
		// a lower floor outright rather than warning.
		minSdk = 26
		targetSdk = 37
		versionCode = 1
		versionName = "1.0"
	}

	buildTypes {
		release {
			optimization {
				enable = true
				packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
			}
		}
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_11
		targetCompatibility = JavaVersion.VERSION_11
	}
	buildFeatures {
		compose = true
	}
}

spotless {
	// Spotless runs ktlint over all the Kotlin, sources and build scripts alike.
	//
	// The style is also declared in the root .editorconfig, but it is repeated here via
	// editorConfigOverride because Spotless hands ktlint file contents from its own cache
	// directory: the filename .editorconfig matches its sections on is not the real one, so
	// .editorconfig alone leaves ktlint on its space-indent default and it tries to rewrite the
	// tab-indented project. Keep the two in step.
	val editorConfig =
		mapOf(
			"indent_style" to "tab",
			"indent_size" to "4",
			"ktlint_code_style" to "ktlint_official",
			"max_line_length" to "140",
			// Compose conventionally names @Composable functions in PascalCase, which collides with
			// the standard function-naming rule. The value is an annotation name without the '@'.
			"ktlint_function_naming_ignore_when_annotated_with" to "Composable",
		)
	kotlin {
		target("src/**/*.kt")
		trimTrailingWhitespace()
		endWithNewline()
		ktlint().editorConfigOverride(editorConfig)
	}
	kotlinGradle {
		trimTrailingWhitespace()
		endWithNewline()
		ktlint().editorConfigOverride(editorConfig)
	}
}

dependencies {
	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.appcompat)
	implementation(libs.androidx.compose.ui)
	implementation(libs.androidx.compose.ui.graphics)
	implementation(libs.androidx.compose.ui.tooling.preview)
	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.lifecycle.runtime.ktx)
	implementation(libs.androidx.tv.foundation)
	implementation(libs.androidx.tv.material)
	androidTestImplementation(platform(libs.androidx.compose.bom))
	androidTestImplementation(libs.androidx.compose.ui.test.junit4)
	debugImplementation(libs.androidx.compose.ui.test.manifest)
	debugImplementation(libs.androidx.compose.ui.tooling)
	// Leak detection for the debug build only. It installs itself through a ContentProvider, so
	// there is no initialisation call to wire up.
	debugImplementation(libs.squareup.leakcanary.android)
}
