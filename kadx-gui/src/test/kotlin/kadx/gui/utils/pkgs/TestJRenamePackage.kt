package kadx.gui.utils.pkgs

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 包重命名名称校验测试。
 *
 * **做什么**：校验 [JRenamePackage.isValidPackageName] 对合法 / 非法包名的判断。
 */
class TestJRenamePackage {

	@Test
	fun isValidName() {
		valid("foo")
		valid("foo.bar")
		valid(".bar")

		invalid("")
		invalid("0foo")
		invalid("foo.")
		invalid("do")
		invalid("foo.if")
		invalid("foo.if.bar")
	}

	private fun valid(name: String) {
		assertThat(JRenamePackage.isValidPackageName(name))
			.`as`("expect valid: %s", name)
			.isTrue()
	}

	private fun invalid(name: String) {
		assertThat(JRenamePackage.isValidPackageName(name))
			.`as`("expect invalid: %s", name)
			.isFalse()
	}
}
