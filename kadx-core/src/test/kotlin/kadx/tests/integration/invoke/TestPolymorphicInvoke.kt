package kadx.tests.integration.invoke

import kadx.core.dex.nodes.ClassNode
import kadx.tests.api.SmaliTest
import kadx.tests.api.extensions.profiles.TestProfile
import kadx.tests.api.extensions.profiles.TestWithProfiles
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * `invoke-polymorphic` 指令还原：Java 使用 invokevirtual，smali 直接使用 invoke-polymorphic。
 */
class TestPolymorphicInvoke : SmaliTest() {

	@TestWithProfiles(TestProfile.DX_J8, TestProfile.D8_J11)
	fun test() {
		val cls: ClassNode = getClassNode(TestPolymorphicInvokeFixture.TestCls::class.java)
		assertThat(cls).code()
			.containsOne("return (String) methodHandle.invoke(this, 1, 2);")
		assertThat(cls).disasmCode()
			.containsOne("invoke-polymorphic")
	}

	@TestWithProfiles(TestProfile.JAVA8, TestProfile.JAVA11)
	fun testJava() {
		assertThat(getClassNode(TestPolymorphicInvokeFixture.TestCls::class.java))
			.code()
			.containsOne("return (String) methodHandle.invoke(this, 1, 2);")
		// java uses 'invokevirtual'
	}

	@Test
	fun testSmali() {
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("String ret = (String) methodHandle.invoke(this, 10, 20);")
	}
}
