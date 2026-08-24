package com.neilturner.inputtest

import android.app.Application

class InputTestApplication : Application() {

	override fun onCreate() {
		super.onCreate()
		// App-wide initialization (DI, analytics, crash reporting, WorkManager, etc.)
		// can be wired up here later.
	}
}
