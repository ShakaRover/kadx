package jadx.api

import jadx.api.metadata.ICodeAnnotation
import jadx.api.metadata.ICodeNodeRef

/**
 * Java 侧节点接口：类、方法、字段、变量、包等对外暴露的“Java 视图”都实现它。
 *
 * 这是公共 API（插件与 GUI 会遍历这些节点），方法名与 JVM 签名必须保持不变。
 * 注意：这些方法全部保留为显式函数（而非 Kotlin 属性），以最大程度兼容 Java 调用方。
 */
interface JavaNode {

	/** 对应的内部代码节点引用（ClassNode/MethodNode/FieldNode/VarNode/PackageNode）。 */
	fun getCodeNodeRef(): ICodeNodeRef

	/** 名称（变量名可能为 null）。 */
	fun getName(): String?

	/** 全限定名。 */
	fun getFullName(): String

	/** 声明所在类（包节点返回 null）。 */
	fun getDeclaringClass(): JavaClass?

	/** 顶层父类（包节点返回 null）。 */
	fun getTopParentClass(): JavaClass?

	/** 定义位置（在反编译代码中的字符偏移）。 */
	fun getDefPos(): Int

	/** 该节点被哪些节点使用。 */
	fun getUseIn(): List<JavaNode>

	/** 移除别名（恢复原始名称）。 */
	fun removeAlias()

	/** 判断给定注解是否属于当前节点自身。 */
	fun isOwnCodeAnnotation(ann: ICodeAnnotation): Boolean
}
