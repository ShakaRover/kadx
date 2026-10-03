package jadx.core.dex.regions

import jadx.core.dex.attributes.AttrNode
import jadx.core.dex.nodes.IContainer
import jadx.core.dex.nodes.IRegion
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 所有“区域（Region）”的公共基类。
 *
 * **什么是 Region？** 反编译器把方法体从“基本块（Block）图”提升为“区域树”：
 * 每个区域代表一段结构化代码（顺序块、if、loop、switch、try/catch、synchronized…）。
 * 区域之间通过 [parent] 形成父子关系，子区域列表由 [subBlocks] 给出。
 *
 * 本类只提供两件通用能力：
 * 1. 维护父区域指针 [parent]（[IRegion] 接口声明为可变属性，因此这里用属性覆写）；
 * 2. [updateParent]：把一个子容器挂到新的父区域上。
 *
 * Kotlin 转换说明：
 * - [IRegion] 是 Kotlin 接口且把 `parent` 声明为 `var`，所以这里必须用属性覆写
 *   （不能用“私有字段 + 显式 getParent/setParent”，否则无法覆写 Kotlin 属性）；
 * - 生成的 JVM 方法名依旧是 `getParent()/setParent()`，Java 调用方零改动。
 */
abstract class AbstractRegion(parent: IRegion?) :
	AttrNode(),
	IRegion {

	/** 父区域；根区域为 null */
	override var parent: IRegion? = parent

	/**
	 * 默认不支持替换子块：只有真正管理子块的区域（如 [Region]、[jadx.core.dex.regions.conditions.IfRegion]）
	 * 才覆写本方法。这里打印一条告警并返回 false，便于定位异常调用。
	 */
	override fun replaceSubBlock(oldBlock: IContainer, newBlock: IContainer): Boolean {
		LOG.warn("Replace sub block not supported for class \"{}\"", this.javaClass)
		return false
	}

	/**
	 * 若 [container] 本身是一个区域，则把它的父指针指向 [newParent]。
	 *
	 * 为什么需要它？区域树重排（替换/合并子区域）时，父指针也必须同步更新，
	 * 否则后续按父指针回溯会走到错误的树上。
	 */
	fun updateParent(container: IContainer, newParent: IRegion) {
		if (container is IRegion) {
			container.parent = newParent
		}
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(AbstractRegion::class.java)
	}
}
