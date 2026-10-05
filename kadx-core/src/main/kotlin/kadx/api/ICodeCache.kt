package kadx.api

import java.io.Closeable
import java.io.IOException

/**
 * 反编译代码缓存接口：按类的全限定名缓存 [ICodeInfo]。
 *
 * 这是公共 API，会被 kadx-cli / kadx-gui / 插件以 Java 或 Kotlin 实现，因此：
 * - 保持接口（而非抽象类），方法签名与 JVM 名完全不变；
 * - 继承 [Closeable]，`close()` 保留 `IOException` 受检异常声明，Java 调用方行为不变。
 */
interface ICodeCache : Closeable {

	/** 添加一个类的代码缓存。 */
	fun add(clsFullName: String, codeInfo: ICodeInfo)

	/** 移除一个类的代码缓存。 */
	fun remove(clsFullName: String)

	/** 获取代码信息；按接口约定不会返回 null（实现可抛异常）。 */
	fun get(clsFullName: String): ICodeInfo

	/** 获取代码字符串；缓存不存在时返回 null。 */
	fun getCode(clsFullName: String): String?

	/** 是否包含指定类的缓存。 */
	fun contains(clsFullName: String): Boolean

	/**
	 * 关闭缓存并释放资源。
	 *
	 * 原接口继承 `Closeable`，`close()` 会抛出 `IOException`；用 `@Throws` 保留该受检异常，
	 * 保证 Java 调用方仍需按原样处理异常。
	 */
	@Throws(IOException::class)
	override fun close()
}
