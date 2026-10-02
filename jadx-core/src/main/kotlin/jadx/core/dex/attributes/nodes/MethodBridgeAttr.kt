package jadx.core.dex.attributes.nodes

import jadx.api.plugins.input.data.attributes.PinnedAttribute
import jadx.core.dex.attributes.AType
import jadx.core.dex.nodes.MethodNode

/**
 * 桥接方法属性：记录某个方法是由哪个 bridge 方法桥接出来的。
 *
 * **用途**：泛型擦除后编译器会生成 bridge 方法，反编译时需要把真正的实现方法
 * 与 bridge 关联起来，便于重命名/去重。
 *
 * **Kotlin 转换说明**：继承 [PinnedAttribute]（构造器需显式调用父类构造器）。
 * [bridgeMth] 声明为只读属性，生成的 `getBridgeMth()` 与原 JVM 方法名一致。
 */
class MethodBridgeAttr(val bridgeMth: MethodNode) : PinnedAttribute() {

	override fun getAttrType(): AType<MethodBridgeAttr> = AType.BRIDGED_BY

	override fun toString(): String = "BRIDGED_BY: $bridgeMth"
}
