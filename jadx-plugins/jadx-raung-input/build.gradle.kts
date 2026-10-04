plugins {
	id("jadx-kotlin")
	id("jadx-library")
}

dependencies {
	api(project(":jadx-core"))

	implementation(libs.raung.asm)
}
