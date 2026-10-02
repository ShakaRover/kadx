package jadx.tests.export

import jadx.tests.api.ExportGradleTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Apache HTTP legacy 库开关对 build.gradle 的影响。
 */
class TestApacheHttpClient : ExportGradleTest() {

	@Test
	fun test() {
		val gradleInfo = getRootNode().getGradleInfoStorage()
		gradleInfo.isUseApacheHttpLegacy = true
		exportGradle("OptionalTargetSdkVersion.xml", "strings.xml")
		assertThat(getAppGradleBuild()).contains("        useLibrary 'org.apache.http.legacy'")

		gradleInfo.isUseApacheHttpLegacy = false
		exportGradle("OptionalTargetSdkVersion.xml", "strings.xml")
		assertThat(getAppGradleBuild()).doesNotContain("        useLibrary 'org.apache.http.legacy'")
	}
}
