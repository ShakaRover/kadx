package kadx.tests.integration.names

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 仅大小写不同的包名（`com.Example.Foo` 与 `com.example.Foo`）：
 * 在不区分大小写的文件系统上二者会映射到同一目录（com/example/foo），因此需要包重命名。
 */
class TestCaseSensitivePkgChecks : SmaliTest() {
	/*
	 * com.Example.Foo and com.example.Foo - same class name in packages that differ only by case.
	 * On case-insensitive FS both would map to the same path (com/example/foo), requiring package
	 * rename.
	 */

	@Test
	fun testPkgConflictOnCaseInsensitiveFS() {
		args.isFsCaseSensitive = false

		val classes = loadFromSmaliFiles()
		assertThat(classes).hasSize(2)

		// all package paths must be distinct when lowercased (no two classes share same dir)
		val distinctPkgPaths = classes.map { it.classInfo.aliasFullPath.lowercase() }.distinct().size.toLong()
		assertThat(distinctPkgPaths).isEqualTo(2L)
	}

	@Test
	fun testPkgConflictOnCaseSensitiveFS() {
		args.isFsCaseSensitive = true

		val classes = loadFromSmaliFiles()
		assertThat(classes).hasSize(2)

		// on case-sensitive FS, original package names should be preserved
		val distinctPkgPaths = classes.map { it.classInfo.aliasFullPath }.distinct().size.toLong()
		assertThat(distinctPkgPaths).isEqualTo(2L)
	}
}
