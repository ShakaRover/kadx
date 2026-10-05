package kadx.plugins.kotlin.metadata.tests

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

class TestJavaParser : IntegrationTest() {

	@Test
	fun test() {
		val sampleCls = getResourceFile("samples/MainKt.class")
		assertThat(getClassNodeFromFiles(listOf(sampleCls), "MainKt"))
			.code()
			.doesNotContain("Exception occurred when reading Kotlin metadata")
	}
}
