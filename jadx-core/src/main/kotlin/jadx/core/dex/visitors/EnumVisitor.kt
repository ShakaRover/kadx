package jadx.core.dex.visitors

import jadx.api.plugins.input.data.AccessFlags
import jadx.core.codegen.TypeGen
import jadx.core.deobf.NameMapper
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.nodes.EnumClassAttr
import jadx.core.dex.attributes.nodes.EnumClassAttr.EnumField
import jadx.core.dex.attributes.nodes.RenameReasonAttr
import jadx.core.dex.attributes.nodes.SkipMethodArgsAttr
import jadx.core.dex.info.AccessInfo
import jadx.core.dex.info.ClassInfo
import jadx.core.dex.info.FieldInfo
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.instructions.IndexInsnNode
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.InvokeNode
import jadx.core.dex.instructions.InvokeType
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.InsnWrapArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.instructions.args.SSAVar
import jadx.core.dex.instructions.mods.ConstructorInsn
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.IContainer
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.dex.regions.Region
import jadx.core.dex.visitors.regions.CheckRegions
import jadx.core.dex.visitors.regions.IfRegionVisitor
import jadx.core.dex.visitors.shrink.CodeShrinkVisitor
import jadx.core.utils.BlockInsnPair
import jadx.core.utils.BlockUtils
import jadx.core.utils.InsnRemover
import jadx.core.utils.InsnUtils
import jadx.core.utils.ListUtils
import jadx.core.utils.Utils
import jadx.core.utils.exceptions.JadxException
import jadx.core.utils.exceptions.JadxRuntimeException
import java.util.ArrayList
import java.util.Collections
import java.util.function.Function
import java.util.function.Predicate

/**
 * 枚举类还原访问者。
 *
 * **做什么**：识别 `$VALUES` 数组、枚举常量字段、合成 `values()`/`valueOf()` 方法与
 * 静态初始化块中的构造器调用，把它们还原为 Java `enum` 结构。
 *
 * **为什么**：javac 把 enum 编译成普通类 + 一堆合成成员，还原后代码可读性大幅提升。
 *
 * **兼容的模式**：普通 javac 模式、Java 15 的重定向数组填充、Kotlin 1.9+ 的 `$ENTRIES` 模式。
 */
@JadxVisitor(
	name = "EnumVisitor",
	desc = "Restore enum classes",
	runAfter = [
		CodeShrinkVisitor::class, // 所有可内联指令已内联
		ModVisitor::class,
		ReplaceNewArray::class, // values 数组已规范化
		IfRegionVisitor::class, // 三元运算符已内联
		CheckRegions::class, // 区域处理已完成
	],
	runBefore = [ExtractFieldInit::class],
)
class EnumVisitor : AbstractVisitor() {

	private lateinit var enumValueOfMth: MethodInfo
	private lateinit var cloneMth: MethodInfo

	override fun init(root: RootNode) {
		enumValueOfMth = MethodInfo.fromDetails(
			root,
			ClassInfo.fromType(root, ArgType.ENUM),
			"valueOf",
			listOf(ArgType.CLASS, ArgType.STRING),
			ArgType.ENUM,
		)

		cloneMth = MethodInfo.fromDetails(
			root,
			ClassInfo.fromType(root, ArgType.OBJECT),
			"clone",
			Collections.emptyList(),
			ArgType.OBJECT,
		)
	}

	@Throws(JadxException::class)
	override fun visit(cls: ClassNode): Boolean {
		if (cls.isEnum()) {
			val converted: Boolean
			try {
				converted = convertToEnum(cls)
			} catch (e: Exception) {
				cls.addWarnComment("Enum visitor error", e)
				return true
			}
			if (!converted) {
				val accessFlags = cls.accessFlags
				if (accessFlags.isEnum()) {
					cls.accessFlags = accessFlags.remove(AccessFlags.ENUM)
					cls.addWarnComment("Failed to restore enum class, 'enum' modifier and super class removed")
				}
			}
		}
		return true
	}

