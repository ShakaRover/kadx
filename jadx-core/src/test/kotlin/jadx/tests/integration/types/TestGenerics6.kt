package jadx.tests.integration.types

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
