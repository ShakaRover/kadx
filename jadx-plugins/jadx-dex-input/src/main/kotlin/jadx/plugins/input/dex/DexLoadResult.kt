package jadx.plugins.input.dex

import jadx.api.plugins.input.ICodeLoader
import jadx.api.plugins.input.data.IClassData
import java.io.Closeable
import java.util.function.Consumer

/**
 * DEX 输入插件交付给 jadx-core 的代码加载结果：包装一组 [DexReader]。
 *
 * **背景**：
 * 1. [visitClasses] 逐个遍历 [DexReader] 交付 class；
 * 2. [close] 释放调用方提供的外部 [Closeable]（如临时文件句柄）；
 * 3. [isEmpty] 指示没有可交付的 DEX，调用方应改用 EmptyCodeLoader 单例。
 */
public class DexLoadResult(
	private val dexReaders: List<DexReader>,
	private val closeable: Closeable?,
) : ICodeLoader {

	override fun visitClasses(consumer: Consumer<IClassData>) {
		for (dexReader in dexReaders) {
			dexReader.visitClasses { consumer.accept(it) }
		}
	}

	override fun close() {
		closeable?.close()
	}

	override val isEmpty: Boolean get() = dexReaders.isEmpty()
}
