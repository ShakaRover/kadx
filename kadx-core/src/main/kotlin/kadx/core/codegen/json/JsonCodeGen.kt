package kadx.core.codegen.json

import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import kadx.api.ICodeInfo
import kadx.api.ICodeWriter
import kadx.api.KadxArgs
import kadx.api.impl.AnnotatedCodeWriter
import kadx.api.impl.SimpleCodeWriter
import kadx.api.metadata.ICodeMetadata
import kadx.api.metadata.annotations.InsnCodeOffset
import kadx.core.codegen.ClassGen
import kadx.core.codegen.MethodGen
import kadx.core.codegen.json.cls.JsonClass
import kadx.core.codegen.json.cls.JsonCodeLine
import kadx.core.codegen.json.cls.JsonField
import kadx.core.codegen.json.cls.JsonMethod
import kadx.core.codegen.utils.CodeGenUtils
import kadx.core.dex.attributes.AFlag
import kadx.core.dex.info.ClassInfo
import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.FieldNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.nodes.RootNode
import kadx.core.utils.GsonUtils
import kadx.core.utils.Utils
import kadx.core.utils.exceptions.KadxRuntimeException
import java.util.ArrayList
import java.util.regex.Pattern

/**
 * JSON 格式的类代码生成器：把 [ClassNode] 转成结构化 JSON（见 [JsonClass] 及其子节点）。
 *
 * **用途**：`kadx --output-format json` 时使用，便于程序化消费反编译结果。
 *
 * **Kotlin 转换说明**：原 Java 用 `String.split(regex)` 切分行；Kotlin 的 `split` 语义不同，
 * 这里改用 `Pattern.compile(...).split(...)` 以严格保持 Java 的“丢弃尾部空串”行为。
 */
class JsonCodeGen(cls: ClassNode) {

	private val cls: ClassNode = cls
	private val root: RootNode = cls.root()
	private val args: KadxArgs = root.getArgs()

	fun process(): String {
		val jsonCls = processCls(cls, null)
		return GSON.toJson(jsonCls)
	}

	private fun processCls(cls: ClassNode, parentCodeGen: ClassGen?): JsonClass {
		val classGen = if (parentCodeGen == null) {
			ClassGen(cls, args)
		} else {
			ClassGen(cls, parentCodeGen)
		}
		val classInfo = cls.classInfo

		val jsonCls = JsonClass()
		jsonCls.pkg = classInfo.aliasPkg
		jsonCls.dex = cls.inputFileName
		jsonCls.name = classInfo.fullName
		if (classInfo.hasAlias()) {
			jsonCls.alias = classInfo.aliasFullName
		}
		jsonCls.type = getClassTypeStr(cls)
		jsonCls.accessFlags = cls.accessFlags.rawValue()
		val superClass = cls.superClass
		if (superClass != null &&
			superClass != ArgType.OBJECT &&
			!cls.contains(AFlag.REMOVE_SUPER_CLASS)
		) {
			jsonCls.superClass = getTypeAlias(classGen, superClass)
		}
		if (!cls.interfaces.isEmpty()) {
			jsonCls.interfaces = Utils.collectionMap(cls.interfaces) { clsType -> getTypeAlias(classGen, clsType) }
		}

		val cw: ICodeWriter = SimpleCodeWriter(args)
		CodeGenUtils.addErrorsAndComments(cw, cls)
		classGen.addClassDeclaration(cw)
		jsonCls.declaration = cw.codeStr

		addFields(cls, jsonCls, classGen)
		addMethods(cls, jsonCls, classGen)
		addInnerClasses(cls, jsonCls, classGen)

		if (!cls.classInfo.isInner) {
			val imports = Utils.collectionMap(classGen.imports) { it.aliasFullName }
			jsonCls.imports = imports.sorted()
		}
		return jsonCls
	}

	private fun addInnerClasses(cls: ClassNode, jsonCls: JsonClass, classGen: ClassGen) {
		val innerClasses = cls.innerClasses
		if (innerClasses.isEmpty()) {
			return
		}
		val innerList = ArrayList<JsonClass>(innerClasses.size)
		jsonCls.innerClasses = innerList
		for (innerCls in innerClasses) {
			if (innerCls.contains(AFlag.DONT_GENERATE)) {
				continue
			}
			innerList.add(processCls(innerCls, classGen))
		}
	}

