plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.jetbrains.kotlin.serialization)
}

android {
	namespace = "com.neilturner.visualmashup"
	compileSdk {
		version = release(37)
	}

	defaultConfig {
		applicationId = "com.neilturner.visualmashup"
		minSdk = 26
		targetSdk = 37
		versionCode = 1
		versionName = "1.0"

	}

	buildTypes {
		release {
			optimization {
				enable = true
			}
			signingConfig = signingConfigs.getByName("debug")
		}
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_21
		targetCompatibility = JavaVersion.VERSION_21
	}
	buildFeatures {
		compose = true
	}
}

dependencies {
	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.appcompat)
	implementation(libs.androidx.compose.ui)
	implementation(libs.androidx.compose.ui.graphics)
	implementation(libs.androidx.compose.animation)
	implementation(libs.androidx.compose.ui.tooling.preview)
	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.lifecycle.runtime.ktx)
	implementation(libs.androidx.lifecycle.runtime.compose)
	implementation(libs.androidx.lifecycle.viewmodel.compose)
	implementation(libs.androidx.tv.foundation)
	implementation(libs.androidx.tv.material)
	implementation(libs.androidx.navigation3.runtime)
	implementation(libs.androidx.navigation3.ui)
	implementation(libs.androidx.lifecycle.viewmodel.navigation3)
	implementation(libs.kotlinx.serialization.core)
	implementation(libs.coil.compose)
	implementation(libs.coil.network.okhttp)
	implementation(libs.kotlinx.coroutines.android)
	androidTestImplementation(platform(libs.androidx.compose.bom))
	androidTestImplementation(libs.androidx.compose.ui.test.junit4)
	debugImplementation(libs.androidx.compose.ui.test.manifest)
	debugImplementation(libs.androidx.compose.ui.tooling)
	debugImplementation(libs.leakcanary)
}