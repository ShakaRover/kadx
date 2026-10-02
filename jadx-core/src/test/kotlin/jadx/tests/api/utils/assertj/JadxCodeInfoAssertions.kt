package jadx.tests.api.utils.assertj

import jadx.api.ICodeInfo
import jadx.api.metadata.annotations.InsnCodeOffset
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.assertj.core.api.AbstractObjectAssert

/**
 * [ICodeInfo] 的 AssertJ 断言封装。
 *
 * 注意：本文件里的 `assertThat` 来自 Java 类 [JadxAssertions]（继承自 AssertJ 的
 * `Assertions`），因此仍可使用 `assertThat(String)` 等重载。
 */
class JadxCodeInfoAssertions(codeInfo: ICodeInfo) : AbstractObjectAssert<JadxCodeInfoAssertions, ICodeInfo>(codeInfo, JadxCodeInfoAssertions::class.java) {

	fun code(): JadxCodeAssertions {
		isNotNull()
		val codeStr = actual.getCodeStr()
		assertThat(codeStr).isNotBlank()
		return JadxCodeAssertions(codeStr)
	}

	fun checkCodeOffsets(): JadxCodeInfoAssertions {
		val dupOffsetCount = actual.getCodeMetadata().getAsMap().values
			.filterIsInstance<InsnCodeOffset>()
			.groupBy { it.getOffset() }
			.values
			.count { it.size > 1 }
		assertThat(dupOffsetCount)
			.describedAs("Found duplicated code offsets")
			.isEqualTo(0)
		return this
	}
}
