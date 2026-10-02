package jadx.api.plugins.pass.types

import jadx.api.plugins.pass.JadxPass
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode

/**
 * 反编译阶段 pass：在类/方法生成代码前后参与处理。
 *
 * **为什么 TYPE 用 `@JvmField`**：与原 Java 接口的静态字段一致，Java 插件与
 * jadx-core 的 Kotlin 代码都以 `JadxDecompilePass.TYPE` 访问。
 */
interface JadxDecompilePass : JadxPass {

	fun init(root: RootNode)

	/**
	 * 访问类。
	 *
	 * @return 返回 false 表示跳过其子方法与内部类的遍历
	 */
	fun visit(cls: ClassNode): Boolean

	/**
	 * 访问方法。
	 */
	fun visit(mth: MethodNode)

	override fun getPassType(): JadxPassType = TYPE

	companion object {
		@JvmField
		val TYPE: JadxPassType = JadxPassType("DecompilePass")
	}
}
