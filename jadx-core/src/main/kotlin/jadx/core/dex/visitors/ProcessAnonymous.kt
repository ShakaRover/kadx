package jadx.core.dex.visitors

import jadx.api.plugins.input.data.AccessFlags
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.nodes.AnonymousClassAttr
import jadx.core.dex.attributes.nodes.AnonymousClassAttr.InlineType
import jadx.core.dex.info.AccessInfo
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.dex.visitors.usage.UsageInfoVisitor
import jadx.core.utils.ListUtils
import jadx.core.utils.exceptions.JadxException
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.HashSet

/**
 * 标记匿名类与 lambda 类（供后续内联）。
 *
 * **做什么**：找出“只被使用一次、只有一个构造器、外部没有其它引用”的匿名类，
 * 打上 [AnonymousClassAttr] 与 [AFlag.DONT_GENERATE]，并调整类依赖，
 * 让外部类在生成代码时可以直接内联这些匿名类。
 *
 * **为什么需要**：反编译结果里大量 `new Runnable() { ... }` 会被 DEX 编译成独立类，
 * 本 Pass 负责把它们“还原”回匿名内部类/lambda 形式。
 */
@JadxVisitor(
	name = "ProcessAnonymous",
	desc = "Mark anonymous and lambda classes (for future inline)",
	runAfter = [
		UsageInfoVisitor::class,
	],
)
class ProcessAnonymous : AbstractVisitor() {

	private var inlineAnonymousClasses: Boolean = false

	override fun init(root: RootNode) {
		inlineAnonymousClasses = root.getArgs().isInlineAnonymousClasses
		if (!inlineAnonymousClasses) {
			return
		}
		for (cls in root.getClasses()) {
			processClass(cls)
		}
		mergeAnonymousDeps(root)
	}

	@Throws(JadxException::class)
	override fun visit(cls: ClassNode): Boolean {
		if (inlineAnonymousClasses && cls.contains(AFlag.CLASS_UNLOADED)) {
			// 仅在类重新加载时进入
			visitClassAndInners(cls)
		}
		return false
	}

	override fun getName(): String = "ProcessAnonymous"

	private fun visitClassAndInners(cls: ClassNode) {
		processClass(cls)
		for (innerCls in cls.innerClasses) {
			visitClassAndInners(innerCls)
		}
	}

	private fun mergeAnonymousDeps(root: RootNode) {
		// 收集边构建双向树：
		// inline 边：anonymous -> outer（一对一）
		// use 边：outer -> *anonymous（一对多）
		val inlineMap = HashMap<ClassNode, ClassNode>()
		val useMap = HashMap<ClassNode, MutableList<ClassNode>>()
		for (anonymousCls in root.getClasses()) {
			val attr = anonymousCls.get(AType.ANONYMOUS_CLASS)
			if (attr != null) {
				val outerCls = attr.outerCls
				var list = useMap[outerCls]
				if (list == null || list.isEmpty()) {
					list = ArrayList(2)
					useMap[outerCls] = list
				}
				list.add(anonymousCls)
				if (!useMap.containsKey(anonymousCls)) {
					useMap[anonymousCls] = ArrayList() // 显式放入叶子
				}
				inlineMap[anonymousCls] = outerCls
			}
		}
		if (inlineMap.isEmpty()) {
			return
		}
		// 从叶子开始向上处理依赖直到根
		val added = HashSet<ClassNode>()
		for ((key, list) in useMap) {
			if (list.isEmpty()) {
				added.clear()
				updateDeps(key, inlineMap, added)
			}
		}
		for (cls in root.getClasses()) {
			val deps = cls.codegenDeps
			if (deps.size > 1) {
				// 去重并排序依赖，复用集合以减少内存分配 :)
				added.clear()
				added.addAll(deps)
				val mutableDeps = deps as MutableList<ClassNode>
				mutableDeps.clear()
				mutableDeps.addAll(added)
				Collections.sort(mutableDeps)
			}
		}
	}

