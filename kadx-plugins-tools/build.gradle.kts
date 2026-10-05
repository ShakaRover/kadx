plugins {
	id("kadx-java")
	id("kadx-kotlin")
	id("kadx-library")
}

dependencies {
	api(project(":kadx-core"))

	implementation(project(":kadx-commons:kadx-app-commons"))

	implementation(libs.gson)
	implementation(libs.commons.io)

	testImplementation(libs.mockwebserver)
}
