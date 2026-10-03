package jadx.gui.cache.code.disk.adapters

import java.io.DataInput
import java.io.DataOutput
import java.io.IOException

/**
 * 代码缓存的二进制读写适配器。
 *
 * **做什么**：为某一种值类型 [T] 提供“写入 [DataOutput] / 从 [DataInput] 读出”的能力，
 * 供 `CodeMetadataAdapter` 在持久化代码元数据时按类型分派。
 *
 * **泛型与可空性说明**：原 Java 的 `T` 是引用类型，允许 `null`（例如空类型、空注解）。
 * Kotlin 侧把需要支持 `null` 的实现声明为 `DataAdapter<X?>`，从而在类型系统里显式表达。
 *
 * **为什么保留 `@Throws`**：保持与原 Java 接口一致的 JVM `throws IOException` 声明。
 */
interface DataAdapter<T> {

	@Throws(IOException::class)
	fun write(out: DataOutput, value: T)

	@Throws(IOException::class)
	fun read(input: DataInput): T
}