	private fun convertToEnum(cls: ClassNode): Boolean {
		val superType = cls.superClass
		if (superType != null && superType.getObject() == ArgType.ENUM.getObject()) {
			cls.add(AFlag.REMOVE_SUPER_CLASS)
		}
		val classInitMth = cls.getClassInitMth()
		if (classInitMth == null) {
			cls.addWarnComment("Enum class init method not found")
			return false
		}
		val staticRegion: Region? = classInitMth.region
		if (staticRegion == null || checkNotNull(classInitMth.getBasicBlocks()).isEmpty()) {
			return false
		}
		// 收集静态方法线性部分的基本块（忽略方法末尾的分支）
		val staticBlocks = ArrayList<BlockNode>()
		for (subBlock in staticRegion.getSubBlocks()) {
			if (subBlock is BlockNode) {
				staticBlocks.add(subBlock)
			} else {
				break
			}
		}
		if (staticBlocks.isEmpty()) {
			cls.addWarnComment("Unexpected branching in enum static init block")
			return false
		}
		val data = EnumData(cls, classInitMth, staticBlocks)
		if (!searchValuesField(data)) {
			return false
		}
		var enumFields: List<EnumField>? = null
		val arrArg = checkNotNull(data.valuesInitInsn).getArg(0)
		if (arrArg.isInsnWrap) {
			val wrappedInsn = (arrArg as InsnWrapArg).wrapInsn
			enumFields = extractEnumFieldsFromInsn(data, wrappedInsn)
		} else if (arrArg.isRegister) {
			// Kotlin 1.9+ 的 $ENTRIES 模式：数组寄存器有多个使用点，
			// 导致 CodeShrinkVisitor 无法内联到 SPUT
			val assignInsn = (arrArg as RegisterArg).getAssignInsn()
			if (assignInsn != null) {
				enumFields = extractEnumFieldsFromInsn(data, assignInsn)
			}
		}
		if (enumFields == null) {
			cls.addWarnComment("Unknown enum class pattern. Please report as an issue!")
			return false
		}
		data.toRemove.add(checkNotNull(data.valuesInitInsn))

		// 全部检查完成，开始变换
		val attr = EnumClassAttr(enumFields)
		attr.staticMethod = classInitMth
		cls.addAttr(attr)

		for (enumField in attr.fields) {
			val fieldNode = enumField.field
			val name = enumField.nameStr
			if (name != null &&
				fieldNode.getAlias() != name &&
				NameMapper.isValidAndPrintable(name) &&
				cls.root().getArgs().isRenameValid
			) {
				fieldNode.getFieldInfo().alias = name
			}
			fieldNode.add(AFlag.DONT_GENERATE)
			processConstructorInsn(data, enumField, classInitMth)
		}
		checkNotNull(data.valuesField).add(AFlag.DONT_GENERATE)
		InsnRemover.removeAllAndUnbind(classInitMth, data.toRemove)
		if (classInitMth.countInsns() == 0L) {
			classInitMth.add(AFlag.DONT_GENERATE)
		} else if (data.toRemove.isNotEmpty()) {
			CodeShrinkVisitor.shrinkMethod(classInitMth)
		}
		removeEnumMethods(cls, checkNotNull(data.valuesField))
		fixAccessFlags(cls)
		cls.add(AFlag.CONVERTED_ENUM)
		return true
	}

	private fun fixAccessFlags(cls: ClassNode) {
		// 移除无效的访问标志
		cls.accessFlags = cls.accessFlags
			.remove(AccessFlags.FINAL)
			.remove(AccessFlags.ABSTRACT)
			.remove(AccessFlags.STATIC)
		for (mth in cls.methods) {
			if (mth.getMethodInfo().isConstructor()) {
				mth.accessFlags = mth.accessFlags.remove(AccessInfo.VISIBILITY_FLAGS)
			}
		}
	}

