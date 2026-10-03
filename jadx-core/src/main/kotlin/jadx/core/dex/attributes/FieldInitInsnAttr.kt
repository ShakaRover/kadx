package jadx.core.dex.attributes

import jadx.api.plugins.input.data.attributes.IJadxAttrType
import jadx.api.plugins.input.data.attributes.PinnedAttribute
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode

/**
 * 字段初始化指令属性：记录“某个字段是在哪个方法里的哪条指令被初始化的”。
 *
 * **用途**：反编译字段初始化（例如 `private int x = 5;`）时，需要找到把常量写入字段的
 * 那条赋值指令，以及它所在的方法（通常是构造器或 `<clinit>`）。
 *
 * **Kotlin 转换说明**：
 * - 继承自 [PinnedAttribute]（常驻属性，卸载时保留）；
 * - `mth` 保持私有并配显式 `getInsnMth()`，因为原 Java 的方法名是 `getInsnMth` 而非 `getMth`；
 * - 构造参数非空（原 Java 用 `requireNonNull`），Kotlin 直接用非空类型即可。
 */
class FieldInitInsnAttr(
	private val mth: MethodNode,
	val insn: InsnNode,
) : PinnedAttribute() {

	/** 初始化指令所在的方法（原 Java 方法名：getInsnMth） */
	fun getInsnMth(): MethodNode = mth

	override val attrType: IJadxAttrType<*> get() = AType.FIELD_INIT_INSN

	override fun toString(): String = "INIT{$insn}"
}
