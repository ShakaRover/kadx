package jadx.tests.integration.annotations

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 参数注解：方法参数上的注解（含默认值和显式值）都应正确还原。
 */
class TestParamAnnotations : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestParamAnnotationsFixture.TestCls::class.java))
			.code()
			.contains("void test1(@A int i) {")
			.contains("void test2(int i, @A int j) {")
			.contains("void test3(@A(i = 5) int i) {")
	}
}
