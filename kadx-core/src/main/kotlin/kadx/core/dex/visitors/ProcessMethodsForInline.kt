package kadx.core.dex.visitors

import kadx.core.Consts
import kadx.core.dex.attributes.AFlag
import kadx.core.dex.info.AccessInfo
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.nodes.RootNode
import kadx.core.dex.visitors.usage.UsageInfoVisitor
import kadx.core.utils.exceptions.KadxException

/**
 * 预先挑选“值得内联”的方法并打上标记。
 *
 * **做什么**：在用法分析（[UsageInfoVisitor]）之后运行；对每个类扫描方法，
 * 把 synthetic（编译器生成、名字含 `$`）的静态方法、或在开启匿名类内联时的构造器，
 * 标记为 [AFlag.METHOD_CANDIDATE_FOR_INLINE]，供后续 [MarkMethodsForInline] / [InlineMethods] 处理。
 * 同时调整类依赖，保证“被内联的方法所属类”先于其使用方生成代码。
 */
@KadxVisitor(
	name = "ProcessMethodsForInline",
	desc = "Mark methods for future inline",
	runAfter = [
		UsageInfoVisitor::class,
	],
)
class ProcessMethodsForInline : AbstractVisitor() {

	private var inlineMethods: Boolean = false

	override fun init(root: RootNode) {
		inlineMethods = root.getArgs().isInlineMethods
	}

	@Throws(KadxException::class)
	override fun visit(cls: ClassNode): Boolean {
		if (!inlineMethods) {
			return false
		}
		for (mth in cls.methods) {
			if (canInline(mth)) {
				mth.add(AFlag.METHOD_CANDIDATE_FOR_INLINE)
				fixClassDependencies(mth)
			}
		}
		return true
	}

	override fun getName(): String = "ProcessMethodsForInline"

	companion object {
		private fun canInline(mth: MethodNode): Boolean {
			if (mth.isNoCode() || mth.contains(AFlag.DONT_GENERATE)) {
				return false
			}
			val accessFlags = mth.accessFlags
			val isSynthetic = accessFlags.isSynthetic() || mth.name.contains("$")
			return isSynthetic && canInlineMethod(mth, accessFlags)
		}

		private fun canInlineMethod(mth: MethodNode, accessFlags: AccessInfo): Boolean {
			if (accessFlags.isStatic()) {
				return true
			}
			return mth.isConstructor() && mth.root().getArgs().isInlineAnonymousClasses
		}

		private fun fixClassDependencies(mth: MethodNode) {
			val parentClass = mth.topParentClass
			for (useInMth in mth.useIn) {
				// 移除可能的跨类依赖，强制“含内联方法的类”先于其使用方被处理
				val useTopCls = useInMth.topParentClass
				if (useTopCls !== parentClass) {
					parentClass.removeDependency(useTopCls)
					useTopCls.addCodegenDep(parentClass)
					if (Consts.DEBUG_USAGE) {
						parentClass.addDebugComment("Remove dependency: $useTopCls to inline $mth")
						useTopCls.addDebugComment("Add dependency: $parentClass to inline $mth")
					}
				}
			}
		}
	}
}
