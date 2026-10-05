plugins {
	id("kadx-kotlin")
	id("kadx-library")
}

dependencies {
	api(project(":kadx-core"))

	// show bytecode disassemble
	implementation(libs.raung.disasm)

	testImplementation(project(":kadx-core"))
}
