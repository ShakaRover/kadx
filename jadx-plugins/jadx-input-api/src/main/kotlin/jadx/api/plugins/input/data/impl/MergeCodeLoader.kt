package jadx.api.plugins.input.data.impl

import jadx.api.plugins.input.ICodeLoader
import jadx.api.plugins.input.data.IClassData
import java.io.Closeable
import java.util.function.Consumer

/**
 * 合并多个代码加载器的装饰器：按顺序把每个子加载器的类交付给同一消费者。
 *
 * **背景**：一个输入（如 APK）可能同时包含 classes.dex 和 res/ 下的 class，
 * 本类把它们统一成一个 [ICodeLoader] 视图。
 *
 * @param codeLoaders 要合并的子加载器列表
 * @param closeable 关闭时需要额外释放的资源；可为 null
 */
public class MergeCodeLoader(
	private val codeLoaders: List<ICodeLoader>,
	private val closeable: Closeable?,
) : ICodeLoader {

	/** 便捷构造：无额外需要释放的资源 */
	public constructor(codeLoaders: List<ICodeLoader>) : this(codeLoaders, null)

	override fun visitClasses(consumer: Consumer<IClassData>) {
		for (codeLoader in codeLoaders) {
			codeLoader.visitClasses(consumer)
		}
	}

	override fun isEmpty(): Boolean {
		for (codeLoader in codeLoaders) {
			if (!codeLoader.isEmpty()) {
				return false
			}
		}
		return true
	}

	override fun close() {
		for (codeLoader in codeLoaders) {
			codeLoader.close()
		}
		closeable?.close()
	}
}
