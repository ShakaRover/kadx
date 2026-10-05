plugins {
	id("kadx-kotlin")
	id("kadx-library")
}

dependencies {
	api(project(":kadx-core"))

	implementation(project(":kadx-plugins:kadx-dex-input"))

	implementation(libs.smali) {
		exclude(group = "com.beust", module = "jcommander") // exclude old jcommander namespace
	}
	implementation(libs.guava.jre) // force the latest version for smali
}
