package jadx.tests.integration.names

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 同一包内仅大小写不同的类名（`com.example.User` 与 `com.example.user`）：
 * 在不区分大小写的文件系统上二者会冲突，必须重命名以保证路径唯一。
 */
class TestCaseSensitiveClassInPkgChecks : SmaliTest() {
	/*
	 * com.example.User and com.example.user - class names differ only by case in the same package.
	 * On case-insensitive FS both would map to the same file path, requiring class rename.
	 */

	@Test
	fun testClassConflictOnCaseInsensitiveFS() {
		args.isFsCaseSensitive = false

		val classes = loadFromSmaliFiles()
		assertThat(classes).hasSize(2)

		val distinct = classes.map { it.classInfo.aliasFullPath.lowercase() }.distinct().size.toLong()
		assertThat(distinct).isEqualTo(2L)
	}

	@Test
	fun testClassConflictOnCaseSensitiveFS() {
		args.isFsCaseSensitive = true

		val classes = loadFromSmaliFiles()
		assertThat(classes).hasSize(2)

		val distinct = classes.map { it.classInfo.aliasFullPath }.distinct().size.toLong()
		assertThat(distinct).isEqualTo(2L)
	}
}
