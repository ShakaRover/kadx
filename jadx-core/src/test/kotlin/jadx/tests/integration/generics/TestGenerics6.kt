package jadx.tests.integration.generics

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 泛型集合的 for-each：元素类型应还原为具体类 `A` 或接口 `I`。
 */
class TestGenerics6 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestGenerics6Fixture.TestCls::class.java))
			.code()
			.containsOne("for (A a : as) {")
			.containsOne("for (I i : is) {")
	}
}
