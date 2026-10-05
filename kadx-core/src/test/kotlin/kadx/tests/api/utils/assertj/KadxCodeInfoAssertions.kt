package kadx.tests.api.utils.assertj

import kadx.api.ICodeInfo
import kadx.api.metadata.annotations.InsnCodeOffset
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.assertj.core.api.AbstractObjectAssert

/**
 * [ICodeInfo] 的 AssertJ 断言封装。
 *
 * 注意：本文件里的 `assertThat` 来自 Java 类 [KadxAssertions]（继承自 AssertJ 的
 * `Assertions`），因此仍可使用 `assertThat(String)` 等重载。
 */
class KadxCodeInfoAssertions(codeInfo: ICodeInfo) : AbstractObjectAssert<KadxCodeInfoAssertions, ICodeInfo>(codeInfo, KadxCodeInfoAssertions::class.java) {

	fun code(): KadxCodeAssertions {
		isNotNull()
		val codeStr = actual.codeStr
		assertThat(codeStr).isNotBlank()
		return KadxCodeAssertions(codeStr)
	}

	fun checkCodeOffsets(): KadxCodeInfoAssertions {
		val dupOffsetCount = actual.codeMetadata.getAsMap().values
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
