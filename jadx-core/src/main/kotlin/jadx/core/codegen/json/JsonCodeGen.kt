package jadx.core.codegen.json

import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import jadx.api.ICodeInfo
import jadx.api.ICodeWriter
import jadx.api.JadxArgs
import jadx.api.impl.AnnotatedCodeWriter
import jadx.api.impl.SimpleCodeWriter
import jadx.api.metadata.ICodeMetadata
import jadx.api.metadata.annotations.InsnCodeOffset
import jadx.core.codegen.ClassGen
import jadx.core.codegen.MethodGen
import jadx.core.codegen.json.cls.JsonClass
import jadx.core.codegen.json.cls.JsonCodeLine
import jadx.core.codegen.json.cls.JsonField
import jadx.core.codegen.json.cls.JsonMethod
import jadx.core.codegen.utils.CodeGenUtils
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.info.ClassInfo
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.GsonUtils
import jadx.core.utils.Utils
import jadx.core.utils.exceptions.JadxRuntimeException
import java.util.ArrayList
import java.util.regex.Pattern

/**
 * JSON 格式的类代码生成器：把 [ClassNode] 转成结构化 JSON（见 [JsonClass] 及其子节点）。
 *
 * **用途**：`jadx --output-format json` 时使用，便于程序化消费反编译结果。
 *
 * **Kotlin 转换说明**：原 Java 用 `String.split(regex)` 切分行；Kotlin 的 `split` 语义不同，
 * 这里改用 `Pattern.compile(...).split(...)` 以严格保持 Java 的“丢弃尾部空串”行为。
 */
class JsonCodeGen(cls: ClassNode) {

	private val cls: ClassNode = cls
	private val root: RootNode = cls.root()
	private val args: JadxArgs = root.getArgs()

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
		jsonCls.declaration = cw.getCodeStr()

		addFields(cls, jsonCls, classGen)
		addMethods(cls, jsonCls, classGen)
		addInnerClasses(cls, jsonCls, classGen)

		if (!cls.classInfo.isInner) {
			val imports = Utils.collectionMap(classGen.getImports()) { it.aliasFullName }
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
			jsonField.name = field.getName()
			if (field.getFieldInfo().hasAlias()) {
				jsonField.alias = field.getAlias()
			}

			val cw: ICodeWriter = SimpleCodeWriter(args)
			classGen.addField(cw, field)
			jsonField.declaration = cw.getCodeStr()
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
			jsonMth.name = mth.getName()
			if (mth.getMethodInfo().hasAlias()) {
				jsonMth.alias = mth.getAlias()
			}
			jsonMth.signature = mth.getMethodInfo().shortId
			jsonMth.returnType = getTypeAlias(classGen, mth.getReturnType())
			jsonMth.arguments = Utils.collectionMap(mth.getMethodInfo().argumentsTypes) { clsType -> getTypeAlias(classGen, clsType) }

			val mthGen = MethodGen(classGen, mth)
			val cw: ICodeWriter = AnnotatedCodeWriter(args)
			mthGen.addDefinition(cw)
			jsonMth.declaration = cw.getCodeStr()
			jsonMth.accessFlags = mth.accessFlags.rawValue()
			jsonMth.lines = fillMthCode(mth, mthGen)
			jsonMth.offset = "0x" + java.lang.Long.toHexString(mth.getMethodCodeOffset())
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
			throw JadxRuntimeException("Method generation error", e)
		}
		val code: ICodeInfo = cw.finish()
		val codeStr = code.getCodeStr()
		if (codeStr.isEmpty()) {
			return emptyList()
		}

		val lines = Pattern.compile(args.getCodeNewLineStr()).split(codeStr)
		val metadata: ICodeMetadata = code.getCodeMetadata()
		val lineMapping = metadata.getLineMapping()
		val mthCodeOffset = mth.getMethodCodeOffset() + 16

		val linesCount = lines.size
		val codeLines = ArrayList<JsonCodeLine>(linesCount)
		var lineStartPos = 0
		val newLineLen = args.getCodeNewLineStr().length
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
		return code.getCodeStr()
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
