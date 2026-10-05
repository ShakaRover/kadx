package kadx.core.dex.visitors.fixaccessmodifiers

import kadx.api.plugins.input.data.AccessFlags
import kadx.core.dex.info.AccessInfo
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.ICodeNode
import kadx.core.dex.nodes.RootNode
import kadx.core.utils.exceptions.KadxRuntimeException

/**
 * 可见性检查工具：判断某个节点（类/方法/字段）被另一个类使用时，
 * 其访问修饰符是否足够“宽”（否则调用方无法访问，需要提升可见性）。
 *
 * **规则概述**：
 * - 同一顶层类内部：无需处理；
 * - 同一包内：private 成员需要提升为 package-private；
 * - 跨包：private/package-private 需要提升为 public（若调用方是其子类则 protected）；
 *   protected 成员若调用方不是子类也需要提升为 public。
 *
 * **Kotlin 转换说明**：原 Java 为 package-private，Kotlin 用 `internal`；
 * 回调接口 [OnBadVisibilityCallback] 转为 `fun interface` 以便 lambda 调用；
 * `do-while` 遍历用 `while` 等价改写，避免 `!!`。
 */
internal class VisibilityUtils(private val root: RootNode) {

	fun checkVisibility(targetNode: ICodeNode, callerNode: ICodeNode, callback: OnBadVisibilityCallback) {
		val targetCls = if (targetNode is ClassNode) targetNode else checkNotNull(targetNode.declaringClass)
		val callerCls = if (callerNode is ClassNode) callerNode else checkNotNull(callerNode.declaringClass)

		if (targetCls == callerCls || inSameTopClass(targetCls, callerCls)) {
			return
		}

		if (inSamePkg(targetCls, callerCls)) {
			visitDeclaringNodes(targetNode) { node ->
				if (node.accessFlags.isPrivate()) {
					callback.onBadVisibility(node, 0) // PACKAGE_PRIVATE
				}
			}
		} else {
			visitDeclaringNodes(targetNode) { node ->
				val nodeVisFlags = node.accessFlags.visibility
				if (nodeVisFlags.isPublic()) {
					return@visitDeclaringNodes
				}

				if (nodeVisFlags.isPrivate() || nodeVisFlags.isPackagePrivate()) {
					val nodeDeclaringCls = node.declaringClass
					val expectedVisFlag = if (nodeDeclaringCls != null && isSuperType(callerCls, nodeDeclaringCls)) {
						AccessFlags.PROTECTED
					} else {
						AccessFlags.PUBLIC
					}
					callback.onBadVisibility(node, expectedVisFlag)
				} else if (nodeVisFlags.isProtected()) {
					val nodeDeclaringCls = node.declaringClass
					if (nodeDeclaringCls == null || !isSuperType(callerCls, nodeDeclaringCls)) {
						callback.onBadVisibility(node, AccessFlags.PUBLIC)
					}
				} else {
					throw KadxRuntimeException("$nodeVisFlags is not supported")
				}
			}
		}
	}

	/** 从目标节点开始，沿声明类链逐层应用 [action]。 */
	private fun visitDeclaringNodes(targetNode: ICodeNode, action: (ICodeNode) -> Unit) {
		var currentNode: ICodeNode? = targetNode
		while (currentNode != null) {
			action(currentNode)
			currentNode = currentNode.declaringClass
		}
	}

	private fun inSamePkg(cls1: ClassNode, cls2: ClassNode): Boolean = cls1.packageNode == cls2.packageNode

	private fun inSameTopClass(cls1: ClassNode, cls2: ClassNode): Boolean = cls1.topParentClass == cls2.topParentClass

	private fun isSuperType(cls: ClassNode, superCls: ClassNode): Boolean = checkNotNull(root.getClsp()).getSuperTypes(cls.rawName).any { it == superCls.rawName }

	/** 可见性不足时的回调。 */
	internal fun interface OnBadVisibilityCallback {
		fun onBadVisibility(node: ICodeNode, expectedVisFlag: Int)
	}
}
