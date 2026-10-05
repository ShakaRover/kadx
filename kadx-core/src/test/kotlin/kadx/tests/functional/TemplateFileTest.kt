package kadx.tests.functional

import kadx.core.export.TemplateFile
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [TemplateFile] 变量替换生成 Gradle 构建脚本的正确性。
 */
class TemplateFileTest {

	@Test
	@Throws(Exception::class)
	fun testBuildGradle() {
		val tmpl = TemplateFile.fromResources("/export/android/app.build.gradle.tmpl")
		tmpl.add("applicationId", "SOME_ID")
		tmpl.add("minSdkVersion", 1)
		tmpl.add("targetSdkVersion", 2)
		tmpl.add("versionCode", 3)
		tmpl.add("versionName", "1.2.3")
		tmpl.add("additionalOptions", "useLibrary 'org.apache.http.legacy'")
		tmpl.add("compileSdkVersion", 4)
		val res = tmpl.build()
		println(res)

		assertThat(res).contains("applicationId 'SOME_ID'")
		assertThat(res).contains("targetSdkVersion 2")
		assertThat(res).contains("versionCode 3")
		assertThat(res).contains("versionName \"1.2.3\"")
		assertThat(res).contains("compileSdkVersion 4")
	}
}
