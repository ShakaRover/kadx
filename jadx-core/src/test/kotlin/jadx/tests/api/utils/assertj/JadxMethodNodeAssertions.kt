package jadx.tests.api.utils.assertj

import jadx.core.dex.nodes.MethodNode
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

/**
 * [MethodNode] 的 AssertJ 断言封装：提供便捷的 `code()` 入口。
 */
class JadxMethodNodeAssertions(mth: MethodNode) : AbstractObjectAssert<JadxMethodNodeAssertions, MethodNode>(mth, JadxMethodNodeAssertions::class.java) {

	fun code(): JadxCodeAssertions {
		isNotNull()
		val codeStr = actual.getCodeStr()
		assertThat(codeStr).isNotBlank()
		return JadxCodeAssertions(codeStr)
	}
}
