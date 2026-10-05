package kadx.tests.integration.names

import kadx.core.Consts
import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 大小写敏感文件系统检查：类名 `A` 与 `a` 在不区分大小写的文件系统上会映射到同一路径，
 * 因此需要触发重命名；开启大小写敏感后应保留原始名字。
 */
class TestCaseSensitiveChecks : SmaliTest() {
	/*
	 * public class A {}
	 * public class a {}
	 */

	@Test
	fun test() {
		args.isFsCaseSensitive = false

		val classes = loadFromSmaliFiles()
		for (cls in classes) {
			assertThat(cls.`package`).isEqualTo(Consts.DEFAULT_PACKAGE_NAME)
		}
		val namesCount = classes.map { it.alias.lowercase() }.distinct().size.toLong()
		assertThat(namesCount).isEqualTo(2L)
	}

	@Test
	fun testCaseSensitiveFS() {
		args.isFsCaseSensitive = true

		val classes = loadFromSmaliFiles()
		for (cls in classes) {
			assertThat(cls.`package`).isEqualTo(Consts.DEFAULT_PACKAGE_NAME)
		}
		val names = classes.map { it.alias }
		assertThat(names).containsExactlyInAnyOrder("A", "a")
	}

	@Test
	fun testWithDeobfuscation() {
		enableDeobfuscation()

		val classes = loadFromSmaliFiles()
		for (cls in classes) {
			assertThat(cls.`package`).isNotEmpty()
			assertThat(cls.`package`).isNotEqualTo(Consts.DEFAULT_PACKAGE_NAME)
		}
		val namesCount = classes.map { it.alias.lowercase() }.distinct().size.toLong()
		assertThat(namesCount).isEqualTo(2L)
	}
}
