package kadx.core.utils.files

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

/**
 * [FileUtils.buildInputsHash] 的回归测试。
 *
 * 重点覆盖「同 mtime、不同大小」：旧实现只哈希 mtime，替换 APK 后若 mtime 恰好相同
 * （或被工具回写为原值）就会命中脏缓存。
 */
class FileUtilsTest {

	@field:TempDir
	private lateinit var tempDir: Path

	private val fixedMtime: FileTime = FileTime.fromMillis(1_700_000_000_000L)

	@Test
	fun inputsHashChangesWhenOnlySizeDiffers() {
		val file = tempDir.resolve("input.apk")
		Files.write(file, ByteArray(16) { 1.toByte() })
		Files.setLastModifiedTime(file, fixedMtime)
		val before = FileUtils.buildInputsHash(listOf(file))

		// 换成不同内容/大小，但把 mtime 回写为同一值
		Files.write(file, ByteArray(4096) { 2.toByte() })
		Files.setLastModifiedTime(file, fixedMtime)

		assertThat(FileUtils.buildInputsHash(listOf(file))).isNotEqualTo(before)
	}

	@Test
	fun inputsHashChangesWhenOnlyMtimeDiffers() {
		val file = tempDir.resolve("input.apk")
		Files.write(file, ByteArray(32) { 3.toByte() })
		Files.setLastModifiedTime(file, fixedMtime)
		val before = FileUtils.buildInputsHash(listOf(file))

		Files.setLastModifiedTime(file, FileTime.fromMillis(fixedMtime.toMillis() + 1000))

		assertThat(FileUtils.buildInputsHash(listOf(file))).isNotEqualTo(before)
	}

	@Test
	fun inputsHashIsStableForUnchangedInput() {
		val file = tempDir.resolve("input.apk")
		Files.write(file, ByteArray(64) { 7.toByte() })
		Files.setLastModifiedTime(file, fixedMtime)

		assertThat(FileUtils.buildInputsHash(listOf(file)))
			.isEqualTo(FileUtils.buildInputsHash(listOf(file)))
	}
}
