package kadx.api.plugins.pass.types

import kadx.api.plugins.pass.KadxPass
import kadx.core.dex.nodes.RootNode

/**
 * 准备阶段 pass：在反编译开始前对整棵 AST 做一次性初始化。
 *
 * **为什么 TYPE 用 `@JvmField`**：原 Java 接口的 `TYPE` 是静态字段，Java 插件与
 * kadx-core 的 Kotlin 代码都以 `KadxPreparePass.TYPE` 访问；Kotlin 接口 companion
 * 中的 `@JvmField val` 会生成同样的 `public static final` 字段。
 */
interface KadxPreparePass : KadxPass {

	fun init(root: RootNode)

	override fun getPassType(): KadxPassType = TYPE

	companion object {
		@JvmField
		val TYPE: KadxPassType = KadxPassType("PreparePass")
	}
}
