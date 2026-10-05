package kadx.tests.integration.debuginfo

import kadx.tests.api.IntegrationTest
import kadx.tests.api.extensions.profiles.TestProfile
import kadx.tests.api.extensions.profiles.TestWithProfiles
import org.assertj.core.api.Assertions.assertThat

/**
 * 弱引用赋值场景的行号映射（java / dx 两种输入）。
 *
 * fixture 通过 `//` 注释填充保持 `TestCls` 的原始行号，故映射字符串不变。
 */
class TestLineNumbers2 : IntegrationTest() {

	@TestWithProfiles(TestProfile.DX_J8, TestProfile.JAVA8)
	fun test() {
		printLineNumbers()

		val cls = getClassNode(TestLineNumbers2Fixture.TestCls::class.java)
		val linesMapStr = cls.getCode().codeMetadata.getLineMapping().toString()
		if (isJavaInput()) {
			assertThat(linesMapStr).isEqualTo("{6=16, 9=17, 12=21, 13=22, 14=23, 15=24, 16=25, 18=27, 21=30, 22=31}")
		} else {
			assertThat(linesMapStr).isEqualTo("{6=16, 9=17, 12=21, 13=22, 14=23, 15=24, 16=25, 17=27, 19=27, 22=30, 23=31}")
		}
	}
}
