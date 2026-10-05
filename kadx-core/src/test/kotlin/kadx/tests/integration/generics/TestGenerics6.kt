package kadx.tests.integration.generics

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 泛型集合的 for-each：元素类型应还原为具体类 `A` 或接口 `I`。
 */
class TestGenerics6 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestGenerics6Fixture.TestCls::class.java))
			.code()
			.containsOne("for (A a : as) {")
			.containsOne("for (I i : is) {")
	}
}
