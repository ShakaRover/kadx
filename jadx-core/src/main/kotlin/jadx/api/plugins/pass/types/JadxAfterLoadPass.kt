package jadx.api.plugins.pass.types

import jadx.api.JadxDecompiler
import jadx.api.plugins.pass.JadxPass

/**
 * 加载后阶段 pass：在输入文件全部加载完成、开始反编译之前执行一次。
 *
 * **为什么 TYPE 用 `@JvmField`**：与原 Java 接口的静态字段一致，Java 插件与
 * jadx-core 的 Kotlin 代码都以 `JadxAfterLoadPass.TYPE` 访问。
 */
interface JadxAfterLoadPass : JadxPass {

	fun init(decompiler: JadxDecompiler)

	override fun getPassType(): JadxPassType = TYPE

	companion object {
		@JvmField
		val TYPE: JadxPassType = JadxPassType("AfterLoadPass")
	}
}
