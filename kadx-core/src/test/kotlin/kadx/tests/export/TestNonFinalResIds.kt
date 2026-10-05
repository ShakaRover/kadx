package kadx.tests.export

import kadx.tests.api.ExportGradleTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * `android.nonFinalResIds` 开关对 gradle.properties 的影响。
 */
class TestNonFinalResIds : ExportGradleTest() {

	@Test
	fun test() {
		val gradleInfo = getRootNode().gradleInfoStorage
		gradleInfo.isNonFinalResIds = false
		exportGradle("OptionalTargetSdkVersion.xml", "strings.xml")
		assertFalse(getGradleProperiesFile().exists())

		gradleInfo.isNonFinalResIds = true
		exportGradle("OptionalTargetSdkVersion.xml", "strings.xml")
		assertThat(getGradleProperties()).containsOne("android.nonFinalResIds=false")
	}
}
