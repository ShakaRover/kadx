package kadx.tests.integration.annotations

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 注解定义：`@Target` / `@Retention` 以及注解方法都应正确还原。
 */
class TestAnnotations2 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestAnnotations2Fixture.TestCls::class.java))
			.code()
			.contains("@Target({ElementType.TYPE})")
			.contains("@Retention(RetentionPolicy.RUNTIME)")
			.contains("public @interface A {")
			.contains("float f();")
			.contains("int i();")
	}
}
