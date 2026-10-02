package jadx.tests.api.compiler

import java.security.SecureClassLoader
import java.util.concurrent.ConcurrentHashMap

/**
 * 从内存中已编译的 [JavaClassObject] 加载类的类加载器。
 *
 * **做什么**：把编译器产出的 class 字节缓存到 [clsMap]，首次加载时用
 * [SecureClassLoader.defineClass] 定义，并缓存已加载的 [Class]（[clsCache]）。
 *
 * **为什么重写 `loadClass`**：测试需要优先使用内存中的新编译结果，避免父加载器
 * 命中同名旧类。原 Java 的 `findClass`/`loadClass` 覆写语义在此原样保留。
 */
class DynamicClassLoader : SecureClassLoader() {

	private val clsMap = ConcurrentHashMap<String, JavaClassObject>()
	private val clsCache = ConcurrentHashMap<String, Class<*>>()

	fun add(className: String, clsObject: JavaClassObject) {
		clsMap[className] = clsObject
	}

	override fun findClass(name: String): Class<*> {
		val cls = replaceClass(name)
		if (cls != null) {
			return cls
		}
		return super.findClass(name)
	}

	override fun loadClass(name: String): Class<*> {
		val cls = replaceClass(name)
		if (cls != null) {
			return cls
		}
		return super.loadClass(name)
	}

	/** 若 [name] 对应内存中的 class 字节则定义并返回，否则返回 null。 */
	fun replaceClass(name: String): Class<*>? {
		val cacheCls = clsCache[name]
		if (cacheCls != null) {
			return cacheCls
		}
		val clsObject = clsMap[name] ?: return null
		val clsBytes = clsObject.getBytes()
		val cls = super.defineClass(name, clsBytes, 0, clsBytes.size)
		clsCache[name] = cls
		return cls
	}

	fun getClassObjects(): Collection<JavaClassObject> = clsMap.values
}
