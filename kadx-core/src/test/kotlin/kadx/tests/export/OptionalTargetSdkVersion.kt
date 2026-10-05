package kadx.tests.export

import kadx.tests.api.ExportGradleTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * manifest 未声明 targetSdkVersion 时的 Gradle 输出。
 */
class OptionalTargetSdkVersion : ExportGradleTest() {

	@Test
	fun test() {
		exportGradle("OptionalTargetSdkVersion.xml", "strings.xml")

		assertThat(getAppGradleBuild()).contains("targetSdkVersion 14").doesNotContain("        vectorDrawables.useSupportLibrary = true")
	}
}
