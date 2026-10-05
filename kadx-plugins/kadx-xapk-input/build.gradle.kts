plugins {
	id("kadx-kotlin")
	id("kadx-library")
}

dependencies {
	api(project(":kadx-core"))

	implementation(project(":kadx-plugins:kadx-dex-input"))
	implementation(libs.gson)
}
