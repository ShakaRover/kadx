package kadx.api.plugins.pass.types

/**
 * Pass 类型标识：用一个字符串名字区分「准备 / 反编译 / 加载后」等 pass 类别。
 *
 * **为什么不用 data class**：它被当作 Map 的 key（见 `KadxDecompiler.customPasses`），
 * 依赖按值相等语义；这里手工保留原 Java 的 `equals` / `hashCode` / `toString`，
 * 保证行为完全一致。
 */
class KadxPassType(private val cls: String) {

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other !is KadxPassType) {
			return false
		}
		return cls == other.cls
	}

	override fun hashCode(): Int = cls.hashCode()

	override fun toString(): String = cls
}
