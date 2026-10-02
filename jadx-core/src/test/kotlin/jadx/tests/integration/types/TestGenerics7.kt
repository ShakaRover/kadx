package jadx.tests.integration.types

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