	/**
	 * 搜索 "$VALUES" 字段（保存所有枚举值）。
	 */
	private fun searchValuesField(data: EnumData): Boolean {
		val clsType = data.cls.classInfo.type
		val valuesCandidates = ArrayList<FieldNode>()
		for (f in data.cls.fields) {
			if (f.accessFlags.isStatic() &&
				f.type.isArray() &&
				f.type.getArrayRootElement() == clsType
			) {
				valuesCandidates.add(f)
			}
		}

		if (valuesCandidates.isEmpty()) {
			data.cls.addWarnComment("\$VALUES field not found")
			return false
		}
		if (valuesCandidates.size > 1) {
			valuesCandidates.removeIf { f -> !f.accessFlags.isSynthetic() }
		}
		if (valuesCandidates.size > 1) {
			var valuesOpt: FieldNode? = null
			for (f in valuesCandidates) {
				if (f.getName() == "\$VALUES") {
					valuesOpt = f
					break
				}
			}
			if (valuesOpt != null) {
				valuesCandidates.clear()
				valuesCandidates.add(valuesOpt)
			}
		}
		if (valuesCandidates.size != 1) {
			data.cls.addWarnComment("Found several \"values\" enum fields: $valuesCandidates")
			return false
		}
		data.valuesField = valuesCandidates[0]

		// 搜索 "$VALUES" 数组初始化并收集枚举字段
		val valuesInitPair = getValuesInitInsn(data) ?: return false
		data.valuesInitInsn = valuesInitPair.insn
		return true
	}

	private fun processConstructorInsn(data: EnumData, enumField: EnumField, classInitMth: MethodNode) {
		val co = enumField.constrInsn
		val enumClsInfo = co.getClassType()
		if (enumClsInfo != data.cls.classInfo) {
			val enumCls = data.cls.root().resolveClass(enumClsInfo)
			if (enumCls != null) {
				processEnumCls(data.cls, enumField, enumCls)
			}
		}
		val ctrMth = data.cls.root().resolveMethod(co.callMth)
		if (ctrMth != null) {
			markArgsForSkip(ctrMth)
		}
		val coResArg = co.getResult()
		if (coResArg == null || checkNotNull(coResArg.sVar).getUseList().size <= 2) {
			data.toRemove.add(co)
		} else {
			var varUseFound = false
			for (useArg in checkNotNull(coResArg.sVar).getUseList()) {
				if (!data.toRemove.contains(useArg.getParentInsn())) {
					varUseFound = true
					break
				}
			}
			if (varUseFound) {
				// 构造器结果在其它地方使用 -> 用枚举字段 get（SGET）替换构造器
				val enumGet = IndexInsnNode(InsnType.SGET, enumField.field.getFieldInfo(), 0)
				enumGet.setResult(coResArg.duplicate())
				BlockUtils.replaceInsn(classInitMth, co, enumGet)
			}
		}
	}

	private fun extractEnumFieldsFromInsn(enumData: EnumData, wrappedInsn: InsnNode): List<EnumField>? {
		when (wrappedInsn.getType()) {
			InsnType.FILLED_NEW_ARRAY -> return extractEnumFieldsFromFilledArray(enumData, wrappedInsn)

			// 处理 values 数组填充的重定向（Java 15 新增）
			InsnType.INVOKE -> return extractEnumFieldsFromInvoke(enumData, wrappedInsn as InvokeNode)

			InsnType.NEW_ARRAY -> {
				val arg = wrappedInsn.getArg(0)
				if (arg.isZeroLiteral()) {
					// 空枚举
					return Collections.emptyList()
				}
				return null
			}

			else -> return null
		}
	}

