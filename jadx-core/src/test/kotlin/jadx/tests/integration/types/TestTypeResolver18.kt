package jadx.tests.integration.types

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
