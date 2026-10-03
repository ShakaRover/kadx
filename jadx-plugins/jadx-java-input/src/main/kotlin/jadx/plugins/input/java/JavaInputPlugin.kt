package jadx.plugins.input.java

import jadx.api.plugins.JadxPlugin
import jadx.api.plugins.JadxPluginContext
import jadx.api.plugins.JadxPluginInfo
import jadx.api.plugins.input.ICodeLoader
import jadx.api.plugins.input.JadxCodeInput
import jadx.api.plugins.input.data.impl.EmptyCodeLoader
import jadx.plugins.input.java.utils.JavaClassParseException
import org.jetbrains.annotations.Nullable
import java.io.Closeable
import java.io.InputStream
import java.nio.file.Path

/**
 * Java 输入插件入口：注册 .class/.jar 文件加载能力。
 *
 **做什么**：[init] 向插件上下文注册 code input 工厂（文件列表 → [ICodeLoader]）；
 * 静态 load* 方法供外部程序（测试/嵌入场景）直接构造加载器而不走插件机制。
 */
class JavaInputPlugin : JadxPlugin {

	override fun getPluginInfo(): JadxPluginInfo = JadxPluginInfo("java-input", "Java Input", "Load .class and .jar files")

	override fun init(context: JadxPluginContext) {
		// JadxCodeInput 是 Kotlin 接口（非 fun interface），无 SAM 转换，用匿名对象实现
		context.addCodeInput(object : JadxCodeInput {
			override fun loadFiles(inputFiles: java.util.List<Path>): ICodeLoader {
				val loader = JavaInputLoader(context.getZipReader(), context.files().getPluginTempDir())
				// loadFiles 参数是 java List（接口声明），转成 kotlin List 传给 collectFiles
				val readers = loader.collectFiles(inputFiles.toList())
				return if (readers.isEmpty()) EmptyCodeLoader.INSTANCE else JavaLoadResult(readers, null)
			}
		})
	}

	companion object {
		/** 从文件列表加载 class/jar；空结果返回 [EmptyCodeLoader] */
		@JvmStatic
		fun loadClassFiles(inputFiles: List<Path>): ICodeLoader = loadClassFiles(inputFiles, null)

		@JvmStatic
		fun loadClassFiles(inputFiles: List<Path>, @Nullable closeable: Closeable?): ICodeLoader {
			val readers = JavaInputLoader().collectFiles(inputFiles)
			if (readers.isEmpty()) {
				return EmptyCodeLoader.INSTANCE
			}
			return JavaLoadResult(readers, closeable)
		}

		/**
		 * Method for provide several inputs by using load methods from [JavaInputLoader] class.
		 */
		@JvmStatic
		fun load(loader: (JavaInputLoader) -> List<JavaClassReader>): ICodeLoader = wrapClassReaders(loader(JavaInputLoader()))

		/**
		 * Convenient method for load class file or jar from input stream.
		 * Should be used only once per JadxDecompiler instance.
		 * For load several times use [load] method.
		 */
		@JvmStatic
		fun loadFromInputStream(input: InputStream, fileName: String): ICodeLoader = try {
			wrapClassReaders(JavaInputLoader().loadInputStream(input, fileName))
		} catch (e: Exception) {
			throw JavaClassParseException("Failed to read input stream", e)
		}

		/**
		 * Convenient method for load single class file by content.
		 * Should be used only once per JadxDecompiler instance.
		 * For load several times use [load] method.
		 */
		@JvmStatic
		fun loadSingleClass(content: ByteArray, fileName: String): ICodeLoader {
			val reader = JavaInputLoader().loadClass(content, fileName)
			return JavaLoadResult(listOf(reader))
		}

		/** 把 reader 列表包装成 [ICodeLoader]；空列表返回 [EmptyCodeLoader] */
		@JvmStatic
		fun wrapClassReaders(readers: List<JavaClassReader>): ICodeLoader {
			if (readers.isEmpty()) {
				return EmptyCodeLoader.INSTANCE
			}
			return JavaLoadResult(readers)
		}
	}
}
