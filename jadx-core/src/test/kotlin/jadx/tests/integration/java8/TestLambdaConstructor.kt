package jadx.tests.integration.java8

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 构造器方法引用（`RuntimeException::new`）应被还原；fallback 模式下退化为显式调用。
 */
class TestLambdaConstructor : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestLambdaConstructorFixture.TestCls::class.java))
			.code()
			.containsOne("return RuntimeException::new;")
	}

	@Test
	fun testFallback() {
		setFallback()
		assertThat(getClassNode(TestLambdaConstructorFixture.TestCls::class.java))
			.code()
			.containsOne("r0 = java.lang.RuntimeException::new")
			.containsOne("return r0")
	}
}
