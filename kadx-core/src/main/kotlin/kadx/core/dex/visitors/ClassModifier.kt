package kadx.core.dex.visitors

import kadx.api.plugins.input.data.AccessFlags
import kadx.api.plugins.input.data.attributes.KadxAttrType
import kadx.core.Consts
import kadx.core.dex.attributes.AFlag
import kadx.core.dex.attributes.AType
import kadx.core.dex.attributes.nodes.FieldReplaceAttr
import kadx.core.dex.attributes.nodes.MethodReplaceAttr
import kadx.core.dex.attributes.nodes.RenameReasonAttr
import kadx.core.dex.attributes.nodes.SkipMethodArgsAttr
import kadx.core.dex.info.AccessInfo
import kadx.core.dex.info.ClassInfo
import kadx.core.dex.info.FieldInfo
import kadx.core.dex.info.MethodInfo
import kadx.core.dex.instructions.IndexInsnNode
import kadx.core.dex.instructions.InsnType
import kadx.core.dex.instructions.InvokeNode
import kadx.core.dex.instructions.InvokeType
import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.instructions.args.InsnArg
import kadx.core.dex.instructions.args.InsnWrapArg
import kadx.core.dex.instructions.args.RegisterArg
import kadx.core.dex.instructions.args.SSAVar
import kadx.core.dex.instructions.mods.ConstructorInsn
import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.FieldNode
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.visitors.fixaccessmodifiers.FixAccessModifiers
import kadx.core.dex.visitors.usage.UsageInfoVisitor
import kadx.core.utils.BlockUtils
import kadx.core.utils.InsnRemover
import kadx.core.utils.exceptions.KadxException
import java.util.ArrayList

/**
 * 类修饰访问者：移除合成类/方法/字段。
 *
 * **做什么**：
 * 1. 删除空的合成类（无字段/方法/内部类的 synthetic 类）；
 * 2. 删除指向外部类的合成字段（并改写构造器中的赋值）；
 * 3. 删除合成桥接方法（bridge method），把调用重定向到真正的方法；
 * 4. 删除多余的空构造器 / 空的 `<clinit>`。
 *
 * **为什么**：这些内容都是编译器生成的实现细节，保留会严重干扰反编译可读性。
 */
@KadxVisitor(
	name = "ClassModifier",
	desc = "Remove synthetic classes, methods and fields",
	runAfter = [
		ModVisitor::class,
		FixAccessModifiers::class,
		ProcessAnonymous::class,
		ExtractFieldInit::class,
	],
)
class ClassModifier : AbstractVisitor() {

	@Throws(KadxException::class)
	override fun visit(cls: ClassNode): Boolean {
		if (cls.contains(AFlag.PACKAGE_INFO)) {
			return false
		}
		for (inner in cls.innerClasses) {
			visit(inner)
		}
		if (isEmptySyntheticClass(cls)) {
			cls.add(AFlag.DONT_GENERATE)
			return false
		}
		removeSyntheticFields(cls)
		for (mth in cls.methods) {
			removeSyntheticMethods(mth)
		}
		for (mth in cls.methods) {
			removeEmptyMethods(mth)
		}
		return false
	}

