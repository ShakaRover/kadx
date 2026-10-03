package jadx.plugins.input.java

import jadx.api.plugins.input.ICodeLoader
import jadx.api.plugins.input.data.IClassData
import org.jetbrains.annotations.Nullable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.function.Consumer

/**
 * Java 输入加载结果：一组 [JavaClassReader] + 可选的关闭钩子。
 *
 **做什么**：实现 [ICodeLoader]，[visitClasses] 逐个把 reader 转成 IClassData 回调给核心；
 * 单个文件失败只记日志不中断（尽力反编译原则）。
 */
class JavaLoadResult @JvmOverloads constructor(
	private val readers: List<JavaClassReader>,
	@Nullable private val closeable: Closeable? = null,
) : ICodeLoader {

	override fun visitClasses(consumer: Consumer<IClassData>) {
		for (reader in readers) {
			try {
				consumer.accept(reader.loadClassData())
			} catch (e: Exception) {
				LOG.error("Failed to load class data for file: {}", reader.fileName, e)
			}
		}
	}

	override val isEmpty: Boolean get() = readers.isEmpty()

	override fun close() {
		closeable?.close()
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(JavaLoadResult::class.java)
	}
}