	private fun extractEnumFieldsFromInvoke(enumData: EnumData, invokeNode: InvokeNode): List<EnumField>? {
		val callMth = invokeNode.callMth
		val valuesMth = enumData.cls.root().resolveMethod(callMth)
		if (valuesMth == null || valuesMth.isVoidReturn()) {
			return null
		}
		val returnBlock = Utils.getOne(valuesMth.getPreExitBlocks())
		val returnInsn = BlockUtils.getLastInsn(returnBlock)
		val wrappedInsn = InsnUtils.getWrappedInsn(InsnUtils.getSingleArg(returnInsn)) ?: return null
		val enumFields = extractEnumFieldsFromInsn(enumData, wrappedInsn)
		if (enumFields != null && ListUtils.isSingleElement(valuesMth.getUseIn(), enumData.classInitMth)) {
			valuesMth.add(AFlag.DONT_GENERATE)
			if (valuesMth.getName() == "\$values") {
				// Kotlin 用于初始化 values 的合成方法
				// 重命名为实际的 values 方法，以便在 $ENTRIES 初始化代码中使用
				valuesMth.getMethodInfo().alias = "values"
			}
		}
		return enumFields
	}

	private fun getValuesInitInsn(data: EnumData): BlockInsnPair? {
		val searchField = checkNotNull(data.valuesField).getFieldInfo()
		for (blockNode in data.staticBlocks) {
			for (insn in blockNode.getInstructions()) {
				if (insn.getType() == InsnType.SPUT) {
					val indexInsnNode = insn as IndexInsnNode
					val f = indexInsnNode.index as FieldInfo
					if (f == searchField) {
						return BlockInsnPair(blockNode, indexInsnNode)
					}
				}
			}
		}
		return null
	}

	private fun extractEnumFieldsFromFilledArray(enumData: EnumData, arrFillInsn: InsnNode): List<EnumField>? {
		val enumFields = ArrayList<EnumField>()
		for (arg in arrFillInsn.getArguments()) {
			var field: EnumField? = null
			if (arg.isInsnWrap) {
				val wrappedInsn = (arg as InsnWrapArg).wrapInsn
				field = processEnumFieldByWrappedInsn(enumData, wrappedInsn)
			} else if (arg.isRegister) {
				field = processEnumFieldByRegister(enumData, arg as RegisterArg)
			}
			if (field == null) {
				return null
			}
			enumFields.add(field)
		}
		enumData.toRemove.add(arrFillInsn)
		return enumFields
	}

	private fun processEnumFieldByWrappedInsn(data: EnumData, wrappedInsn: InsnNode): EnumField? {
		if (wrappedInsn.getType() == InsnType.SGET) {
			return processEnumFieldByField(data, wrappedInsn)
		}
		val constructorInsn = castConstructorInsn(wrappedInsn)
		if (constructorInsn != null) {
			val enumFieldNode = createFakeField(data.cls, "EF" + constructorInsn.getOffset())
			data.cls.addField(enumFieldNode)
			return createEnumFieldByConstructor(data, enumFieldNode, constructorInsn)
		}
		return null
	}

	private fun processEnumFieldByField(data: EnumData, sgetInsn: InsnNode): EnumField? {
		if (sgetInsn.getType() != InsnType.SGET) {
			return null
		}
		val fieldInfo = (sgetInsn as IndexInsnNode).index as FieldInfo
		val enumFieldNode = data.cls.searchField(fieldInfo) ?: return null
		val sputInsn = searchFieldPutInsn(data, enumFieldNode) ?: return null

		val co = getConstructorInsn(sputInsn) ?: return null
		val sgetResult = sgetInsn.getResult()
		if (sgetResult == null || checkNotNull(sgetResult.sVar).getUseCount() == 1) {
			data.toRemove.add(sgetInsn)
		}
		data.toRemove.add(sputInsn)
		return createEnumFieldByConstructor(data, enumFieldNode, co)
	}

