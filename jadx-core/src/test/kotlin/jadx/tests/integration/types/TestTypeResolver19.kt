package jadx.tests.integration.types

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Issue 1407：`byte` 数组到 `int` 数组的赋值应保留原始形态，不额外插入无意义的强转。
 */
class TestTypeResolver19 : SmaliTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestTypeResolver19Fixture.TestCls::class.java))
			.code()
			.containsOne("iArr[i] = bArr[i];")
			.containsOne("iArr[i] = i2;")
	}
}
