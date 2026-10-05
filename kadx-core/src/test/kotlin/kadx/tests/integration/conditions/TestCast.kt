package kadx.tests.integration.conditions

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 三元表达式中的强制转换：byte/short 的字面量与字段访问都应正确插入 cast。
 */
class TestCast : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestCastFixture.TestCls::class.java))
			.code()
			.contains("write(a ? (byte) 0 : (byte) 1);")
			.contains("write(a ? (byte) 0 : this.myByte);")
			.contains("write(a ? (byte) 0 : (byte) 127);")
			.contains("write(a ? (short) 0 : (short) 1);")
			.contains("write(a ? this.myShort : (short) 0);")
			.contains("write(a ? Short.MIN_VALUE : (short) 0);")
	}
}
