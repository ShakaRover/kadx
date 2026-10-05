package kadx.tests.api.utils.assertj

import kadx.core.dex.nodes.MethodNode
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

/**
 * [MethodNode] 的 AssertJ 断言封装：提供便捷的 `code()` 入口。
 */
class KadxMethodNodeAssertions(mth: MethodNode) : AbstractObjectAssert<KadxMethodNodeAssertions, MethodNode>(mth, KadxMethodNodeAssertions::class.java) {

	fun code(): KadxCodeAssertions {
		isNotNull()
		val codeStr = actual.codeStr
		assertThat(codeStr).isNotBlank()
		return KadxCodeAssertions(codeStr)
	}
}
