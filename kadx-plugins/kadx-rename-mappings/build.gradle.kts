plugins {
	id("kadx-kotlin")
	id("kadx-library")
}

dependencies {
	api(project(":kadx-core"))

	api(libs.mapping.io) {
		exclude("org.ow2.asm:asm")
		exclude("net.fabricmc:tiny-remapper")
	}

	testRuntimeOnly(project(":kadx-plugins:kadx-dex-input"))
	testRuntimeOnly(project(":kadx-plugins:kadx-smali-input"))
}
