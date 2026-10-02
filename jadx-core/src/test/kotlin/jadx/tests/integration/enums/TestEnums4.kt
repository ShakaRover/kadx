package jadx.tests.integration.enums

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 带可变参数构造器的枚举：应还原 `CODE(".dex", ".class")` 与 `String... extensions` 签名。
 */
class TestEnums4 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestEnums4Fixture.TestCls::class.java))
			.code()
			.containsOne("CODE(\".dex\", \".class\"),")
			.containsOne("ResType(String... extensions) {")
		// assertThat(code, not(containsString("private ResType")));
	}
}
