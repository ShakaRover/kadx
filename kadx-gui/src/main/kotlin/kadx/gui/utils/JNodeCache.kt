package kadx.gui.utils

import kadx.api.JavaClass
import kadx.api.JavaField
import kadx.api.JavaMethod
import kadx.api.JavaNode
import kadx.api.JavaPackage
import kadx.api.JavaVariable
import kadx.api.metadata.ICodeNodeRef
import kadx.core.utils.exceptions.KadxRuntimeException
import kadx.gui.KadxWrapper
import kadx.gui.treemodel.JClass
import kadx.gui.treemodel.JField
import kadx.gui.treemodel.JMethod
import kadx.gui.treemodel.JNode
import kadx.gui.treemodel.JPackage
import kadx.gui.treemodel.JVariable
import java.util.concurrent.ConcurrentHashMap

/**
 * Java 节点（[JavaNode]）到界面树节点（[JNode]）的缓存。
 *
 * **做什么**：保证同一个 Java 节点只对应一个界面节点实例，避免树里出现重复节点。
 *
 * **为什么不是 `data class`**：持有缓存、以身份语义使用。
 *
 * **为什么没有容量上限（S9-P2 结论）**：本缓存承担的是**身份稳定性**契约 —— 同一个
 * [ICodeNodeRef] 必须始终映射到同一个 [JNode] 实例（树节点、选中与展开状态、结果表格行
 * 都按身份比较；`makeFrom` 里就靠 `getJavaNode()?.getCodeNodeRef() === nodeRef` 判断能否复用）。
 * 若改成 LRU 淘汰，被淘汰的节点再次访问会造出第二个实例，同一节点在树里出现两份、
 * 选中/展开状态错乱。而且淘汰也**省不下内存**：结果行（`ResultsModel.rows`）与树模型
 * 本身就持有这些 JNode 的强引用。
 * 因此这里不加上限；搜索路径的增长改由 [kadx.gui.search.SearchTask.MAX_RESULTS_LIMIT]
 * 在源头限制结果条数。
 */
class JNodeCache(private val wrapper: KadxWrapper) {

	private val cache: MutableMap<ICodeNodeRef, JNode> = ConcurrentHashMap()

	/** 由代码节点引用（[ICodeNodeRef]）构造界面节点。 */
	fun makeFrom(nodeRef: ICodeNodeRef?): JNode? {
		if (nodeRef == null) {
			return null
		}
		// 这里不能用 computeIfAbsent：convert() 可能递归访问缓存，会抛 'Recursive update' 异常
		var jNode = cache[nodeRef]
		if (jNode == null || jNode.getJavaNode()?.getCodeNodeRef() !== nodeRef) {
			val newNode = checkNotNull(convert(nodeRef))
			cache[nodeRef] = newNode
			jNode = newNode
		}
		return jNode
	}

	fun put(nodeRef: ICodeNodeRef, jNode: JNode) {
		cache[nodeRef] = jNode
	}

	fun put(javaNode: JavaNode, jNode: JNode) {
		cache[javaNode.getCodeNodeRef()] = jNode
	}

	/** 由 Java 节点构造界面节点。 */
	fun makeFrom(javaNode: JavaNode?): JNode? {
		if (javaNode == null) {
			return null
		}
		return makeFrom(javaNode.getCodeNodeRef())
	}

	/** 由 [JavaClass] 构造 [JClass]。 */
	fun makeFrom(javaCls: JavaClass?): JClass? {
		if (javaCls == null) {
			return null
		}
		val nodeRef = javaCls.getCodeNodeRef()
		var jCls = cache[nodeRef] as JClass?
		if (jCls == null || jCls.getCls() !== javaCls) {
			jCls = convert(javaCls)
			cache[nodeRef] = jCls
		}
		return jCls
	}

	/** 构造包节点并写入缓存。 */
	fun newJPackage(javaPkg: JavaPackage, synthetic: Boolean, pkgEnabled: Boolean, classes: List<JClass>): JPackage {
		val jPackage = JPackage(javaPkg, pkgEnabled, classes, ArrayList(), synthetic)
		put(javaPkg, jPackage)
		return jPackage
	}

	fun remove(javaNode: JavaNode) {
		cache.remove(javaNode.getCodeNodeRef())
	}

	/**
	 * 移除某个类及其成员在缓存中的节点。
	 *
	 * 注意：这里刻意先判断 [JavaClass.loadingWouldRequireDecompilation]，
	 * 因为若为了取成员而触发整类反编译，代价极高，而卸载类之后这些节点本来也会失效。
	 */
	fun removeWholeClass(javaCls: JavaClass) {
		remove(javaCls)
		if (!javaCls.loadingWouldRequireDecompilation()) {
			javaCls.getMethods().forEach { remove(it) }
			javaCls.getFields().forEach { remove(it) }
			javaCls.getInnerClasses().forEach { remove(it) }
			javaCls.getInlinedClasses().forEach { remove(it) }
		}
	}

	fun reset() {
		cache.clear()
	}

	private fun convert(cls: JavaClass): JClass {
		val parentCls = cls.declaringClass
		if (parentCls === cls) {
			return JClass(cls, null, this)
		}
		return JClass(cls, makeFrom(parentCls), this)
	}

	private fun convert(nodeRef: ICodeNodeRef): JNode? {
		val javaNode = wrapper.getDecompiler().getJavaNodeByRef(nodeRef)
		return convert(javaNode)
	}

	private fun convert(node: JavaNode?): JNode? {
		if (node == null) {
			return null
		}
		if (node is JavaClass) {
			return convert(node)
		}
		if (node is JavaMethod) {
			return JMethod(node, checkNotNull(makeFrom(node.declaringClass)))
		}
		if (node is JavaField) {
			return JField(node, checkNotNull(makeFrom(node.declaringClass)))
		}
		if (node is JavaVariable) {
			val jMth = makeFrom(node.getMth()) as JMethod
			return JVariable(jMth, node)
		}
		if (node is JavaPackage) {
			throw KadxRuntimeException("Unexpected JPackage (missing from cache): $node")
		}
		throw KadxRuntimeException("Unknown type for JavaNode: " + node.javaClass)
	}
}
