package jadx.core.dex.visitors

import jadx.core.dex.attributes.AType
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.MethodNode

/**
 * 深度优先遍历工具：把一个 [IDexTreeVisitor] 施加到类/方法树上的统一入口。
 *
 * **做什么**：先访问类本身，若其返回 true，再递归访问内部类，最后访问类中的方法。
 *
 * **为什么这么写**：反编译某个类时，任何一个 Pass 出错都不应让整个反编译崩溃。
 * 因此这里捕获三类异常（栈溢出、BootstrapMethodError、普通异常），把错误信息
 * 挂到对应的类/方法节点上，继续处理其余节点。
 *
 * **Kotlin 转换说明**：原 Java 是多 catch（`StackOverflowError | BootstrapMethodError | Exception`），
 * Kotlin 无多 catch 语法，改为三个顺序 catch（三者类型互不包含，语义等价）。
 * 本类只有静态方法，故声明为 `object` 单例；`@JvmStatic` 保证 Java 侧 `DepthTraversal.visit(...)` 不变。
 */
object DepthTraversal {

	@JvmStatic
	fun visit(visitor: IDexTreeVisitor, cls: ClassNode) {
		try {
			if (visitor.visit(cls)) {
				for (inCls in cls.innerClasses) {
					visit(visitor, inCls)
				}
				for (mth in cls.methods) {
					visit(visitor, mth)
				}
			}
		} catch (e: StackOverflowError) {
			cls.addError(e.javaClass.simpleName + " in pass: " + visitor.javaClass.simpleName, e)
		} catch (e: BootstrapMethodError) {
			cls.addError(e.javaClass.simpleName + " in pass: " + visitor.javaClass.simpleName, e)
		} catch (e: Exception) {
			cls.addError(e.javaClass.simpleName + " in pass: " + visitor.javaClass.simpleName, e)
		}
	}

	@JvmStatic
	fun visit(visitor: IDexTreeVisitor, mth: MethodNode) {
		try {
			if (mth.contains(AType.JADX_ERROR)) {
				return
			}
			visitor.visit(mth)
		} catch (e: StackOverflowError) {
			mth.addError(e.javaClass.simpleName + " in pass: " + visitor.javaClass.simpleName, e)
		} catch (e: BootstrapMethodError) {
			mth.addError(e.javaClass.simpleName + " in pass: " + visitor.javaClass.simpleName, e)
		} catch (e: Exception) {
			mth.addError(e.javaClass.simpleName + " in pass: " + visitor.javaClass.simpleName, e)
		}
	}
}
