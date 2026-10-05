package kadx.api.impl

import kadx.api.ICodeCache
import kadx.api.ICodeInfo
import java.io.IOException

/**
 * 代码缓存装饰器基类：把所有调用转发给内部的后备缓存 [backCache]。
 *
 * **做什么**：子类只需覆写自己关心的少数方法（如 GUI 的字符串缓存只覆写 `get/add`），
 * 其余行为直接委托。
 *
 * **为什么 `backCache` 用 `@JvmField protected`**：原 Java 是 `protected final` 字段，
 * 子类 `CodeStringCache` / `FixedCodeCache`（Java）会直接访问该字段，必须保持为字段。
 *
 * **为什么 close 带 `@Throws`**：`ICodeCache.close()` 声明了受检 `IOException`，
 * 保留后 Java 调用方仍需按原样处理异常。
 */
abstract class DelegateCodeCache(
	@JvmField protected val backCache: ICodeCache,
) : ICodeCache {

	override fun add(clsFullName: String, codeInfo: ICodeInfo) {
		backCache.add(clsFullName, codeInfo)
	}

	override fun remove(clsFullName: String) {
		backCache.remove(clsFullName)
	}

	override fun get(clsFullName: String): ICodeInfo = backCache.get(clsFullName)

	override fun getCode(clsFullName: String): String? = backCache.getCode(clsFullName)

	override fun contains(clsFullName: String): Boolean = backCache.contains(clsFullName)

	@Throws(IOException::class)
	override fun close() {
		backCache.close()
	}
}
