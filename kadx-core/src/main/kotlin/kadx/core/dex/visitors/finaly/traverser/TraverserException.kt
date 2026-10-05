package kadx.core.dex.visitors.finaly.traverser

/**
 * finally 遍历过程中可预期的错误（受检异常）。
 *
 * **为什么是受检异常**：遍历算法在遇到无法解析的块/状态时，调用方（finally 恢复逻辑）
 * 需要显式处理并回退，而不是让整个反编译崩溃。
 *
 * **Kotlin 转换说明**：`open` 是因为 Java 侧有子类
 * `InstructionActivePathTraverserHandler.UnresolvableBlockException` 继承它。
 */
open class TraverserException(msg: String) : Exception(msg)
