package jadx.tests.integration.annotations

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 注解定义：`@Target` / `@Retention` 以及注解方法都应正确还原。
 */
class TestAnnotations2 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestAnnotations2Fixture.TestCls::class.java))
			.code()
			.contains("@Target({ElementType.TYPE})")
			.contains("@Retention(RetentionPolicy.RUNTIME)")
			.contains("public @interface A {")
			.contains("float f();")
			.contains("int i();")
	}
}
