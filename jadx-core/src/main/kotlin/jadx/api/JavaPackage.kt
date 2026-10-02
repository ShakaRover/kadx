package jadx.api

import jadx.api.metadata.ICodeAnnotation
import jadx.api.metadata.ICodeNodeRef
import jadx.core.dex.info.PackageInfo
import jadx.core.dex.nodes.PackageNode
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NotNull
import java.util.ArrayList
import java.util.Objects

/**
 * 包的 Java 视图：把内部 [PackageNode] 包装成对插件/GUI 友好的对象。
 *
 * 公共 API；getter 保留显式函数形态。禁止改成 `data class`（引用语义 + 自定义 equals/hashCode）。
 * 包节点在树中通过父子引用相连，因此 equals 只比较底层 [PackageNode]。
 */
class JavaPackage internal constructor(
	private val pkgNode: PackageNode,
	private val classes: List<JavaClass>,
	private val clsListNoDup: List<JavaClass>,
	private val subPkgs: List<JavaPackage>,
) : JavaNode,
	Comparable<JavaPackage> {

	/** 只有子包列表、没有去重类列表时使用（去重列表默认与普通列表相同）。 */
	internal constructor(pkgNode: PackageNode, classes: List<JavaClass>, subPkgs: List<JavaPackage>) :
		this(pkgNode, classes, classes, subPkgs)

	override fun getName(): String = pkgNode.getAliasPkgInfo().name

	override fun getFullName(): String = pkgNode.getAliasPkgInfo().fullName

	/** 原始（未去混淆）包名。 */
	fun getRawName(): String = pkgNode.getPkgInfo().name

	/** 原始（未去混淆）包全名。 */
	fun getRawFullName(): String = pkgNode.getPkgInfo().fullName

	fun getSubPackages(): List<JavaPackage> = subPkgs

	fun getClasses(): List<JavaClass> = classes

	fun getClassesNoDup(): List<JavaClass> = clsListNoDup

	fun isRoot(): Boolean = pkgNode.isRoot()

	fun isLeaf(): Boolean = pkgNode.isLeaf()

	fun isDefault(): Boolean = getFullName().isEmpty()

	fun rename(alias: String) {
		pkgNode.rename(alias)
	}

	override fun removeAlias() {
		pkgNode.removeAlias()
	}

	/** 父包是否被重命名过。 */
	fun isParentRenamed(): Boolean {
		val parent: PackageInfo? = pkgNode.getPkgInfo().parentPkg
		val aliasParent: PackageInfo? = pkgNode.getAliasPkgInfo().parentPkg
		return !Objects.equals(parent, aliasParent)
	}

	/** 当前包是否为 [ancestor] 的后代（含自身）。 */
	fun isDescendantOf(ancestor: JavaPackage): Boolean {
		var current: JavaPackage? = this
		while (current != null) {
			if (ancestor == current) {
				return true
			}
			val parentPkg = current.getPkgNode().getParentPkg()
			current = parentPkg?.javaNode
		}
		return false
	}

	override fun getCodeNodeRef(): ICodeNodeRef = pkgNode

	@ApiStatus.Internal
	fun getPkgNode(): PackageNode = pkgNode

	override fun getDeclaringClass(): JavaClass? = null

	override fun getTopParentClass(): JavaClass? = null

	override fun getDefPos(): Int = 0

	override fun getUseIn(): List<JavaNode> {
		val list = ArrayList<JavaNode>()
		addUseIn(list)
		return list
	}

	/** 递归收集本包及其子包下的所有类。 */
	fun addUseIn(list: MutableList<JavaNode>) {
		list.addAll(classes)
		for (subPkg in subPkgs) {
			subPkg.addUseIn(list)
		}
	}

	override fun isOwnCodeAnnotation(ann: ICodeAnnotation): Boolean = false

	override fun compareTo(other: @NotNull JavaPackage): Int = pkgNode.compareTo(other.pkgNode)

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other == null || javaClass != other.javaClass) {
			return false
		}
		val that = other as JavaPackage
		return pkgNode == that.pkgNode
	}

	override fun hashCode(): Int = pkgNode.hashCode()

	override fun toString(): String = pkgNode.toString()
}
