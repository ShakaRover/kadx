package jadx.tests.integration.types

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Issue 1002：为让类型推断成功，需要在使用处插入额外强转，例如 `(List<T>) listUnion`。
 */
class TestTypeResolver16 : SmaliTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestTypeResolver16Fixture.TestCls::class.java))
			.code()
			.containsOne("(List<T>) listUnion")
	}

	@Test
	fun testSmali() {
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("(List<T>) listUnion")
	}
}
