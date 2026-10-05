package kadx.tests.export

import kadx.tests.api.ExportGradleTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 应用名含非法字符时，Gradle wrapper 输出应对其做安全处理。
 */
class IllegalCharsForGradleWrapper : ExportGradleTest() {

	@Test
	fun test() {
		exportGradle("IllegalCharsForGradleWrapper.xml", "strings.xml")

		assertThat(getSettingsGradle()).contains("'KadxTestApp'")
	}
}
