package kadx.core.utils

/**
 * 反编译过程中挂在 [kadx.core.dex.nodes.RootNode] 上的缓存容器。
 *
 * **用途**：目前只缓存顶层包名集合（`RenameVisitor` 计算后写入，`NameGen` 读取，
 * 用于避免变量名与包名冲突）。
 *
 * **Kotlin 转换说明**：`var rootPkgs` 会生成 `getRootPkgs()/setRootPkgs()`，
 * 与 Java 调用方保持完全兼容。
 */
class CacheStorage {

	/** 顶层包名集合，默认空集合（原 Java 用 `Collections.emptySet()`） */
	var rootPkgs: Set<String> = emptySet()
}
