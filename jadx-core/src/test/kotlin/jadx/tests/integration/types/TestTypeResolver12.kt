package jadx.tests.integration.types

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
