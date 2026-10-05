package kadx.api.plugins.input.data.impl

import kadx.api.plugins.input.ICodeLoader
import kadx.api.plugins.input.data.IClassData
import java.util.function.Consumer

/**
 * 空的代码加载器：不交付任何类。
 *
 * **使用场景**：输入插件解析后没有发现任何类时，返回本单例占位，
 * 让调用方无需处理 null。
 */
public class EmptyCodeLoader : ICodeLoader {

	override val isEmpty: Boolean get() = true

	override fun visitClasses(consumer: Consumer<IClassData>) {
		// 空实现：没有类可交付
	}

	override fun close() {
		// 无资源需要释放
	}

	public companion object {
		/** 全局单例（原 Java 的 public static final INSTANCE）*/
		@JvmField
		public val INSTANCE: EmptyCodeLoader = EmptyCodeLoader()
	}
}