	private fun processEnumFieldByRegister(data: EnumData, arg: RegisterArg): EnumField? {
		val assignInsn = arg.getAssignInsn()
		if (assignInsn != null && assignInsn.getType() == InsnType.SGET) {
			return processEnumFieldByField(data, assignInsn)
		}

		val ssaVar = checkNotNull(arg.sVar)
		if (ssaVar.getUseCount() == 0) {
			return null
		}
		val constrInsn = ssaVar.assign.getParentInsn()
		if (constrInsn == null || constrInsn.getType() != InsnType.CONSTRUCTOR) {
			return null
		}
		var enumFieldNode = searchEnumField(data, ssaVar)
		if (enumFieldNode == null) {
			enumFieldNode = createFakeField(data.cls, "EF" + arg.regNum)
			data.cls.addField(enumFieldNode)
		}
		return createEnumFieldByConstructor(data, enumFieldNode, constrInsn as ConstructorInsn)
	}

	private fun createFakeField(cls: ClassNode, name: String): FieldNode {
		val fldInfo = FieldInfo.from(cls.root(), cls.classInfo, name, cls.getType())
		val enumFieldNode = FieldNode(cls, fldInfo, 0)
		enumFieldNode.add(AFlag.SYNTHETIC)
		enumFieldNode.addInfoComment("Fake field, exist only in values array")
		return enumFieldNode
	}

	private fun searchEnumField(data: EnumData, ssaVar: SSAVar): FieldNode? {
		val sputInsn = ssaVar.getUseList()[0].getParentInsn()
		if (sputInsn == null || sputInsn.getType() != InsnType.SPUT) {
			return null
		}
		val fieldInfo = (sputInsn as IndexInsnNode).index as FieldInfo
		val enumFieldNode = data.cls.searchField(fieldInfo) ?: return null
		data.toRemove.add(sputInsn)
		return enumFieldNode
	}

	private fun createEnumFieldByConstructor(data: EnumData, enumFieldNode: FieldNode, constructorInsn: ConstructorInsn): EnumField? {
		// 通常构造器签名是 '<init>(Ljava/lang/String;I)V'，有时一个或两个参数会被省略
		var co = constructorInsn
		val cls = data.cls
		val clsInfo = co.getClassType()
		val constrCls = cls.root().resolveClass(clsInfo) ?: return null
		if (constrCls == cls) {
			// 允许同类
		} else if (constrCls.contains(AType.ANONYMOUS_CLASS)) {
			// 允许已标记为匿名的外部类
		} else {
			return null
		}
		val ctrMth = cls.root().resolveMethod(co.callMth) ?: return null
		// 通常构造器签名是 '<init>(Ljava/lang/String;I)V'
		// 有时一个或两个参数会被内联或省略
		var nameStr: String? = null
		if (co.getArgsCount() == 0) {
			val ctrInsn = searchEnumSuperCtrInsn(ctrMth)
			if (ctrInsn != null && ctrInsn.getArgsCount() != 0) {
				nameStr = getConstString(ctrMth.root(), ctrInsn.getArg(0))
			}
		} else {
			nameStr = getConstString(cls.root(), co.getArg(0))
			// 校验并尝试内联额外的构造器参数
			val regs = ArrayList<RegisterArg>()
			co.getRegisterArgs(regs)
			if (regs.isNotEmpty()) {
				val replacedCo = inlineExternalRegs(data, co)
					?: throw JadxRuntimeException("Init of enum field '${enumFieldNode.getName()}' uses external variables")
				data.toRemove.add(co)
				co = replacedCo
			}
		}
		return EnumField(enumFieldNode, co, nameStr)
	}

	private fun searchEnumSuperCtrInsn(ctrMth: MethodNode): ConstructorInsn? {
		for (block in checkNotNull(ctrMth.getBasicBlocks())) {
			for (insn in block.getInstructions()) {
				if (insn.getType() == InsnType.CONSTRUCTOR) {
					val ctrCall = insn as ConstructorInsn
					if (ctrCall.isSuper &&
						ctrCall.getArgsCount() != 0 &&
						ctrCall.callMth.rawFullId == ENUM_SUPER_CONSTRUCTOR_ID
					) {
						return ctrCall
					}
				}
			}
		}
		return null
	}

