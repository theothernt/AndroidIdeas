import java.util.Properties
import kotlin.apply

plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.ksp)
}

android {
	namespace = "com.neilturner.persistentlist"
	compileSdk {
		version = release(36)
	}

	val sambaProperties = Properties().apply {
		val propertiesFile = rootProject.file("samba.properties")
		if (propertiesFile.exists()) {
			load(propertiesFile.inputStream())
		}
	}

	defaultConfig {
		applicationId = "com.neilturner.persistentlist"
		minSdk = 24
		targetSdk = 36
		versionCode = 1
		versionName = "1.0"

		buildConfigField("String", "SAMBA_IP", "\"${sambaProperties.getProperty("samba.ip", "")}\"")
		buildConfigField("String", "SAMBA_SHARE", "\"${sambaProperties.getProperty("samba.share", "")}\"")
		buildConfigField("String", "SAMBA_USERNAME", "\"${sambaProperties.getProperty("samba.username", "")}\"")
		buildConfigField("String", "SAMBA_PASSWORD", "\"${sambaProperties.getProperty("samba.password", "")}\"")
	}

	buildTypes {
		debug {
		}
		release {
			isShrinkResources = true
			isMinifyEnabled = true
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
			signingConfig = signingConfigs.getByName("debug")
		}
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_21
		targetCompatibility = JavaVersion.VERSION_21
	}
	buildFeatures {
		compose = true
		buildConfig = true
	}
}

dependencies {
	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.appcompat)
	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.compose.ui)
	implementation(libs.androidx.compose.ui.graphics)
	implementation(libs.androidx.compose.ui.tooling.preview)
	implementation(libs.androidx.compose.material3)
	implementation(libs.androidx.tv.foundation)
	implementation(libs.androidx.tv.material)
	implementation(libs.androidx.lifecycle.runtime.ktx)
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.room.runtime)
	implementation(libs.androidx.room.ktx)
	ksp(libs.androidx.room.compiler)
	implementation(libs.smbj)
	implementation(libs.slf4j.simple)
	implementation(libs.koin.android)
	implementation(libs.koin.androidx.compose)
	androidTestImplementation(platform(libs.androidx.compose.bom))
	androidTestImplementation(libs.androidx.compose.ui.test.junit4)
	debugImplementation(libs.androidx.compose.ui.tooling)
	debugImplementation(libs.androidx.compose.ui.test.manifest)
}