	companion object {

		private fun isEmptySyntheticClass(cls: ClassNode): Boolean = cls.accessFlags.isSynthetic() &&
			cls.fields.isEmpty() &&
			cls.methods.isEmpty() &&
			cls.innerClasses.isEmpty()

		/**
		 * 删除指向外部类、或将被内联（匿名类）的合成字段。
		 */
		private fun removeSyntheticFields(cls: ClassNode) {
			val inline = cls.isAnonymous()
			if (inline || cls.classInfo.isInner) {
				for (field in cls.fields) {
					val fldType = field.type
					if (field.accessFlags.isSynthetic() && fldType.isObject() && !fldType.isGenericType()) {
						val clsInfo = ClassInfo.fromType(cls.root(), fldType)
						val fieldsCls = cls.root().resolveClass(clsInfo) ?: continue
						val parentClass = cls.classInfo.parentClass
						val isParentInst = parentClass == fieldsCls.classInfo
						if (inline || isParentInst) {
							var found = 0
							for (mth in cls.methods) {
								if (removeFieldUsageFromConstructor(mth, field, fieldsCls)) {
									found++
								}
							}
							if (found != 0) {
								if (isParentInst) {
									field.addAttr(FieldReplaceAttr(fieldsCls.classInfo))
								}
								field.add(AFlag.DONT_GENERATE)
							}
						}
					}
				}
			}
		}

		private fun removeFieldUsageFromConstructor(mth: MethodNode, field: FieldNode, fieldsCls: ClassNode): Boolean {
			if (mth.isNoCode() || !mth.accessFlags.isConstructor()) {
				return false
			}
			val args = mth.argRegs
			if (args.isEmpty() || mth.contains(AFlag.SKIP_FIRST_ARG)) {
				return false
			}
			val arg = args[0]
			if (arg.getType() != fieldsCls.classInfo.type) {
				return false
			}
			val block = checkNotNull(checkNotNull(mth.enterBlock).cleanSuccessors)[0]
			val instructions = block.instructions
			if (instructions.isEmpty()) {
				return false
			}
			val insn = instructions[0]
			if (insn.type != InsnType.IPUT) {
				return false
			}
			val putInsn = insn as IndexInsnNode
			val fieldInfo = putInsn.index as FieldInfo
			if (fieldInfo != field.fieldInfo || putInsn.getArg(0) != arg) {
				return false
			}
			mth.skipFirstArgument()
			InsnRemover.remove(mth, block, insn)
			// 该参数还有其它使用点 -> 用 IGET 指令包裹
			if (checkNotNull(arg.sVar).useCount != 0) {
				val iget = IndexInsnNode(InsnType.IGET, fieldInfo, 1)
				iget.addArg(insn.getArg(1))
				for (insnArg in ArrayList(checkNotNull(arg.sVar).useList)) {
					insnArg.wrapInstruction(mth, iget)
				}
			}
			return true
		}

		private fun removeSyntheticMethods(mth: MethodNode) {
			if (mth.isNoCode() || mth.contains(AFlag.DONT_GENERATE)) {
				return
			}
			val af = mth.accessFlags
			if (!af.isSynthetic()) {
				return
			}
			val cls = mth.parentClass
			if (removeBridgeMethod(cls, mth)) {
				if (Consts.DEBUG) {
					mth.addDebugComment("Removed as synthetic bridge method")
				} else {
					mth.add(AFlag.DONT_GENERATE)
				}
				return
			}
			// 删除内部类的合成构造器
			if (mth.isConstructor() &&
				(mth.contains(AFlag.METHOD_CANDIDATE_FOR_INLINE) || mth.contains(AFlag.ANONYMOUS_CONSTRUCTOR))
			) {
				val insn = BlockUtils.getOnlyOneInsnFromMth(mth)
				if (insn != null) {
					val args = mth.argRegs
					if (isRemovedClassInArgs(cls, args)) {
						modifySyntheticMethod(cls, mth, insn, args)
					}
				}
			}
		}

		private fun isRemovedClassInArgs(cls: ClassNode, mthArgs: List<RegisterArg>): Boolean {
			var removedFound = false
			for (arg in mthArgs) {
				val argType = arg.getType()
				if (!argType.isObject()) {
					continue
				}
				var remove = false
				val argCls = cls.root().resolveClass(argType)
				if (argCls == null) {
					// 检查是否属于当前顶层类缺失的类
					val argClsInfo = ClassInfo.fromType(cls.root(), argType)
					val parent = argClsInfo.parentClass
					if (parent != null && cls.fullName.startsWith(parent.fullName)) {
						remove = true
					}
				} else {
					if (argCls.contains(AFlag.DONT_GENERATE) || isEmptySyntheticClass(argCls)) {
						remove = true
					}
				}
				if (remove) {
					arg.add(AFlag.REMOVE)
					removedFound = true
				}
			}
			return removedFound
		}

		/**
		 * 删除合成构造器，并把调用重定向到已存在的构造器。
		 */
		private fun modifySyntheticMethod(cls: ClassNode, mth: MethodNode, insn: InsnNode, args: List<RegisterArg>) {
			if (insn.type == InsnType.CONSTRUCTOR) {
				val constr = insn as ConstructorInsn
				if (constr.isThis && args.isNotEmpty()) {
					// 删除非静态类的第一个参数（对外部类的引用）
					val firstArg = args[0]
					if (firstArg.getType() == cls.parentClass.classInfo.type) {
						SkipMethodArgsAttr.skipArg(mth, 0)
					}
					// 删除未使用的参数
					val argsCount = args.size
					for (i in 0 until argsCount) {
						val arg = args[i]
						val sVar = arg.sVar
						if (sVar != null && sVar.useCount == 0) {
							SkipMethodArgsAttr.skipArg(mth, i)
						}
					}
					val callMth = constr.callMth
					val callMthNode = cls.root().resolveMethod(callMth)
					if (callMthNode != null) {
						mth.addAttr(MethodReplaceAttr(callMthNode))
						mth.add(AFlag.DONT_GENERATE)
						// 被标记方法的代码生成顺序应该已经确定
						UsageInfoVisitor.replaceMethodUsage(callMthNode, mth)
					}
				}
			}
		}

		private fun removeBridgeMethod(cls: ClassNode, mth: MethodNode): Boolean {
			if (cls.root().getArgs().isInlineMethods) { // 简单 wrapper 删除与内联等价
				val allInsns = BlockUtils.collectAllInsns(checkNotNull(mth.basicBlocks))
				if (allInsns.size == 1) {
					var wrappedInsn = allInsns[0]
					if (wrappedInsn.type == InsnType.RETURN) {
						val arg = wrappedInsn.getArg(0)
						if (arg.isInsnWrap) {
							wrappedInsn = (arg as InsnWrapArg).wrapInsn
						}
					}
					return checkSyntheticWrapper(mth, wrappedInsn)
				}
			}
			return false
		}

		private fun checkSyntheticWrapper(mth: MethodNode, insn: InsnNode): Boolean {
			val insnType = insn.type
			if (insnType != InsnType.INVOKE) {
				return false
			}
			val invokeInsn = insn as InvokeNode
			if (invokeInsn.invokeType == InvokeType.SUPER) {
				return false
			}
			val callMth = invokeInsn.callMth
			val wrappedMth = mth.root().resolveMethod(callMth) ?: return false
			val wrappedAccFlags = wrappedMth.accessFlags
			if (wrappedAccFlags.isStatic()) {
				return false
			}
			if (callMth.argsCount != mth.methodInfo.argsCount) {
				return false
			}
			// 仅重命名来自当前类的方法
			if (mth.parentClass != wrappedMth.parentClass) {
				return false
			}
			// 所有参数必须是由方法参数传入的寄存器（只允许 cast 指令）
			for (arg in insn.getArguments()) {
				if (!registersAndCastsOnly(arg)) {
					return false
				}
			}
			// 确认可删除，按需修改可见性与名字
			if (!wrappedAccFlags.isPublic() && !mth.root().getArgs().isRespectBytecodeAccModifiers) {
				// 必须为 public
				FixAccessModifiers.changeVisibility(wrappedMth, AccessFlags.PUBLIC)
			}
			val alias = mth.alias
			if (wrappedMth.alias != alias) {
				wrappedMth.rename(alias)
				RenameReasonAttr.forNode(wrappedMth).append("merged with bridge method [inline-methods]")
			}
			wrappedMth.addAttr(MethodReplaceAttr(mth))
			wrappedMth.copyAttributeFrom(mth, AType.METHOD_OVERRIDE)
			wrappedMth.addDebugComment("Method merged with bridge method: " + mth.methodInfo.shortId)
			return true
		}

		private fun registersAndCastsOnly(arg: InsnArg): Boolean {
			if (arg.isRegister) {
				return true
			}
			if (arg.isInsnWrap) {
				val wrapInsn = (arg as InsnWrapArg).wrapInsn
				if (wrapInsn.type == InsnType.CHECK_CAST) {
					return registersAndCastsOnly(wrapInsn.getArg(0))
				}
			}
			return false
		}

		/**
		 * 删除 public 的空构造器（static 或默认）。
		 */
		private fun removeEmptyMethods(mth: MethodNode) {
			if (mth.argRegs.isNotEmpty()) {
				return
			}
			val af = mth.accessFlags
			val publicConstructor = mth.isConstructor() && af.isPublic()
			val enumDefConstructor = mth.isConstructor() && mth.parentClass.contains(AFlag.CONVERTED_ENUM)
			val clsInit = mth.methodInfo.isClassInit() && af.isStatic()
			if (publicConstructor || enumDefConstructor || clsInit) {
				if (!BlockUtils.isAllBlocksEmpty(mth.basicBlocks)) {
					return
				}
				if (clsInit) {
					mth.add(AFlag.DONT_GENERATE)
				} else {
					// 若存在其它构造器或构造器带注解，则不要删除默认构造器
					if (mth.isDefaultConstructor() &&
						!isNonDefaultConstructorExists(mth) &&
						!mth.contains(KadxAttrType.ANNOTATION_LIST)
					) {
						mth.add(AFlag.DONT_GENERATE)
					}
				}
			}
		}

		private fun isNonDefaultConstructorExists(defCtor: MethodNode): Boolean {
			val parentClass = defCtor.parentClass
			for (mth in parentClass.methods) {
				if (mth !== defCtor && mth.isConstructor() && !mth.isDefaultConstructor()) {
					return true
				}
			}
			return false
		}
	}
}
