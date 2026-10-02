package jadx.tests.api.compiler

import java.io.Closeable
import java.io.File
import java.io.IOException
import javax.tools.FileObject
import javax.tools.ForwardingJavaFileManager
import javax.tools.JavaFileManager.Location
import javax.tools.JavaFileObject
import javax.tools.JavaFileObject.Kind
import javax.tools.StandardJavaFileManager

/**
 * 编译产物重定向的 `JavaFileManager`。
 *
 * **做什么**：拦截 `getJavaFileForOutput`，把编译器写出的 class 字节收进
 * [DynamicClassLoader] 的内存缓存，而不是落到磁盘；同时把源码文件列表转换成
 * [JavaFileObject] 列表。
 *
 * **Java 兼容性**：无参 `getClassLoader()` 与继承的 `getClassLoader(Location)` 构成重载，
 * 仍被 Kotlin 版 `TestCompiler` 调用；`close()` 标注 `@Throws(IOException)` 保留受检异常表面。
 */
class ClassFileManager(standardManager: StandardJavaFileManager) :
	ForwardingJavaFileManager<StandardJavaFileManager>(standardManager),
	Closeable {

	private val classLoader = DynamicClassLoader()

	fun getJavaFileObjectsFromFiles(sourceFiles: List<File>): List<JavaFileObject> {
		val list = ArrayList<JavaFileObject>()
		for (javaFileObject in fileManager.getJavaFileObjectsFromFiles(sourceFiles)) {
			list.add(javaFileObject)
		}
		return list
	}

	override fun getJavaFileForOutput(
		location: Location,
		className: String,
		kind: Kind,
		sibling: FileObject?,
	): JavaFileObject {
		val clsObject = JavaClassObject(className, kind)
		classLoader.add(className, clsObject)
		return clsObject
	}

	override fun getClassLoader(location: Location): ClassLoader = classLoader

	fun getClassLoader(): DynamicClassLoader = classLoader

	@Throws(IOException::class)
	override fun close() {
		fileManager.close()
	}
}