	private fun updateDeps(leafCls: ClassNode, inlineMap: Map<ClassNode, ClassNode>, added: MutableSet<ClassNode>) {
		val topNode: ClassNode
		var current = leafCls
		while (true) {
			if (!added.add(current)) {
				current.addWarnComment("Loop in anonymous inline: $current, path: $added")
				for (c in added) {
					undoAnonymousMark(c)
				}
				return
			}
			val next = inlineMap[current]
			if (next == null) {
				topNode = current.topParentClass
				break
			}
			current = next
		}
		if (added.size <= 2) {
			// 第一层依赖已处理
			return
		}
		var deps = topNode.codegenDeps
		if (deps.isEmpty()) {
			deps = ArrayList(added.size)
			topNode.codegenDeps = deps
		}
		val mutableDeps = deps as MutableList<ClassNode>
		for (add in added) {
			mutableDeps.add(add.topParentClass)
		}
	}

	companion object {
		private fun processClass(cls: ClassNode) {
			try {
				markAnonymousClass(cls)
			} catch (e: StackOverflowError) {
				cls.addError("Anonymous visitor error", e)
			} catch (e: Exception) {
				cls.addError("Anonymous visitor error", e)
			}
		}

		private fun markAnonymousClass(cls: ClassNode) {
			if (!canBeAnonymous(cls)) {
				return
			}
			val anonymousConstructor = ListUtils.filterOnlyOne(cls.methods) { it.isConstructor() } ?: return
			val inlineType = checkUsage(cls, anonymousConstructor) ?: return
			val baseType = getBaseType(cls) ?: return
			val outerCls: ClassNode
			if (inlineType == InlineType.INSTANCE_FIELD) {
				outerCls = cls.useInMth[0].parentClass
			} else {
				outerCls = anonymousConstructor.getUseIn()[0].parentClass
			}
			outerCls.addInlinedClass(cls)
			cls.addAttr(AnonymousClassAttr(outerCls, baseType, inlineType))
			cls.add(AFlag.DONT_GENERATE)
			anonymousConstructor.add(AFlag.ANONYMOUS_CONSTRUCTOR)

			// 强制匿名类先于外部类处理，外部类的实际使用会在匿名类处理时移除，
			// 见 ModVisitor.processAnonymousConstructor
			val topOuterCls = outerCls.topParentClass
			cls.removeDependency(topOuterCls)
			ListUtils.safeRemove(outerCls.useIn, cls)

			// 把依赖移到 codegen 阶段
			if (cls.isTopClass()) {
				topOuterCls.removeDependency(cls)
				topOuterCls.addCodegenDep(cls)
			}
		}

		private fun undoAnonymousMark(cls: ClassNode) {
			val attr = checkNotNull(cls.get(AType.ANONYMOUS_CLASS))
			val outerCls = attr.outerCls
			cls.dependencies = ListUtils.safeAdd(cls.dependencies, outerCls.topParentClass)
			outerCls.useIn = ListUtils.safeAdd(outerCls.useIn, cls)

			cls.remove(AType.ANONYMOUS_CLASS)
			cls.remove(AFlag.DONT_GENERATE)
			for (mth in cls.methods) {
				if (mth.isConstructor()) {
					mth.remove(AFlag.ANONYMOUS_CONSTRUCTOR)
				}
			}
			cls.addDebugComment("Anonymous mark cleared")
		}

		private fun canBeAnonymous(cls: ClassNode): Boolean {
			if (cls.accessFlags.isSynthetic()) {
				return true
			}
			val shortName = cls.classInfo.shortName
			if (shortName.contains("$") || Character.isDigit(shortName[0])) {
				return true
			}
			if (cls.useIn.size == 1 && cls.useInMth.size == 1) {
				val useMth = cls.useInMth[0]
				// 允许在枚举类 init 中使用
				return useMth.getMethodInfo().isClassInit() && useMth.parentClass.isEnum()
			}
			return false
		}

		/**
		 * 检查：
		 * - 类只有一个构造器且只用一次（允许字段初始化的公共代码）；
		 * - 方法/字段未在外部使用（仅允许内部类中的 synthetic 使用）；
		 * - 若构造器只在类初始化中使用，检查能否按实例字段内联。
		 *
		 * @return 决定的内联方式
		 */
		private fun checkUsage(cls: ClassNode, ctr: MethodNode): InlineType? {
			if (ctr.getUseIn().size != 1) {
				// 检查是否在所有构造器的公共字段初始化中使用
				if (!checkForCommonFieldInit(ctr)) {
					return null
				}
			}
			val ctrUseMth = ctr.getUseIn()[0]
			val ctrUseCls = ctrUseMth.parentClass
			if (ctrUseCls == cls) {
				if (checkForInstanceFieldUsage(cls, ctr)) {
					return InlineType.INSTANCE_FIELD
				}
				// 排除自使用
				return null
			}
			if (ctrUseCls.topParentClass == cls) {
				// 排除内部类中的使用
				return null
			}
			if (!checkMethodsUsage(cls, ctr, ctrUseMth)) {
				return null
			}
			for (field in cls.fields) {
				for (useMth in field.getUseIn()) {
					if (badMethodUsage(cls, useMth, field.accessFlags)) {
						return null
					}
				}
			}
			return InlineType.CONSTRUCTOR
		}

		private fun checkMethodsUsage(cls: ClassNode, ctr: MethodNode, ctrUseMth: MethodNode): Boolean {
			for (mth in cls.methods) {
				if (mth === ctr) {
					continue
				}
				for (useMth in mth.getUseIn()) {
					if (useMth == ctrUseMth) {
						continue
					}
					if (badMethodUsage(cls, useMth, mth.accessFlags)) {
						return false
					}
				}
			}
			return true
		}

		private fun checkForInstanceFieldUsage(cls: ClassNode, ctr: MethodNode): Boolean {
			val ctrUseMth = ctr.getUseIn()[0]
			if (!ctrUseMth.getMethodInfo().isClassInit()) {
				return false
			}
			if (cls.useInMth.isEmpty()) {
				// 无外部使用，无需内联
				return false
			}
			val instFld = ListUtils.filterOnlyOne(cls.fields) { f ->
				f.accessFlags.containsFlags(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL) &&
					f.fieldInfo.type == cls.classInfo.type
			} ?: return false
			val instFldUseIn = instFld.getUseIn()
			if (instFldUseIn.size != 2 ||
				!instFldUseIn.contains(ctrUseMth) || // 在类初始化中初始化
				!instFldUseIn.containsAll(cls.useInMth) // 类仅通过该字段使用
			) {
				return false
			}
			if (!checkMethodsUsage(cls, ctr, ctrUseMth)) {
				return false
			}
			for (field in cls.fields) {
				if (field === instFld) {
					continue
				}
				for (useMth in field.getUseIn()) {
					if (badMethodUsage(cls, useMth, field.accessFlags)) {
						return false
					}
				}
			}
			instFld.add(AFlag.INLINE_INSTANCE_FIELD)
			return true
		}

		private fun badMethodUsage(cls: ClassNode, useMth: MethodNode, accessFlags: AccessInfo): Boolean {
			val useCls = useMth.parentClass
			if (useCls == cls) {
				return false
			}
			if (accessFlags.isSynthetic()) {
				// 允许内部类中的 synthetic 使用
				return useCls.parentClass != cls
			}
			return true
		}

		/**
		 * 检查：
		 * + 都在构造器中
		 * + 所有使用都在同一个类
		 * - 同一字段 put（忽略：方法尚未加载）
		 */
		private fun checkForCommonFieldInit(ctrMth: MethodNode): Boolean {
			val ctrUse = ctrMth.getUseIn()
			if (ctrUse.isEmpty()) {
				return false
			}
			val firstUseCls = ctrUse[0].parentClass
			return ListUtils.allMatch(ctrUse) { m -> m.isConstructor() && m.parentClass == firstUseCls }
		}

		private fun getBaseType(cls: ClassNode): ArgType? {
			val interfacesCount = cls.interfaces.size
			if (interfacesCount > 1) {
				return null
			}
			val superCls = cls.superClass
			if (superCls == null || superCls == ArgType.OBJECT) {
				if (interfacesCount == 1) {
					return cls.interfaces[0]
				}
				return ArgType.OBJECT
			}
			if (interfacesCount == 0) {
				return superCls
			}
			// 检查父类是否已实现该接口（罕见情况）
			val interfaceType = cls.interfaces[0]
			if (checkNotNull(cls.root().getClsp()).isImplements(superCls.getObject(), interfaceType.getObject())) {
				return superCls
			}
			if (cls.root().getArgs().isAllowInlineKotlinLambda) {
				if (superCls.getObject() == "kotlin.jvm.internal.Lambda") {
					// 内联这类类会有不同的语义：缺少 'arity' 属性。
					// 目前不清楚这如何影响代码执行。
					return interfaceType
				}
			}
			return null
		}
	}
}
