package kadx.api.plugins.pass.types

import kadx.api.KadxDecompiler
import kadx.api.plugins.pass.KadxPass

/**
 * 加载后阶段 pass：在输入文件全部加载完成、开始反编译之前执行一次。
 *
 * **为什么 TYPE 用 `@JvmField`**：与原 Java 接口的静态字段一致，Java 插件与
 * kadx-core 的 Kotlin 代码都以 `KadxAfterLoadPass.TYPE` 访问。
 */
interface KadxAfterLoadPass : KadxPass {

	fun init(decompiler: KadxDecompiler)

	override fun getPassType(): KadxPassType = TYPE

	companion object {
		@JvmField
		val TYPE: KadxPassType = KadxPassType("AfterLoadPass")
	}
}
