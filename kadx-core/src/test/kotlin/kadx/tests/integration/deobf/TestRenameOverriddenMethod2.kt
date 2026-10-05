package kadx.tests.integration.deobf

import kadx.core.dex.attributes.AType
import kadx.core.dex.attributes.nodes.MethodOverrideAttr
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 覆写方法重命名（多实现）：接口 `I.call()` 被两个类实现，
 * 应共享同一别名，且覆写属性记录 3 个相关方法节点、覆写列表为空。
 */
class TestRenameOverriddenMethod2 : IntegrationTest() {

	@Test
	fun test() {
		enableDeobfuscation()
		args.deobfuscationMinLength = 100 // rename everything

		val cls = getClassNode(TestRenameOverriddenMethod2Fixture.TestCls::class.java)
		assertThat(cls)
			.code()
			.countString(2, "@Override")
			.countString(3, "int mo0call()")

		assertThat(searchCls(cls.innerClasses, "I"))
			.extracting { c -> c.searchMethodByShortName("call") }
			.isNotNull()
			.extracting { m -> checkNotNull(m).get(AType.METHOD_OVERRIDE) }
			.isNotNull()
			.satisfies(
				java.util.function.Consumer<MethodOverrideAttr?> { ovrdAttr ->
					val attr = checkNotNull(ovrdAttr)
					org.assertj.core.api.Assertions.assertThat(attr.relatedMthNodes).hasSize(3)
					org.assertj.core.api.Assertions.assertThat(attr.overrideList).isEmpty()
				},
			)
	}
}
