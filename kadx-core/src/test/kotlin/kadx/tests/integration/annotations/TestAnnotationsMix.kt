package kadx.tests.integration.annotations

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 复杂注解混用：注解方法参数、异常、数组、嵌套注解等都应正确还原。
 */
class TestAnnotationsMix : IntegrationTest() {

	@Test
	fun test() {
		// useDexInput();
		assertThat(getClassNode(TestAnnotationsMixFixture.TestCls::class.java))
			.code()
			.doesNotContain("int i = false;")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		getClassNode(TestAnnotationsMixFixture.TestCls::class.java)
	}

	@Test
	fun testDeclaration() {
		assertThat(getClassNode(TestAnnotationsMixFixture.TestCls::class.java))
			.code()
			.doesNotContain("Thread thread = new Thread();")
			.contains("new Thread();")
	}
}
