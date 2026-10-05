package kadx.tests.integration.types

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * `WeakReference<T>` 取值后的类型还原：应推断为泛型 `T` 而非 `Object`。
 */
class TestTypeResolver12 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestTypeResolver12Fixture.TestCls::class.java))
			.code()
			.containsOne("T obj = this.ref.get();")
	}

	@Test
	fun testNoDebug() {
		noDebugInfo()
		assertThat(getClassNode(TestTypeResolver12Fixture.TestCls::class.java))
			.code()
			.doesNotContain("Object obj")
			.containsOne("T t = this.ref.get();")
	}
}
