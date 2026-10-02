package jadx.api.plugins.pass.impl

import jadx.api.JadxDecompiler
import jadx.api.plugins.pass.JadxPassInfo
import jadx.api.plugins.pass.types.JadxAfterLoadPass
import java.util.function.Consumer

/**
 * 用 lambda/方法引用快速实现一个「加载后 pass」。
 *
 * **做什么**：把实际逻辑封装成 [Consumer]，在 [init] 时回调。
 * 常用于测试或插件里只需一行初始化代码的场景。
 */
class SimpleAfterLoadPass(name: String, init: Consumer<JadxDecompiler>) : JadxAfterLoadPass {

	private val info: JadxPassInfo = SimpleJadxPassInfo(name)

	// 原 Java 参数名为 init（Kotlin 关键字不能作属性名），这里改名为 initConsumer，仅内部使用
	private val initConsumer: Consumer<JadxDecompiler> = init

	override fun getInfo(): JadxPassInfo = info

	override fun init(decompiler: JadxDecompiler) {
		initConsumer.accept(decompiler)
	}
}