	private fun inlineExternalRegs(data: EnumData, co: ConstructorInsn): ConstructorInsn? {
		val resCo = co.copyWithoutResult<ConstructorInsn>()
		val regs = ArrayList<RegisterArg>()
		resCo.getRegisterArgs(regs)
		for (reg in regs) {
			val enumField = checkExternalRegUsage(data, reg) ?: return null
			val enumUse = IndexInsnNode(InsnType.SGET, enumField, 0)
			val replaced = resCo.replaceArg(reg, InsnArg.wrapArg(enumUse))
			if (!replaced) {
				return null
			}
		}
		return resCo
	}

	private fun checkExternalRegUsage(data: EnumData, reg: RegisterArg): FieldInfo? {
		val cls = data.cls
		val ssaVar = checkNotNull(reg.sVar)
		val assignInsn = InsnUtils.checkInsnType(ssaVar.assignInsn, InsnType.CONSTRUCTOR)
		if (assignInsn == null || (assignInsn as ConstructorInsn).getClassType() != cls.classInfo) {
			return null
		}
		var enumField: FieldInfo? = null
		for (useArg in ssaVar.getUseList()) {
			val useInsn = useArg.getParentInsn() ?: return null
			when (useInsn.getType()) {
				InsnType.SPUT -> {
					val field = (useInsn as IndexInsnNode).index as FieldInfo
					if (field.declClass != cls.classInfo || field.type != cls.getType()) {
						return null
					}
					enumField = field
				}

				InsnType.CONSTRUCTOR -> {
					val useCo = useInsn as ConstructorInsn
					if (useCo.getClassType() != cls.classInfo) {
						return null
					}
				}

				InsnType.FILLED_NEW_ARRAY -> {
					// 允许在 values 初始化指令中使用
					val valuesArg = checkNotNull(data.valuesInitInsn).getArg(0)
					val unwrapped = valuesArg.unwrap()
					if (unwrapped != null) {
						if (unwrapped !== useInsn) {
							return null
						}
					} else if (valuesArg.isRegister) {
						val valuesAssign = (valuesArg as RegisterArg).getAssignInsn()
						if (valuesAssign !== useInsn) {
							return null
						}
					} else {
						return null
					}
				}

				else -> return null
			}
		}
		if (enumField != null) {
			data.toRemove.add(assignInsn)
		}
		return enumField
	}

	private fun searchFieldPutInsn(data: EnumData, enumFieldNode: FieldNode): InsnNode? {
		for (block in data.staticBlocks) {
			for (sputInsn in block.getInstructions()) {
				if (sputInsn != null && sputInsn.getType() == InsnType.SPUT) {
					val f = (sputInsn as IndexInsnNode).index as FieldInfo
					val fieldNode = data.cls.searchField(f)
					if (fieldNode == enumFieldNode) {
						return sputInsn
					}
				}
			}
		}
		return null
	}

