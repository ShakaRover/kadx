plugins {
	id("kadx-library")
	id("kadx-kotlin")
}

dependencies {
	api(project(":kadx-core"))

	implementation(libs.kotlin.metadata.jvm)

	testImplementation(
		project
			.project(":kadx-core")
			.sourceSets
			.getByName("test")
			.output,
	)
	testImplementation(libs.commons.lang3)

	testRuntimeOnly(project(":kadx-plugins:kadx-smali-input"))
	testRuntimeOnly(project(":kadx-plugins:kadx-java-input"))
}
