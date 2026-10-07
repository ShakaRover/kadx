package kadx.gui.utils.cache.code.disk.adapters

import kadx.api.metadata.annotations.VarRef
import kadx.gui.cache.code.disk.adapters.VarRefAdapter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * [VarRefAdapter] 的往返与健壮性测试。
 *
 * **背景（真实线上错误）**：`VarNode.defPos` 初值为 0，只有声明被真正发出后才由
 * `ClassNode` 回填（`setDefPosition`）。所以“只有使用点、声明被后续 pass 删除”的变量会保持 0。
 * 旧实现写入侧照样把 0 落盘，而读取侧 `VarRef.fromPos` 明确拒绝 0 →
 * `IllegalArgumentException: Zero refPos` → **整个类的元数据读取失败**并回退成重新反编译：
 * ```
 * ERROR: Failed to read code cache for com.eclipsesource.mmv8.PlatformDetector
 *   Caused by: java.lang.IllegalArgumentException: Zero refPos
 *     at kadx.gui.cache.code.disk.adapters.VarRefAdapter.read(VarRefAdapter.kt:28)
 * ```
 * 现在：写入侧用 [VarRefAdapter.canPersist] 拦掉（由 `CodeAnnotationAdapter` 落成空标签），
 * 读取侧对磁盘上已存在的坏缓存也容忍（返回 null，调用方跳过该条目）。
 */
class VarRefAdapterTest {

	private fun writeVarRef(value: VarRef?): ByteArray {
		val buf = ByteArrayOutputStream()
		DataOutputStream(buf).use { VarRefAdapter.INSTANCE.write(it, value) }
		return buf.toByteArray()
	}

	private fun readVarRef(bytes: ByteArray): VarRef? =
		DataInputStream(ByteArrayInputStream(bytes)).use { VarRefAdapter.INSTANCE.read(it) }

	@Test
	fun validRefPosRoundTrips() {
		assertThat(readVarRef(writeVarRef(VarRef.fromPos(42)))?.getRefPos()).isEqualTo(42)
	}

	@Test
	fun zeroRefPosReadsAsNullInsteadOfThrowing() {
		// 模拟磁盘上已有的坏缓存：UVInt 0
		val bytes = writeVarRef(VarRef.FixedVarRef(0))
		assertThat(bytes).containsExactly(0.toByte())

		// 关键：不抛 IllegalArgumentException，而是返回 null 让调用方跳过该条目
		assertThat(readVarRef(bytes)).isNull()
	}

	@Test
	fun nullWritesZeroAndReadsNull() {
		assertThat(writeVarRef(null)).containsExactly(0.toByte())
		assertThat(readVarRef(writeVarRef(null))).isNull()
	}

	@Test
	fun canPersistRejectsUnknownPosition() {
		// 写入侧守卫：位置未知的引用不得落盘（否则读取侧无法表示）
		assertThat(VarRefAdapter.canPersist(VarRef.FixedVarRef(0))).isFalse()
		assertThat(VarRefAdapter.canPersist(VarRef.fromPos(1))).isTrue()
		assertThat(VarRefAdapter.canPersist(VarRef.fromPos(9999))).isTrue()
	}
}

/**
 * 写入侧守卫测试：`CodeAnnotationAdapter` 必须把“位置未知的 VarRef”落成空标签，
 * 而不是 VAR_REF 标签 —— 否则磁盘上会写出读取侧表示不了的条目。
 *
 * 用未加载的 `RootNode(KadxArgs())` 即可构造适配器（子适配器只持有 root 引用）。
 */
class CodeAnnotationAdapterWriteTest {

	private val adapter = kadx.gui.cache.code.disk.adapters.CodeAnnotationAdapter(
		kadx.core.dex.nodes.RootNode(kadx.api.KadxArgs()),
	)

	private fun writeAnn(value: kadx.api.metadata.ICodeAnnotation?): ByteArray {
		val buf = ByteArrayOutputStream()
		DataOutputStream(buf).use { adapter.write(it, value) }
		return buf.toByteArray()
	}

	@Test
	fun zeroRefPosVarRefIsPersistedAsNullTag() {
		assertThat(writeAnn(VarRef.FixedVarRef(0))).containsExactly(0.toByte())
	}

	@Test
	fun validVarRefIsPersistedWithRealTagAndRoundTrips() {
		val bytes = writeAnn(VarRef.fromPos(11))
		assertThat(bytes[0]).isNotEqualTo(0.toByte())
		DataInputStream(ByteArrayInputStream(bytes)).use { input ->
			assertThat((adapter.read(input) as VarRef).getRefPos()).isEqualTo(11)
		}
	}

	@Test
	fun nullStaysNull() {
		assertThat(writeAnn(null)).containsExactly(0.toByte())
	}
}
