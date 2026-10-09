plugins {
	id("kadx-kotlin")
	id("kadx-library")
}

dependencies {
	api(project(":kadx-core"))

	implementation(project(":kadx-plugins:kadx-dex-input"))

	implementation(libs.ksmali)
	// 直接用 ksmali 的 lexer/parser（ANTLR4）完成内存内汇编，故自行声明 antlr 运行时
	implementation(libs.antlr.runtime)
}
