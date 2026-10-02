package jadx.tests.integration.debuginfo

import jadx.tests.api.IntegrationTest
import jadx.tests.api.extensions.profiles.TestProfile
import jadx.tests.api.extensions.profiles.TestWithProfiles
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat

/**
 * 构造器中 `super(...)` 调用的行号映射。
 *
 * fixture 通过 `//` 注释填充保持 `TestCls` 的原始行号，故映射字符串不变。
 */
class TestLineNumbers3 : IntegrationTest() {

	@TestWithProfiles(TestProfile.DX_J8, TestProfile.JAVA8)
	fun test() {
		val cls = getClassNode(TestLineNumbers3Fixture.TestCls::class.java)
		assertThat(cls).code().containsOne("super(message == null ? \"\" : message.toString());")
		val linesMapStr = cls.getCode().getCodeMetadata().getLineMapping().toString()
		assertThat(linesMapStr).isEqualTo("{4=13, 5=14, 6=15}")
	}
}
