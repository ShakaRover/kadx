package jadx.core.dex.visitors.fixaccessmodifiers

import jadx.api.plugins.input.data.AccessFlags
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.nodes.NotificationAttrNode
import jadx.core.dex.info.AccessInfo
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.dex.visitors.AbstractVisitor
import jadx.core.dex.visitors.JadxVisitor
import jadx.core.dex.visitors.ModVisitor
import jadx.core.utils.exceptions.JadxException
import java.util.HashSet

/**
 * 修正类与方法的访问修饰符，保证反编译结果可编译。
 *
 * **做什么**：当某个类/方法被其它类使用时，如果其可见性太窄（private/package-private/
 * protected 且调用方不是子类），就把它提升到足够宽（public/protected）。
 * 也保证顶层类至少是 public。若开启“尊重字节码访问修饰符”则跳过。
 *
 * **为什么**：原始字节码的访问标志在反编译后可能不再合法（例如内联、桥接合并
 * 改变了调用关系），必须按实际使用关系放宽，否则生成的 Java 代码无法编译。
 *
 * **Kotlin 转换说明**：[changeVisibility] 是公共静态方法，放 companion + `@JvmStatic`；
 * stream 收集改为普通循环 + HashSet；引用比较用 `!==`。
 */
@JadxVisitor(
	name = "FixAccessModifiers",
	desc = "Change class and method access modifiers if needed",
	runAfter = [ModVisitor::class],
)
class FixAccessModifiers : AbstractVisitor() {

	private lateinit var visibilityUtils: VisibilityUtils

	private var respectAccessModifiers = false

	override fun init(root: RootNode) {
		this.visibilityUtils = VisibilityUtils(root)
		this.respectAccessModifiers = root.getArgs().isRespectBytecodeAccModifiers
	}

	@Throws(JadxException::class)
	override fun visit(cls: ClassNode): Boolean {
		if (respectAccessModifiers) {
			return true
		}
		fixClassVisibility(cls)
		return true
	}

	override fun visit(mth: MethodNode) {
		if (respectAccessModifiers || mth.contains(AFlag.DONT_GENERATE)) {
			return
		}
		fixMethodVisibility(mth)
	}

	private fun fixClassVisibility(cls: ClassNode) {
		val accessFlags = cls.accessFlags
		if (cls.isTopClass() && accessFlags.isPublic()) {
			return
		}

		if (cls.isTopClass() && (accessFlags.isPrivate() || accessFlags.isProtected())) {
			changeVisibility(cls, AccessFlags.PUBLIC)
			return
		}

		for (useCls in cls.useIn) {
			visibilityUtils.checkVisibility(cls, useCls) { node, visFlag ->
				changeVisibility(node as NotificationAttrNode, visFlag)
			}
		}

		for (useMth in cls.useInMth) {
			val inlineAttr = useMth.get(AType.METHOD_INLINE)
			val isInline = inlineAttr != null && !inlineAttr.notNeeded()
			val isCandidateForInline = useMth.contains(AFlag.METHOD_CANDIDATE_FOR_INLINE)

			if (isInline || isCandidateForInline) {
				val usedInClss = HashSet<ClassNode>()
				for (uMth in useMth.useIn) {
					usedInClss.add(uMth.parentClass)
				}

				for (useCls in usedInClss) {
					visibilityUtils.checkVisibility(cls, useCls) { node, visFlag ->
						changeVisibility(node as NotificationAttrNode, visFlag)
					}
				}
			}
		}
	}

	private fun fixMethodVisibility(mth: MethodNode) {
		val accessFlags = mth.accessFlags
		val overrideAttr = mth.get(AType.METHOD_OVERRIDE)
		if (overrideAttr != null && overrideAttr.overrideList.isNotEmpty()) {
			// 可见性不能比父类方法更窄
			val parentMD = overrideAttr.overrideList[0]
			val parentAccInfo = AccessInfo(parentMD.rawAccessFlags, AccessInfo.AFType.METHOD)
			if (accessFlags.isVisibilityWeakerThan(parentAccInfo)) {
				changeVisibility(mth, parentAccInfo.visibility.rawValue())
			}
		}

		for (useMth in mth.useIn) {
			visibilityUtils.checkVisibility(mth, useMth) { node, visFlag ->
				changeVisibility(node as NotificationAttrNode, visFlag)
			}
		}
	}

	companion object {
		/** 把节点的可见性改为 [newVisFlag]，并记录一条变更注释。 */
		fun changeVisibility(node: NotificationAttrNode, newVisFlag: Int) {
			val accessFlags = node.accessFlags
			val newAccFlags = accessFlags.changeVisibility(newVisFlag)
			if (newAccFlags !== accessFlags) {
				node.accessFlags = newAccFlags
				node.addInfoComment("Access modifiers changed from: " + accessFlags.visibilityName())
			}
		}
	}
}
