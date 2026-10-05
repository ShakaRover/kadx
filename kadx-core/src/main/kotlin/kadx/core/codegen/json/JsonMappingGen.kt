package kadx.core.codegen.json

import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import kadx.api.KadxArgs
import kadx.core.codegen.json.mapping.JsonClsMapping
import kadx.core.codegen.json.mapping.JsonFieldMapping
import kadx.core.codegen.json.mapping.JsonMapping
import kadx.core.codegen.json.mapping.JsonMthMapping
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.FieldNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.nodes.RootNode
import kadx.core.utils.GsonUtils
import kadx.core.utils.exceptions.KadxRuntimeException
import kadx.core.utils.files.FileUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileWriter
import java.io.Writer
import java.util.ArrayList

/**
 * 生成 `mapping.json`：把类 / 字段 / 方法的原名与别名映射导出为 JSON，供重命名工具或人工参考。
 *
 * **Kotlin 转换说明**：静态方法放入 `companion object`；
 * `try-with-resources` 用 Kotlin 的 `use { }` 改写（语义相同，必定关闭 writer）。
 */
class JsonMappingGen private constructor() {

	companion object {
		private val LOG = LoggerFactory.getLogger(JsonMappingGen::class.java)

		private val GSON: Gson = GsonUtils.defaultGsonBuilder()
			.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES)
			.disableHtmlEscaping()
			.create()

		fun dump(root: RootNode) {
			val mapping = JsonMapping()
			fillMapping(mapping, root)

			val args: KadxArgs = root.getArgs()
			val outDirSrc = checkNotNull(args.outDirSrc).getAbsoluteFile()
			val mappingFile = File(outDirSrc, "mapping.json")
			FileUtils.makeDirsForFile(mappingFile)
			try {
				FileWriter(mappingFile).use { writer: Writer ->
					GSON.toJson(mapping, writer)
					LOG.info("Save mappings to {}", mappingFile.getAbsolutePath())
				}
			} catch (e: Exception) {
				throw KadxRuntimeException("Failed to save mapping json", e)
			}
		}

		private fun fillMapping(mapping: JsonMapping, root: RootNode) {
			val classes = root.getClasses(true)
			val clsMappings = ArrayList<JsonClsMapping>(classes.size)
			mapping.classes = clsMappings
			for (cls in classes) {
				val classInfo = cls.classInfo
				val jsonCls = JsonClsMapping()
				jsonCls.name = classInfo.rawName
				jsonCls.alias = classInfo.aliasFullName
				jsonCls.setInner(classInfo.isInner)
				jsonCls.json = cls.topParentClass.classInfo.aliasFullPath + ".json"
				if (classInfo.isInner) {
					jsonCls.topClass = cls.topParentClass.classInfo.fullName
				}
				addFields(cls, jsonCls)
				addMethods(cls, jsonCls)
				clsMappings.add(jsonCls)
			}
		}

		private fun addMethods(cls: ClassNode, jsonCls: JsonClsMapping) {
			val methods = cls.methods
			if (methods.isEmpty()) {
				return
			}
			val mthMappings = ArrayList<JsonMthMapping>(methods.size)
			jsonCls.methods = mthMappings
			for (method in methods) {
				val jsonMethod = JsonMthMapping()
				val methodInfo = method.methodInfo
				jsonMethod.signature = methodInfo.shortId
				jsonMethod.name = methodInfo.name
				jsonMethod.alias = methodInfo.alias
				jsonMethod.offset = "0x" + java.lang.Long.toHexString(method.methodCodeOffset)
				mthMappings.add(jsonMethod)
			}
		}

		private fun addFields(cls: ClassNode, jsonCls: JsonClsMapping) {
			val fields = cls.fields
			if (fields.isEmpty()) {
				return
			}
			val fldMappings = ArrayList<JsonFieldMapping>(fields.size)
			jsonCls.fields = fldMappings
			for (field in fields) {
				val jsonField = JsonFieldMapping()
				jsonField.name = field.name
				jsonField.alias = field.alias
				fldMappings.add(jsonField)
			}
		}
	}
}
