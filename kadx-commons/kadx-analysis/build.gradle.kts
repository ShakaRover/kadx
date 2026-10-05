plugins {
	id("kadx-kotlin")
	id("kadx-library")
}

dependencies {
	implementation(project(":kadx-core"))

	implementation(libs.gson)

	testRuntimeOnly(project(":kadx-plugins:kadx-dex-input"))
	testRuntimeOnly(project(":kadx-plugins:kadx-smali-input"))
}
