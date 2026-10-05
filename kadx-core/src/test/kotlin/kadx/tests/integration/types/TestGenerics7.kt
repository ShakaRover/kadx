package kadx.tests.integration.types

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 从 `Object[]` 取出泛型 `T` 时的类型还原：应生成 `T t = (T) objArr[i];`。
 *
 * Issue https://github.com/skylot/jadx/issues/956
 */
class TestGenerics7 : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestGenerics7Fixture.TestCls::class.java))
			.code()
			.containsOne("T t = (T) objArr[i];")
	}
}
