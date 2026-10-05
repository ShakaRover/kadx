package kadx.tests.integration.types

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 泛型内部类 `Entry<K, V>` 的局部变量类型推断：应补全 `Entry<K, V>` 而非裸 `Entry`。
 */
class TestGenerics6 : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestGenerics6Fixture.TestCls::class.java))
			.code()
			.doesNotContain("Entry entry = get(k);")
			.containsOne("Entry<K, V> entry = get(k);")
	}
}