	private fun addFields(cls: ClassNode, jsonCls: JsonClass, classGen: ClassGen) {
		val fieldsList = ArrayList<JsonField>()
		jsonCls.fields = fieldsList
		for (field in cls.fields) {
			if (field.contains(AFlag.DONT_GENERATE)) {
				continue
			}
			val jsonField = JsonField()
			jsonField.name = field.name
			if (field.getFieldInfo().hasAlias()) {
				jsonField.alias = field.alias
			}

			val cw: ICodeWriter = SimpleCodeWriter(args)
			classGen.addField(cw, field)
			jsonField.declaration = cw.codeStr
			jsonField.accessFlags = field.accessFlags.rawValue()
			fieldsList.add(jsonField)
		}
	}

	private fun addMethods(cls: ClassNode, jsonCls: JsonClass, classGen: ClassGen) {
		val mthList = ArrayList<JsonMethod>()
		jsonCls.methods = mthList
		for (mth in cls.methods) {
			if (mth.contains(AFlag.DONT_GENERATE)) {
				continue
			}
			val jsonMth = JsonMethod()
			jsonMth.name = mth.name
			if (mth.methodInfo.hasAlias()) {
				jsonMth.alias = mth.alias
			}
			jsonMth.signature = mth.methodInfo.shortId
			jsonMth.returnType = getTypeAlias(classGen, mth.returnType)
			jsonMth.arguments = Utils.collectionMap(mth.methodInfo.argumentsTypes) { clsType -> getTypeAlias(classGen, clsType) }

			val mthGen = MethodGen(classGen, mth)
			val cw: ICodeWriter = AnnotatedCodeWriter(args)
			mthGen.addDefinition(cw)
			jsonMth.declaration = cw.codeStr
			jsonMth.accessFlags = mth.accessFlags.rawValue()
			jsonMth.lines = fillMthCode(mth, mthGen)
			jsonMth.offset = "0x" + java.lang.Long.toHexString(mth.methodCodeOffset)
			mthList.add(jsonMth)
		}
	}

	private fun fillMthCode(mth: MethodNode, mthGen: MethodGen): List<JsonCodeLine> {
		if (mth.isNoCode()) {
			return emptyList()
		}

		val cw: ICodeWriter = mth.root().makeCodeWriter()
		try {
			mthGen.addInstructions(cw)
		} catch (e: Exception) {
			throw KadxRuntimeException("Method generation error", e)
		}
		val code: ICodeInfo = cw.finish()
		val codeStr = code.codeStr
		if (codeStr.isEmpty()) {
			return emptyList()
		}

		val lines = Pattern.compile(args.codeNewLineStr).split(codeStr)
		val metadata: ICodeMetadata = code.codeMetadata
		val lineMapping = metadata.getLineMapping()
		val mthCodeOffset = mth.methodCodeOffset + 16

		val linesCount = lines.size
		val codeLines = ArrayList<JsonCodeLine>(linesCount)
		var lineStartPos = 0
		val newLineLen = args.codeNewLineStr.length
		for (i in 0 until linesCount) {
			val codeLine = lines[i]
			val line = i + 2
			val jsonCodeLine = JsonCodeLine()
			jsonCodeLine.code = codeLine
			jsonCodeLine.sourceLine = lineMapping[line]
			val obj = metadata.getAt(lineStartPos)
			if (obj is InsnCodeOffset) {
				val offset = obj.getOffset()
				jsonCodeLine.offset = "0x" + java.lang.Long.toHexString(mthCodeOffset + offset * 2)
			}
			codeLines.add(jsonCodeLine)
			lineStartPos += codeLine.length + newLineLen
		}
		return codeLines
	}

	private fun getTypeAlias(classGen: ClassGen, clsType: ArgType): String {
		val code: ICodeWriter = SimpleCodeWriter(args)
		classGen.useType(code, clsType)
		return code.codeStr
	}

	private fun getClassTypeStr(cls: ClassNode): String {
		if (cls.isEnum()) {
			return "enum"
		}
		if (cls.accessFlags.isInterface()) {
			return "interface"
		}
		return "class"
	}

	companion object {
		private val GSON: Gson = GsonUtils.defaultGsonBuilder()
			.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES)
			.disableHtmlEscaping()
			.create()
	}
}
