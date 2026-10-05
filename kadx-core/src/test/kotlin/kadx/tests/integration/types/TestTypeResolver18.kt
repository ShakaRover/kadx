package kadx.tests.integration.types

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 泛型 `T` 经 `instanceof Closeable` 判断后的强转调用应保留：`((Closeable) t).close();`。
 */
class TestTypeResolver18 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestTypeResolver18Fixture.TestCls::class.java))
			.code()
			.containsOne("((Closeable) t).close();")
	}
}
