package kadx.cli

import kadx.api.KadxArgs.RenameEnum
import kadx.core.utils.exceptions.KadxArgsValidateException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * [KadxCLIArgs.RenameConverter] 的转换测试。
 *
 * **做什么**：验证 `all` / `none` / 非法值的解析结果与错误信息。
 */
class RenameConverterTest {

	private lateinit var converter: KadxCLIArgs.RenameConverter

	@BeforeEach
	fun init() {
		converter = KadxCLIArgs.RenameConverter("someParam")
	}

	@Test
	fun all() {
		val set = converter.convert("all")
		assertThat(set).hasSize(3)
		assertThat(set).contains(RenameEnum.CASE)
		assertThat(set).contains(RenameEnum.VALID)
		assertThat(set).contains(RenameEnum.PRINTABLE)
	}

	@Test
	fun none() {
		val set = converter.convert("none")
		assertThat(set).isEmpty()
	}

	@Test
	fun wrong() {
		val thrown = assertThrows(
			KadxArgsValidateException::class.java,
			{ converter.convert("wrong") },
			"Expected convert() to throw, but it didn't",
		)

		assertThat(thrown.message)
			.isEqualTo("'wrong' is unknown for parameter someParam, possible values are case, valid, printable")
	}
}
