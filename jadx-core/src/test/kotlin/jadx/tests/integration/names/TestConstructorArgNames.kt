package jadx.tests.integration.names

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 构造器参数名与字段名冲突：构造器参数 `STR` 与静态字段 `STR` 同名时应重命名参数，
 * 保证赋值语义正确。
 */
class TestConstructorArgNames : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestConstructorArgNamesFixture.TestCls::class.java))
			.code()
			.containsOne("this.str = str;")
			.containsOne("this.store = STR2;")
			.containsOne("this.store = STR;")
	}
}
