package kadx.api.plugins.pass.impl

import kadx.api.KadxDecompiler
import kadx.api.plugins.pass.KadxPassInfo
import kadx.api.plugins.pass.types.KadxAfterLoadPass
import java.util.function.Consumer

/**
 * 用 lambda/方法引用快速实现一个「加载后 pass」。
 *
 * **做什么**：把实际逻辑封装成 [Consumer]，在 [init] 时回调。
 * 常用于测试或插件里只需一行初始化代码的场景。
 */
class SimpleAfterLoadPass(name: String, init: Consumer<KadxDecompiler>) : KadxAfterLoadPass {

	private val info: KadxPassInfo = SimpleKadxPassInfo(name)

	// 原 Java 参数名为 init（Kotlin 关键字不能作属性名），这里改名为 initConsumer，仅内部使用
	private val initConsumer: Consumer<KadxDecompiler> = init

	override fun getInfo(): KadxPassInfo = info

	override fun init(decompiler: KadxDecompiler) {
		initConsumer.accept(decompiler)
	}
}
