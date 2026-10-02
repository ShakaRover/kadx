package jadx.core.dex.visitors.finaly.traverser.handlers

/**
 * 遍历处理器（handler）的公共基类。
 *
 * **作用**：finally 重复指令搜索被拆成多个“处理器”，每个处理器负责一小步
 * （收集块信息、比较指令、查找前驱、合并路径等）。它们都以本类为根，
 * 便于遍历控制器用类型判断统一分发。
 *
 * **Kotlin 转换说明**：原 Java 只有类声明、没有成员，此处原样保留为
 * 抽象类，保证 [AbstractBlockPathTraverserHandler] 与
 * [AbstractActivePathTraverserHandler] 两条继承链不变。
 */
abstract class AbstractBlockTraverserHandler
