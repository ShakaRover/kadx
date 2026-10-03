package jadx.gui.device.debugger.smali

import jadx.api.ICodeInfo
import jadx.api.JadxArgs
import jadx.api.plugins.input.data.AccessFlags
import jadx.api.plugins.input.data.AccessFlagsScope
import jadx.api.plugins.input.data.ICatch
import jadx.api.plugins.input.data.IClassData
import jadx.api.plugins.input.data.ICodeReader
import jadx.api.plugins.input.data.IDebugInfo
import jadx.api.plugins.input.data.IFieldData
import jadx.api.plugins.input.data.ILocalVar
import jadx.api.plugins.input.data.IMethodData
import jadx.api.plugins.input.data.IMethodRef
import jadx.api.plugins.input.data.ISeqConsumer
import jadx.api.plugins.input.data.ITry
import jadx.api.plugins.input.data.annotations.AnnotationVisibility
import jadx.api.plugins.input.data.annotations.EncodedType
import jadx.api.plugins.input.data.annotations.EncodedValue
import jadx.api.plugins.input.data.annotations.IAnnotation
import jadx.api.plugins.input.data.attributes.JadxAttrType
import jadx.api.plugins.input.data.attributes.types.AnnotationsAttr
import jadx.api.plugins.input.insns.InsnData
import jadx.api.plugins.input.insns.InsnIndexType
import jadx.api.plugins.input.insns.Opcode
import jadx.api.plugins.input.insns.custom.ISwitchPayload
import jadx.core.dex.attributes.AttributeStorage
import jadx.core.dex.instructions.IndexInsnNode
import jadx.core.dex.instructions.InsnDecoder
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.InvokeNode
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.utils.StringUtils
import jadx.core.utils.Utils
import jadx.core.utils.exceptions.JadxRuntimeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.AbstractMap.SimpleEntry
import java.util.LinkedHashMap
import java.util.Locale

/**
 * 把 [ClassNode] 反汇编成 smali 文本。
 *
 * **做什么**：遍历类的字段/方法/注解/调试信息，逐条指令格式化为 smali，
 * 同时记录「smali 行号 -> 代码偏移」「方法 -> 寄存器信息」等映射，
 * 供 GUI 断点、单步、寄存器查看使用。
 *
 * **为什么保留大量私有格式化方法**：这是纯机械式迁移，尽量逐行对应原 Java，
 * 方便与原实现对照排查问题。
 */
class Smali private constructor() {

	/** 最终生成的 smali 代码信息。 */
	private lateinit var codeInfo: ICodeInfo

	/** 方法原始全名 -> 方法调试元数据。 */
	private val insnMap: MutableMap<String, SmaliMethodNode> = HashMap()

	private val printFileOffset: Boolean = true
	private val printBytecode: Boolean = true

	private var isJavaBytecode: Boolean = false

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(Smali::class.java)

		private var insnDecoder: SmaliInsnDecoder? = null

		private const val CODE_OFFSET_COLUMN_WIDTH = 4
		private const val BYTECODE_COLUMN_WIDTH = 20 + 3 // 3 个字符用于省略号
		private val FMT_BYTECODE_COL = "%-" + (BYTECODE_COLUMN_WIDTH - 3) + "s"

		private val INSN_COL_WIDTH = "const-method-handle".length
		private val FMT_INSN_COL = "%-" + INSN_COL_WIDTH + "s"
		private const val FMT_FILE_OFFSET = "%08x:"
		private const val FMT_CODE_OFFSET = "%04x:"
		private const val FMT_TARGET_OFFSET = "%04x"
		private const val FMT_GOTO = ":goto_" + FMT_TARGET_OFFSET
		private const val FMT_COND = ":cond_" + FMT_TARGET_OFFSET
		private const val FMT_DATA = ":array_" + FMT_TARGET_OFFSET
		private const val FMT_P_SWITCH = ":p_switch_" + FMT_TARGET_OFFSET
		private const val FMT_S_SWITCH = ":s_switch_" + FMT_TARGET_OFFSET
		private const val FMT_P_SWITCH_CASE = ":p_case_" + FMT_TARGET_OFFSET
		private const val FMT_S_SWITCH_CASE = ":s_case_" + FMT_TARGET_OFFSET

		private const val FMT_TRY_TAG = "try_" + FMT_TARGET_OFFSET + ":"
		private const val FMT_TRY_END_TAG = "try_end_" + FMT_TARGET_OFFSET + ":"
		private const val FMT_CATCH_TAG = "catch_" + FMT_TARGET_OFFSET + ":"
		private const val FMT_CATCH_ALL_TAG = "catch_all_" + FMT_TARGET_OFFSET + ":"
		private const val FMT_GOTO_TAG = "goto_" + FMT_TARGET_OFFSET + ":"
		private const val FMT_COND_TAG = "cond_" + FMT_TARGET_OFFSET + ":"
		private const val FMT_DATA_TAG = "array_" + FMT_TARGET_OFFSET + ":"
		private const val FMT_P_SWITCH_TAG = "p_switch_" + FMT_TARGET_OFFSET + ":"
		private const val FMT_S_SWITCH_TAG = "s_switch_" + FMT_TARGET_OFFSET + ":"
		private const val FMT_P_SWITCH_CASE_TAG = "p_case_" + FMT_TARGET_OFFSET + ":"
		private const val FMT_S_SWITCH_CASE_TAG = "s_case_" + FMT_TARGET_OFFSET + ":"

