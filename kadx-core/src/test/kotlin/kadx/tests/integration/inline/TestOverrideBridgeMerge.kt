package kadx.tests.integration.inline

import kadx.api.metadata.ICodeAnnotation
import kadx.api.metadata.annotations.NodeDeclareRef
import kadx.core.dex.nodes.ClassNode
import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 覆写 bridge 方法：Java 输入中 `test` 不被内联；smali 中 bridge 方法应合并为 `apply`。
 */
class TestOverrideBridgeMerge : SmaliTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestOverrideBridgeMergeFixture.TestCls::class.java))
			.code()
			.containsOne("Integer test(String str) {") // not inlined
	}

	@Test
	fun testSmali() {
		val cls: ClassNode = getClassNodeFromSmali()
		val mthDef: ICodeAnnotation = NodeDeclareRef(getMethod(cls, "apply"))
		assertThat(cls)
			.checkCodeAnnotationFor("apply(String str) {", mthDef)
			.code()
			.containsOne("@Override")
			.containsOne("public Integer apply(String str) {")
			.doesNotContain("test(String str)")
	}
}
