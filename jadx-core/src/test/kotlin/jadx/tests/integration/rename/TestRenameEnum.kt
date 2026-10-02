package jadx.tests.integration.rename

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 枚举重命名：修改内部枚举类短名后，重新反编译应使用新枚举名且枚举常量不变。
 */
class TestRenameEnum : IntegrationTest() {

	@Test
	fun test() {
		val cls = getClassNode(TestRenameEnumFixture.TestCls::class.java)
		assertThat(cls)
			.code()
			.containsOnlyOnce("public enum A ")
			.containsOnlyOnce("ONE {")

		cls.innerClasses[0].classInfo.changeShortName("ARenamed")

		assertThat(cls)
			.reloadCode(this)
			.containsOnlyOnce("public enum ARenamed ")
			.containsOnlyOnce("ONE {")
	}
}