		/** 反汇编一个类（含其内部类）。 */
		fun disassemble(cls: ClassNode): Smali {
			val topCls = cls.topParentClass
			val code = SmaliWriter(topCls)
			val smali = Smali()
			smali.isJavaBytecode = topCls.inputFileName?.endsWith(".class") == true // TODO: add flag to api
			smali.writeClass(code, topCls)
			smali.codeInfo = code.finish()
			return smali
		}
	}

	/** @return 反汇编得到的完整 smali 文本 */
	fun getCode(): String = codeInfo.getCodeStr()

	/** @return 方法定义在 smali 文本中的位置，未找到返回 -1 */
	fun getMethodDefPos(mthFullRawID: String): Int = insnMap[mthFullRawID]?.getDefPos() ?: -1

	/** @return 方法调试元数据，未找到返回 null */
	fun getMethodNode(mthFullRawID: String): SmaliMethodNode? = insnMap[mthFullRawID]

	/** @return 方法寄存器总数，未找到返回 -1 */
	fun getRegCount(mthFullRawID: String): Int = insnMap[mthFullRawID]?.getRegCount() ?: -1

	/** @return 参数寄存器起始编号，未找到返回 -1 */
	fun getParamRegStart(mthFullRawID: String): Int = insnMap[mthFullRawID]?.getParamRegStart() ?: -1

	/** 按方法全名与代码偏移取 smali 文本位置，未找到返回 -1。 */
	fun getInsnPosByCodeOffset(mthFullRawID: String, codeOffset: Long): Int = insnMap[mthFullRawID]?.getInsnPos(codeOffset) ?: -1

	/** 按 smali 行号定位到方法全名与代码偏移。 */
	fun getMthFullIDAndCodeOffsetByLine(line: Int): Map.Entry<String, Int>? {
		for ((key, value) in insnMap) {
			val codeOffset = value.getLineMapping()[line]
			if (codeOffset != null) {
				return SimpleEntry(key, codeOffset)
			}
		}
		return null
	}

	/** @return 方法的寄存器列表，未找到返回空列表 */
	fun getRegisterList(mthFullRawID: String): List<SmaliRegister> = insnMap[mthFullRawID]?.getRegList() ?: emptyList()

	/**
	 * @return 无结果返回 null；字段操作返回 FieldInfo；寄存器结果返回寄存器编号
	 */
	fun getResultRegOrField(mthFullRawID: String, codeOffset: Long): Any? {
		val info = insnMap[mthFullRawID]
		if (info != null) {
			val insn = info.getInsnNode(codeOffset)
			if (insn != null) {
				if (insn.type == InsnType.IPUT) {
					return (insn as IndexInsnNode).index
				}
				if (insn.type == InsnType.INVOKE) {
					if (insn is InvokeNode) {
						if (insn.argsCount > 0) {
							return (insn.getArg(0) as RegisterArg).regNum
						}
					}
				}
				val regArg = insn.result
				if (regArg != null) {
					return regArg.regNum
				}
			}
		}
		return null
	}

	private fun writeClass(smali: SmaliWriter, cls: ClassNode) {
		val clsData = cls.getClsData()
		if (clsData == null) {
			smali.startLine("###### Class ${cls.fullName} is created by jadx")
			return
		}
		val clsAttributes = AttributeStorage.fromList(clsData.attributes)
		smali.startLine("Class: " + clsData.type)
			.startLine("AccessFlags: " + AccessFlags.format(clsData.accessFlags, AccessFlagsScope.CLASS))
			.startLine("SuperType: " + clsData.superType)
			.startLine("Interfaces: " + clsData.interfacesTypes)
			.startLine("SourceFile: " + clsAttributes.get(JadxAttrType.SOURCE_FILE))

		val annotationsAttr = clsAttributes.get(JadxAttrType.ANNOTATION_LIST)
		if (annotationsAttr != null) {
			val annos = annotationsAttr.list
			if (annos.isNotEmpty()) {
				smali.startLine("# ${annos.size} annotations")
				writeAnnotations(smali, ArrayList(annos))
				smali.startLine()
			}
		}

		val fields = ArrayList<RawField>()
		val colWidths = intArrayOf(0, 0) // 第一个是访问标志宽度，第二个是名字宽度
		val mthIndex = intArrayOf(0)
		val line = LineInfo()
		clsData.visitFieldsAndMethods(
			object : ISeqConsumer<IFieldData> {
				override fun accept(f: IFieldData) {
					val fld = RawField.make(f)
					fields.add(fld)
					if (fld.accessFlag.length > colWidths[0]) {
						colWidths[0] = fld.accessFlag.length
					}
					if (fld.name.length > colWidths[1]) {
						colWidths[1] = fld.name.length
					}
				}
			},
			object : ISeqConsumer<IMethodData> {
				override fun accept(m: IMethodData) {
					if (fields.isNotEmpty()) {
						writeFields(smali, clsData, fields, colWidths)
						fields.clear()
					}
					try {
						writeMethod(smali, cls.methods[mthIndex[0]++], m, line)
					} catch (e: Throwable) {
						val methodRef = m.methodRef
						val mthFullName = methodRef.parentClassType + "->" + methodRef.name
						smali.setIndent(0)
						smali.startLine("Failed to write method: " + mthFullName + "\n" + Utils.getStackTrace(e))
						LOG.error("Failed to write smali code for method: {}", mthFullName, e)
					}
					line.reset()
				}
			},
		)

		if (fields.isNotEmpty()) { // 没有方法的情况
			writeFields(smali, clsData, fields, colWidths)
		}
		for (innerClass in cls.innerClasses) {
			writeClass(smali, innerClass)
		}
	}

	private fun writeFields(smali: SmaliWriter, classData: IClassData, fields: List<RawField>, colWidths: IntArray) {
		smali.startLine().startLine("# fields")
		val whites = String(ByteArray(maxOf(colWidths[0], colWidths[1]))).replace("\u0000", " ")
		for (fld in fields) {
			smali.startLine()
			var pad = colWidths[0] - fld.accessFlag.length
			if (pad > 0) {
				fld.accessFlag += whites.substring(0, pad)
			}
			smali.add(".field ").add(fld.accessFlag)
			pad = colWidths[1] - fld.name.length
			if (pad > 0) {
				fld.name += whites.substring(0, pad)
			}
			smali.add(fld.name).add(" ")
			smali.add(": ").add(fld.type)
			if (fld.isStatic) {
				val constVal = fld.attributes.get(JadxAttrType.CONSTANT_VALUE)
				if (constVal != null) {
					smali.add(" # init val = ")
					writeEncodedValue(smali, constVal, false)
				}
			}
			val annotationsAttr = fld.attributes.get(JadxAttrType.ANNOTATION_LIST)
			if (annotationsAttr != null) {
				smali.incIndent()
				writeAnnotations(smali, annotationsAttr.list)
				smali.decIndent()
			}
		}
		smali.startLine()
	}

	private fun writeMethod(smali: SmaliWriter, methodNode: MethodNode, mth: IMethodData, line: LineInfo) {
		if (insnDecoder == null) {
			insnDecoder = SmaliInsnDecoder(methodNode)
		}
		smali.startLine().startLine(".method ")
		writeMethodDef(smali, mth, line)
		val codeReader = mth.codeReader
		if (codeReader != null) {
			val regsCount = codeReader.registersCount
			line.smaliMthNode.setParamRegStart(getParamStartRegNum(mth))
			line.smaliMthNode.setRegCount(regsCount)
			val nodes = HashMap<Long, InsnNode>(codeReader.unitsCount / 2)
			line.smaliMthNode.setInsnNodes(nodes, codeReader.unitsCount)
			line.smaliMthNode.initRegInfoList(regsCount, codeReader.unitsCount)

			smali.incIndent()
			smali.startLine(".registers ").add(regsCount.toString())

			writeTries(codeReader, line)
			val debugInfo = codeReader.debugInfo
			val localVars: List<ILocalVar> = debugInfo?.localVars ?: emptyList()
			formatMthParamInfo(mth, smali, line, regsCount, localVars)
			if (debugInfo != null) {
				formatDbgInfo(debugInfo, localVars, line)
			}
			smali.newLine()
			smali.startLine()
			// 第一遍：填充 switch 指令的 payload 偏移
			codeReader.visitInstructions { insn ->
				val opcode = insn.opcode
				if (opcode == Opcode.PACKED_SWITCH || opcode == Opcode.SPARSE_SWITCH) {
					insn.decode()
					line.addPayloadOffset(insn.offset, insn.target)
				}
			}
			codeReader.visitInstructions { insn ->
				val node = decodeInsn(insn, line)
				nodes[insn.offset.toLong()] = node
			}
			line.write(smali)
			insnMap[methodNode.methodInfo.rawFullId] = line.smaliMthNode

			smali.decIndent()
		}
		smali.startLine(".end method")
	}

	private fun writeTries(codeReader: ICodeReader, line: LineInfo) {
		val tries = codeReader.tries
		for (aTry in tries) {
			val end = aTry.endOffset
			val tryEndTip = String.format(FMT_TRY_END_TAG, end)
			val tryStartTip = String.format(FMT_TRY_TAG, aTry.startOffset)
			val tryStartTipExtra = " # :" + tryStartTip.substring(0, tryStartTip.length - 1)

			line.addTip(aTry.startOffset, tryStartTip, " # :" + tryEndTip.substring(0, tryEndTip.length - 1))
			line.addTip(end, tryEndTip, tryStartTipExtra)

			val iCatch: ICatch = aTry.catch
			val addresses = iCatch.handlers
			var addr = 0
			for (i in addresses.indices) {
				addr = addresses[i]
				val catchTip = String.format(FMT_CATCH_TAG, addr)
				line.addTip(addr, catchTip, " # " + iCatch.types[i])
				line.addTip(addr, catchTip, tryStartTipExtra)
				line.addTip(aTry.startOffset, tryStartTip, " # :" + catchTip.substring(0, catchTip.length - 1))
			}
			addr = iCatch.catchAllHandler
			if (addr > -1) {
				val catchAllTip = String.format(FMT_CATCH_ALL_TAG, addr)
				line.addTip(addr, catchAllTip, tryStartTipExtra)
				line.addTip(aTry.startOffset, tryStartTip, " # :" + catchAllTip.substring(0, catchAllTip.length - 1))
			}
		}
	}

	private fun decodeInsn(insn: InsnData, lineInfo: LineInfo): InsnNode {
		insn.decode()
		val node = checkNotNull(insnDecoder).decode(insn)
		formatInsn(insn, node, lineInfo)
		return node
	}

	private fun formatInsn(insn: InsnData, node: InsnNode, line: LineInfo) {
		val lw = line.getLineWriter()
		lw.delete(0, lw.length)
		fmtCols(insn, line)
		if (fmtPayloadInsn(insn, line)) {
			return
		}
		lw.append(formatInsnName(insn)).append(" ")
		fmtRegs(insn, node.type, line)
		if (!tryFormatTargetIns(insn, node.type, line)) {
			if (hasLiteral(insn)) {
				lw.append(", ").append(literal(insn))
			} else if (node.type == InsnType.INVOKE) {
				lw.append(", ").append(method(insn))
			} else if (insn.indexType == InsnIndexType.FIELD_REF) {
				lw.append(", ").append(field(insn))
			} else if (insn.indexType == InsnIndexType.STRING_REF) {
				lw.append(", ").append(str(insn))
			} else if (insn.indexType == InsnIndexType.TYPE_REF) {
				lw.append(", ").append(type(insn))
			} else if (insn.opcode == Opcode.CONST_METHOD_HANDLE) {
				lw.append(", ").append(methodHandle(insn))
			} else if (insn.opcode == Opcode.CONST_METHOD_TYPE) {
				lw.append(", ").append(proto(insn, insn.index))
			}
		}
		line.addInsnLine(insn.offset, lw.toString())
	}

	private fun formatInsnName(insn: InsnData): String {
		if (isJavaBytecode) {
			// 添加 api opcode，因为不使用寄存器
			return String.format(
				"%-" + INSN_COL_WIDTH + "s | %-15s",
				insn.opcodeMnemonic,
				insn.opcode.name.lowercase(Locale.ROOT).replace('_', '-'),
			)
		}
		return String.format(FMT_INSN_COL, insn.opcodeMnemonic)
	}

	private fun tryFormatTargetIns(insn: InsnData, insnType: InsnType, line: LineInfo): Boolean {
		when (insnType) {
			InsnType.IF -> {
				val target = insn.target
				line.addTip(target, String.format(FMT_COND_TAG, target), "")
				line.getLineWriter().append(", ").append(String.format(FMT_COND, target))
				return true
			}

			InsnType.GOTO -> {
				val target = insn.target
				line.addTip(target, String.format(FMT_GOTO_TAG, target), "")
				line.getLineWriter().append(String.format(FMT_GOTO, target))
				return true
			}

			InsnType.FILL_ARRAY -> {
				val target = insn.target
				line.addTip(target, String.format(FMT_DATA_TAG, target), "")
				line.getLineWriter().append(", ").append(String.format(FMT_DATA, target))
				return true
			}

			InsnType.SWITCH -> {
				val target = insn.target
				if (insn.opcode == Opcode.PACKED_SWITCH) {
					line.addTip(target, String.format(FMT_P_SWITCH_TAG, target), "")
					line.getLineWriter().append(", ").append(String.format(FMT_P_SWITCH, target))
				} else {
					line.addTip(target, String.format(FMT_S_SWITCH_TAG, target), "")
					line.getLineWriter().append(", ").append(String.format(FMT_S_SWITCH, target))
				}
				return true
			}

			else -> {}
		}
		return false
	}

	private fun writeMethodDef(smali: SmaliWriter, mth: IMethodData, lineInfo: LineInfo) {
		smali.add(AccessFlags.format(mth.accessFlags, AccessFlagsScope.METHOD))

		val methodRef = mth.methodRef
		methodRef.load()
		lineInfo.smaliMthNode.setDefPos(smali.getLength())
		smali.add(methodRef.name)
			.add('(')
		methodRef.argTypes.forEach(smali::add)
		smali.add(')')
		smali.add(methodRef.returnType)

		val mthAttributes = AttributeStorage.fromList(mth.attributes)
		val annotationsAttr = mthAttributes.get(JadxAttrType.ANNOTATION_LIST)
		if (annotationsAttr != null && !annotationsAttr.isEmpty) {
			smali.incIndent()
			writeAnnotations(smali, annotationsAttr.list)
			smali.decIndent()
			smali.startLine()
		}
	}

	private fun formatMthParamInfo(
		mth: IMethodData,
		smali: SmaliWriter,
		line: LineInfo,
		regsCount: Int,
		localVars: List<ILocalVar>,
	) {
		val types = mth.methodRef.argTypes
		if (types.isEmpty()) {
			return
		}
		var paramStart = 0
		var regNum = line.smaliMthNode.getParamRegStart()
		if (!hasStaticFlag(mth.accessFlags)) {
			// 添加 'this' 寄存器
			line.addRegName(regNum, "p0")
			line.smaliMthNode.setParamReg(regNum, "p0")
			regNum++
			paramStart++
		}
		if (localVars.isEmpty()) {
			return
		}
		val params = arrayOfNulls<ILocalVar>(regsCount)
		for (v in localVars) {
			if (v.isMarkedAsParameter) {
				params[v.regNum] = v
			}
		}
		smali.newLine()
		for (paramType in types) {
			val param = params[regNum]
			if (param != null) {
				val name = Utils.getOrElse(param.name, "")
				val type = Utils.getOrElse(param.signature, paramType)
				val varName = "p$paramStart"
				smali.startLine(".param $varName, \"$name\" # $type")
				line.addRegName(regNum, varName)
				line.smaliMthNode.setParamReg(regNum, varName)
			}
			val regSize = if (isWideType(paramType)) 2 else 1
			regNum += regSize
			paramStart += regSize
		}
	}

	private fun getParamStartRegNum(mth: IMethodData): Int {
		val codeReader = mth.codeReader
		if (codeReader != null) {
			var startNum = codeReader.registersCount
			if (startNum > 0) {
				for (argType in mth.methodRef.argTypes) {
					if (isWideType(argType)) {
						startNum -= 2
					} else {
						startNum -= 1
					}
				}
				if (!hasStaticFlag(mth.accessFlags)) {
					startNum--
				}
				return startNum
			}
		}
		return -1
	}

	private fun isWideType(type: String): Boolean = type == "D" || type == "J"

	private fun writeAnnotations(smali: SmaliWriter, annoList: List<IAnnotation>) {
		if (annoList.isNotEmpty()) {
			for (i in annoList.indices) {
				smali.startLine()
				writeAnnotation(smali, annoList[i])
				if (i != annoList.size - 1) {
					smali.startLine()
				}
			}
		}
	}

	private fun writeAnnotation(smali: SmaliWriter, anno: IAnnotation) {
		smali.add(".annotation")
			.add(" ")
		val vby: AnnotationVisibility? = anno.visibility
		if (vby != null) {
			smali.add(vby.toString().lowercase()).add(" ")
		}
		smali.add(anno.annotationClass)
		anno.values.forEach { (k, v) ->
			smali.incIndent()
			smali.startLine(k).add(" = ")
			writeEncodedValue(smali, v, true)
			smali.decIndent()
		}
		smali.startLine(".end annotation")
	}

	private fun formatDbgInfo(dbgInfo: IDebugInfo, localVars: List<ILocalVar>, line: LineInfo) {
		dbgInfo.sourceLineMapping.forEach { (codeOffset, srcLine) ->
			if (codeOffset > -1) {
				line.addDebugLineTip(codeOffset, String.format(".line %d", srcLine), "")
			}
		}
		for (localVar in localVars) {
			if (localVar.isMarkedAsParameter) {
				continue
			}
			val type = localVar.type
			val sign = localVar.signature
			val longTypeStr: String = if (sign == null || sign.trim().isEmpty()) {
				", \"${localVar.name}\":$type"
			} else {
				", \"${localVar.name}\":$type, \"${localVar.signature}\""
			}
			line.addTip(
				localVar.startOffset,
				".local " + formatVarName(line.smaliMthNode, localVar),
				longTypeStr,
			)
			line.addTip(
				localVar.endOffset,
				".end local " + formatVarName(line.smaliMthNode, localVar),
				" # \"${localVar.name}\":$type",
			)
		}
	}

	private fun formatVarName(smaliMthNode: SmaliMethodNode, localVar: ILocalVar): String {
		val paramRegStart = smaliMthNode.getParamRegStart()
		val regNum = localVar.regNum
		if (regNum < paramRegStart) {
			return "v$regNum"
		}
		return "p" + (regNum - paramRegStart)
	}

	@Suppress("UNCHECKED_CAST")
	private fun writeEncodedValue(smali: SmaliWriter, value: EncodedValue, wrapArray: Boolean) {
		val stringUtils = smali.classNode.root().stringUtils
		when (value.type) {
			EncodedType.ENCODED_ARRAY -> {
				smali.add("{")
				if (wrapArray) {
					smali.incIndent()
					smali.startLine()
				}
				val values = value.value as List<EncodedValue>
				for (i in values.indices) {
					writeEncodedValue(smali, values[i], wrapArray)
					if (i != values.size - 1) {
						smali.add(",")
						if (wrapArray) {
							smali.startLine()
						} else {
							smali.add(" ")
						}
					}
				}
				if (wrapArray) {
					smali.decIndent()
					smali.startLine("}")
				}
			}

			EncodedType.ENCODED_NULL -> smali.add("null")

			EncodedType.ENCODED_ANNOTATION -> writeAnnotation(smali, value.value as IAnnotation)

			EncodedType.ENCODED_BYTE -> smali.add(stringUtils.formatByte((value.value as Byte).toLong(), false))

			EncodedType.ENCODED_SHORT -> smali.add(stringUtils.formatShort((value.value as Short).toLong(), false))

			EncodedType.ENCODED_CHAR -> smali.add(stringUtils.unescapeChar(value.value as Char))

			EncodedType.ENCODED_INT -> smali.add(stringUtils.formatInteger((value.value as Int).toLong(), false))

			EncodedType.ENCODED_LONG -> smali.add(stringUtils.formatLong(value.value as Long, false))

			EncodedType.ENCODED_FLOAT -> smali.add(StringUtils.formatFloat(value.value as Float))

			EncodedType.ENCODED_DOUBLE -> smali.add(StringUtils.formatDouble(value.value as Double))

			EncodedType.ENCODED_STRING -> smali.add(stringUtils.unescapeString(value.value as String))

			EncodedType.ENCODED_TYPE -> smali.add(ArgType.parse(value.value as String).toString() + ".class")

			else -> smali.add(value.value.toString())
		}
	}

	private fun fmtRegs(insn: InsnData, insnType: InsnType, line: LineInfo) {
		val appendBrace = insnType == InsnType.INVOKE || isRegList(insn)
		val lw = line.getLineWriter()
		if (insnType == InsnType.INVOKE) {
			val resultReg = insn.resultReg
			if (resultReg != -1) {
				lw.append(line.getRegName(resultReg)).append(" <= ")
			}
		}
		if (appendBrace) {
			lw.append("{")
		}
		if (isRangeRegIns(insn)) {
			lw.append(line.getRegName(insn.getReg(0)))
				.append(" .. ")
				.append(line.getRegName(insn.getReg(insn.regsCount - 1)))
		} else if (insn.regsCount > 0) {
			for (i in 0 until insn.regsCount) {
				if (i > 0) {
					lw.append(", ")
				}
				lw.append(line.getRegName(insn.getReg(i)))
			}
		}
		if (appendBrace) {
			lw.append("}")
		}
	}

	private val insnColStart: Int get() {
		var start = 0
		if (printFileOffset) {
			start += 8 + 1 + 1 // 加 1 个空格和 1 个 ':'
		}
		if (printBytecode) {
			start += BYTECODE_COLUMN_WIDTH + 1 // 加 1 个空格
		}
		return start
	}

	private fun fmtCols(insn: InsnData, line: LineInfo) {
		if (printFileOffset) {
			line.getLineWriter().append(String.format("$FMT_FILE_OFFSET ", insn.fileOffset))
		}
		if (printBytecode) {
			formatByteCode(line.getLineWriter(), insn.byteCode)
			line.getLineWriter().append(" ")
			line.getLineWriter().append(String.format("$FMT_CODE_OFFSET ", insn.offset))
		}
	}

	private fun formatByteCode(smali: StringBuilder, bytes: ByteArray) {
		val maxLen = minOf(bytes.size, 4 * 2) // 最多 4 个单元
		val inHex = StringBuilder()
		for (i in 0 until maxLen) {
			inHex.append(String.format("%02x", bytes[i]))
			if (i % 2 == 1) {
				inHex.append(' ')
			}
		}
		smali.append(String.format(FMT_BYTECODE_COL, inHex))
		if (maxLen < bytes.size) {
			smali.append("...")
		} else {
			smali.append("   ")
		}
	}

	private fun fmtPayloadInsn(insn: InsnData, line: LineInfo): Boolean {
		val opcode = insn.opcode
		if (opcode == Opcode.PACKED_SWITCH_PAYLOAD) {
			line.getLineWriter().append("packed-switch-payload")
			line.addInsnLine(insn.offset, line.getLineWriter().toString())

			val payload = insn.payload as ISwitchPayload?
			if (payload != null) {
				fmtSwitchPayload(insn, FMT_P_SWITCH_CASE, FMT_P_SWITCH_CASE_TAG, line, payload)
			}
			return true
		}
		if (opcode == Opcode.SPARSE_SWITCH_PAYLOAD) {
			line.getLineWriter().append("sparse-switch-payload")
			line.addInsnLine(insn.offset, line.getLineWriter().toString())

			val payload = insn.payload as ISwitchPayload?
			if (payload != null) {
				fmtSwitchPayload(insn, FMT_S_SWITCH_CASE, FMT_S_SWITCH_CASE_TAG, line, payload)
			}
			return true
		}
		if (opcode == Opcode.FILL_ARRAY_DATA_PAYLOAD) {
			line.getLineWriter().append("fill-array-data-payload")
			line.addInsnLine(insn.offset, line.getLineWriter().toString())
			return true
		}
		return false
	}

	private fun fmtSwitchPayload(insn: InsnData, fmtTarget: String, fmtTag: String, line: LineInfo, payload: ISwitchPayload) {
		var lineStart = insnColStart
		lineStart += CODE_OFFSET_COLUMN_WIDTH + 1 + 1 // 加 1 个空格和 1 个 ':'
		val basicIndent = String(ByteArray(lineStart)).replace("\u0000", " ")
		val indent = JadxArgs.DEFAULT_INDENT_STR + basicIndent
		val keys = payload.keys
		val targets = payload.targets
		val switchOffset = line.payloadOffsetMap[insn.offset]
			?: throw JadxRuntimeException("Unknown switch insn for payload at " + insn.offset)
		for (i in keys.indices) {
			val target = switchOffset + targets[i]
			line.addInsnLine(
				insn.offset,
				String.format("%scase %d: -> $fmtTarget", indent, keys[i], target),
			)
			line.addTip(target, String.format(fmtTag, target), String.format(" # case %d", keys[i]))
		}
		line.addInsnLine(insn.offset, basicIndent + ".end payload")
	}

	private fun literal(insn: InsnData): String {
		val it = insn.literal
		var tip = ""
		if (it > Int.MAX_VALUE) {
			if (isWideIns(insn)) {
				tip = " # double: " + java.lang.Double.longBitsToDouble(it)
			} else if (getOpenCodeByte(insn) == 0x15) { // CONST_HIGH16 = 0x15;
				tip = " # float: " + java.lang.Float.intBitsToFloat(it.toInt())
			}
		} else if (it <= 0) {
			return "" + it + tip
		}
		return "0x" + java.lang.Long.toHexString(it) + tip
	}

	private fun str(insn: InsnData): String = String.format(
		"\"%s\" # string@%04x",
		checkNotNull(insn.indexAsString)
			.replace("\n", "\\n")
			.replace("\t", "\\t"),
		insn.index,
	)

	private fun type(insn: InsnData): String = String.format("%s # type@%04x", insn.indexAsType, insn.index)

	private fun field(insn: InsnData): String = String.format("%s # field@%04x", checkNotNull(insn.indexAsField).toString(), insn.index)

	private fun method(insn: InsnData): String {
		val op = insn.opcode
		if (op == Opcode.INVOKE_CUSTOM || op == Opcode.INVOKE_CUSTOM_RANGE) {
			val callSite = checkNotNull(insn.indexAsCallSite)
			callSite.load()
			return String.format("%s # call_site@%04x", callSite.toString(), insn.index)
		}
		val mthRef = checkNotNull(insn.indexAsMethod)
		mthRef.load()
		if (op == Opcode.INVOKE_POLYMORPHIC || op == Opcode.INVOKE_POLYMORPHIC_RANGE) {
			return String.format(
				"%s, %s # method@%04x, proto@%04x",
				mthRef.toString(),
				checkNotNull(insn.getIndexAsProto(insn.target)).toString(),
				insn.index,
				insn.target,
			)
		}
		return String.format("%s # method@%04x", mthRef.toString(), insn.index)
	}

	private fun proto(insn: InsnData, protoIndex: Int): String = String.format("%s # proto@%04x", checkNotNull(insn.getIndexAsProto(protoIndex)).toString(), protoIndex)

	private fun methodHandle(insn: InsnData): String = String.format(
		"%s # method_handle@%04x",
		checkNotNull(insn.indexAsMethodHandle).toString(),
		insn.index,
	)

	internal fun isRangeRegIns(insn: InsnData): Boolean = when (insn.opcode) {
		Opcode.INVOKE_VIRTUAL_RANGE,
		Opcode.INVOKE_SUPER_RANGE,
		Opcode.INVOKE_DIRECT_RANGE,
		Opcode.INVOKE_STATIC_RANGE,
		Opcode.INVOKE_INTERFACE_RANGE,
		Opcode.FILLED_NEW_ARRAY_RANGE,
		Opcode.INVOKE_CUSTOM_RANGE,
		Opcode.INVOKE_POLYMORPHIC_RANGE,
		-> true

		else -> false
	}

	private fun getOpenCodeByte(insn: InsnData): Int = insn.rawOpcodeUnit and 0xff

	private fun isWideIns(insn: InsnData): Boolean = insn.opcode == Opcode.CONST_WIDE

	private fun hasLiteral(insn: InsnData): Boolean {
		val opcode = getOpenCodeByte(insn)
		return insn.opcode == Opcode.CONST ||
			insn.opcode == Opcode.CONST_WIDE ||
			(opcode >= 0xd0 && opcode <= 0xe2) // add-int/lit16 到 ushr-int/lit8
	}

	private fun isRegList(insn: InsnData): Boolean = insn.opcode == Opcode.FILLED_NEW_ARRAY || insn.opcode == Opcode.FILLED_NEW_ARRAY_RANGE

	private inner class LineInfo {
		internal var smaliMthNode: SmaliMethodNode = SmaliMethodNode()
		private val lineWriter = StringBuilder(50)

		private var lastDebugTip = ""
		private val insnOffsetMap: MutableMap<Int, MutableList<String>> = LinkedHashMap()
		private val regNameMap: MutableMap<Int, String> = HashMap()
		private var tipMap: MutableMap<Int, MutableMap<String, Any>> = mutableMapOf()
		internal var payloadOffsetMap: MutableMap<Int, Int> = mutableMapOf()

		fun getLineWriter(): StringBuilder = lineWriter

		fun reset() {
			lastDebugTip = ""
			payloadOffsetMap = mutableMapOf()
			tipMap = mutableMapOf()
			insnOffsetMap.clear()
			regNameMap.clear()
			smaliMthNode = SmaliMethodNode()
		}

		fun addRegName(regNum: Int, name: String) {
			regNameMap[regNum] = name
		}

		fun getRegName(regNum: Int): String = regNameMap[regNum] ?: "v$regNum"

		fun addInsnLine(codeOffset: Int, insnLine: String) {
			val insnList = insnOffsetMap.computeIfAbsent(codeOffset) { ArrayList(1) }
			insnList.add(insnLine)
		}

		fun addTip(offset: Int, tip: String, extra: String) {
			val innerMap = tipMap.computeIfAbsent(offset) { LinkedHashMap() }
			val obj = innerMap[tip]
			if (obj != null) {
				when (obj) {
					is String -> {
						if (obj.isEmpty()) {
							innerMap[tip] = 2
						} else {
							val extras = ArrayList<String>(2)
							extras.add(obj)
							extras.add(extra)
							innerMap[tip] = extras
						}
					}

					is Int -> innerMap[tip] = obj + 1

					is MutableList<*> -> {
						if (extra.isNotEmpty()) {
							@Suppress("UNCHECKED_CAST")
							(obj as MutableList<String>).add(extra)
						}
					}

					else -> {}
				}
			} else {
				innerMap[tip] = extra
			}
		}

		fun addDebugLineTip(offset: Int, tip: String, extra: String) {
			if (tip == lastDebugTip) {
				return
			}
			lastDebugTip = tip
			val innerMap = tipMap.computeIfAbsent(offset) { LinkedHashMap() }
			innerMap[tip] = extra
		}

		fun addPayloadOffset(curOffset: Int, payloadOffset: Int) {
			payloadOffsetMap[payloadOffset] = curOffset
		}

		fun write(smali: SmaliWriter) {
			val lineOffset = insnColStart
			for ((codeOffset, lines) in insnOffsetMap) {
				writeTip(smali, codeOffset, lineOffset)
				smaliMthNode.setInsnInfo(codeOffset, lineOffset + smali.getLength())
				smaliMthNode.attachLine(smali.getLine(), codeOffset)
				smali.attachSourceLine(codeOffset)
				for (s in lines) {
					smali.add(s).startLine()
				}
			}
		}

		private fun writeTip(smali: SmaliWriter, codeOffset: Int, lineOffset: Int) {
			val tip = tipMap[codeOffset]
			if (tip != null) {
				for ((key, value) in tip) {
					val start = maxOf(0, lineOffset - key.length)
					if (start > 0) {
						smali.add(String(ByteArray(start)).replace("\u0000", " "))
					}
					when (value) {
						is Int -> smali.add(String.format("%s # %d refs", key, value)).startLine()

						is String -> smali.add("$key$value").startLine()

						is List<*> -> {
							@Suppress("UNCHECKED_CAST")
							val extras = value as List<String>
							smali.add("$key${extras[0]}").startLine()
							val pad = String(ByteArray(lineOffset)).replace("\u0000", " ")
							for (i in 1 until extras.size) {
								smali.add("$pad${extras[i]}").startLine()
							}
						}

						else -> smali.add("$key$value").startLine()
					}
				}
			}
		}
	}

	/** 指令解码器：对不支持的 invoke-custom/polymorphic 等指令做降级处理。 */
	private class SmaliInsnDecoder(mthNode: MethodNode) : InsnDecoder(mthNode) {
		public override fun decode(insn: InsnData): InsnNode {
			try {
				return super.decode(insn)
			} catch (e: Exception) {
				return when (insn.opcode) {
					Opcode.INVOKE_CUSTOM,
					Opcode.INVOKE_CUSTOM_RANGE,
					Opcode.INVOKE_POLYMORPHIC,
					Opcode.INVOKE_POLYMORPHIC_RANGE,
					Opcode.CONST_METHOD_HANDLE,
					Opcode.CONST_METHOD_TYPE,
					-> InsnNode(InsnType.INVOKE, insn.regsCount)

					else -> throw RuntimeException(e)
				}
			}
		}

		// 该方法在 smali 场景下不会被调用，返回空数组即可。
		override fun process(codeReader: ICodeReader): Array<InsnNode?> = arrayOfNulls(0)
	}

	/** 字段原始信息（用于对齐列宽）。 */
	private class RawField {
		var isStatic: Boolean = false
		var accessFlag: String = ""
		var name: String = ""
		var type: String = ""
		lateinit var attributes: AttributeStorage

		companion object {
			fun make(f: IFieldData): RawField {
				val field = RawField()
				field.isStatic = hasStaticFlag(f.accessFlags)
				field.accessFlag = AccessFlags.format(f.accessFlags, AccessFlagsScope.FIELD)
				field.name = checkNotNull(f.name)
				field.type = checkNotNull(f.type)
				field.attributes = AttributeStorage.fromList(f.attributes)
				return field
			}
		}
	}
}

/** 判断访问标志中是否包含 STATIC。 */
private fun hasStaticFlag(flag: Int): Boolean = (flag and AccessFlags.STATIC) != 0
