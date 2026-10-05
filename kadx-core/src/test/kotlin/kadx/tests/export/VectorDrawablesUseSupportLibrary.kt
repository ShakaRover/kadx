package kadx.tests.export

import kadx.tests.api.ExportGradleTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * vectorDrawables.useSupportLibrary 的生成条件（fillType / pathData 与 minSdk）。
 */
class VectorDrawablesUseSupportLibrary : ExportGradleTest() {

	@Test
	fun test() {
		val gradleInfo = getRootNode().gradleInfoStorage
		gradleInfo.isVectorFillType = true
		exportGradle("OptionalTargetSdkVersion.xml", "strings.xml")
		assertThat(getAppGradleBuild()).contains("        vectorDrawables.useSupportLibrary = true")

		gradleInfo.isVectorFillType = false
		gradleInfo.isVectorPathData = true
		exportGradle("OptionalTargetSdkVersion.xml", "strings.xml")
		assertThat(getAppGradleBuild()).contains("        vectorDrawables.useSupportLibrary = true")

		exportGradle("MinSdkVersion25.xml", "strings.xml")
		assertThat(getAppGradleBuild()).doesNotContain("        vectorDrawables.useSupportLibrary = true")
	}
}
