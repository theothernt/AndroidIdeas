plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.compose)
}

android {
	namespace = "com.neilturner.fourlayers"
	compileSdk {
		version = release(36)
	}

	defaultConfig {
		applicationId = "com.neilturner.fourlayers"
		minSdk = 24
		targetSdk = 36
		versionCode = 1
		versionName = "1.0"

	}

	buildTypes {
		release {
			isShrinkResources = true
			isMinifyEnabled = true
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
			signingConfig = signingConfigs.getByName("debug")
		}
		debug {
			//isMinifyEnabled = true
			//applicationIdSuffix = ".debug"
			//versionNameSuffix = "-debug"
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
	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.appcompat)
	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.compose.ui)
	implementation(libs.androidx.compose.ui.graphics)
	implementation(libs.androidx.compose.ui.tooling.preview)
	implementation(libs.androidx.tv.foundation)
	implementation(libs.androidx.tv.material)
	implementation(libs.androidx.compose.material3)
	implementation(libs.androidx.lifecycle.runtime.ktx)
	implementation(libs.androidx.activity.compose)

	implementation(libs.media3.exoplayer)
	implementation(libs.media3.common)
	implementation(libs.media3.session)
	implementation(libs.media3.ui)
	implementation(libs.media3.ui.compose)

	implementation(libs.coil.compose)
	implementation(libs.coil.network.okhttp)
	implementation(libs.koin.android)
	implementation(libs.koin.compose)

	androidTestImplementation(platform(libs.androidx.compose.bom))
	androidTestImplementation(libs.androidx.compose.ui.test.junit4)
	debugImplementation(libs.androidx.compose.ui.tooling)
	debugImplementation(libs.androidx.compose.ui.test.manifest)
}
