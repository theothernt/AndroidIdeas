package com.neilturner.dreamcompose

import android.app.Application
import leakcanary.LeakCanary
import shark.LibraryLeakReferenceMatcher
import shark.ReferencePattern

/**
 * Debug-only application that teaches LeakCanary about a leak that the Android platform causes.
 *
 * The platform's `android.service.dreams.DreamService` holds inner Binder classes that keep the
 * service alive after it has been destroyed. See [platformDreamServiceLeaks] for the details.
 */
class DreamComposeDebugApplication : Application() {
	override fun onCreate() {
		super.onCreate()
		configureLeakCanary()
	}
}

private fun configureLeakCanary() {
	LeakCanary.config =
		LeakCanary.config.copy(
			referenceMatchers = LeakCanary.config.referenceMatchers + platformDreamServiceLeaks(),
		)
}

/**
 * The platform's `android.service.dreams.DreamService` declares inner classes that extend
 * `android.os.Binder` and are handed to the system, so the system holds a native reference to
 * them. Being inner (non-static) classes they also hold a synthetic `this$0` reference back to
 * the service, which keeps the service alive after it has been destroyed.
 *
 * The references live in framework code, so an app cannot prevent them. LeakCanary's own guidance
 * for this shape is to file a bug against the platform, and to classify the references as library
 * leaks so that they are not reported as application leaks.
 */
private fun platformDreamServiceLeaks() =
	listOf(
		LibraryLeakReferenceMatcher(
			pattern =
				ReferencePattern.InstanceFieldPattern(
					className = "android.service.dreams.DreamService\$DreamServiceWrapper",
					fieldName = "this\$0",
				),
			description = "The platform DreamService keeps its non-static inner Binder wrapper alive.",
		),
		LibraryLeakReferenceMatcher(
			pattern =
				ReferencePattern.InstanceFieldPattern(
					className = "android.service.dreams.DreamService\$DreamActivityCallback",
					fieldName = "this\$0",
				),
			description = "The platform DreamService keeps its non-static inner Binder callback alive.",
		),
	)