	private fun removeEnumMethods(cls: ClassNode, valuesField: FieldNode) {
		val clsType = cls.classInfo.type
		val valuesMethodShortId = "values()" + TypeGen.signature(ArgType.array(clsType))
		var valuesMethod: MethodNode? = null
		// 移除编译器生成的方法
		for (mth in cls.methods) {
			val mi = mth.getMethodInfo()
			if (mi.isClassInit() || mth.isNoCode()) {
				continue
			}
			val shortId = mi.shortId
			if (mi.isConstructor()) {
				markArgsForSkip(mth)
				// 移除 super 构造器调用
				val superCtrInsn = searchEnumSuperCtrInsn(mth)
				if (superCtrInsn != null) {
					superCtrInsn.add(AFlag.DONT_GENERATE)
					InsnRemover.remove(mth, superCtrInsn)
				}
				if (isDefaultConstructor(mth, shortId)) {
					mth.add(AFlag.DONT_GENERATE)
				}
			} else if (mi.shortId == valuesMethodShortId) {
				if (isValuesMethod(mth, clsType)) {
					valuesMethod = mth
					mth.add(AFlag.DONT_GENERATE)
				} else {
					// 自定义 values 方法 => 重命名以解决与枚举方法的冲突
					mth.getMethodInfo().alias = "valuesCustom"
					mth.addAttr(RenameReasonAttr(mth).append("to resolve conflict with enum method"))
				}
			} else if (isValuesMethod(mth, clsType)) {
				if (mth.getMethodInfo().alias != "values" && mth.getUseIn().isNotEmpty()) {
					// 重命名以使用默认 values 方法
					mth.getMethodInfo().alias = "values"
					mth.addAttr(RenameReasonAttr(mth).append("to match enum method name"))
					mth.add(AFlag.DONT_RENAME)
				}
				valuesMethod = mth
				mth.add(AFlag.DONT_GENERATE)
			} else if (simpleValueOfMth(mth, clsType)) {
				mth.add(AFlag.DONT_GENERATE)
			}
		}
		val valuesFieldInfo = valuesField.getFieldInfo()
		for (mth in cls.methods) {
			// 修复对 'values' 字段与 'values()' 方法的访问
			fixValuesAccess(mth, valuesFieldInfo, clsType, valuesMethod)
		}
	}

	private fun markArgsForSkip(mth: MethodNode) {
		// 跳过第一个和第二个参数
		SkipMethodArgsAttr.skipArg(mth, 0)
		if (mth.getMethodInfo().argsCount > 1) {
			SkipMethodArgsAttr.skipArg(mth, 1)
		}
	}

	private fun isDefaultConstructor(mth: MethodNode, shortId: String): Boolean {
		val defaultId = shortId == "<init>(Ljava/lang/String;I)V" ||
			shortId == "<init>(Ljava/lang/String;)V"
		if (defaultId) {
			// 检查内容
			return mth.countInsns() == 0L
		}
		return false
	}

	private fun isValuesMethod(mth: MethodNode, clsType: ArgType): Boolean {
		val retType = mth.getReturnType()
		if (!retType.isArray() || retType.getArrayElement() != clsType) {
			return false
		}
		val returnInsn = BlockUtils.getOnlyOneInsnFromMth(mth)
		if (returnInsn == null || returnInsn.getType() != InsnType.RETURN || returnInsn.getArgsCount() != 1) {
			return false
		}
		val wrappedInsn = InsnUtils.getWrappedInsn(InsnUtils.getSingleArg(returnInsn))
		val castInsn = InsnUtils.checkInsnType(wrappedInsn, InsnType.CHECK_CAST) as? IndexInsnNode
		if (castInsn != null && castInsn.index == ArgType.array(clsType)) {
			val invokeInsn = InsnUtils.checkInsnType(InsnUtils.getWrappedInsn(InsnUtils.getSingleArg(castInsn)), InsnType.INVOKE) as? InvokeNode
			return invokeInsn != null && invokeInsn.callMth == cloneMth
		}
		return false
	}

	private fun simpleValueOfMth(mth: MethodNode, clsType: ArgType): Boolean {
		val returnInsn = InsnUtils.searchSingleReturnInsn(mth) { insn -> insn.getArgsCount() == 1 } ?: return false
		val wrappedInsn = InsnUtils.getWrappedInsn(InsnUtils.getSingleArg(returnInsn))
		val castInsn = InsnUtils.checkInsnType(wrappedInsn, InsnType.CHECK_CAST) as? IndexInsnNode
		if (castInsn != null && castInsn.index == clsType) {
			val invokeInsn = InsnUtils.checkInsnType(InsnUtils.getWrappedInsn(InsnUtils.getSingleArg(castInsn)), InsnType.INVOKE) as? InvokeNode
			return invokeInsn != null && invokeInsn.callMth == enumValueOfMth
		}
		return false
	}

