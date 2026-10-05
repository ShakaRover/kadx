package kadx.tests.integration.annotations

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 注解取值：负数 int、布尔值、默认值等注解参数都应正确还原。
 */
class TestAnnotations : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestAnnotationsFixture.TestCls::class.java))
			.code()
			.doesNotContain("@A(a = 255)")
			.containsOne("@A(a = -1)")
			.containsOne("@A(a = -253)")
			.containsOne("@A(a = -11253)")
			.containsOne("@V(false)")
			.doesNotContain("@D()")
			.containsOne("@D")
			.containsOne("int a();")
			.containsOne("float value() default 1.1f;")
	}
}
