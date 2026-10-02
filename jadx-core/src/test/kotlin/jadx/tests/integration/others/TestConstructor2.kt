package jadx.tests.integration.others

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 构造函数调用的是 Object 而非实例类型：`new A()` 不应被错误还原成 return 形式。
 */
class TestConstructor2 : SmaliTest() {

	@Test
	fun test() {
		assertThat(getClassNodeFromSmaliFiles())
			.code()
			.containsOne("A a = new A();")
			.doesNotContain("return")
	}
}
