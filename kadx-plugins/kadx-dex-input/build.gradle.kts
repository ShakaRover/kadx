plugins {
	id("kadx-kotlin")
	id("kadx-library")
}

dependencies {
	api(project(":kadx-core"))

	// TODO: finish own smali printer
	implementation(libs.ksmali.baksmali)

	// compile smali files in tests
	testImplementation(libs.ksmali)
}
