package kadx.api

import kadx.api.metadata.ICodeAnnotation
import kadx.api.metadata.ICodeNodeRef

/**
 * Java 侧节点接口：类、方法、字段、变量、包等对外暴露的“Java 视图”都实现它。
 *
 * 这是公共 API（插件与 GUI 会遍历这些节点），方法名与 JVM 签名必须保持不变。
 * 接口成员以 Kotlin 属性形式声明时，JVM 上仍生成 `getXxx()`，Java 实现/调用方零改动。
 */
interface JavaNode {

	/** 对应的内部代码节点引用（ClassNode/MethodNode/FieldNode/VarNode/PackageNode）。 */
	fun getCodeNodeRef(): ICodeNodeRef

	/** 名称（变量名可能为 null）。 */
	fun getName(): String?

	/** 全限定名。 */
	fun getFullName(): String

	/** 声明所在类（包节点返回 null）。 */
	val declaringClass: JavaClass?

	/** 顶层父类（包节点返回 null）。 */
	fun getTopParentClass(): JavaClass?

	/** 定义位置（在反编译代码中的字符偏移）。 */
	fun getDefPos(): Int

	/** 该节点被哪些节点使用。 */
	val useIn: List<JavaNode>

	/** 移除别名（恢复原始名称）。 */
	fun removeAlias()

	/** 判断给定注解是否属于当前节点自身。 */
	fun isOwnCodeAnnotation(ann: ICodeAnnotation): Boolean
}
