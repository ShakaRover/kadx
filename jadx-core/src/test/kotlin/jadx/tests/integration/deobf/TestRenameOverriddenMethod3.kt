package jadx.tests.integration.deobf

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 覆写方法重命名（抽象类）：对抽象方法 `A.call()` 指定别名后，子类覆写也应使用该别名。
 */
class TestRenameOverriddenMethod3 : IntegrationTest() {

	@Test
	fun test() {
		addMthRename(TestRenameOverriddenMethod3Fixture.TestCls::class.java.name + "\$A", "call()I", "callRenamed")
		assertThat(getClassNode(TestRenameOverriddenMethod3Fixture.TestCls::class.java))
			.code()
			.countString(1, "@Override")
			.countString(2, "int callRenamed()")
	}
}
