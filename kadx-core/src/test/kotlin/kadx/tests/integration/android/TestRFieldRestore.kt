package kadx.tests.integration.android

import kadx.api.plugins.input.data.annotations.EncodedValue
import kadx.api.plugins.input.data.attributes.KadxAttrType
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * R 字段恢复（未知 R 类）：常量还原为 `R.id.Button` 后，
 * 应自动生成 `R` / `R.id` 类与 `Button` 常量字段，且值与原常量一致。
 */
class TestRFieldRestore : IntegrationTest() {

	@Test
	fun test() {
		// unknown R class
		disableCompilation()

		val map = HashMap<Int, String>()
		val buttonConstValue = 2131230730
		map[buttonConstValue] = "id.Button"
		setResMap(map)

		val cls = getClassNode(TestRFieldRestoreFixture.TestCls::class.java)
		assertThat(cls)
			.code()
			.containsOne("return R.id.Button;")
			.doesNotContain("import R;")

		// check 'R' class
		val rCls = checkNotNull(cls.root().searchClassByFullAlias("R"))
		org.assertj.core.api.Assertions.assertThat(rCls).isNotNull()

		// check inner 'id' class
		val innerClasses = rCls.innerClasses
		org.assertj.core.api.Assertions.assertThat(innerClasses).hasSize(1)
		val idCls = innerClasses[0]
		org.assertj.core.api.Assertions.assertThat(idCls.shortName).isEqualTo("id")

		// check 'Button' field
		val buttonField = idCls.searchFieldByName("Button")
		org.assertj.core.api.Assertions.assertThat(buttonField).isNotNull()
		val constVal: EncodedValue = checkNotNull(checkNotNull(buttonField).get(KadxAttrType.CONSTANT_VALUE))
		val buttonValue = constVal.value as Int
		org.assertj.core.api.Assertions.assertThat(buttonValue).isEqualTo(buttonConstValue)
	}
}
