package kadx.core.dex.visitors

import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.nodes.RootNode
import kadx.core.utils.exceptions.KadxException

/**
 * 访问者抽象基类：为 [IDexTreeVisitor] 提供全部空实现。
 *
 * **做什么**：子类只需覆写自己关心的回调（通常是 [visit]），无需实现全部方法。
 * [getName] 默认取运行时的简单类名，[toString] 直接返回该名字，方便日志输出。
 *
 * **为什么保留 `@Throws` 与 `open`**：仓库里大量 Java 访问者继承本类并覆写
 * `visit(...) throws KadxException`，因此基类方法必须把该受检异常写入签名；
 * Kotlin 的 `override` 成员默认就是 `open`，Java 子类可继续覆写。
 */
abstract class AbstractVisitor : IDexTreeVisitor {

	@Throws(KadxException::class)
	override fun init(root: RootNode) {
		// 默认无操作
	}

	@Throws(KadxException::class)
	override fun visit(cls: ClassNode): Boolean {
		// 默认无操作，继续遍历子节点
		return true
	}

	@Throws(KadxException::class)
	override fun visit(mth: MethodNode) {
		// 默认无操作
	}

	override fun getName(): String = javaClass.simpleName

	override fun toString(): String = getName()
}