	private fun fixValuesAccess(mth: MethodNode, valuesFieldInfo: FieldInfo, clsType: ArgType, valuesMethod: MethodNode?) {
		val mi = mth.getMethodInfo()
		if (mi.isConstructor() || mi.isClassInit() || mth.isNoCode() || mth === valuesMethod) {
			return
		}
		// 搜索 values 字段的使用点
		val insnTest = Predicate<InsnNode> { insn -> (insn as IndexInsnNode).index == valuesFieldInfo }
		val useInsn = InsnUtils.searchInsn(mth, InsnType.SGET, insnTest)
		if (useInsn == null) {
			return
		}
		// 把 'values' 字段访问替换为 'values()' 方法
		InsnUtils.replaceInsns(
			mth,
			Function { insn ->
				if (insn.getType() == InsnType.SGET && insnTest.test(insn)) {
					val valueMth = if (valuesMethod == null) {
						getValueMthInfo(mth.root(), clsType)
					} else {
						valuesMethod.getMethodInfo()
					}
					val invokeNode = InvokeNode(valueMth, InvokeType.STATIC, 0)
					invokeNode.setResult(insn.getResult())
					if (valuesMethod == null) {
						// 强制使用枚举方法（可能与自定义方法重名而被重命名）
						invokeNode.add(AFlag.FORCE_RAW_NAME)
					}
					mth.addDebugComment("Replace access to removed values field (${valuesFieldInfo.name}) with 'values()' method")
					invokeNode
				} else {
					null
				}
			},
		)
	}

	private fun getValueMthInfo(root: RootNode, clsType: ArgType): MethodInfo = MethodInfo.fromDetails(root, ClassInfo.fromType(root, clsType), "values", Collections.emptyList(), ArgType.array(clsType))

	private fun processEnumCls(cls: ClassNode, field: EnumField, innerCls: ClassNode) {
		// 移除构造器，因为它是匿名类
		for (innerMth in innerCls.methods) {
			if (innerMth.accessFlags.isConstructor()) {
				innerMth.add(AFlag.DONT_GENERATE)
			}
		}
		field.cls = innerCls
		if (innerCls.parentClass != cls) {
			// 不是内部类
			cls.addInlinedClass(innerCls)
			innerCls.add(AFlag.DONT_GENERATE)
		}
	}

	private fun getConstructorInsn(insn: InsnNode): ConstructorInsn? {
		if (insn.getArgsCount() != 1) {
			return null
		}
		val arg = insn.getArg(0)
		if (arg.isInsnWrap) {
			return castConstructorInsn((arg as InsnWrapArg).wrapInsn)
		}
		if (arg.isRegister) {
			return castConstructorInsn((arg as RegisterArg).getAssignInsn())
		}
		return null
	}

	private fun castConstructorInsn(coCandidate: InsnNode?): ConstructorInsn? {
		if (coCandidate != null && coCandidate.getType() == InsnType.CONSTRUCTOR) {
			return coCandidate as ConstructorInsn
		}
		return null
	}

	private fun getConstString(root: RootNode, arg: InsnArg): String? {
		if (arg.isInsnWrap) {
			val constInsn = (arg as InsnWrapArg).wrapInsn
			val constValue = InsnUtils.getConstValueByInsn(root, constInsn)
			if (constValue is String) {
				return constValue
			}
		}
		return null
	}

	private class EnumData(
		val cls: ClassNode,
		val classInitMth: MethodNode,
		val staticBlocks: List<BlockNode>,
	) {
		val toRemove: MutableList<InsnNode> = ArrayList()
		var valuesField: FieldNode? = null
		var valuesInitInsn: InsnNode? = null
	}

	override fun getName(): String = "EnumVisitor"

	companion object {
		private const val ENUM_SUPER_CONSTRUCTOR_ID = "java.lang.Enum.<init>(Ljava/lang/String;I)V"
	}
}
