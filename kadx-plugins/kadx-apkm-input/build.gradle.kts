plugins {
	id("kadx-library")
	id("kadx-kotlin")
}

dependencies {
	api(project(":kadx-core"))

	implementation(project(":kadx-plugins:kadx-dex-input"))
	implementation(libs.gson)
}
